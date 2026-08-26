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

enum class TerminalApprovalMode {
  EVERY_COMMAND,
  DANGEROUS_COMMANDS_ONLY,
}

interface TerminalApprovalModeStore {
  fun getMode(): TerminalApprovalMode

  fun setMode(mode: TerminalApprovalMode)
}

/** Persists the user's terminal approval policy locally. No command approval is persisted. */
class AndroidTerminalApprovalModeStore(context: Context) : TerminalApprovalModeStore {
  private val preferences =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override fun getMode(): TerminalApprovalMode =
    preferences
      .getString(KEY_APPROVAL_MODE, null)
      ?.let { stored -> TerminalApprovalMode.entries.firstOrNull { it.name == stored } }
      ?: TerminalApprovalMode.EVERY_COMMAND

  override fun setMode(mode: TerminalApprovalMode) {
    preferences.edit().putString(KEY_APPROVAL_MODE, mode.name).apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "jarvis_terminal_safety"
    const val KEY_APPROVAL_MODE = "approval_mode"
  }
}

internal object ConfirmEveryTerminalCommandModeStore : TerminalApprovalModeStore {
  override fun getMode(): TerminalApprovalMode = TerminalApprovalMode.EVERY_COMMAND

  override fun setMode(mode: TerminalApprovalMode) = Unit
}

/**
 * Conservative, deterministic policy for the optional low-interruption mode.
 *
 * A command runs without a prompt only when the complete command matches a narrow read-only
 * allowlist. Shell control operators, redirection, substitution, multiline input, state-changing
 * ADB operations, and unknown executables therefore always require confirmation.
 */
internal object TerminalCommandSafetyPolicy {
  private const val TOKEN = "[A-Za-z0-9._/:@%+=,-]+"
  private const val PROGRAM = "[A-Za-z0-9._+-]+"
  private const val SAFE_SINGLE_QUOTED = "'[^']*'"
  private const val SAFE_DOUBLE_QUOTED = "\"[^\"`$\\\\]*\""
  private const val OUTPUT_ARGUMENT = "(?:$TOKEN|$SAFE_SINGLE_QUOTED|$SAFE_DOUBLE_QUOTED)"

  private val safeCommands =
    listOf(
      Regex("^(?:pwd|whoami|uptime)$"),
      Regex("^id(?:\\s+-[A-Za-z]+)?$"),
      Regex("^uname(?:\\s+-[A-Za-z]+)?$"),
      Regex("^date(?:\\s+$TOKEN)*$"),
      Regex("^(?:echo|printf)(?:\\s+$OUTPUT_ARGUMENT)*$"),
      Regex("^command\\s+-v\\s+$PROGRAM$"),
      Regex("^which(?:\\s+-a)?\\s+$PROGRAM$"),
      Regex("^type\\s+$PROGRAM$"),
      Regex("^ls(?:\\s+(?:-[A-Za-z]+|$TOKEN))*$"),
      Regex("^(?:df|free)(?:\\s+-[A-Za-z]+)?$"),
      Regex("^getprop(?:\\s+[A-Za-z0-9._-]+)?$"),
      Regex("^tmux\\s+(?:ls|list-sessions)$"),
      Regex("^tmux\\s+display-message\\s+-p\\s+'#S'$"),
      Regex("^adb\\s+(?:version|get-state|devices(?:\\s+-l)?)$"),
      Regex("^git(?:\\s+-C\\s+$OUTPUT_ARGUMENT)?\\s+(?:add|branch\\s+--show-current|commit|diff|fetch|log|rev-parse|show|status)(?:\\s+$OUTPUT_ARGUMENT)*$"),
      Regex("(?i)^(?!.*(?:install|uninstall|publish|upload|wrapper))(?:jarvis-gradle|(?:\\./)?gradlew)(?:\\s+$OUTPUT_ARGUMENT)*$"),
      Regex("^tmux\\s+(?:capture-pane|display-message|has-session|list-panes|list-sessions|list-windows|ls|rename-window|select-window)(?:\\s+$OUTPUT_ARGUMENT)*$"),
      Regex("^(?:grep|rg|find|head|tail|stat|du|wc|file|readlink|realpath|pgrep|ps)(?:\\s+$OUTPUT_ARGUMENT)*$"),
    )

  fun requiresApproval(command: String): Boolean {
    val normalized = command.trim()
    if (normalized.isEmpty() || normalized.contains('\n') || normalized.contains('\r')) return true
    return safeCommands.none { pattern -> pattern.matches(normalized) }
  }
}
