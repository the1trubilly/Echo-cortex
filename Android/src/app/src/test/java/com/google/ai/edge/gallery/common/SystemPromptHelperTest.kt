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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptHelperTest {
  @Test
  fun combinePrompts_keepsSavedPromptsSeparateAndOrdered() {
    val result =
      SystemPromptHelper.combinePrompts(
        taskPrompt = "Task rules",
        systemInstructions = "Always verify",
        personalityPrompt = "Be warm",
      )

    assertEquals(
      """Task rules

## Saved System Instructions
Always verify

## Saved Personality Prompt
Be warm""",
      result,
    )
  }

  @Test
  fun combinePrompts_omitsEmptySavedSections() {
    assertEquals(
      "Task rules",
      SystemPromptHelper.combinePrompts(
        taskPrompt = "Task rules",
        systemInstructions = "  ",
        personalityPrompt = "",
      ),
    )
  }

  @Test
  fun savedPrompts_applyOnlyToConversationalTasks() {
    assertTrue(SystemPromptHelper.usesSavedPrompts(BuiltInTaskId.LLM_CHAT))
    assertTrue(SystemPromptHelper.usesSavedPrompts(BuiltInTaskId.LLM_AGENT_CHAT))
    assertTrue(SystemPromptHelper.usesSavedPrompts(BuiltInTaskId.LLM_ASK_IMAGE))
    assertTrue(SystemPromptHelper.usesSavedPrompts(BuiltInTaskId.LLM_ASK_AUDIO))
    assertFalse(SystemPromptHelper.usesSavedPrompts(BuiltInTaskId.LLM_TINY_GARDEN))
    assertFalse(SystemPromptHelper.usesSavedPrompts(BuiltInTaskId.LLM_MOBILE_ACTIONS))
    assertFalse(SystemPromptHelper.usesSavedPrompts(BuiltInTaskId.LLM_PROMPT_LAB))
  }
}
