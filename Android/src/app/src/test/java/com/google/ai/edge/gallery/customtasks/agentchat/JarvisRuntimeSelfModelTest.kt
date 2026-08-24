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

import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.RuntimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisRuntimeSelfModelTest {
  @Test
  fun appendTo_identifiesExactOpenAiRuntimeAndCurrentLimitations() {
    val result =
      JarvisRuntimeSelfModel.appendTo(
        systemInstructions = "Base instructions\n\n## Saved Personality Prompt\nBe warm",
        model =
          Model(
            name = "gpt-5.6-terra",
            displayName = "OpenAI · GPT-5.6 Medium",
            runtimeType = RuntimeType.OPENAI,
            llmSupportImage = true,
          ),
        enabledSkillNames = listOf("research", "calendar"),
        enabledMcpToolNames = listOf("search_web"),
        appVersion = "test-version",
        nativeCortexEnabled = true,
      )

    assertTrue(result.contains("Active model shown to the user: OpenAI · GPT-5.6 Medium"))
    assertTrue(result.contains("Exact configured provider model ID: `gpt-5.6-terra`"))
    assertTrue(result.contains("OpenAI Responses API"))
    assertTrue(result.contains("Current accepted inputs: text, images"))
    assertTrue(result.contains("Enabled Skills: calendar, research"))
    assertTrue(result.contains("Enabled MCP tools: search_web"))
    assertTrue(result.contains("OpenAI Responses function calls run through"))
    assertTrue(result.contains("Tool results are returned to the model"))
    assertTrue(result.contains("Termux terminal and ADB-via-Termux functions are registered"))
    assertTrue(result.contains("persistent android-jarvis tmux session"))
    assertTrue(result.contains("approving every command and approving dangerous commands"))
    assertTrue(result.contains("connection verified against this phone's Android build fingerprint"))
    assertTrue(result.contains("automatically resumes the already-approved command"))
    assertTrue(result.contains("pairing code is handled by native code"))
    assertTrue(result.contains("native Kotlin Cortex"))
    assertTrue(result.contains("retrieves a bounded, hash-verified packet"))
    assertTrue(result.contains("semantic records, links, checkpoints"))
    assertTrue(result.indexOf("## Saved Personality Prompt") < result.indexOf(JarvisRuntimeSelfModel.SECTION_HEADER))
  }

  @Test
  fun appendTo_describesLocalToolExecutionAndDoesNotInventAudio() {
    val result =
      JarvisRuntimeSelfModel.appendTo(
        systemInstructions = "Base instructions",
        model =
          Model(
            name = "local-model",
            runtimeType = RuntimeType.LITERT_LM,
            llmSupportAudio = true,
          ),
        enabledSkillNames = listOf("notes"),
        enabledMcpToolNames = emptyList(),
        appVersion = "test-version",
        nativeCortexEnabled = false,
      )

    assertTrue(result.contains("on-device LiteRT-LM"))
    assertTrue(result.contains("Current accepted inputs: text, audio clips"))
    assertTrue(result.contains("Enabled Skills and MCP tools run through"))
    assertFalse(result.contains("Current accepted inputs: text, images"))
    assertTrue(result.contains("Native long-term Cortex memory is disabled"))
  }

  @Test
  fun appendTo_replacesAnExistingRuntimeSectionInsteadOfDuplicatingIt() {
    val model = Model(name = "model", runtimeType = RuntimeType.LITERT_LM)
    val first =
      JarvisRuntimeSelfModel.appendTo(
        systemInstructions = "Base instructions",
        model = model,
        enabledSkillNames = emptyList(),
        enabledMcpToolNames = emptyList(),
        appVersion = "first",
      )
    val second =
      JarvisRuntimeSelfModel.appendTo(
        systemInstructions = first,
        model = model,
        enabledSkillNames = emptyList(),
        enabledMcpToolNames = emptyList(),
        appVersion = "second",
      )

    assertEquals(1, Regex(Regex.escape(JarvisRuntimeSelfModel.SECTION_HEADER)).findAll(second).count())
    assertFalse(second.contains("Android Jarvis first"))
    assertTrue(second.contains("Android Jarvis second"))
  }
}
