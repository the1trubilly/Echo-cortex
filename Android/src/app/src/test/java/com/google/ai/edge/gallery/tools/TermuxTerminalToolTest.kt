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

class TermuxTerminalToolTest {
  @Test
  fun terminalStatus_reportsInstalledPermissionStateWithoutExecution() {
    val runner = FakeRunner()
    val tool =
      TermuxTerminalTool(
        environment = FakeEnvironment(installed = true, permissionGranted = false),
        runner = runner,
      )

    val result = tool.terminalStatus()

    assertEquals("available", result["status"])
    assertEquals("not_granted", result["run_command_permission"])
    assertFalse(runner.wasCalled)
  }

  @Test
  fun runTerminal_requiresOneTimeApprovalAndReturnsOutput() = runBlocking {
    val runner = FakeRunner(stdout = "JARVIS_TERMUX_OK\n")
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      TermuxTerminalTool(
          environment = FakeEnvironment(installed = true, permissionGranted = true),
          runner = runner,
        )
        .apply { onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel)) }

    val invocation = async(Dispatchers.Default) { tool.runTerminal("printf JARVIS_TERMUX_OK") }
    val permission = channel.receive() as AskSensitiveToolCallPermissionAction
    assertEquals("printf JARVIS_TERMUX_OK", permission.command)
    permission.result.complete(PermissionResult.ALLOW_ONCE)
    val progressStarted = channel.receive() as SkillProgressToolAction
    val progressFinished = channel.receive() as SkillProgressToolAction
    val result = invocation.await()

    assertTrue(progressStarted.inProgress)
    assertFalse(progressFinished.inProgress)
    assertEquals("succeeded", result["status"])
    assertEquals("JARVIS_TERMUX_OK\n", result["stdout"])
    assertEquals("printf JARVIS_TERMUX_OK", runner.lastCommand)
  }

  @Test
  fun runTerminal_deniedCommandNeverReachesTermux() = runBlocking {
    val runner = FakeRunner()
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      TermuxTerminalTool(
          environment = FakeEnvironment(installed = true, permissionGranted = true),
          runner = runner,
        )
        .apply { onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel)) }

    val invocation = async(Dispatchers.Default) { tool.runTerminal("rm important-file") }
    val permission = channel.receive() as AskSensitiveToolCallPermissionAction
    permission.result.complete(PermissionResult.DENY)
    val result = invocation.await()

    assertEquals("failed", result["status"])
    assertEquals("Command denied by user.", result["error"])
    assertFalse(runner.wasCalled)
  }

  @Test
  fun runAdb_wrapsArgumentsInCheckedAdbCommand() = runBlocking {
    val runner = FakeRunner(stdout = "device-list")
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      TermuxTerminalTool(
          environment = FakeEnvironment(installed = true, permissionGranted = true),
          runner = runner,
        )
        .apply { onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel)) }

    val invocation = async(Dispatchers.Default) { tool.runAdb("adb devices -l") }
    val permission = channel.receive() as AskSensitiveToolCallPermissionAction
    assertEquals("adb devices -l", permission.command)
    permission.result.complete(PermissionResult.ALLOW_ONCE)
    channel.receive()
    channel.receive()
    val result = invocation.await()

    assertEquals("succeeded", result["status"])
    assertTrue(runner.lastCommand.endsWith("adb devices -l"))
  }

  @Test
  fun runAdb_deviceCommandPairsIfNeededAndTargetsVerifiedPhone() = runBlocking {
    val runner = FakeRunner(stdout = "SM-S928U\n")
    val connectionProvider = FakeSelfAdbConnectionProvider(serial = "192.168.50.17:41234")
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      TermuxTerminalTool(
          environment = FakeEnvironment(installed = true, permissionGranted = true),
          runner = runner,
          selfAdbConnectionProvider = connectionProvider,
        )
        .apply { onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel)) }

    val invocation = async(Dispatchers.Default) { tool.runAdb("shell getprop ro.product.model") }
    val permission = channel.receive() as AskSensitiveToolCallPermissionAction
    permission.result.complete(PermissionResult.ALLOW_ONCE)
    channel.receive()
    channel.receive()
    val result = invocation.await()

    assertTrue(connectionProvider.getOrPairCalled)
    assertEquals("succeeded", result["status"])
    assertTrue(
      runner.lastCommand.endsWith(
        "adb -s '192.168.50.17:41234' shell getprop ro.product.model"
      )
    )
  }

  @Test
  fun runAdb_rejectsModelSuppliedTargetBeforeApprovalOrExecution() {
    val runner = FakeRunner()
    val tool =
      TermuxTerminalTool(
        environment = FakeEnvironment(installed = true, permissionGranted = true),
        runner = runner,
      )

    val result = tool.runAdb("-s 192.168.50.99:5555 shell id")

    assertEquals("failed", result["status"])
    assertFalse(runner.wasCalled)
  }

  @Test
  fun dangerousOnlyMode_runsAllowlistedReadOnlyCommandWithoutApproval() = runBlocking {
    val runner = FakeRunner(stdout = "/data/data/com.termux/files/home\n")
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      TermuxTerminalTool(
          environment = FakeEnvironment(installed = true, permissionGranted = true),
          runner = runner,
          approvalModeStore = FakeApprovalModeStore(TerminalApprovalMode.DANGEROUS_COMMANDS_ONLY),
        )
        .apply { onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel)) }

    val result = tool.runTerminal("pwd")

    assertEquals("succeeded", result["status"])
    assertEquals("pwd", runner.lastCommand)
    assertTrue(channel.receive() is SkillProgressToolAction)
    assertTrue(channel.receive() is SkillProgressToolAction)
  }

  @Test
  fun dangerousOnlyMode_unknownCommandStillRequiresApproval() = runBlocking {
    val runner = FakeRunner()
    val channel = Channel<ToolAction>(Channel.UNLIMITED)
    val tool =
      TermuxTerminalTool(
          environment = FakeEnvironment(installed = true, permissionGranted = true),
          runner = runner,
          approvalModeStore = FakeApprovalModeStore(TerminalApprovalMode.DANGEROUS_COMMANDS_ONLY),
        )
        .apply { onAttach(ToolExecutionContext(taskId = "test", actionChannel = channel)) }

    val invocation = async(Dispatchers.Default) { tool.runTerminal("pkg install python") }
    val permission = channel.receive() as AskSensitiveToolCallPermissionAction
    assertEquals("pkg install python", permission.command)
    permission.result.complete(PermissionResult.DENY)

    assertEquals("failed", invocation.await()["status"])
    assertFalse(runner.wasCalled)
  }

  private class FakeEnvironment(
    private val installed: Boolean,
    private val permissionGranted: Boolean,
  ) : TermuxEnvironment {
    override fun status(): TermuxEnvironmentStatus =
      TermuxEnvironmentStatus(
        installed = installed,
        versionName = if (installed) "test-version" else "",
        runCommandPermissionGranted = permissionGranted,
      )
  }

  private class FakeRunner(private val stdout: String = "") : TermuxCommandRunner {
    var wasCalled = false
    var lastCommand = ""

    override suspend fun run(command: String, timeoutMs: Long): TermuxCommandResult {
      wasCalled = true
      lastCommand = command
      return TermuxCommandResult(
        exitCode = 0,
        stdout = stdout,
        stderr = "",
        internalErrorCode = -1,
        internalErrorMessage = "",
        stdoutTruncated = false,
        stderrTruncated = false,
      )
    }
  }

  private class FakeApprovalModeStore(private val mode: TerminalApprovalMode) :
    TerminalApprovalModeStore {
    override fun getMode(): TerminalApprovalMode = mode

    override fun setMode(mode: TerminalApprovalMode) = Unit
  }

  private class FakeSelfAdbConnectionProvider(private val serial: String) :
    SelfAdbConnectionProvider {
    var getOrPairCalled = false

    override suspend fun checkConnection(): SelfAdbConnectionResult =
      SelfAdbConnectionResult(succeeded = false, message = "Not connected")

    override suspend fun getOrPair(
      actionChannel: kotlinx.coroutines.channels.SendChannel<ToolAction>?
    ): SelfAdbConnectionResult {
      getOrPairCalled = true
      return SelfAdbConnectionResult(
        succeeded = true,
        serial = serial,
        message = "Verified this phone",
      )
    }
  }
}
