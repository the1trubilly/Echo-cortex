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

package com.google.ai.edge.gallery.common

import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.SystemPromptRepository
import com.google.ai.edge.gallery.data.Task
import kotlinx.coroutines.flow.firstOrNull

/** Helper object for system prompt retrieval and compilation. */
object SystemPromptHelper {

  internal const val SYSTEM_INSTRUCTIONS_HEADER = "## Saved System Instructions"
  internal const val PERSONALITY_PROMPT_HEADER = "## Saved Personality Prompt"
  private val TASKS_WITH_SAVED_PROMPTS =
    setOf(
      BuiltInTaskId.LLM_CHAT,
      BuiltInTaskId.LLM_AGENT_CHAT,
      BuiltInTaskId.LLM_ASK_IMAGE,
      BuiltInTaskId.LLM_ASK_AUDIO,
    )

  /**
   * Retrieves the effective system prompt for the given [Task].
   *
   * Returns the user-defined custom prompt from the [SystemPromptRepository] if available;
   * otherwise, falls back to the task's default system prompt.
   *
   * @param repo The optional [SystemPromptRepository] for custom overrides. If null, returns the
   *   default.
   * @param task The target [Task] containing the identifier and the default fallback system prompt.
   * @return A [String] representing the effective system prompt instructions.
   */
  suspend fun getTaskSystemPrompt(repo: SystemPromptRepository?, task: Task): String {
    if (repo == null) return task.defaultSystemPrompt
    val customPrompt = repo.getCustomSystemPrompt(task.id).firstOrNull()
    return customPrompt ?: task.defaultSystemPrompt
  }

  /**
   * Compiles the prompt passed to model initialization in this order: task instructions, saved
   * System Instructions, then saved Personality Prompt.
   */
  suspend fun getEffectiveSystemPrompt(repo: SystemPromptRepository?, task: Task): String {
    val taskPrompt = getTaskSystemPrompt(repo, task)
    if (repo == null || !usesSavedPrompts(task.id)) return taskPrompt

    val savedPrompts = repo.getSavedPrompts().firstOrNull()
    return combinePrompts(
      taskPrompt = taskPrompt,
      systemInstructions = savedPrompts?.systemInstructions.orEmpty(),
      personalityPrompt = savedPrompts?.personalityPrompt.orEmpty(),
    )
  }

  internal fun combinePrompts(
    taskPrompt: String,
    systemInstructions: String,
    personalityPrompt: String,
  ): String =
    buildList {
        if (taskPrompt.isNotBlank()) add(taskPrompt)
        if (systemInstructions.isNotBlank()) {
          add("$SYSTEM_INSTRUCTIONS_HEADER\n${systemInstructions.trim()}")
        }
        if (personalityPrompt.isNotBlank()) {
          add("$PERSONALITY_PROMPT_HEADER\n${personalityPrompt.trim()}")
        }
      }
      .joinToString("\n\n")

  internal fun usesSavedPrompts(taskId: String): Boolean = taskId in TASKS_WITH_SAVED_PROMPTS
}
