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
      - Prompt assembly order: task instructions, saved System Instructions, saved Personality Prompt, then this authoritative runtime section.
      - Memory status: $memoryDescription
      - Self-extension status: you cannot currently edit the APK or source code, grant yourself permissions, or autonomously install/update tools. You may inspect the facts provided here, explain the architecture, and propose additions. Future self-extension must use an app-level propose, review, install, test, and rollback flow with user approval.

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
