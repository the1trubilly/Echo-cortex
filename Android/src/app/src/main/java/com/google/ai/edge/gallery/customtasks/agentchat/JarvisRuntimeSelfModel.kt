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

package com.google.ai.edge.gallery.customtasks.agentchat

import com.google.ai.edge.gallery.BuildConfig
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.RuntimeType

/** Builds truthful, current-session facts that Jarvis can use to explain how it works. */
object JarvisRuntimeSelfModel {
  internal const val SECTION_HEADER = "## Runtime Self-Model (authoritative current-session facts)"
  private const val START_MARKER = "<!-- JARVIS_RUNTIME_SELF_MODEL_START -->"
  private const val END_MARKER = "<!-- JARVIS_RUNTIME_SELF_MODEL_END -->"
  private const val MAX_LISTED_CAPABILITIES = 24

  fun appendTo(
    systemInstructions: String,
    model: Model,
    enabledSkillNames: List<String>,
    enabledMcpToolNames: List<String>,
    appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    nativeCortexEnabled: Boolean = BuildConfig.NATIVE_CORTEX_ENABLED,
  ): String {
    val baseInstructions = removeExistingSection(systemInstructions)
    val modelDisplayName = model.displayName.ifBlank { model.name }
    val inputModalities =
      buildList {
          add("text")
          if (model.llmSupportImage) add("images")
          if (model.llmSupportAudio) add("audio clips")
        }
        .joinToString(", ")
    val runtimeDescription = runtimeDescription(model.runtimeType)
    val skillSummary = summarizeNames(enabledSkillNames)
    val mcpSummary = summarizeNames(enabledMcpToolNames)
    val toolExecutionDescription =
      when {
        model.runtimeType == RuntimeType.OPENAI ->
          "OpenAI Responses function calls run through Android Jarvis's existing Skill/MCP tool " +
            "dispatcher. Tool results are returned to the model for the next reasoning step, " +
            "with Android permissions and explicit confirmation where required."
        enabledSkillNames.isEmpty() && enabledMcpToolNames.isEmpty() ->
          "No Skill or MCP capability is enabled for this session."
        else ->
          "Enabled Skills and MCP tools run through Android Jarvis's tool dispatcher, with " +
            "Android permissions and explicit confirmation where required."
      }
    val nativeToolDescription =
      "Termux terminal and ADB-via-Termux functions are registered as native, provider-neutral " +
        "tools. Commands run inside the persistent android-jarvis tmux session. The user chooses " +
        "between approving every command and approving dangerous commands; the latter uses a " +
        "narrow deterministic read-only allowlist and asks for unknown or state-changing commands. " +
        "Operational use still depends on Termux setup, Android's Run commands in Termux " +
        "permission, the tmux package, and the android-tools package. For a device ADB action, " +
        "the native tool checks for a connection verified against this phone's Android build " +
        "fingerprint. If disconnected, it opens Developer options, accepts Android's temporary " +
        "six-digit pairing code through a private notification reply, pairs and reconnects, then " +
        "automatically resumes the already-approved command against only the verified phone. The " +
        "pairing code is handled by native code and is not exposed to the model. A native Code on " +
        "the Go bridge can inspect or edit the private Jarvis repository and can make the other " +
        "installed Jarvis match this one. Natural requests such as 'update Main to match you' are " +
        "translated into the correct counterpart build, signing, and in-place install while " +
        "preserving the other app's data. Code on the Go receives one short shared-storage bridge " +
        "script through verified self-ADB; command output is returned to this chat and transient " +
        "bridge files are removed after a completed operation. Jarvis can begin an auditable " +
        "development session only from a clean phone checkout; perform bounded source listing, " +
        "exact search and line reads; apply validated unified diffs after showing the exact patch " +
        "for approval; and run a high-level counterpart verification cycle. That cycle runs unit " +
        "tests, builds, preserves the installed counterpart APK, updates without clearing data, " +
        "launches a real Agent Chat prompt, checks process and Logcat state, captures a screenshot, " +
        "feeds the screenshot back as OpenAI vision input, renders it in chat, and commits only the " +
        "reviewed paths after success. A failed device test restores the prior counterpart APK. A " +
        "verified update can be rolled back with an auditable Git revert plus the saved APK."
    val memoryDescription =
      if (nativeCortexEnabled) {
        "Jarvis Alpha's native Kotlin Cortex saves Billy's exact turn and Jarvis's completed " +
          "reply separately as Markdown. Before each Agent Chat turn, it retrieves a bounded, " +
          "hash-verified packet from prior sessions with strict USER_STATED versus OTHER_AGENT " +
          "provenance and writes a retrieval receipt. This is the first native ThreadKeeper " +
          "memory-cycle slice; semantic records, links, checkpoints, synthesis, and outcome " +
          "learning are not implemented yet."
      } else {
        "Native long-term Cortex memory is disabled in this build. Saved chat history and any " +
          "explicitly enabled memory skill are the only available continuity mechanisms."
      }

    val runtimeSection =
      """
      $START_MARKER
      $SECTION_HEADER
      - Application: Android Jarvis $appVersion.
      - Active model shown to the user: $modelDisplayName.
      - Exact configured provider model ID: `${model.name}`.
      - Inference path: $runtimeDescription
      - Current accepted inputs: $inputModalities.
      - Enabled Skills: $skillSummary.
      - Enabled MCP tools: $mcpSummary.
      - Tool execution status: $toolExecutionDescription
      - Native terminal/device tools: $nativeToolDescription
      - Prompt assembly order: task instructions, saved System Instructions, saved Personality Prompt, then this authoritative runtime section.
      - Memory status: $memoryDescription
      - Self-extension status: you cannot grant yourself permissions or weaken the user's terminal safety mode. Registered native tools enforce either per-command approval or a deterministic low-risk allowlist; dangerous and unknown commands require exact-command approval. When the user naturally asks you to add or change your Android capabilities, use the native auditable development session, inspect before editing, submit validated patches for review, and update the other installed Jarvis as the test target. A build is not proof: require the returned prompt-test state, Logcat check, screenshot hash, and visible screenshot before claiming success. Use the simpler counterpart-sync operation only when no source edit or prompt test is needed.

      Runtime truth rules:
      - When asked what model, provider, runtime, inputs, memory, or tools you use, answer from this section. Do not say you lack visibility into these facts.
      - Distinguish the Android Jarvis identity from the active underlying model and provider.
      - Never claim a capability, tool call, installation, code change, memory write, or self-modification occurred unless the runtime or a tool result confirms it.
      $END_MARKER
      """
        .trimIndent()

    return listOf(baseInstructions, runtimeSection).filter { it.isNotBlank() }.joinToString("\n\n")
  }

  private fun runtimeDescription(runtimeType: RuntimeType): String =
    when (runtimeType) {
      RuntimeType.OPENAI ->
        "OpenAI Responses API over the network using OpenAiAgentRuntimeExecutor, selected by " +
          "RoutingAgentRuntimeExecutor."
      RuntimeType.LITERT_LM ->
        "on-device LiteRT-LM using DefaultAgentRuntimeExecutor, selected by " +
          "RoutingAgentRuntimeExecutor."
      RuntimeType.AICORE -> "on-device Android AICore through the app's local model path."
      RuntimeType.UNKNOWN -> "the app's legacy or unspecified model runtime."
    }

  private fun summarizeNames(names: List<String>): String {
    val normalized = names.map(String::trim).filter(String::isNotEmpty).distinct().sorted()
    if (normalized.isEmpty()) return "none"
    val visible = normalized.take(MAX_LISTED_CAPABILITIES).joinToString(", ")
    val remaining = normalized.size - MAX_LISTED_CAPABILITIES
    return if (remaining > 0) "$visible, and $remaining more" else visible
  }

  private fun removeExistingSection(instructions: String): String {
    val start = instructions.indexOf(START_MARKER)
    if (start < 0) return instructions.trim()
    val end = instructions.indexOf(END_MARKER, start)
    if (end < 0) return instructions.trim()
    return (instructions.substring(0, start) + instructions.substring(end + END_MARKER.length))
      .trim()
  }
}
