package com.google.ai.edge.gallery.agent

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.OpenAiCredentialsRepository
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private object OpenAiSessionMarker

@Singleton
class OpenAiAgentRuntimeExecutor
@Inject
constructor(
  private val credentialsRepository: OpenAiCredentialsRepository,
  private val apiClient: OpenAiApiClient,
) : AgentRuntimeExecutor {
  private var model: Model? = null
  private var systemInstruction: String = ""
  private val conversation = mutableListOf<OpenAiConversationMessage>()

  override suspend fun initialize(
    context: Context,
    config: AgentRuntimeConfig,
    onDone: (String) -> Unit,
  ) {
    if (!credentialsRepository.hasApiKey()) {
      onDone("Add an OpenAI API key in Settings before using OpenAI models.")
      return
    }

    model = config.model
    systemInstruction = config.systemInstruction.orEmpty()
    synchronized(conversation) { conversation.clear() }
    config.model.instance = OpenAiSessionMarker
    onDone("")
  }

  override fun executeStream(
    context: AgentExecutionContext,
    request: AgentRequest,
  ): Flow<AgentEvent> = callbackFlow {
    trySend(AgentEvent.LoopInitiated(request))

    val currentModel = model
    val apiKey = credentialsRepository.readApiKey()
    if (currentModel == null) {
      trySend(AgentEvent.Error("OpenAI is not initialized."))
      close()
      return@callbackFlow
    }
    if (apiKey == null) {
      trySend(AgentEvent.Error("Add an OpenAI API key in Settings and try again."))
      close()
      return@callbackFlow
    }
    if (request.attachments.any { it is Attachment.AudioBytes || it is Attachment.AudioUri }) {
      trySend(AgentEvent.Error("This OpenAI chat model does not accept audio clips."))
      close()
      return@callbackFlow
    }

    val job =
      launch(Dispatchers.IO) {
        var userMessage: OpenAiConversationMessage? = null
        try {
          val imageDataUrls =
            request.attachments.filterIsInstance<Attachment.ImageBitmap>().map { image ->
              image.bitmap.toJpegDataUrl()
            }
          userMessage =
            OpenAiConversationMessage(
              role = "user",
              text = request.query,
              imageDataUrls = imageDataUrls,
            )
          val requestMessages =
            synchronized(conversation) {
              conversation.add(userMessage)
              conversation.toList()
            }

          val finalText =
            apiClient.streamResponse(
              apiKey = apiKey,
              request =
                OpenAiResponseRequest(
                  model = currentModel.name,
                  instructions = systemInstruction,
                  messages = requestMessages,
                  safetyIdentifier = credentialsRepository.getOrCreateSafetyIdentifier(),
                ),
              onTextDelta = { delta ->
                trySend(AgentEvent.StreamToken(token = delta, done = false))
              },
            )

          synchronized(conversation) {
            conversation.add(OpenAiConversationMessage(role = "assistant", text = finalText))
          }
          trySend(AgentEvent.StreamToken(token = "", done = true))
          trySend(AgentEvent.LoopTerminated(finalResponse = finalText))
        } catch (cancelled: CancellationException) {
          removeFailedTurn(userMessage)
          trySend(AgentEvent.LoopCancelled)
          throw cancelled
        } catch (error: Exception) {
          removeFailedTurn(userMessage)
          trySend(AgentEvent.Error(error.message ?: "OpenAI request failed."))
        } finally {
          close()
        }
      }

    awaitClose {
      apiClient.cancel()
      job.cancel()
    }
  }

  override suspend fun execute(
    context: AgentExecutionContext,
    request: AgentRequest,
  ): AgentResponse {
    var output = ""
    var successful = true
    executeStream(context, request).collect { event ->
      when (event) {
        is AgentEvent.LoopTerminated -> output = event.finalResponse
        is AgentEvent.Error -> {
          successful = false
          output = event.errorMessage
        }
        else -> Unit
      }
    }
    return AgentResponse(output = output, isSuccessful = successful)
  }

  override suspend fun resetConversation(
    systemInstruction: String?,
    messages: List<AgentConversationMessage>,
  ) {
    apiClient.cancel()
    this.systemInstruction = systemInstruction.orEmpty()
    synchronized(conversation) {
      conversation.clear()
      conversation.addAll(
        messages.map { message ->
          OpenAiConversationMessage(
            role =
              when (message.role) {
                AgentConversationRole.USER -> "user"
                AgentConversationRole.ASSISTANT -> "assistant"
              },
            text = message.content,
          )
        }
      )
    }
  }

  override fun interrupt() {
    apiClient.cancel()
  }

  override fun cleanUp(onDone: () -> Unit) {
    apiClient.cancel()
    model?.instance = null
    model = null
    systemInstruction = ""
    synchronized(conversation) { conversation.clear() }
    onDone()
  }

  private fun removeFailedTurn(userMessage: OpenAiConversationMessage?) {
    if (userMessage == null) return
    synchronized(conversation) {
      val index = conversation.indexOfLast { it === userMessage }
      if (index >= 0) conversation.removeAt(index)
    }
  }

  private fun Bitmap.toJpegDataUrl(): String {
    val bytes = ByteArrayOutputStream()
    check(compress(Bitmap.CompressFormat.JPEG, 90, bytes)) { "Could not prepare the image." }
    return "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(bytes.toByteArray())}"
  }
}
