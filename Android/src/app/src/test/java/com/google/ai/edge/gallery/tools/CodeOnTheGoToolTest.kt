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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeOnTheGoToolTest {
  @Test
  fun status_reportsIdeTerminalAndCounterpartWithoutExecution() {
    val bridge = FakeBridge()
    val tool =
      createTool(
        currentPackageName = JARVIS_ALPHA_PACKAGE,
        bridge = bridge,
      )

    val result = tool.codeOnTheGoStatus()

    assertEquals("available", result["status"])
    assertEquals("test-version", result["code_on_the_go_version"])
    assertEquals(JARVIS_MAIN_PACKAGE, result["counterpart_package"])
    assertFalse(bridge.executeCalled)
    assertFalse(bridge.installCalled)
  }

  @Test
  fun runCommand_requiresApprovalAndReturnsCodeOnTheGoOutput() = runBlocking {
    val bridge = FakeBridge(commandOutput = "jarvis-alpha-native-cortex\n")
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      createTool(bridge = bridge).apply {
        onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel))
      }

    val invocation =
      async(Dispatchers.Default) { tool.runInCodeOnTheGo("git status --short --branch") }
    val approval = channel.receive() as AskSensitiveToolCallPermissionAction
    assertEquals("git status --short --branch", approval.command)
    approval.result.complete(PermissionResult.ALLOW_ONCE)
    assertTrue((channel.receive() as SkillProgressToolAction).inProgress)
    assertFalse((channel.receive() as SkillProgressToolAction).inProgress)
    val result = invocation.await()

    assertEquals("succeeded", result["status"])
    assertEquals("jarvis-alpha-native-cortex\n", result["output"])
    assertEquals("git status --short --branch", bridge.lastCommand)
    assertEquals("192.168.50.17:41234", bridge.lastSerial)
  }

  @Test
  fun deniedCommandNeverReachesCodeOnTheGo() = runBlocking {
    val bridge = FakeBridge()
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      createTool(bridge = bridge).apply {
        onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel))
      }

    val invocation = async(Dispatchers.Default) { tool.runInCodeOnTheGo("rm important-file") }
    val approval = channel.receive() as AskSensitiveToolCallPermissionAction
    approval.result.complete(PermissionResult.DENY)
    val result = invocation.await()

    assertEquals("failed", result["status"])
    assertFalse(bridge.executeCalled)
  }

  @Test
  fun dangerousOnlyModeAllowsNarrowReadOnlyCommandWithoutApproval() = runBlocking {
    val bridge = FakeBridge(commandOutput = CODE_ON_THE_GO_PROJECT_ROOT)
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      createTool(
          bridge = bridge,
          approvalMode = TerminalApprovalMode.DANGEROUS_COMMANDS_ONLY,
        )
        .apply { onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel)) }

    val result = tool.runInCodeOnTheGo("pwd")

    assertEquals("succeeded", result["status"])
    assertEquals("pwd", bridge.lastCommand)
    assertTrue(channel.receive() is SkillProgressToolAction)
    assertTrue(channel.receive() is SkillProgressToolAction)
  }

  @Test
  fun naturalCounterpartUpdateFromAlphaBuildsAndInstallsMain() = runBlocking {
    val bridge = FakeBridge(commandOutput = "BUILD SUCCESSFUL")
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      createTool(
          currentPackageName = JARVIS_ALPHA_PACKAGE,
          bridge = bridge,
          approvalMode = TerminalApprovalMode.DANGEROUS_COMMANDS_ONLY,
        )
        .apply { onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel)) }

    val invocation = async(Dispatchers.Default) { tool.updateOtherJarvisToMatchThisOne() }
    val approval = channel.receive() as AskSensitiveToolCallPermissionAction
    assertTrue(approval.command.contains("assembleDebug"))
    assertTrue(approval.command.contains(JARVIS_MAIN_PACKAGE))
    approval.result.complete(PermissionResult.ALLOW_ONCE)
    assertTrue((channel.receive() as SkillProgressToolAction).inProgress)
    assertTrue((channel.receive() as SkillProgressToolAction).inProgress)
    assertFalse((channel.receive() as SkillProgressToolAction).inProgress)
    val result = invocation.await()

    assertEquals("succeeded", result["status"])
    assertEquals("Android Jarvis Main", result["target"])
    assertTrue(bridge.lastCommand.contains("assembleDebug"))
    assertEquals(JARVIS_MAIN_PACKAGE, bridge.lastInstallPackage)
    assertTrue(bridge.installCalled)
  }

  @Test
  fun mainTargetsAlphaAndAlphaTargetsMain() {
    val alphaTarget = jarvisCounterpartFor(JARVIS_MAIN_PACKAGE)
    val mainTarget = jarvisCounterpartFor(JARVIS_ALPHA_PACKAGE)

    assertEquals("assembleAlpha", alphaTarget?.gradleTask)
    assertEquals(JARVIS_ALPHA_PACKAGE, alphaTarget?.packageName)
    assertEquals("assembleDebug", mainTarget?.gradleTask)
    assertEquals(JARVIS_MAIN_PACKAGE, mainTarget?.packageName)
  }

  @Test
  fun requestScriptRunsFromPrivateProjectAndPublishesAtomicResult() {
    val paths = CodeOnTheGoBridgePaths("abc123")
    val script = buildCodeOnTheGoRequestScript("printf 'hello'", paths)

    assertTrue(script.contains("cd '$CODE_ON_THE_GO_PROJECT_ROOT'"))
    assertTrue(script.contains("printf 'hello'"))
    assertTrue(script.contains("result-abc123.log.tmp"))
    assertTrue(script.contains("mv -f"))
    assertTrue(script.contains("result-abc123.exit"))
  }

  @Test
  fun terminalNavigationUsesVisibleLabelBounds() {
    val paths = CodeOnTheGoBridgePaths("abc123")
    val command = buildOpenCodeOnTheGoTerminalCommand(paths)

    assertTrue(paths.allTransientPaths.all { it.startsWith("$BRIDGE_DIRECTORY/") })
    assertTrue(paths.terminalUi.endsWith("terminal-abc123.xml"))
    assertTrue(command.contains("uiautomator dump"))
    assertTrue(command.contains("text=\"Terminal\""))
    assertTrue(command.contains("input tap"))
    assertTrue(command.contains("exit 73"))
  }

  @Test
  fun displaySizeParserUsesLastReportedOverride() {
    assertEquals(
      1080 to 2340,
      parseLastDisplaySize("Physical size: 1440x3120\nOverride size: 1080x2340\n"),
    )
  }

  @Test
  fun unifiedPatchValidation_acceptsSourceAndRejectsSecretsTraversalAndBinaryContent() {
    val accepted =
      validateUnifiedPatch(
        """
        diff --git a/app/src/main/java/example/Test.kt b/app/src/main/java/example/Test.kt
        index 1111111..2222222 100644
        --- a/app/src/main/java/example/Test.kt
        +++ b/app/src/main/java/example/Test.kt
        @@ -1 +1 @@
        -old
        +new
        """.trimIndent()
      )

    assertTrue(accepted.accepted)
    assertEquals(listOf("app/src/main/java/example/Test.kt"), accepted.paths)
    assertFalse(
      validateUnifiedPatch(
          "diff --git a/../secret b/../secret\nGIT binary patch"
        )
        .accepted
    )
    assertFalse(
      validateUnifiedPatch(
          "diff --git a/local.properties b/local.properties\n--- a/local.properties\n+++ b/local.properties"
        )
        .accepted
    )
  }

  @Test
  fun patchApplicationRequiresAChangedReviewedPath() {
    val command =
      buildApplyJarvisPatchCommand(
        encodedPatch = "cGF0Y2g=",
        patchPath = "$BRIDGE_DIRECTORY/patch-test.diff",
        reviewedPaths = listOf("app/src/main/Test.kt"),
      )

    assertTrue(command.contains("PATCH_DID_NOT_CHANGE_REVIEWED_PATHS"))
    assertTrue(command.contains("git status --porcelain=v1 -- 'app/src/main/Test.kt'"))
    assertTrue(command.contains("'/sdcard/Download/AndroidJarvisBridge/patch-test.diff'"))
  }

  @Test
  fun gitStatusParser_returnsOnlySafeChangedPaths() {
    assertEquals(
      setOf("app/src/main/A.kt", "app/src/main/New.kt"),
      parseGitStatusPaths(" M app/src/main/A.kt\n?? app/src/main/New.kt\n?? ../outside"),
    )
  }

  @Test
  fun verifiedDevelopment_stagesOnlyReviewedPathsAndReturnsScreenshotEvidence() = runBlocking {
    val baseline = "a".repeat(40)
    val verified = "b".repeat(40)
    val reviewedPath = "app/src/main/java/example/Test.kt"
    val store = FakeDevelopmentSessionStore()
    store.write(
      JarvisDevelopmentSession(
        id = "session123",
        goal = "Change visible test marker",
        branch = "jarvis-alpha-native-cortex",
        baselineCommit = baseline,
        startedAtEpochMs = 1L,
        counterpartPackage = JARVIS_MAIN_PACKAGE,
        changedPaths = listOf(reviewedPath),
      )
    )
    val bridge =
      FakeBridge(
        commandResults =
          listOf(
            CodeOnTheGoCommandResult(
              exitCode = 0,
              output =
                "__JARVIS_BRANCH__=jarvis-alpha-native-cortex\n" +
                  "__JARVIS_HEAD__=$baseline\n__JARVIS_STATUS__\n M $reviewedPath",
              error = "",
            ),
            CodeOnTheGoCommandResult(exitCode = 0, output = "BUILD SUCCESSFUL", error = ""),
            CodeOnTheGoCommandResult(
              exitCode = 0,
              output = "__JARVIS_VERIFIED_COMMIT__=$verified",
              error = "",
            ),
          )
      )
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      createTool(
          currentPackageName = JARVIS_ALPHA_PACKAGE,
          bridge = bridge,
          developmentSessionStore = store,
        )
        .apply { onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel)) }

    val invocation =
      async(Dispatchers.Default) {
        tool.verifyUpdateAndPromptTestOtherJarvis(
          prompt = "Reverse KO_SIVRAJ and reply with only the result.",
          expectedText = "JARVIS_OK",
          commitMessage = "Add verified capability",
        )
      }
    val approval = channel.receive() as AskSensitiveToolCallPermissionAction
    approval.result.complete(PermissionResult.ALLOW_ONCE)
    val result = invocation.await()

    assertEquals("succeeded", result["status"])
    assertEquals(verified, result["verified_commit"])
    assertEquals("true", result["expected_text_found"])
    assertTrue(result[TOOL_RESULT_INPUT_IMAGE_DATA_URL].orEmpty().startsWith("data:image/jpeg;base64,"))
    assertTrue(bridge.installCalled)
    assertTrue(bridge.testCalled)
    assertFalse(bridge.commands.last().contains("git add -A"))
    assertTrue(bridge.commands.last().contains("git add -- '$reviewedPath'"))
    assertEquals(verified, store.read()?.verifiedCommit)
  }

  private fun createTool(
    currentPackageName: String = JARVIS_ALPHA_PACKAGE,
    bridge: FakeBridge = FakeBridge(),
    approvalMode: TerminalApprovalMode = TerminalApprovalMode.EVERY_COMMAND,
    developmentSessionStore: JarvisDevelopmentSessionStore = FakeDevelopmentSessionStore(),
  ): CodeOnTheGoTool =
    CodeOnTheGoTool(
      currentPackageName = currentPackageName,
      codeOnTheGoEnvironment = FakeCodeOnTheGoEnvironment(),
      terminalEnvironment = FakeTermuxEnvironment(),
      bridge = bridge,
      selfAdbConnectionProvider = FakeSelfAdbConnectionProvider(),
      approvalModeStore = FakeApprovalModeStore(approvalMode),
      developmentSessionStore = developmentSessionStore,
    )

  private class FakeCodeOnTheGoEnvironment : CodeOnTheGoEnvironment {
    override fun status(): CodeOnTheGoEnvironmentStatus =
      CodeOnTheGoEnvironmentStatus(installed = true, versionName = "test-version")
  }

  private class FakeTermuxEnvironment : TermuxEnvironment {
    override fun status(): TermuxEnvironmentStatus =
      TermuxEnvironmentStatus(
        installed = true,
        versionName = "test-termux",
        runCommandPermissionGranted = true,
      )
  }

  private class FakeApprovalModeStore(private val mode: TerminalApprovalMode) :
    TerminalApprovalModeStore {
    override fun getMode(): TerminalApprovalMode = mode

    override fun setMode(mode: TerminalApprovalMode) = Unit
  }

  private class FakeSelfAdbConnectionProvider : SelfAdbConnectionProvider {
    override suspend fun checkConnection(): SelfAdbConnectionResult =
      SelfAdbConnectionResult(
        succeeded = true,
        serial = "192.168.50.17:41234",
        message = "Verified this phone",
      )

    override suspend fun getOrPair(
      actionChannel: kotlinx.coroutines.channels.SendChannel<ToolAction>?
    ): SelfAdbConnectionResult = checkConnection()
  }

  private class FakeDevelopmentSessionStore : JarvisDevelopmentSessionStore {
    private var session: JarvisDevelopmentSession? = null

    override fun read(): JarvisDevelopmentSession? = session

    override fun write(session: JarvisDevelopmentSession) {
      this.session = session
    }

    override fun clear() {
      session = null
    }
  }

  private class FakeBridge(
    private val commandOutput: String = "",
    commandResults: List<CodeOnTheGoCommandResult> = emptyList(),
  ) : CodeOnTheGoBridge {
    private val queuedCommandResults = commandResults.toMutableList()
    var executeCalled = false
    var installCalled = false
    var testCalled = false
    var lastCommand = ""
    var lastSerial = ""
    var lastInstallPackage = ""
    val commands = mutableListOf<String>()

    override suspend fun execute(
      command: String,
      serial: String,
      returnPackageName: String,
      timeoutMs: Long,
    ): CodeOnTheGoCommandResult {
      executeCalled = true
      lastCommand = command
      lastSerial = serial
      commands += command
      return if (queuedCommandResults.isNotEmpty()) queuedCommandResults.removeAt(0)
      else CodeOnTheGoCommandResult(exitCode = 0, output = commandOutput, error = "")
    }

    override suspend fun install(
      apkPath: String,
      targetPackageName: String,
      serial: String,
    ): CodeOnTheGoInstallResult {
      installCalled = true
      lastInstallPackage = targetPackageName
      return CodeOnTheGoInstallResult(
        succeeded = true,
        output = "Success",
        error = "",
        backupApkPath = "/sdcard/Download/AndroidJarvisEvidence/backup.apk",
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
    ): JarvisPromptTestResult =
      JarvisPromptTestResult(
        launched = true,
        processRunning = true,
        expectedTextFound = true,
        fatalCrashDetected = false,
        screenshotBase64 = "c2NyZWVuc2hvdA==",
        screenshotPath = "/sdcard/Download/AndroidJarvisEvidence/test.png",
        screenshotSha256 = "a".repeat(64),
        logcatErrors = "",
        error = "",
      ).also { testCalled = true }

    override suspend fun restoreBackup(
      backupApkPath: String,
      targetPackageName: String,
      serial: String,
    ): CodeOnTheGoInstallResult =
      CodeOnTheGoInstallResult(
        succeeded = true,
        output = "Success",
        error = "",
        backupApkPath = backupApkPath,
      )
  }
}
