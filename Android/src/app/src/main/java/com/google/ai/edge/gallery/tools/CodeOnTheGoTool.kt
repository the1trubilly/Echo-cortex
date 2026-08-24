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
import android.os.Build
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** Native bridge that lets Jarvis operate Code on the Go through verified self-ADB. */
class CodeOnTheGoTool(
  private val currentPackageName: String,
  private val codeOnTheGoEnvironment: CodeOnTheGoEnvironment,
  private val terminalEnvironment: TermuxEnvironment,
  private val bridge: CodeOnTheGoBridge,
  private val selfAdbConnectionProvider: SelfAdbConnectionProvider,
  private val approvalModeStore: TerminalApprovalModeStore,
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
      "bridge" to
        "verified self-ADB opens Code on the Go, injects one shared-storage script, and reads a " +
          "bounded result file",
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

  private suspend fun prepareOperation(
    displayName: String,
    approvalText: String,
    forceApproval: Boolean,
  ): PreparedCodeOnTheGoOperation {
    val normalizedApproval = approvalText.trim()
    if (normalizedApproval.isBlank()) {
      return PreparedCodeOnTheGoOperation(error = "Command cannot be empty.")
    }
    if (normalizedApproval.length > MAX_COMMAND_CHARS) {
      return PreparedCodeOnTheGoOperation(
        error = "Command is longer than $MAX_COMMAND_CHARS characters."
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
        TerminalCommandSafetyPolicy.requiresApproval(normalizedApproval)
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
    const val CODE_COMMAND_TIMEOUT_MS = 10 * 60_000L
    const val BUILD_TIMEOUT_MS = 20 * 60_000L
    val SAFE_ADB_SERIAL = Regex("^[A-Za-z0-9._:%\\-\\[\\]]+$")
  }
}

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
            "${"monkey -p $CODE_ON_THE_GO_PACKAGE -c android.intent.category.LAUNCHER 1".shellSingleQuote()}",
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
          "monkey -p $returnPackageName -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1"
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
    )
  }
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
internal const val JARVIS_MAIN_PACKAGE = "com.google.aiedge.gallery"
internal const val JARVIS_ALPHA_PACKAGE = "com.google.aiedge.gallery.alpha"
private const val EXIT_MARKER = "__ANDROID_JARVIS_EXIT__="
private const val OUTPUT_MARKER = "__ANDROID_JARVIS_OUTPUT__"
private const val BRIDGE_STEP_TIMEOUT_MS = 20_000L
private const val INSTALL_TIMEOUT_MS = 2 * 60_000L
private const val CODE_ON_THE_GO_OPEN_DELAY_MS = 2_500L
private const val TERMINAL_FOCUS_DELAY_MS = 350L
private const val TERMINAL_FOCUS_PERCENT = 42
private val DEFAULT_DISPLAY_SIZE = 1080 to 2340
private val ANDROID_PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")
