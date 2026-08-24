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
  fun displaySizeParserUsesLastReportedOverride() {
    assertEquals(
      1080 to 2340,
      parseLastDisplaySize("Physical size: 1440x3120\nOverride size: 1080x2340\n"),
    )
  }

  private fun createTool(
    currentPackageName: String = JARVIS_ALPHA_PACKAGE,
    bridge: FakeBridge = FakeBridge(),
    approvalMode: TerminalApprovalMode = TerminalApprovalMode.EVERY_COMMAND,
  ): CodeOnTheGoTool =
    CodeOnTheGoTool(
      currentPackageName = currentPackageName,
      codeOnTheGoEnvironment = FakeCodeOnTheGoEnvironment(),
      terminalEnvironment = FakeTermuxEnvironment(),
      bridge = bridge,
      selfAdbConnectionProvider = FakeSelfAdbConnectionProvider(),
      approvalModeStore = FakeApprovalModeStore(approvalMode),
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

  private class FakeBridge(private val commandOutput: String = "") : CodeOnTheGoBridge {
    var executeCalled = false
    var installCalled = false
    var lastCommand = ""
    var lastSerial = ""
    var lastInstallPackage = ""

    override suspend fun execute(
      command: String,
      serial: String,
      returnPackageName: String,
      timeoutMs: Long,
    ): CodeOnTheGoCommandResult {
      executeCalled = true
      lastCommand = command
      lastSerial = serial
      return CodeOnTheGoCommandResult(exitCode = 0, output = commandOutput, error = "")
    }

    override suspend fun install(
      apkPath: String,
      targetPackageName: String,
      serial: String,
    ): CodeOnTheGoInstallResult {
      installCalled = true
      lastInstallPackage = targetPackageName
      return CodeOnTheGoInstallResult(succeeded = true, output = "Success", error = "")
    }
  }
}
