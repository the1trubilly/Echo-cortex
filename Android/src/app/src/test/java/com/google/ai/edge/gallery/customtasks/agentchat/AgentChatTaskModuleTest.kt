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

import com.google.ai.edge.gallery.common.SystemPromptHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentChatTaskModuleTest {
  @Test
  fun effectiveBasePrompt_preservesSavedPromptsWhenMcpIsDisabled() {
    val combinedPrompt =
      SystemPromptHelper.combinePrompts(
        taskPrompt = DEFAULT_SYSTEM_PROMPT_TRIMMED,
        systemInstructions = "Always verify",
        personalityPrompt = "Be warm",
      )

    val result = getEffectiveBaseSystemPrompt(combinedPrompt, hasMcpTools = false)

    assertTrue(result.startsWith(DEFAULT_SYSTEM_PROMPT_SKILLS_ONLY_TRIMMED))
    assertTrue(result.endsWith("## Saved Personality Prompt\nBe warm"))
  }

  @Test
  fun effectiveBasePrompt_doesNotChangeCustomTaskPrompt() {
    val customPrompt = "Custom task instructions"

    assertEquals(
      customPrompt,
      getEffectiveBaseSystemPrompt(customPrompt, hasMcpTools = false),
    )
  }
}
