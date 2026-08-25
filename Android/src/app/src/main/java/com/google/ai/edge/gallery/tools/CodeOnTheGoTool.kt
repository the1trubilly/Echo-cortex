/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.tools

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Native bridge that lets Jarvis operate Code on the Go through verified self-ADB. */
class CodeOnTheGoTool internal constructor(
  private val currentPackageName: String,
  private val codeOnTheGoEnvironment: CodeOnTheGoEnvironment,
  private val terminalEnvironment: TermuxEnvironment,
  private val bridge: CodeOnTheGoBridge,
  private val selfAdbConnectionProvider: SelfAdbConnectionProvider,
  private val approvalModeStore: TerminalApprovalModeStore,
  private val developmentSessionStore: JarvisDevelopmentSessionStore,
) : ToolDefinition {
  constructor(
    context: Context
  ) : this(
    currentPackageName = context.packageName,
    codeOnTheGoEnvironment = AndroidCodeOnTheGoEnvironment(context),
    terminalEnvironment = AndroidTermuxEnvironment(context),
    bridge = AndroidCodeOnTheGoBridge(AndroidTermuxCommandRunner(context)),
    selfAdbConnectionProvider =
      AndroidSelfAdbConnectionProvider(context, AndroidTermuxCommandRunner(context)),
    approvalModeStore = AndroidTerminalApprovalModeStore(context),
    developmentSessionStore = AndroidJarvisDevelopmentSessionStore(context),
  )

  override val alwaysAllow: Boolean = false
  override var executionContext: ToolExecutionContext? = null

  @Tool(
    description =
      "Report whether Code on the Go and its Jarvis project bridge are available. This performs " +
        "no build, edit, install, or terminal command."
  )
  fun codeOnTheGoStatus(): Map<String, String> {
    val ide = codeOnTheGoEnvironment.status()
    val terminal = terminalEnvironment.status()
    val counterpart = jarvisCounterpartFor(currentPackageName)
    return linkedMapOf(
      "status" to if (ide.installed && terminal.installed) "available" else "setup_required",
      "code_on_the_go_installed" to ide.installed.toString(),
      "code_on_the_go_version" to ide.versionName,
      "termux_installed" to terminal.installed.toString(),
      "termux_run_command_permission" to
        if (terminal.runCommandPermissionGranted) "granted" else "not_granted",
      "approval_mode" to approvalModeStore.getMode().name.lowercase(),
      "project_root" to CODE_ON_THE_GO_PROJECT_ROOT,
      "current_jarvis_package" to currentPackageName,
      "counterpart_package" to counterpart?.packageName.orEmpty(),
      "development_session" to
        developmentSessionStore.read()?.id?.let { "active:$it" }.orEmpty().ifBlank { "none" },
      "bridge" to
        "verified self-ADB opens Code on the Go, injects one shared-storage script, and reads a " +
          "bounded result file",
    )
  }

  @Tool(
    description =
      "Begin an auditable Android Jarvis development session in Code on the Go. Call this before " +
        "editing. It records the current branch and commit and refuses to start when the phone " +
        "checkout already has changes, so existing work is never silently mixed in or overwritten."
  )
  fun beginJarvisDevelopment(
    @ToolParam(description = "A concise description of the user-visible capability to implement.")
    goal: String
  ): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      val normalizedGoal = goal.trim()
      if (normalizedGoal.length !in 3..MAX_GOAL_CHARS) {
        return@runBlocking errorResult("Development goal must be 3-$MAX_GOAL_CHARS characters.")
      }
      val target =
        jarvisCounterpartFor(currentPackageName)
          ?: return@runBlocking errorResult("This is not a recognized Main or Alpha build.")
      val result =
        executeCodeCommand(
          displayName = "Start Jarvis development",
          approvalText = "Inspect the phone checkout and start: $normalizedGoal",
          command = DEVELOPMENT_STATUS_COMMAND,
          forceApproval = false,
          knownReadOnly = true,
          progressLabel = "Checking Jarvis source",
        )
      if (!result.succeeded) return@runBlocking result.toToolResult()
      val branch = result.output.markerValue(BRANCH_MARKER)
      val head = result.output.markerValue(HEAD_MARKER)
      val status = result.output.substringAfter(STATUS_MARKER, "").trimEnd('\r', '\n')
      if (!head.matches(GIT_COMMIT)) {
        return@runBlocking errorResult("Code on the Go did not return a valid Git commit.")
      }
      if (status.isNotBlank()) {
        return@runBlocking linkedMapOf(
          "status" to "blocked_dirty_checkout",
          "message" to
            "The phone checkout already has changes. Jarvis did not edit or discard them.",
          "git_status" to status,
          "branch" to branch,
          "head" to head,
        )
      }
      val session =
        JarvisDevelopmentSession(
          id = UUID.randomUUID().toString().replace("-", "").take(12),
          goal = normalizedGoal,
          branch = branch,
          baselineCommit = head,
          startedAtEpochMs = System.currentTimeMillis(),
          counterpartPackage = target.packageName,
        )
      developmentSessionStore.write(session)
      linkedMapOf(
        "status" to "ready",
        "session_id" to session.id,
        "goal" to session.goal,
        "branch" to session.branch,
        "baseline_commit" to session.baselineCommit,
        "counterpart" to target.displayName,
        "next" to "Inspect the relevant source, then apply a validated unified diff.",
      )
    }

  @Tool(
    description =
      "Read a bounded line range from one file in the private Android Jarvis repository. Paths are " +
        "repository-relative; build output, Git internals, secrets, and parent traversal are refused."
  )
  fun readJarvisSource(
    @ToolParam(description = "Repository-relative file path, such as app/src/main/.../File.kt.")
    path: String,
    @ToolParam(description = "First one-based line number.") startLine: Int,
    @ToolParam(description = "Number of lines to return, from 1 through 400.") lineCount: Int,
  ): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      val safePath = validateRepositoryPath(path, requireFile = true)
        ?: return@runBlocking errorResult("Unsafe or unsupported repository path.")
      if (startLine < 1 || lineCount !in 1..MAX_READ_LINES) {
        return@runBlocking errorResult("Use a positive start line and 1-$MAX_READ_LINES lines.")
      }
      val endLine = startLine + lineCount - 1
      val command =
        "if [ -f ${safePath.shellSingleQuote()} ]; then " +
          "nl -ba ${safePath.shellSingleQuote()} | sed -n '${startLine},${endLine}p'; " +
          "else printf 'FILE_NOT_FOUND\\n' >&2; exit 66; fi"
      executeCodeCommand(
          displayName = "Read Jarvis source",
          approvalText = "Read $safePath lines $startLine-$endLine",
          command = command,
          forceApproval = false,
          knownReadOnly = true,
          progressLabel = "Reading Jarvis source",
        )
        .toToolResult() + mapOf("path" to safePath)
    }

  @Tool(
    description =
      "Search the Android Jarvis repository for an exact text fragment. Results are bounded and " +
        "exclude generated build output. Use this instead of guessing filenames."
  )
  fun searchJarvisSource(
    @ToolParam(description = "Exact text fragment to find; regular expressions are not used.")
    query: String,
    @ToolParam(description = "Repository-relative directory or file, or '.' for the whole project.")
    path: String,
  ): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      val normalizedQuery = query.trim()
      if (normalizedQuery.isBlank() || normalizedQuery.length > MAX_SEARCH_CHARS) {
        return@runBlocking errorResult("Search text must be 1-$MAX_SEARCH_CHARS characters.")
      }
      val safePath = validateRepositoryPath(path.ifBlank { "." }, requireFile = false)
        ?: return@runBlocking errorResult("Unsafe or unsupported repository path.")
      val encoded =
        Base64.getEncoder().encodeToString(normalizedQuery.toByteArray(StandardCharsets.UTF_8))
      val command =
        "jarvis_query=\"\$(printf '%s' ${encoded.shellSingleQuote()} | base64 -d)\"; " +
          "rg -n --fixed-strings --glob '!**/build/**' -- \"\$jarvis_query\" " +
          "${safePath.shellSingleQuote()} | head -n $MAX_SEARCH_RESULTS"
      executeCodeCommand(
          displayName = "Search Jarvis source",
          approvalText = "Search $safePath for: $normalizedQuery",
          command = command,
          forceApproval = false,
          knownReadOnly = true,
          progressLabel = "Searching Jarvis source",
        )
        .toToolResult() + mapOf("path" to safePath, "query" to normalizedQuery)
    }

  @Tool(
    description =
      "List a bounded portion of the Android Jarvis repository tree. Generated build output and " +
        "Git internals are excluded."
  )
  fun listJarvisSource(
    @ToolParam(description = "Repository-relative directory, or '.' for the project root.")
    path: String
  ): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      val safePath = validateRepositoryPath(path.ifBlank { "." }, requireFile = false)
        ?: return@runBlocking errorResult("Unsafe or unsupported repository path.")
      val command =
        "if [ -d ${safePath.shellSingleQuote()} ]; then " +
          "find ${safePath.shellSingleQuote()} -maxdepth 3 " +
          "-not -path '*/build/*' -not -path '*/.git/*' -print | sort | head -n 300; " +
          "else printf 'DIRECTORY_NOT_FOUND\\n' >&2; exit 66; fi"
      executeCodeCommand(
          displayName = "List Jarvis source",
          approvalText = "List $safePath",
          command = command,
          forceApproval = false,
          knownReadOnly = true,
          progressLabel = "Listing Jarvis source",
        )
        .toToolResult() + mapOf("path" to safePath)
    }

  @Tool(
    description =
      "Apply one validated unified Git diff to the private Jarvis repository. A development session " +
        "must already be active. The patch is checked before application; absolute/parent paths, " +
        "Git internals, generated output, credentials, symlinks, submodules, and binary patches are " +
        "refused. This always shows the user an exact one-time approval."
  )
  fun applyJarvisPatch(
    @ToolParam(description = "Concise explanation of the intended user-visible change.")
    summary: String,
    @ToolParam(description = "A standard unified diff beginning with diff --git a/... b/....")
    patch: String,
  ): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      val session =
        developmentSessionStore.read()
          ?: return@runBlocking errorResult("Begin a Jarvis development session before editing.")
      val normalizedSummary = summary.trim()
      if (normalizedSummary.length !in 3..MAX_SUMMARY_CHARS) {
        return@runBlocking errorResult("Patch summary must be 3-$MAX_SUMMARY_CHARS characters.")
      }
      val validation = validateUnifiedPatch(patch)
      if (!validation.accepted) return@runBlocking errorResult(validation.message)
      val encoded =
        Base64.getEncoder().encodeToString(patch.toByteArray(StandardCharsets.UTF_8))
      val patchPath = "\$PREFIX/tmp/jarvis-${session.id}.diff"
      val command =
        "mkdir -p \$PREFIX/tmp; trap 'rm -f ${patchPath}' EXIT; " +
          "printf '%s' ${encoded.shellSingleQuote()} | base64 -d > ${patchPath}; " +
          "git apply --check ${patchPath} && git apply ${patchPath} && git diff --check && " +
          "git status --short"
      val approvalText =
        buildString {
          append("Apply Jarvis source patch: ").append(normalizedSummary)
          append("\nFiles: ").append(validation.paths.joinToString(", "))
          append("\n\n").append(patch)
        }
      val result =
        executeCodeCommand(
          displayName = "Edit Android Jarvis",
          approvalText = approvalText,
          command = command,
          forceApproval = true,
          knownReadOnly = false,
          progressLabel = "Applying Jarvis patch",
        )
      if (result.succeeded) {
        developmentSessionStore.write(
          session.copy(changedPaths = (session.changedPaths + validation.paths).distinct().sorted())
        )
      }
      result.toToolResult() +
        mapOf(
          "session_id" to session.id,
          "summary" to normalizedSummary,
          "files" to validation.paths.joinToString(", "),
        )
    }

  @Tool(
    description =
      "Run one command in Code on the Go's private Jarvis repository terminal and return its " +
        "exit code and output. Use this for inspecting or editing the Android Jarvis source and " +
        "for Git or Gradle work that needs Code on the Go's embedded Android toolchain. The " +
        "existing command-approval mode is enforced. State-changing and unknown commands require " +
        "one-time approval. Do not include credentials."
  )
  fun runInCodeOnTheGo(
    @ToolParam(
      description =
        "The exact Bash command to run from Echo-cortex/Android/src inside Code on the Go."
    )
    command: String
  ): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      if (command.trim().length > MAX_COMMAND_CHARS) {
        return@runBlocking errorResult("Command is longer than $MAX_COMMAND_CHARS characters.")
      }
      val prepared =
        prepareOperation(
          displayName = "Code on the Go",
          approvalText = command,
          forceApproval = false,
        )
      prepared.error.takeIf(String::isNotBlank)?.let { return@runBlocking errorResult(it) }
      val actionChannel = prepared.actionChannel ?: return@runBlocking errorResult(MISSING_CHAT)
      actionChannel.send(
        SkillProgressToolAction(
          label = "Running Code on the Go",
          inProgress = true,
          addItemTitle = "Code on the Go command",
          addItemDescription = command,
        )
      )
      val result =
        try {
          bridge.execute(
            command = command,
            serial = prepared.serial,
            returnPackageName = currentPackageName,
            timeoutMs = CODE_COMMAND_TIMEOUT_MS,
          )
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          CodeOnTheGoCommandResult(
            exitCode = null,
            output = "",
            error = error.message ?: "Code on the Go bridge failed.",
          )
        }
      actionChannel.send(
        SkillProgressToolAction(
          label = if (result.succeeded) "Code on the Go finished" else "Code on the Go failed",
          inProgress = false,
          addItemTitle = "Code on the Go result",
          addItemDescription = result.toProgressDescription(),
        )
      )
      result.toToolResult()
    }

  @Tool(
    description =
      "Make the other Android Jarvis app match this one using Code on the Go. Use this high-level " +
        "operation when the user says things like 'update Main to match you', 'put this version on " +
        "Main', 'take your APK and install it over Main', 'sync the other Jarvis', or the equivalent " +
        "request for Alpha. Alpha builds and updates Main; Main builds and updates Alpha. Android " +
        "cannot literally install one package's APK over the differently named package, so this " +
        "tool builds the matching counterpart from the same source, signs it, and performs an " +
        "in-place update that preserves the other app's data. The current Jarvis app is never " +
        "overwritten by this operation. One approval covers the hidden build and install steps."
  )
  fun updateOtherJarvisToMatchThisOne(): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      val target =
        jarvisCounterpartFor(currentPackageName)
          ?: return@runBlocking errorResult(
            "This Jarvis package is not a recognized Main or Alpha build."
          )
      val buildCommand =
        "./gradlew ${target.gradleTask} && cp ${target.apkPath.shellSingleQuote()} " +
          target.exportedApkPath.shellSingleQuote()
      val approvalText =
        "$buildCommand && adb install -r -t ${target.exportedApkPath} over ${target.packageName}"
      val prepared =
        prepareOperation(
          displayName = "Build and install ${target.displayName}",
          approvalText = approvalText,
          forceApproval = true,
        )
      prepared.error.takeIf(String::isNotBlank)?.let { return@runBlocking errorResult(it) }
      val actionChannel = prepared.actionChannel ?: return@runBlocking errorResult(MISSING_CHAT)
      actionChannel.send(
        SkillProgressToolAction(
          label = "Building ${target.displayName}",
          inProgress = true,
          addItemTitle = "Code on the Go build",
          addItemDescription = buildCommand,
        )
      )
      val buildResult =
        try {
          bridge.execute(
            command = buildCommand,
            serial = prepared.serial,
            returnPackageName = currentPackageName,
            timeoutMs = BUILD_TIMEOUT_MS,
          )
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          CodeOnTheGoCommandResult(
            exitCode = null,
            output = "",
            error = error.message ?: "Code on the Go build bridge failed.",
          )
        }
      if (!buildResult.succeeded) {
        actionChannel.send(
          SkillProgressToolAction(
            label = "${target.displayName} build failed",
            inProgress = false,
            addItemTitle = "Code on the Go build result",
            addItemDescription = buildResult.toProgressDescription(),
          )
        )
        return@runBlocking buildResult.toToolResult() +
          mapOf("target" to target.displayName, "target_package" to target.packageName)
      }

      actionChannel.send(
        SkillProgressToolAction(
          label = "Installing ${target.displayName}",
          inProgress = true,
          addItemTitle = "ADB in-place update",
          addItemDescription = target.packageName,
        )
      )
      val installResult =
        try {
          bridge.install(
            apkPath = target.exportedApkPath,
            targetPackageName = target.packageName,
            serial = prepared.serial,
          )
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          CodeOnTheGoInstallResult(
            succeeded = false,
            output = "",
            error = error.message ?: "ADB installation failed.",
          )
        }
      actionChannel.send(
        SkillProgressToolAction(
          label =
            if (installResult.succeeded) {
              "${target.displayName} installed"
            } else {
              "${target.displayName} install failed"
            },
          inProgress = false,
          addItemTitle = "Counterpart update result",
          addItemDescription = installResult.toProgressDescription(),
        )
      )
      linkedMapOf(
        "status" to if (installResult.succeeded) "succeeded" else "failed",
        "target" to target.displayName,
        "target_package" to target.packageName,
        "apk_path" to target.exportedApkPath,
        "build_exit_code" to (buildResult.exitCode?.toString() ?: "unavailable"),
        "build_output" to buildResult.output,
        "install_output" to installResult.output,
        "error" to installResult.error,
      )
    }

  @Tool(
    description =
      "Complete the active Jarvis development cycle: verify that only reviewed patch paths changed, " +
        "run unit tests, build the other Main/Alpha app, preserve its current APK for rollback, " +
        "install without clearing data, launch its Agent Chat with a real prompt, check process and " +
        "Logcat state, capture a screenshot, and commit only the reviewed paths after every check " +
        "passes. A failed device test automatically restores the previous counterpart APK. The " +
        "screenshot is shown to the user and supplied to the model as visual evidence."
  )
  fun verifyUpdateAndPromptTestOtherJarvis(
    @ToolParam(description = "Natural test prompt to send to the freshly installed counterpart.")
    prompt: String,
    @ToolParam(
      description =
        "Short exact response text to wait for. It must not occur in the prompt; ask the counterpart " +
          "to transform a nonce so the expected output is independently visible."
    )
    expectedText: String,
    @ToolParam(description = "Focused Git commit message, 3-72 characters with no newline.")
    commitMessage: String,
  ): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      val session =
        developmentSessionStore.read()
          ?: return@runBlocking errorResult("Begin a Jarvis development session before verification.")
      val target =
        jarvisCounterpartFor(currentPackageName)
          ?: return@runBlocking errorResult("This is not a recognized Main or Alpha build.")
      if (session.counterpartPackage != target.packageName || session.changedPaths.isEmpty()) {
        return@runBlocking errorResult("The active session has no reviewed changes for this counterpart.")
      }
      val normalizedPrompt = prompt.trim()
      val normalizedExpected = expectedText.trim()
      val normalizedCommitMessage = commitMessage.trim()
      if (normalizedPrompt.length !in 3..MAX_PROMPT_TEST_CHARS) {
        return@runBlocking errorResult("Prompt test must be 3-$MAX_PROMPT_TEST_CHARS characters.")
      }
      if (
        normalizedExpected.length !in 2..MAX_EXPECTED_TEXT_CHARS ||
          normalizedPrompt.contains(normalizedExpected)
      ) {
        return@runBlocking errorResult(
          "Expected text must be 2-$MAX_EXPECTED_TEXT_CHARS characters and absent from the prompt."
        )
      }
      if (
        normalizedCommitMessage.length !in 3..MAX_COMMIT_MESSAGE_CHARS ||
          normalizedCommitMessage.contains('\n') ||
          normalizedCommitMessage.contains('\r')
      ) {
        return@runBlocking errorResult("Commit message must be 3-$MAX_COMMIT_MESSAGE_CHARS characters on one line.")
      }
      val buildCommand =
        "git diff --check && ./gradlew testAlphaUnitTest ${target.gradleTask} && " +
          "cp ${target.apkPath.shellSingleQuote()} ${target.exportedApkPath.shellSingleQuote()}"
      val approvalText =
        "Verify only ${session.changedPaths.joinToString(", ")}; build and update " +
          "${target.displayName}; run the visible prompt test; capture a screenshot; checkpoint as: " +
          normalizedCommitMessage
      val prepared =
        prepareOperation(
          displayName = "Test and update ${target.displayName}",
          approvalText = approvalText,
          forceApproval = true,
        )
      prepared.error.takeIf(String::isNotBlank)?.let { return@runBlocking errorResult(it) }
      val actionChannel = prepared.actionChannel ?: return@runBlocking errorResult(MISSING_CHAT)

      suspend fun progress(label: String, inProgress: Boolean, title: String, detail: String) {
        actionChannel.send(
          SkillProgressToolAction(
            label = label,
            inProgress = inProgress,
            addItemTitle = title,
            addItemDescription = detail.take(MAX_PROGRESS_DESCRIPTION_CHARS),
          )
        )
      }

      val checkoutState =
        bridge.execute(
          command = DEVELOPMENT_STATUS_COMMAND,
          serial = prepared.serial,
          returnPackageName = currentPackageName,
          timeoutMs = CODE_COMMAND_TIMEOUT_MS,
        )
      if (!checkoutState.succeeded) return@runBlocking checkoutState.toToolResult()
      val currentHead = checkoutState.output.markerValue(HEAD_MARKER)
      val statusText = checkoutState.output.substringAfter(STATUS_MARKER, "").trimEnd('\r', '\n')
      val changedPaths = parseGitStatusPaths(statusText)
      val unexpectedPaths = changedPaths - session.changedPaths.toSet()
      if (currentHead != session.baselineCommit || changedPaths.isEmpty() || unexpectedPaths.isNotEmpty()) {
        return@runBlocking linkedMapOf(
          "status" to "blocked_unreviewed_changes",
          "message" to "Verification stopped before building; the checkout no longer matches the reviewed session.",
          "expected_head" to session.baselineCommit,
          "actual_head" to currentHead,
          "reviewed_paths" to session.changedPaths.joinToString(", "),
          "changed_paths" to changedPaths.joinToString(", "),
          "unexpected_paths" to unexpectedPaths.joinToString(", "),
        )
      }

      progress("Building ${target.displayName}", true, "Verified build", buildCommand)
      val buildResult =
        bridge.execute(
          command = buildCommand,
          serial = prepared.serial,
          returnPackageName = currentPackageName,
          timeoutMs = BUILD_TIMEOUT_MS,
        )
      if (!buildResult.succeeded) {
        progress("${target.displayName} build failed", false, "Build result", buildResult.toProgressDescription())
        return@runBlocking buildResult.toToolResult() +
          mapOf("target" to target.displayName, "session_id" to session.id, "phase" to "build")
      }

      progress("Installing ${target.displayName}", true, "Rollback-protected install", target.packageName)
      val installResult = bridge.install(target.exportedApkPath, target.packageName, prepared.serial)
      if (!installResult.succeeded) {
        progress("${target.displayName} install failed", false, "Install result", installResult.toProgressDescription())
        return@runBlocking linkedMapOf(
          "status" to "failed",
          "phase" to "install",
          "target" to target.displayName,
          "session_id" to session.id,
          "build_output" to buildResult.output,
          "install_output" to installResult.output,
          "error" to installResult.error,
        )
      }

      progress("Testing ${target.displayName}", true, "Prompt test", normalizedPrompt)
      val promptTest =
        bridge.testCounterpart(
          targetPackageName = target.packageName,
          deepLinkScheme = deepLinkSchemeFor(target.packageName),
          prompt = normalizedPrompt,
          expectedText = normalizedExpected,
          serial = prepared.serial,
          returnPackageName = currentPackageName,
          timeoutMs = PROMPT_TEST_TIMEOUT_MS,
        )
      if (promptTest.screenshotBase64.isNotBlank()) {
        actionChannel.send(
          PublishToolImageAction(
            base64 = promptTest.screenshotBase64,
            caption = "${target.displayName} prompt-test screen · ${promptTest.screenshotPath}",
          )
        )
      }
      if (!promptTest.succeeded) {
        progress("Prompt test failed; restoring counterpart", true, "Automatic rollback", installResult.backupApkPath)
        val rollback =
          bridge.restoreBackup(installResult.backupApkPath, target.packageName, prepared.serial)
        progress(
          if (rollback.succeeded) "Previous ${target.displayName} restored" else "Counterpart rollback failed",
          false,
          "Prompt test result",
          promptTest.error.ifBlank { "Expected response was not visibly confirmed." },
        )
        return@runBlocking promptTest.toToolResult(
          status = if (rollback.succeeded) "failed_rolled_back" else "failed_rollback_failed",
          target = target,
          sessionId = session.id,
          rollback = rollback,
        )
      }

      progress("Saving verified Jarvis change", true, "Git checkpoint", normalizedCommitMessage)
      val encodedCommitMessage =
        Base64.getEncoder().encodeToString(normalizedCommitMessage.toByteArray(StandardCharsets.UTF_8))
      val reviewedPathArguments = session.changedPaths.joinToString(" ") { it.shellSingleQuote() }
      val commitCommand =
        "git add -- $reviewedPathArguments && git diff --cached --check && " +
          "{ git config user.name >/dev/null 2>&1 || git config user.name 'Android Jarvis'; } && " +
          "{ git config user.email >/dev/null 2>&1 || git config user.email 'jarvis@local.invalid'; } && " +
          "jarvis_message=\"\$(printf '%s' ${encodedCommitMessage.shellSingleQuote()} | base64 -d)\" && " +
          "git commit -m \"\$jarvis_message\" -- $reviewedPathArguments && " +
          "printf '${VERIFIED_COMMIT_MARKER}%s\\n' \"\$(git rev-parse HEAD)\""
      val commitResult =
        bridge.execute(
          command = commitCommand,
          serial = prepared.serial,
          returnPackageName = currentPackageName,
          timeoutMs = CODE_COMMAND_TIMEOUT_MS,
        )
      val verifiedCommit = commitResult.output.markerValue(VERIFIED_COMMIT_MARKER)
      if (!commitResult.succeeded || !verifiedCommit.matches(GIT_COMMIT)) {
        progress("Visible test passed; Git checkpoint failed", false, "Git result", commitResult.toProgressDescription())
        return@runBlocking promptTest.toToolResult(
          status = "verified_uncommitted",
          target = target,
          sessionId = session.id,
          rollback = null,
          extraError = commitResult.error.ifBlank { "Git checkpoint did not complete." },
        )
      }
      developmentSessionStore.write(
        session.copy(verifiedCommit = verifiedCommit, backupApkPath = installResult.backupApkPath)
      )
      progress(
        "${target.displayName} verified and saved",
        false,
        "Development checkpoint",
        "$verifiedCommit\n${promptTest.screenshotPath}",
      )
      promptTest.toToolResult(
        status = "succeeded",
        target = target,
        sessionId = session.id,
        rollback = null,
      ) +
        mapOf(
          "verified_commit" to verifiedCommit,
          "backup_apk" to installResult.backupApkPath,
          "build_output" to buildResult.output,
          "install_output" to installResult.output,
        )
    }

  @Tool(
    description =
      "Roll back the last verified counterpart update from the active development session. This " +
        "creates a Git revert commit so history remains auditable and restores the exact APK saved " +
        "before the update. It never resets or deletes Git history and always requires approval."
  )
  fun rollbackLastVerifiedJarvisUpdate(): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      val session =
        developmentSessionStore.read()
          ?: return@runBlocking errorResult("There is no active Jarvis development session.")
      if (
        !session.verifiedCommit.matches(GIT_COMMIT) ||
          session.backupApkPath.isBlank() ||
          session.counterpartPackage.isBlank()
      ) {
        return@runBlocking errorResult("The active session has no verified update to roll back.")
      }
      val target =
        jarvisCounterpartFor(currentPackageName)
          ?.takeIf { it.packageName == session.counterpartPackage }
          ?: return@runBlocking errorResult("The recorded counterpart does not match this Jarvis build.")
      val prepared =
        prepareOperation(
          displayName = "Roll back ${target.displayName}",
          approvalText =
            "Create a Git revert of ${session.verifiedCommit} and restore ${session.backupApkPath}",
          forceApproval = true,
        )
      prepared.error.takeIf(String::isNotBlank)?.let { return@runBlocking errorResult(it) }
      val revertCommand =
        "test \"\$(git rev-parse HEAD)\" = ${session.verifiedCommit.shellSingleQuote()} && " +
          "git revert --no-edit ${session.verifiedCommit.shellSingleQuote()} && " +
          "printf '${REVERT_COMMIT_MARKER}%s\\n' \"\$(git rev-parse HEAD)\""
      val revert =
        bridge.execute(
          command = revertCommand,
          serial = prepared.serial,
          returnPackageName = currentPackageName,
          timeoutMs = CODE_COMMAND_TIMEOUT_MS,
        )
      if (!revert.succeeded) {
        return@runBlocking revert.toToolResult() + mapOf("phase" to "git_revert")
      }
      val install = bridge.restoreBackup(session.backupApkPath, target.packageName, prepared.serial)
      if (install.succeeded) developmentSessionStore.clear()
      linkedMapOf(
        "status" to if (install.succeeded) "rolled_back" else "source_reverted_apk_restore_failed",
        "target" to target.displayName,
        "revert_commit" to revert.output.markerValue(REVERT_COMMIT_MARKER),
        "install_output" to install.output,
        "error" to install.error,
      )
    }

  private suspend fun executeCodeCommand(
    displayName: String,
    approvalText: String,
    command: String,
    forceApproval: Boolean,
    knownReadOnly: Boolean,
    progressLabel: String,
    timeoutMs: Long = CODE_COMMAND_TIMEOUT_MS,
  ): CodeOnTheGoCommandResult {
    val prepared =
      prepareOperation(
        displayName = displayName,
        approvalText = approvalText,
        forceApproval = forceApproval,
        knownReadOnly = knownReadOnly,
      )
    prepared.error.takeIf(String::isNotBlank)?.let {
      return CodeOnTheGoCommandResult(exitCode = null, output = "", error = it)
    }
    val actionChannel =
      prepared.actionChannel
        ?: return CodeOnTheGoCommandResult(exitCode = null, output = "", error = MISSING_CHAT)
    actionChannel.send(
      SkillProgressToolAction(
        label = progressLabel,
        inProgress = true,
        addItemTitle = displayName,
        addItemDescription = approvalText.take(MAX_PROGRESS_DESCRIPTION_CHARS),
      )
    )
    val result =
      try {
        bridge.execute(
          command = command,
          serial = prepared.serial,
          returnPackageName = currentPackageName,
          timeoutMs = timeoutMs,
        )
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        CodeOnTheGoCommandResult(
          exitCode = null,
          output = "",
          error = error.message ?: "$displayName failed.",
        )
      }
    actionChannel.send(
      SkillProgressToolAction(
        label = if (result.succeeded) "$displayName finished" else "$displayName failed",
        inProgress = false,
        addItemTitle = "$displayName result",
        addItemDescription = result.toProgressDescription(),
      )
    )
    return result
  }

  private suspend fun prepareOperation(
    displayName: String,
    approvalText: String,
    forceApproval: Boolean,
    knownReadOnly: Boolean = false,
  ): PreparedCodeOnTheGoOperation {
    val normalizedApproval = approvalText.trim()
    if (normalizedApproval.isBlank()) {
      return PreparedCodeOnTheGoOperation(error = "Command cannot be empty.")
    }
    if (normalizedApproval.length > MAX_APPROVAL_CHARS) {
      return PreparedCodeOnTheGoOperation(
        error = "Approval details are longer than $MAX_APPROVAL_CHARS characters."
      )
    }
    val ideStatus = codeOnTheGoEnvironment.status()
    if (!ideStatus.installed) {
      return PreparedCodeOnTheGoOperation(
        error = "Code on the Go is not installed. Install it and open its Jarvis terminal once."
      )
    }
    val terminalStatus = terminalEnvironment.status()
    if (!terminalStatus.installed) {
      return PreparedCodeOnTheGoOperation(
        error = "Termux is required for the verified self-ADB bridge."
      )
    }
    val actionChannel =
      executionContext?.actionChannel
        ?: return PreparedCodeOnTheGoOperation(error = MISSING_CHAT)
    val requiresApproval =
      forceApproval ||
        approvalModeStore.getMode() == TerminalApprovalMode.EVERY_COMMAND ||
        (!knownReadOnly && TerminalCommandSafetyPolicy.requiresApproval(normalizedApproval))
    if (requiresApproval) {
      val approval =
        AskSensitiveToolCallPermissionAction(
          toolName = displayName,
          command = normalizedApproval,
        )
      actionChannel.send(approval)
      if (approval.result.await() != PermissionResult.ALLOW_ONCE) {
        return PreparedCodeOnTheGoOperation(error = "Code on the Go operation denied by user.")
      }
    }
    if (!terminalStatus.runCommandPermissionGranted) {
      val permission =
        RequestPermissionToolAction(permission = TermuxRunCommandContract.RUN_COMMAND_PERMISSION)
      actionChannel.send(permission)
      if (!permission.result.await()) {
        return PreparedCodeOnTheGoOperation(
          error = "Android's Run commands in Termux permission was not granted."
        )
      }
    }
    val connection =
      try {
        selfAdbConnectionProvider.getOrPair(actionChannel)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        return PreparedCodeOnTheGoOperation(
          error = error.message ?: "Jarvis could not prepare self-ADB."
        )
      }
    if (!connection.succeeded) {
      return PreparedCodeOnTheGoOperation(error = connection.message)
    }
    if (!SAFE_ADB_SERIAL.matches(connection.serial)) {
      return PreparedCodeOnTheGoOperation(
        error = "Jarvis could not validate the self-ADB target."
      )
    }
    return PreparedCodeOnTheGoOperation(
      serial = connection.serial,
      actionChannel = actionChannel,
    )
  }

  private fun errorResult(message: String): Map<String, String> =
    mapOf("status" to "failed", "error" to message)

  private companion object {
    const val MISSING_CHAT =
      "Code on the Go operations require an active Jarvis chat for approval and progress."
    const val MAX_COMMAND_CHARS = 8_192
    const val MAX_APPROVAL_CHARS = 48_000
    const val MAX_PROGRESS_DESCRIPTION_CHARS = 4_000
    const val MAX_GOAL_CHARS = 500
    const val MAX_SUMMARY_CHARS = 300
    const val MAX_READ_LINES = 400
    const val MAX_SEARCH_CHARS = 400
    const val MAX_SEARCH_RESULTS = 200
    const val MAX_PROMPT_TEST_CHARS = 1_200
    const val MAX_EXPECTED_TEXT_CHARS = 120
    const val MAX_COMMIT_MESSAGE_CHARS = 72
    const val CODE_COMMAND_TIMEOUT_MS = 10 * 60_000L
    const val BUILD_TIMEOUT_MS = 20 * 60_000L
    const val PROMPT_TEST_TIMEOUT_MS = 3 * 60_000L
    val SAFE_ADB_SERIAL = Regex("^[A-Za-z0-9._:%\\-\\[\\]]+$")
  }
}

internal data class UnifiedPatchValidation(
  val accepted: Boolean,
  val message: String,
  val paths: List<String> = emptyList(),
)

internal fun validateUnifiedPatch(patch: String): UnifiedPatchValidation {
  if (patch.isBlank()) return UnifiedPatchValidation(false, "Patch cannot be empty.")
  if (patch.toByteArray(StandardCharsets.UTF_8).size > MAX_PATCH_BYTES) {
    return UnifiedPatchValidation(false, "Patch exceeds the $MAX_PATCH_BYTES-byte safety limit.")
  }
  if ('\u0000' in patch) return UnifiedPatchValidation(false, "Patch contains a NUL byte.")
  val forbiddenContent =
    listOf("GIT binary patch", "Binary files ", "new file mode 120000", "new file mode 160000", "Subproject commit")
  forbiddenContent.firstOrNull(patch::contains)?.let { marker ->
    return UnifiedPatchValidation(false, "Patch contains unsupported content: $marker")
  }
  val paths =
    DIFF_HEADER.findAll(patch).map { match ->
      match.groupValues[1] to match.groupValues[2]
    }.toList()
  if (paths.isEmpty()) {
    return UnifiedPatchValidation(false, "Patch must contain at least one diff --git header.")
  }
  val normalizedPaths = mutableListOf<String>()
  paths.forEach { (before, after) ->
    val safeBefore = validateRepositoryPath(before, requireFile = true)
    val safeAfter = validateRepositoryPath(after, requireFile = true)
    if (safeBefore == null || safeAfter == null) {
      return UnifiedPatchValidation(false, "Patch targets an unsafe or unsupported path.")
    }
    normalizedPaths += safeAfter
  }
  return UnifiedPatchValidation(
    accepted = true,
    message = "Patch accepted for validation.",
    paths = normalizedPaths.distinct(),
  )
}

internal fun validateRepositoryPath(path: String, requireFile: Boolean): String? {
  val slashPath = path.trim().replace('\\', '/')
  if (slashPath == "." && !requireFile) return "."
  val normalized = slashPath.removePrefix("./").removePrefix("a/").removePrefix("b/")
  if (normalized.isBlank() || normalized.startsWith('/') || ':' in normalized) return null
  if (!SAFE_REPOSITORY_PATH.matches(normalized)) return null
  val segments = normalized.split('/')
  if (segments.any { it.isBlank() || it == "." || it == ".." }) return null
  if (segments.any { it.equals(".git", true) || it.equals("build", true) }) return null
  val filename = segments.last().lowercase()
  if (
    filename == "local.properties" ||
      filename == "keystore.properties" ||
      filename.startsWith(".env") ||
      filename.endsWith(".jks") ||
      filename.endsWith(".keystore") ||
      filename.endsWith(".pem") ||
      filename.endsWith(".key")
  ) {
    return null
  }
  return normalized
}

private fun String.markerValue(marker: String): String =
  lineSequence().firstOrNull { it.startsWith(marker) }?.removePrefix(marker)?.trim().orEmpty()

internal fun parseGitStatusPaths(status: String): Set<String> =
  status
    .lineSequence()
    .mapNotNull { line ->
      if (line.length < 4) return@mapNotNull null
      val rawPath = line.substring(3).substringAfterLast(" -> ").trim().trim('"')
      validateRepositoryPath(rawPath, requireFile = true)
    }
    .toSet()

internal fun deepLinkSchemeFor(packageName: String): String =
  when (packageName) {
    JARVIS_ALPHA_PACKAGE -> "com.google.ai.edge.gallery.alpha"
    else -> "com.google.ai.edge.gallery"
  }

private fun JarvisPromptTestResult.toToolResult(
  status: String,
  target: JarvisCounterpart,
  sessionId: String,
  rollback: CodeOnTheGoInstallResult?,
  extraError: String = "",
): Map<String, String> =
  linkedMapOf(
    "status" to status,
    "target" to target.displayName,
    "target_package" to target.packageName,
    "session_id" to sessionId,
    "launched" to launched.toString(),
    "process_running" to processRunning.toString(),
    "expected_text_found" to expectedTextFound.toString(),
    "fatal_crash_detected" to fatalCrashDetected.toString(),
    "screenshot_path" to screenshotPath,
    "screenshot_sha256" to screenshotSha256,
    "logcat_errors" to logcatErrors,
    "rollback_status" to
      when {
        rollback == null -> "not_needed"
        rollback.succeeded -> "restored"
        else -> "failed"
      },
    "error" to listOf(error, extraError, rollback?.error.orEmpty()).filter(String::isNotBlank).joinToString("\n"),
  ).apply {
    if (screenshotBase64.isNotBlank()) {
      put(TOOL_RESULT_INPUT_IMAGE_DATA_URL, "data:image/jpeg;base64,$screenshotBase64")
    }
  }

private const val MAX_PATCH_BYTES = 32_000
private val DIFF_HEADER = Regex("(?m)^diff --git a/([^\\s]+) b/([^\\s]+)$")
private val SAFE_REPOSITORY_PATH = Regex("^[A-Za-z0-9._+/-]+$")
private val GIT_COMMIT = Regex("^[0-9a-fA-F]{40,64}$")
private const val BRANCH_MARKER = "__JARVIS_BRANCH__="
private const val HEAD_MARKER = "__JARVIS_HEAD__="
private const val STATUS_MARKER = "__JARVIS_STATUS__\n"
private const val VERIFIED_COMMIT_MARKER = "__JARVIS_VERIFIED_COMMIT__="
private const val REVERT_COMMIT_MARKER = "__JARVIS_REVERT_COMMIT__="
private const val DEVELOPMENT_STATUS_COMMAND =
  "printf '__JARVIS_BRANCH__=%s\\n' \"\$(git branch --show-current)\"; " +
    "printf '__JARVIS_HEAD__=%s\\n' \"\$(git rev-parse HEAD)\"; " +
    "printf '__JARVIS_STATUS__\\n'; git status --porcelain=v1 --untracked-files=all"

data class CodeOnTheGoEnvironmentStatus(
  val installed: Boolean,
  val versionName: String,
)

interface CodeOnTheGoEnvironment {
  fun status(): CodeOnTheGoEnvironmentStatus
}

class AndroidCodeOnTheGoEnvironment(private val context: Context) : CodeOnTheGoEnvironment {
  override fun status(): CodeOnTheGoEnvironmentStatus {
    val packageInfo =
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.packageManager.getPackageInfo(
            CODE_ON_THE_GO_PACKAGE,
            PackageManager.PackageInfoFlags.of(0),
          )
        } else {
          @Suppress("DEPRECATION")
          context.packageManager.getPackageInfo(CODE_ON_THE_GO_PACKAGE, 0)
        }
      } catch (_: PackageManager.NameNotFoundException) {
        null
      }
    return CodeOnTheGoEnvironmentStatus(
      installed = packageInfo != null,
      versionName = packageInfo?.versionName.orEmpty(),
    )
  }
}

data class CodeOnTheGoCommandResult(
  val exitCode: Int?,
  val output: String,
  val error: String,
) {
  val succeeded: Boolean
    get() = exitCode == 0 && error.isBlank()

  fun toToolResult(): Map<String, String> =
    linkedMapOf(
      "status" to if (succeeded) "succeeded" else "failed",
      "exit_code" to (exitCode?.toString() ?: "unavailable"),
      "output" to output,
      "error" to error,
    )

  fun toProgressDescription(): String =
    buildString {
      append("Exit code: ").append(exitCode ?: "unavailable")
      output.takeIf(String::isNotBlank)?.let { append("\n\nOutput:\n").append(it) }
      error.takeIf(String::isNotBlank)?.let { append("\n\nError:\n").append(it) }
    }
}

data class CodeOnTheGoInstallResult(
  val succeeded: Boolean,
  val output: String,
  val error: String,
  val backupApkPath: String = "",
) {
  fun toProgressDescription(): String =
    buildString {
      append(if (succeeded) "Installed in place." else "Installation failed.")
      output.takeIf(String::isNotBlank)?.let { append("\n\nOutput:\n").append(it) }
      error.takeIf(String::isNotBlank)?.let { append("\n\nError:\n").append(it) }
    }
}

interface CodeOnTheGoBridge {
  suspend fun execute(
    command: String,
    serial: String,
    returnPackageName: String,
    timeoutMs: Long,
  ): CodeOnTheGoCommandResult

  suspend fun install(
    apkPath: String,
    targetPackageName: String,
    serial: String,
  ): CodeOnTheGoInstallResult

  suspend fun testCounterpart(
    targetPackageName: String,
    deepLinkScheme: String,
    prompt: String,
    expectedText: String,
    serial: String,
    returnPackageName: String,
    timeoutMs: Long,
  ): JarvisPromptTestResult

  suspend fun restoreBackup(
    backupApkPath: String,
    targetPackageName: String,
    serial: String,
  ): CodeOnTheGoInstallResult
}

data class JarvisPromptTestResult(
  val launched: Boolean,
  val processRunning: Boolean,
  val expectedTextFound: Boolean,
  val fatalCrashDetected: Boolean,
  val screenshotBase64: String,
  val screenshotPath: String,
  val screenshotSha256: String,
  val logcatErrors: String,
  val error: String,
) {
  val succeeded: Boolean
    get() = launched && processRunning && expectedTextFound && !fatalCrashDetected && error.isBlank()
}

/**
 * Uses ADB only for UI focus/typing and shared bridge files. The command itself executes inside
 * Code on the Go's private terminal, where its JDK, SDK, Gradle caches, signing key, and repository
 * are available. The model never receives the self-ADB serial or the generated bridge script.
 */
class AndroidCodeOnTheGoBridge(
  private val runner: TermuxCommandRunner,
  private val pause: suspend (Long) -> Unit = { delay(it) },
  private val requestIdFactory: () -> String = {
    UUID.randomUUID().toString().replace("-", "")
  },
) : CodeOnTheGoBridge {
  override suspend fun execute(
    command: String,
    serial: String,
    returnPackageName: String,
    timeoutMs: Long,
  ): CodeOnTheGoCommandResult {
    val requestId = requestIdFactory().filter(Char::isLetterOrDigit).take(48)
    if (requestId.isBlank()) {
      return CodeOnTheGoCommandResult(null, "", "Could not create a bridge request ID.")
    }
    val paths = CodeOnTheGoBridgePaths(requestId)
    val requestScript = buildCodeOnTheGoRequestScript(command, paths)
    val encodedScript =
      Base64.getEncoder()
        .encodeToString(requestScript.toByteArray(StandardCharsets.UTF_8))
    val adb = "adb -s ${serial.shellSingleQuote()}"
    var commandCompleted = false
    try {
      val prepareRemote =
        buildString {
          append("mkdir -p ").append(BRIDGE_DIRECTORY.shellSingleQuote())
          append(" && rm -f ")
          append(paths.allTransientPaths.joinToString(" ") { it.shellSingleQuote() })
          append(" && printf '%s' ").append(encodedScript.shellSingleQuote())
          append(" | base64 -d > ").append(paths.script.shellSingleQuote())
          append(" && chmod 600 ").append(paths.script.shellSingleQuote())
        }
      runner.run("$adb shell ${prepareRemote.shellSingleQuote()}", BRIDGE_STEP_TIMEOUT_MS)
        .requireSuccess("Could not prepare the Code on the Go bridge")

      runner.run(
          "$adb shell " +
            "${(
              "jarvis_component=\"\$(cmd package resolve-activity --brief $CODE_ON_THE_GO_PACKAGE | tail -n 1)\"; " +
                "test -n \"\$jarvis_component\" && am start -W -n \"\$jarvis_component\""
              ).shellSingleQuote()}",
          BRIDGE_STEP_TIMEOUT_MS,
        )
        .requireSuccess("Could not open Code on the Go")
      pause(CODE_ON_THE_GO_OPEN_DELAY_MS)

      val sizeResult =
        runner.run("$adb shell ${"wm size".shellSingleQuote()}", BRIDGE_STEP_TIMEOUT_MS)
      val (width, height) = parseLastDisplaySize(sizeResult.stdout) ?: DEFAULT_DISPLAY_SIZE
      val focusX = width / 2
      val focusY = height * TERMINAL_FOCUS_PERCENT / 100
      runner.run(
          "$adb shell ${"input tap $focusX $focusY".shellSingleQuote()}",
          BRIDGE_STEP_TIMEOUT_MS,
        )
        .requireSuccess("Could not focus the Code on the Go terminal")
      pause(TERMINAL_FOCUS_DELAY_MS)

      val typedCommand = "sh%s${paths.script}"
      runner.run(
          "$adb shell ${"input text ${typedCommand.shellSingleQuote()}".shellSingleQuote()}",
          BRIDGE_STEP_TIMEOUT_MS,
        )
        .requireSuccess("Could not type into the Code on the Go terminal")
      runner.run(
          "$adb shell ${"input keyevent 66".shellSingleQuote()}",
          BRIDGE_STEP_TIMEOUT_MS,
        )
        .requireSuccess("Could not start the Code on the Go command")

      val timeoutSeconds = (timeoutMs / 1_000L).coerceAtLeast(1L)
      val pollRemote =
        "i=0; while [ ! -f ${paths.exit.shellSingleQuote()} ]; do " +
          "sleep 1; i=\$((i+1)); [ \"\$i\" -ge $timeoutSeconds ] && exit 124; done; " +
          "printf '${EXIT_MARKER}%s\\n${OUTPUT_MARKER}\\n' " +
          "\"\$(cat ${paths.exit.shellSingleQuote()})\"; " +
          "cat ${paths.output.shellSingleQuote()}"
      val polled =
        runner.run(
          "$adb shell ${pollRemote.shellSingleQuote()}",
          timeoutMs + BRIDGE_STEP_TIMEOUT_MS,
        )
      if (polled.timedOut || polled.exitCode == 124) {
        return CodeOnTheGoCommandResult(
          exitCode = null,
          output = polled.stdout,
          error =
            "Timed out waiting for Code on the Go. Leave its terminal open and retry the " +
              "approved command.",
        )
      }
      if (!polled.succeeded) {
        return CodeOnTheGoCommandResult(
          exitCode = polled.exitCode,
          output = polled.stdout,
          error =
            polled.stderr.trim().ifBlank {
              polled.internalErrorMessage.ifBlank { "Could not read Code on the Go's result." }
            },
        )
      }
      val exitCode =
        Regex("${Regex.escape(EXIT_MARKER)}(-?[0-9]+)")
          .find(polled.stdout)
          ?.groupValues
          ?.getOrNull(1)
          ?.toIntOrNull()
      if (exitCode == null) {
        return CodeOnTheGoCommandResult(
          exitCode = null,
          output = polled.stdout,
          error = "Code on the Go returned a result without a valid exit code.",
        )
      }
      commandCompleted = true
      return CodeOnTheGoCommandResult(
        exitCode = exitCode,
        output = polled.stdout.substringAfter("$OUTPUT_MARKER\n", "").trimEnd(),
        error = "",
      )
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: CodeOnTheGoBridgeException) {
      return CodeOnTheGoCommandResult(null, "", error.message.orEmpty())
    } finally {
      if (commandCompleted) {
        val cleanupRemote =
          "rm -f " + paths.allTransientPaths.joinToString(" ") { it.shellSingleQuote() }
        runner.run("$adb shell ${cleanupRemote.shellSingleQuote()}", BRIDGE_STEP_TIMEOUT_MS)
      }
      if (returnPackageName.matches(ANDROID_PACKAGE_NAME)) {
        val restoreRemote =
          "am start -W -n $returnPackageName/com.google.ai.edge.gallery.MainActivity >/dev/null 2>&1"
        runner.run("$adb shell ${restoreRemote.shellSingleQuote()}", BRIDGE_STEP_TIMEOUT_MS)
      }
    }
  }

  override suspend fun install(
    apkPath: String,
    targetPackageName: String,
    serial: String,
  ): CodeOnTheGoInstallResult {
    if (!apkPath.startsWith("$BRIDGE_EXPORT_DIRECTORY/") || !apkPath.endsWith(".apk")) {
      return CodeOnTheGoInstallResult(false, "", "Refusing an APK outside Jarvis's export folder.")
    }
    if (!targetPackageName.matches(ANDROID_PACKAGE_NAME)) {
      return CodeOnTheGoInstallResult(false, "", "Invalid target package name.")
    }
    val adb = "adb -s ${serial.shellSingleQuote()}"
    val backupApk =
      "$BRIDGE_EVIDENCE_DIRECTORY/pre-update-${targetPackageName}-${System.currentTimeMillis()}.apk"
    val backupRemote =
      "mkdir -p ${BRIDGE_EVIDENCE_DIRECTORY.shellSingleQuote()}; " +
        "jarvis_installed=\"\$(pm path $targetPackageName | sed -n 's/^package://p' | head -n 1)\"; " +
        "if [ -z \"\$jarvis_installed\" ]; then printf 'TARGET_NOT_INSTALLED\\n' >&2; exit 44; fi; " +
        "cp \"\$jarvis_installed\" ${backupApk.shellSingleQuote()} && " +
        "printf '${BACKUP_MARKER}%s\\n' ${backupApk.shellSingleQuote()}"
    val backupResult =
      runner.run("$adb shell ${backupRemote.shellSingleQuote()}", BRIDGE_STEP_TIMEOUT_MS)
    if (!backupResult.succeeded) {
      return CodeOnTheGoInstallResult(
        succeeded = false,
        output = backupResult.stdout.trim(),
        error =
          backupResult.stderr.trim().ifBlank {
            backupResult.internalErrorMessage.ifBlank {
              "Could not create a rollback APK before updating $targetPackageName."
            }
          },
      )
    }
    val stagedApk = "/data/local/tmp/${apkPath.substringAfterLast('/')}"
    val remote =
      "cp ${apkPath.shellSingleQuote()} ${stagedApk.shellSingleQuote()} && " +
        "pm install -r -t ${stagedApk.shellSingleQuote()}; " +
        "jarvis_status=\$?; rm -f ${stagedApk.shellSingleQuote()}; exit \$jarvis_status"
    val result = runner.run("$adb shell ${remote.shellSingleQuote()}", INSTALL_TIMEOUT_MS)
    return CodeOnTheGoInstallResult(
      succeeded = result.succeeded && result.stdout.lineSequence().any { it.trim() == "Success" },
      output = result.stdout.trim(),
      error =
        if (result.succeeded && result.stdout.lineSequence().any { it.trim() == "Success" }) {
          ""
        } else {
          result.stderr.trim().ifBlank {
            result.internalErrorMessage.ifBlank { result.stdout.trim().ifBlank { "Install failed." } }
          }
        },
      backupApkPath = backupApk,
    )
  }

  override suspend fun restoreBackup(
    backupApkPath: String,
    targetPackageName: String,
    serial: String,
  ): CodeOnTheGoInstallResult {
    if (
      !backupApkPath.startsWith("$BRIDGE_EVIDENCE_DIRECTORY/") ||
        !backupApkPath.endsWith(".apk") ||
        !targetPackageName.matches(ANDROID_PACKAGE_NAME)
    ) {
      return CodeOnTheGoInstallResult(false, "", "Refusing an invalid rollback target.")
    }
    val adb = "adb -s ${serial.shellSingleQuote()}"
    val stagedApk = "/data/local/tmp/jarvis-rollback-${targetPackageName.substringAfterLast('.')}.apk"
    val remote =
      "test -f ${backupApkPath.shellSingleQuote()} && " +
        "cp ${backupApkPath.shellSingleQuote()} ${stagedApk.shellSingleQuote()} && " +
        "pm install -r -t ${stagedApk.shellSingleQuote()}; " +
        "jarvis_status=\$?; rm -f ${stagedApk.shellSingleQuote()}; exit \$jarvis_status"
    val result = runner.run("$adb shell ${remote.shellSingleQuote()}", INSTALL_TIMEOUT_MS)
    val succeeded = result.succeeded && result.stdout.lineSequence().any { it.trim() == "Success" }
    return CodeOnTheGoInstallResult(
      succeeded = succeeded,
      output = result.stdout.trim(),
      error =
        if (succeeded) ""
        else
          result.stderr.trim().ifBlank {
            result.internalErrorMessage.ifBlank { result.stdout.trim().ifBlank { "Rollback failed." } }
          },
      backupApkPath = backupApkPath,
    )
  }

  override suspend fun testCounterpart(
    targetPackageName: String,
    deepLinkScheme: String,
    prompt: String,
    expectedText: String,
    serial: String,
    returnPackageName: String,
    timeoutMs: Long,
  ): JarvisPromptTestResult {
    if (
      !targetPackageName.matches(ANDROID_PACKAGE_NAME) ||
        !returnPackageName.matches(ANDROID_PACKAGE_NAME) ||
        !deepLinkScheme.matches(DEEP_LINK_SCHEME)
    ) {
      return JarvisPromptTestResult(
        launched = false,
        processRunning = false,
        expectedTextFound = false,
        fatalCrashDetected = false,
        screenshotBase64 = "",
        screenshotPath = "",
        screenshotSha256 = "",
        logcatErrors = "",
        error = "Invalid counterpart prompt-test target.",
      )
    }
    val adb = "adb -s ${serial.shellSingleQuote()}"
    val deepLink =
      Uri.Builder()
        .scheme(deepLinkScheme)
        .authority("model")
        .appendPath("llm_agent_chat")
        .appendPath(DEFAULT_PROMPT_TEST_MODEL)
        .appendQueryParameter("query", prompt)
        .build()
        .toString()
    val component = "$targetPackageName/com.google.ai.edge.gallery.MainActivity"
    var launched = false
    var processRunning = false
    var expectedFound = false
    var fatalCrash = false
    var logcatErrors = ""
    var screenshot = ScreenshotEvidence()
    var errorMessage = ""
    try {
      runner.run("$adb logcat -c", BRIDGE_STEP_TIMEOUT_MS)
      val launchRemote =
        "am force-stop $targetPackageName; " +
          "am start -W -a android.intent.action.VIEW -d ${deepLink.shellSingleQuote()} " +
          "-n ${component.shellSingleQuote()}"
      val launchResult =
        runner.run("$adb shell ${launchRemote.shellSingleQuote()}", BRIDGE_STEP_TIMEOUT_MS)
      launched =
        launchResult.succeeded &&
          (launchResult.stdout.contains("Status: ok") || launchResult.stdout.contains("cmp="))
      if (!launched) {
        errorMessage =
          launchResult.stderr.trim().ifBlank {
            launchResult.internalErrorMessage.ifBlank {
              launchResult.stdout.trim().ifBlank { "Counterpart did not launch." }
            }
          }
      } else {
        pause(PROMPT_TEST_INITIAL_DELAY_MS)
        val uiDump = "$BRIDGE_EVIDENCE_DIRECTORY/prompt-test-${requestIdFactory().take(16)}.xml"
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !expectedFound) {
          val probeRemote =
            "mkdir -p ${BRIDGE_EVIDENCE_DIRECTORY.shellSingleQuote()}; " +
              "uiautomator dump ${uiDump.shellSingleQuote()} >/dev/null 2>&1 && " +
              "grep -Fq -- ${expectedText.shellSingleQuote()} ${uiDump.shellSingleQuote()}"
          val probe = runner.run("$adb shell ${probeRemote.shellSingleQuote()}", UI_PROBE_TIMEOUT_MS)
          expectedFound = probe.succeeded
          if (!expectedFound) pause(PROMPT_TEST_POLL_INTERVAL_MS)
        }
        val processResult =
          runner.run(
            "$adb shell ${"pidof $targetPackageName".shellSingleQuote()}",
            BRIDGE_STEP_TIMEOUT_MS,
          )
        processRunning = processResult.succeeded && processResult.stdout.trim().isNotBlank()
        val logcatRemote =
          "logcat -d -t 1000 AndroidRuntime:E '*:S' | " +
            "grep -F -A 30 -B 2 ${targetPackageName.shellSingleQuote()} | tail -n 120"
        val logcatResult =
          runner.run("$adb shell ${logcatRemote.shellSingleQuote()}", BRIDGE_STEP_TIMEOUT_MS)
        logcatErrors = logcatResult.stdout.trim()
        fatalCrash =
          logcatErrors.contains("FATAL EXCEPTION") || logcatErrors.contains("Process: $targetPackageName")
        screenshot = captureScreenshot(serial = serial, evidenceLabel = targetPackageName)
        if (!expectedFound && errorMessage.isBlank()) {
          errorMessage = "Timed out before the expected user-visible response appeared."
        } else if (!processRunning && errorMessage.isBlank()) {
          errorMessage = "The counterpart process was not running after the prompt test."
        } else if (fatalCrash && errorMessage.isBlank()) {
          errorMessage = "A startup or prompt-test crash was found in Logcat."
        } else if (screenshot.error.isNotBlank() && errorMessage.isBlank()) {
          errorMessage = screenshot.error
        }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Exception) {
      errorMessage = error.message ?: "Counterpart prompt test failed."
    } finally {
      val restoreRemote =
        "am start -W -n $returnPackageName/com.google.ai.edge.gallery.MainActivity >/dev/null 2>&1"
      runner.run("$adb shell ${restoreRemote.shellSingleQuote()}", BRIDGE_STEP_TIMEOUT_MS)
    }
    return JarvisPromptTestResult(
      launched = launched,
      processRunning = processRunning,
      expectedTextFound = expectedFound,
      fatalCrashDetected = fatalCrash,
      screenshotBase64 = screenshot.jpegBase64,
      screenshotPath = screenshot.path,
      screenshotSha256 = screenshot.sha256,
      logcatErrors = logcatErrors,
      error = errorMessage,
    )
  }

  private suspend fun captureScreenshot(
    serial: String,
    evidenceLabel: String,
  ): ScreenshotEvidence {
    val safeLabel = evidenceLabel.filter { it.isLetterOrDigit() || it == '.' || it == '-' }.take(80)
    val path =
      "$BRIDGE_EVIDENCE_DIRECTORY/${safeLabel}-${System.currentTimeMillis()}-${requestIdFactory().take(8)}.png"
    val captureCommand =
      "mkdir -p ${BRIDGE_EVIDENCE_DIRECTORY.shellSingleQuote()}; " +
        "adb -s ${serial.shellSingleQuote()} exec-out screencap -p > ${path.shellSingleQuote()}; " +
        "jarvis_size=\$(wc -c < ${path.shellSingleQuote()}); " +
        "jarvis_sha=\$(sha256sum ${path.shellSingleQuote()} | awk '{print \$1}'); " +
        "printf '${SCREENSHOT_SIZE_MARKER}%s\\n${SCREENSHOT_SHA_MARKER}%s\\n' " +
        "\"\$jarvis_size\" \"\$jarvis_sha\""
    val captureResult = runner.run(captureCommand, SCREENSHOT_CAPTURE_TIMEOUT_MS)
    if (!captureResult.succeeded) {
      return ScreenshotEvidence(
        path = path,
        error =
          captureResult.stderr.trim().ifBlank {
            captureResult.internalErrorMessage.ifBlank { "Could not capture the phone screen." }
          },
      )
    }
    val size = captureResult.stdout.markerValue(SCREENSHOT_SIZE_MARKER).toIntOrNull()
      ?: return ScreenshotEvidence(path = path, error = "Screenshot size was unavailable.")
    val sha256 = captureResult.stdout.markerValue(SCREENSHOT_SHA_MARKER)
    if (size !in 1..MAX_SCREENSHOT_BYTES || !sha256.matches(SHA256)) {
      return ScreenshotEvidence(path = path, error = "Screenshot failed size or hash validation.")
    }
    val bytes = ByteArrayOutputStream(size)
    val chunkCount = (size + SCREENSHOT_CHUNK_BYTES - 1) / SCREENSHOT_CHUNK_BYTES
    repeat(chunkCount) { chunkIndex ->
      val expectedBytes = minOf(SCREENSHOT_CHUNK_BYTES, size - chunkIndex * SCREENSHOT_CHUNK_BYTES)
      val expectedBase64Chars = ((expectedBytes + 2) / 3) * 4
      val chunkCommand =
        "dd if=${path.shellSingleQuote()} bs=$SCREENSHOT_CHUNK_BYTES skip=$chunkIndex count=1 " +
          "2>/dev/null | base64 | tr -d '\\n'"
      val chunkResult = runner.run(chunkCommand, SCREENSHOT_CHUNK_TIMEOUT_MS)
      val encoded = chunkResult.fullStdout.trim()
      if (!chunkResult.succeeded || encoded.length != expectedBase64Chars) {
        return ScreenshotEvidence(path = path, sha256 = sha256, error = "Screenshot transfer was incomplete.")
      }
      val decoded =
        try {
          Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
          return ScreenshotEvidence(path = path, sha256 = sha256, error = "Screenshot transfer was unreadable.")
        }
      if (decoded.size != expectedBytes) {
        return ScreenshotEvidence(path = path, sha256 = sha256, error = "Screenshot chunk length did not match.")
      }
      bytes.write(decoded)
    }
    val jpegBase64 = compressScreenshotForModel(bytes.toByteArray())
      ?: return ScreenshotEvidence(path = path, sha256 = sha256, error = "Screenshot image could not be decoded.")
    return ScreenshotEvidence(path = path, sha256 = sha256, jpegBase64 = jpegBase64)
  }
}

private data class ScreenshotEvidence(
  val path: String = "",
  val sha256: String = "",
  val jpegBase64: String = "",
  val error: String = "",
)

private fun compressScreenshotForModel(pngBytes: ByteArray): String? {
  val original = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size) ?: return null
  val largestDimension = maxOf(original.width, original.height)
  val scaled =
    if (largestDimension <= MAX_MODEL_SCREENSHOT_DIMENSION) {
      original
    } else {
      val scale = MAX_MODEL_SCREENSHOT_DIMENSION.toFloat() / largestDimension.toFloat()
      Bitmap.createScaledBitmap(
        original,
        (original.width * scale).toInt().coerceAtLeast(1),
        (original.height * scale).toInt().coerceAtLeast(1),
        true,
      )
    }
  val jpeg = ByteArrayOutputStream()
  val compressed = scaled.compress(Bitmap.CompressFormat.JPEG, MODEL_SCREENSHOT_JPEG_QUALITY, jpeg)
  if (scaled !== original) scaled.recycle()
  original.recycle()
  if (!compressed) return null
  return Base64.getEncoder().encodeToString(jpeg.toByteArray())
}

internal data class CodeOnTheGoBridgePaths(val requestId: String) {
  val script = "$BRIDGE_DIRECTORY/request-$requestId.sh"
  val output = "$BRIDGE_DIRECTORY/result-$requestId.log"
  val outputTemporary = "$output.tmp"
  val exit = "$BRIDGE_DIRECTORY/result-$requestId.exit"
  val exitTemporary = "$exit.tmp"
  val allTransientPaths = listOf(script, output, outputTemporary, exit, exitTemporary)
}

internal fun buildCodeOnTheGoRequestScript(
  command: String,
  paths: CodeOnTheGoBridgePaths,
): String =
  """
  #!/data/data/com.itsaky.androidide/files/usr/bin/bash
  set +e
  cd ${CODE_ON_THE_GO_PROJECT_ROOT.shellSingleQuote()} || exit 72
  (
  $command
  ) >${paths.outputTemporary.shellSingleQuote()} 2>&1
  jarvis_status=${'$'}?
  mv -f ${paths.outputTemporary.shellSingleQuote()} ${paths.output.shellSingleQuote()}
  printf '%s\n' "${'$'}jarvis_status" >${paths.exitTemporary.shellSingleQuote()}
  mv -f ${paths.exitTemporary.shellSingleQuote()} ${paths.exit.shellSingleQuote()}
  exit "${'$'}jarvis_status"
  """
    .trimIndent()

internal fun parseLastDisplaySize(output: String): Pair<Int, Int>? =
  Regex("([0-9]+)x([0-9]+)")
    .findAll(output)
    .lastOrNull()
    ?.let { match ->
      val width = match.groupValues[1].toIntOrNull() ?: return@let null
      val height = match.groupValues[2].toIntOrNull() ?: return@let null
      if (width > 0 && height > 0) width to height else null
    }

internal data class JarvisCounterpart(
  val displayName: String,
  val packageName: String,
  val gradleTask: String,
  val apkPath: String,
  val exportedApkPath: String,
)

internal fun jarvisCounterpartFor(currentPackageName: String): JarvisCounterpart? =
  when (currentPackageName) {
    JARVIS_ALPHA_PACKAGE ->
      JarvisCounterpart(
        displayName = "Android Jarvis Main",
        packageName = JARVIS_MAIN_PACKAGE,
        gradleTask = "assembleDebug",
        apkPath = "app/build/outputs/apk/debug/app-debug.apk",
        exportedApkPath = "$BRIDGE_EXPORT_DIRECTORY/Android-Jarvis-Main-CoGo.apk",
      )
    JARVIS_MAIN_PACKAGE ->
      JarvisCounterpart(
        displayName = "Android Jarvis Alpha",
        packageName = JARVIS_ALPHA_PACKAGE,
        gradleTask = "assembleAlpha",
        apkPath = "app/build/outputs/apk/alpha/app-alpha.apk",
        exportedApkPath = "$BRIDGE_EXPORT_DIRECTORY/Android-Jarvis-Alpha-CoGo.apk",
      )
    else -> null
  }

private data class PreparedCodeOnTheGoOperation(
  val serial: String = "",
  val actionChannel: SendChannel<ToolAction>? = null,
  val error: String = "",
)

private class CodeOnTheGoBridgeException(message: String) : IllegalStateException(message)

private fun TermuxCommandResult.requireSuccess(operation: String) {
  if (succeeded) return
  val detail =
    stderr.trim().ifBlank {
      internalErrorMessage.trim().ifBlank { stdout.trim().ifBlank { "unknown error" } }
    }
  throw CodeOnTheGoBridgeException("$operation: $detail")
}

private fun String.shellSingleQuote(): String = "'" + replace("'", "'\\''") + "'"

internal const val CODE_ON_THE_GO_PACKAGE = "com.itsaky.androidide"
internal const val CODE_ON_THE_GO_PROJECT_ROOT =
  "/data/data/com.itsaky.androidide/files/home/Echo-cortex/Android/src"
internal const val BRIDGE_DIRECTORY = "/sdcard/Download/AndroidJarvisBridge"
internal const val BRIDGE_EXPORT_DIRECTORY = "/sdcard/Download"
internal const val BRIDGE_EVIDENCE_DIRECTORY = "/sdcard/Download/AndroidJarvisEvidence"
internal const val JARVIS_MAIN_PACKAGE = "com.google.aiedge.gallery"
internal const val JARVIS_ALPHA_PACKAGE = "com.google.aiedge.gallery.alpha"
private const val EXIT_MARKER = "__ANDROID_JARVIS_EXIT__="
private const val OUTPUT_MARKER = "__ANDROID_JARVIS_OUTPUT__"
private const val BRIDGE_STEP_TIMEOUT_MS = 20_000L
private const val INSTALL_TIMEOUT_MS = 2 * 60_000L
private const val CODE_ON_THE_GO_OPEN_DELAY_MS = 2_500L
private const val TERMINAL_FOCUS_DELAY_MS = 350L
private const val TERMINAL_FOCUS_PERCENT = 42
private const val DEFAULT_PROMPT_TEST_MODEL = "gpt-5.6-terra"
private const val PROMPT_TEST_INITIAL_DELAY_MS = 4_000L
private const val PROMPT_TEST_POLL_INTERVAL_MS = 2_000L
private const val UI_PROBE_TIMEOUT_MS = 15_000L
private const val SCREENSHOT_CAPTURE_TIMEOUT_MS = 30_000L
private const val SCREENSHOT_CHUNK_TIMEOUT_MS = 20_000L
private const val SCREENSHOT_CHUNK_BYTES = 45_000
private const val MAX_SCREENSHOT_BYTES = 6_000_000
private const val MAX_MODEL_SCREENSHOT_DIMENSION = 1_800
private const val MODEL_SCREENSHOT_JPEG_QUALITY = 86
private const val BACKUP_MARKER = "__JARVIS_BACKUP__="
private const val SCREENSHOT_SIZE_MARKER = "__JARVIS_SCREENSHOT_SIZE__="
private const val SCREENSHOT_SHA_MARKER = "__JARVIS_SCREENSHOT_SHA256__="
private val DEFAULT_DISPLAY_SIZE = 1080 to 2340
private val ANDROID_PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")
private val DEEP_LINK_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]+$")
private val SHA256 = Regex("^[0-9a-f]{64}$")
