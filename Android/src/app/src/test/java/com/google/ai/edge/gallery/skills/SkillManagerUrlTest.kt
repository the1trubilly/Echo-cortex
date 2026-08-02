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

package com.google.ai.edge.gallery.skills

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillManagerUrlTest {
  @Test
  fun githubTreeUrl_isConvertedToRawSkillBaseUrl() {
    assertEquals(
      "https://raw.githubusercontent.com/google-ai-edge/gallery/main/skills/featured/mood-music",
      normalizeRemoteSkillBaseUrl(
        "https://github.com/google-ai-edge/gallery/tree/main/skills/featured/mood-music"
      ),
    )
  }

  @Test
  fun githubSkillMdUrl_isConvertedToRawSkillBaseUrl() {
    assertEquals(
      "https://raw.githubusercontent.com/example/skills/main/weather",
      normalizeRemoteSkillBaseUrl(
        "https://github.com/example/skills/blob/main/weather/SKILL.md?plain=1"
      ),
    )
  }

  @Test
  fun directSkillMdUrl_isReducedToBaseUrl() {
    assertEquals(
      "https://skills.example.com/weather",
      normalizeRemoteSkillBaseUrl("https://skills.example.com/weather/SKILL.md/"),
    )
  }
}
