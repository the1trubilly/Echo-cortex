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
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.runBlocking

/** Native, provider-neutral terminal and ADB capabilities backed by Termux. */
class TermuxTerminalTool(
  private val environment: TermuxEnvironment,
  private val runner: TermuxCommandRunner,
  private val selfAdbConnectionProvider: SelfAdbConnectionProvider =
    ExistingAdbConnectionProvider,
  private val approvalModeStore: TerminalApprovalModeStore =
    ConfirmEveryTerminalCommandModeStore,
) : ToolDefinition {
  constructor(
    context: Context
  ) : this(
    environment = AndroidTermuxEnvironment(context),
    runner = AndroidTermuxCommandRunner(context),
    selfAdbConnectionProvider =
      AndroidSelfAdbConnectionProvider(context, AndroidTermuxCommandRunner(context)),
    approvalModeStore = AndroidTerminalApprovalModeStore(context),
  )

  override val alwaysAllow: Boolean = false
  override var executionContext: ToolExecutionContext? = null

  @Tool(
    description =
      "Check whether the native Termux terminal bridge is installed and authorized. This does " +
        "not execute a command. Call it before runTerminal or runAdb when setup is uncertain."
  )
  fun terminalStatus(): Map<String, String> {
    val status = environment.status()
    return mapOf(
      "status" to if (status.installed) "available" else "setup_required",
      "termux_installed" to status.installed.toString(),
      "termux_version" to status.versionName,
      "run_command_permission" to
        if (status.runCommandPermissionGranted) "granted" else "not_granted",
      "approval_mode" to approvalModeStore.getMode().name.lowercase(),
      "external_apps_setting" to "verified only by running a user-approved test command",
      "execution_runtime" to "persistent tmux session named android-jarvis",
      "tmux_setup" to "requires the Termux tmux package",
      "adb_setup" to
        "requires Termux android-tools; Jarvis checks, pairs with user help, and verifies this phone",
    )
  }

  @Tool(
    description =
      "Run one shell command in the persistent android-jarvis tmux session in Termux and return " +
        "exit code, stdout, and stderr. The user's approval mode is enforced by a deterministic " +
        "on-device safety policy. Never " +
        "use this for credentials or destructive actions unless the user explicitly requested them."
  )
  fun runTerminal(
    @ToolParam(description = "The exact Bash command to run in the Termux home directory.")
    command: String
  ): Map<String, String> = runApproved(displayName = "Termux terminal", command = command)

  @Tool(
    description =
      "Run one ADB command inside the persistent android-jarvis tmux session through Termux's " +
        "android-tools package. Supply arguments after 'adb', for example 'devices -l' or " +
        "'shell getprop ro.product.model'. The user's approval mode is enforced by a deterministic " +
        "on-device safety policy. For device commands, Jarvis automatically checks for a verified " +
        "connection to this same phone. If needed, it opens Developer options and asks the user for " +
        "Android's temporary six-digit pairing code in a private notification, then resumes the " +
        "approved command. Do not call adb pair, connect, or select another device yourself."
  )
  fun runAdb(
    @ToolParam(description = "Arguments after adb. Pairing and selection of this phone are handled internally; never include a pairing code or target selector.")
    adbArguments: String
  ): Map<String, String> {
    val normalizedArguments = adbArguments.trim().removePrefix("adb ").trim()
    if (normalizedArguments.isBlank()) {
      return errorResult("ADB arguments cannot be empty.")
    }
    if (MANAGED_ADB_TRANSPORT.matches(normalizedArguments)) {
      return errorResult(
        "Jarvis manages wireless pairing and connection automatically. Request the intended " +
          "device action instead of calling adb pair/connect/disconnect directly."
      )
    }
    if (ADB_TARGET_SELECTOR.containsMatchIn(normalizedArguments)) {
      return errorResult(
        "ADB target selectors are not accepted. Jarvis verifies and targets only this phone."
      )
    }
    return runApproved(
      displayName = "ADB via Termux",
      commandForApproval = "adb $normalizedArguments",
      commandResolver = { actionChannel ->
        val adbPrefix =
          "command -v adb >/dev/null 2>&1 || { " +
            "echo 'ADB is not installed in Termux. Run: pkg install android-tools' >&2; exit 127; }; "
        if (HOST_ONLY_ADB_COMMAND.matches(normalizedArguments)) {
          ResolvedTerminalCommand(command = "${adbPrefix}adb $normalizedArguments")
        } else {
          val connection = selfAdbConnectionProvider.getOrPair(actionChannel)
          if (!connection.succeeded) {
            ResolvedTerminalCommand(error = connection.message)
          } else if (!SAFE_ADB_SERIAL.matches(connection.serial)) {
            ResolvedTerminalCommand(error = "Jarvis could not validate the ADB target returned by pairing.")
          } else {
            ResolvedTerminalCommand(
              command = "${adbPrefix}adb -s '${connection.serial}' $normalizedArguments"
            )
          }
        }
      },
    )
  }

  private fun runApproved(
    displayName: String,
    command: String = "",
    commandForApproval: String = command,
    commandResolver: (suspend (SendChannel<ToolAction>) -> ResolvedTerminalCommand)? = null,
  ): Map<String, String> =
    runBlocking(Dispatchers.IO) {
      val approvalCommand = commandForApproval.trim()
      if (approvalCommand.isBlank()) return@runBlocking errorResult("Command cannot be empty.")
      if (approvalCommand.length > MAX_COMMAND_CHARS) {
        return@runBlocking errorResult("Command is longer than $MAX_COMMAND_CHARS characters.")
      }
      val status = environment.status()
      if (!status.installed) {
        return@runBlocking errorResult(
          "Termux is not installed. Install the official Termux app, then open Terminal & ADB setup."
        )
      }
      val actionChannel = executionContext?.actionChannel
        ?: return@runBlocking errorResult(
          "Terminal commands require an active Jarvis chat so the user can review and approve them."
        )
      val requiresApproval =
        approvalModeStore.getMode() == TerminalApprovalMode.EVERY_COMMAND ||
          TerminalCommandSafetyPolicy.requiresApproval(commandForApproval)
      if (requiresApproval) {
        val approval =
          AskSensitiveToolCallPermissionAction(
            toolName = displayName,
            command = commandForApproval,
          )
        actionChannel.send(approval)
        if (approval.result.await() != PermissionResult.ALLOW_ONCE) {
          return@runBlocking errorResult("Command denied by user.")
        }
      }
      if (!status.runCommandPermissionGranted) {
        val permission =
          RequestPermissionToolAction(permission = TermuxRunCommandContract.RUN_COMMAND_PERMISSION)
        actionChannel.send(permission)
        if (!permission.result.await()) {
          return@runBlocking errorResult(
            "Android's Run commands in Termux permission was not granted."
          )
        }
      }
      val resolvedCommand =
        try {
          commandResolver?.invoke(actionChannel) ?: ResolvedTerminalCommand(command = command)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          return@runBlocking errorResult(
            error.message ?: "Jarvis could not prepare the approved command."
          )
        }
      if (resolvedCommand.error.isNotBlank()) {
        return@runBlocking errorResult(resolvedCommand.error)
      }
      val normalizedCommand = resolvedCommand.command.trim()
      if (normalizedCommand.isBlank()) {
        return@runBlocking errorResult("Jarvis could not prepare the approved command.")
      }
      if (normalizedCommand.length > MAX_COMMAND_CHARS) {
        return@runBlocking errorResult("Command is longer than $MAX_COMMAND_CHARS characters.")
      }
      actionChannel.send(
        SkillProgressToolAction(
          label = "Running $displayName",
          inProgress = true,
          addItemTitle = displayName,
          addItemDescription = commandForApproval,
        )
      )
      val result =
        try {
          runner.run(normalizedCommand)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          return@runBlocking errorResult(
            error.message ?: "Termux rejected the command before it could run."
          )
        }
      val mapped = result.toToolResult()
      actionChannel.send(
        SkillProgressToolAction(
          label = if (result.succeeded) "$displayName finished" else "$displayName failed",
          inProgress = false,
          addItemTitle = "$displayName result",
          addItemDescription = mapped.toProgressDescription(),
        )
      )
      mapped
    }

  private fun TermuxCommandResult.toToolResult(): Map<String, String> =
    linkedMapOf(
      "status" to if (succeeded) "succeeded" else if (timedOut) "timed_out" else "failed",
      "exit_code" to (exitCode?.toString() ?: "unavailable"),
      "stdout" to stdout,
      "stderr" to stderr,
      "internal_error" to internalErrorMessage,
      "stdout_truncated" to stdoutTruncated.toString(),
      "stderr_truncated" to stderrTruncated.toString(),
    )

  private fun Map<String, String>.toProgressDescription(): String =
    buildString {
      append("Status: ").append(getValue("status"))
      append("\nExit code: ").append(getValue("exit_code"))
      getValue("stdout").takeIf(String::isNotBlank)?.let { append("\n\nstdout:\n").append(it) }
      getValue("stderr").takeIf(String::isNotBlank)?.let { append("\n\nstderr:\n").append(it) }
      getValue("internal_error")
        .takeIf(String::isNotBlank)
        ?.let { append("\n\nTermux error:\n").append(it) }
    }

  private fun errorResult(message: String): Map<String, String> =
    mapOf("status" to "failed", "error" to message)

  private companion object {
    const val MAX_COMMAND_CHARS = 8_192
    val HOST_ONLY_ADB_COMMAND =
      Regex("^(?:version|help|devices(?:\\s+-l)?|mdns\\s+services)$", RegexOption.IGNORE_CASE)
    val MANAGED_ADB_TRANSPORT =
      Regex("^(?:pair|connect|disconnect|kill-server|start-server)(?:\\s+.*)?$", RegexOption.IGNORE_CASE)
    val ADB_TARGET_SELECTOR =
      Regex("(?:^|\\s)(?:-s|-d|-e|-H|-P|-L|--one-device)(?:\\s|=|$)")
    val SAFE_ADB_SERIAL = Regex("^[A-Za-z0-9._:%\\-\\[\\]]+$")
  }
}

private data class ResolvedTerminalCommand(
  val command: String = "",
  val error: String = "",
)
