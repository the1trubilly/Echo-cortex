package com.google.ai.edge.gallery.agent

import android.content.Context
import com.google.ai.edge.gallery.data.RuntimeType
import kotlinx.coroutines.flow.Flow

/** Chooses the on-device or OpenAI implementation while preserving one chat UI/runtime contract. */
class RoutingAgentRuntimeExecutor(
  private val localExecutor: AgentRuntimeExecutor,
  private val openAiExecutor: AgentRuntimeExecutor,
) : AgentRuntimeExecutor {
  private var activeExecutor: AgentRuntimeExecutor = localExecutor

  override suspend fun initialize(
    context: Context,
    config: AgentRuntimeConfig,
    onDone: (String) -> Unit,
  ) {
    activeExecutor =
      if (config.model.runtimeType == RuntimeType.OPENAI) openAiExecutor else localExecutor
    activeExecutor.initialize(context, config, onDone)
  }

  override fun executeStream(
    context: AgentExecutionContext,
    request: AgentRequest,
  ): Flow<AgentEvent> = activeExecutor.executeStream(context, request)

  override suspend fun execute(
    context: AgentExecutionContext,
    request: AgentRequest,
  ): AgentResponse = activeExecutor.execute(context, request)

  override suspend fun resetConversation(
    systemInstruction: String?,
    messages: List<AgentConversationMessage>,
  ) = activeExecutor.resetConversation(systemInstruction, messages)

  override fun interrupt() = activeExecutor.interrupt()

  override fun cleanUp(onDone: () -> Unit) = activeExecutor.cleanUp(onDone)
}
