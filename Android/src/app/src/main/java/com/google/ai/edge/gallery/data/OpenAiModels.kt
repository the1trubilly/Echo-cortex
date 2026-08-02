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

package com.google.ai.edge.gallery.data

private const val OPENAI_CLOUD_MODEL_PATH = "__cloud_provider__/openai"

/** Curated OpenAI chat models shown alongside the downloadable local models. */
fun createOpenAiChatModels(): List<Model> =
  listOf(
    openAiModel(
      id = "chat-latest",
      displayName = "OpenAI · ChatGPT Instant",
      info =
        "The latest Instant model used by ChatGPT. This alias updates over time. " +
          "Runs through the OpenAI API and requires an API key in Settings.",
    ),
    openAiModel(
      id = "gpt-5.6-sol",
      displayName = "OpenAI · GPT-5.6 Sol",
      info =
        "OpenAI's flagship model for the most demanding work. Runs through the OpenAI API " +
          "and requires an API key in Settings.",
    ),
    openAiModel(
      id = "gpt-5.6-terra",
      displayName = "OpenAI · GPT-5.6 Terra",
      info =
        "Balances intelligence, speed, and cost. Runs through the OpenAI API and requires an " +
          "API key in Settings.",
    ),
    openAiModel(
      id = "gpt-5.6-luna",
      displayName = "OpenAI · GPT-5.6 Luna",
      info =
        "The economical GPT-5.6 option for everyday conversations. Runs through the OpenAI API " +
          "and requires an API key in Settings.",
    ),
  )

private fun openAiModel(id: String, displayName: String, info: String): Model =
  Model(
    name = id,
    displayName = displayName,
    info = info,
    learnMoreUrl = "https://developers.openai.com/api/docs/models/$id",
    isLlm = true,
    runtimeType = RuntimeType.OPENAI,
    localFileRelativeDirPathOverride = OPENAI_CLOUD_MODEL_PATH,
    llmSupportImage = true,
    showBenchmarkButton = false,
  )
