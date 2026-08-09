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

import android.util.Log
import androidx.datastore.core.DataStore
import com.google.ai.edge.gallery.agent.AgentRequest
import com.google.ai.edge.gallery.agent.AgentChatExecutor
import com.google.ai.edge.gallery.agent.AgentRuntimeExecutor
import com.google.ai.edge.gallery.cortex.CortexExchangeCaptureRequest
import com.google.ai.edge.gallery.cortex.CortexRecallRequest
import com.google.ai.edge.gallery.cortex.CortexRuntime
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.SystemPromptRepository
import com.google.ai.edge.gallery.proto.UserData
import com.google.ai.edge.gallery.ui.common.chat.ChatMessageText
import com.google.ai.edge.gallery.ui.common.chat.ChatSide
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private const val TAG = "AGAgentChatViewModel"

@HiltViewModel
class AgentChatViewModel
@Inject
constructor(
  systemPromptRepository: SystemPromptRepository,
  userDataDataStore: DataStore<UserData>,
  @AgentChatExecutor runtimeExecutor: AgentRuntimeExecutor,
  private val cortexRuntime: CortexRuntime,
) :
LlmChatViewModel(systemPromptRepository, userDataDataStore, runtimeExecutor) {

  override suspend fun buildRequestMetadata(model: Model, input: String): Map<String, Any> {
    val packet =
      cortexRuntime.recall(
        CortexRecallRequest(query = input, currentSessionId = currentSessionId)
      )
    return if (packet.verified && packet.contextForModel.isNotBlank()) {
      Log.i(
        TAG,
        "Cortex recalled ${packet.artifactIds.size} verified artifacts with receipt ${packet.receiptId}.",
      )
      mapOf(AgentRequest.CORTEX_RECALL_CONTEXT to packet.contextForModel)
    } else {
      if (packet.message.isNotBlank() && packet.receiptId.isNotBlank()) {
        Log.w(TAG, "Cortex recall ${packet.receiptId} was not verified: ${packet.message}")
      }
      emptyMap()
    }
  }

  override suspend fun onResponseCompleted(model: Model, input: String) {
    val messages = uiState.value.messagesByModel[model.name].orEmpty()
    val lastUserIndex =
      messages.indexOfLast { message ->
        message is ChatMessageText && message.side == ChatSide.USER
      }
    if (lastUserIndex < 0) return

    val userMessage = (messages[lastUserIndex] as ChatMessageText).content
    val assistantResponse =
      messages
        .drop(lastUserIndex + 1)
        .filterIsInstance<ChatMessageText>()
        .lastOrNull { message -> message.side == ChatSide.AGENT }
        ?.content
        .orEmpty()
    if (userMessage.isEmpty() || assistantResponse.isEmpty()) return

    val receipt =
      cortexRuntime.captureExchange(
        CortexExchangeCaptureRequest(
          sessionId = currentSessionId,
          taskId = BuiltInTaskId.LLM_AGENT_CHAT,
          modelName = model.name,
          userMessage = userMessage,
          assistantResponse = assistantResponse,
          completedAtEpochMs = System.currentTimeMillis(),
        )
      )
    if (receipt.verified) {
      Log.i(TAG, "Cortex captured verified exchange ${receipt.exchangeId}.")
    } else if (receipt.exchangeId.isNotEmpty()) {
      Log.w(TAG, "Cortex capture ${receipt.exchangeId} was not verified: ${receipt.message}")
    }
  }
}
