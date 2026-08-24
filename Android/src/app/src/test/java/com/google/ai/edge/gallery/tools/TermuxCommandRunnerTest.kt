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

import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxCommandRunnerTest {
  @Test
  fun buildTmuxCommand_runsApprovedCommandInPersistentSession() {
    val wrapped = buildTmuxCommand("printf 'hello from Jarvis\\n'", executionId = 42)

    assertTrue(wrapped.contains("tmux has-session -t 'android-jarvis'"))
    assertTrue(wrapped.contains("tmux new-session -d -s 'android-jarvis'"))
    assertTrue(wrapped.contains("tmux new-window -d -t 'android-jarvis'"))
    assertTrue(wrapped.contains("hello from Jarvis"))
    assertTrue(wrapped.contains("jarvis-42"))
  }

  @Test
  fun buildTmuxCommand_preservesSingleQuotesInApprovedCommand() {
    val wrapped = buildTmuxCommand("printf '%s' \"it's safe\"", executionId = 7)

    assertTrue(wrapped.contains("'\\''"))
    assertTrue(wrapped.contains("jarvis-7"))
  }
}
