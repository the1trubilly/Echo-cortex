package com.google.ai.edge.gallery.agent

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.gallery.customtasks.agentchat.AgentTools
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.OpenAiCredentialsRepository
import com.google.ai.edge.gallery.tools.RuntimeToolDispatcher
import com.google.ai.edge.gallery.tools.ToolExecutionContext
import com.google.ai.edge.litertlm.ToolManager
import com.google.gson.JsonParser
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private object OpenAiSessionMarker
private const val MAX_TOOL_ROUNDS = 12

private data class OpenAiFunctionCall(
  val callId: String,
  val name: String,
  val arguments: String,
)

@Singleton
class OpenAiAgentRuntimeExecutor
@Inject
constructor(
  private val credentialsRepository: OpenAiCredentialsRepository,
  private val apiClient: OpenAiApiClient,
  private val agentTools: AgentTools,
) : AgentRuntimeExecutor {
  private var model: Model? = null
  private var systemInstruction: String = ""
  private var toolExecutionContext: ToolExecutionContext? = null
  private val toolDispatcher = RuntimeToolDispatcher()
  private val conversationItems = mutableListOf<JsonObject>()

  override suspend fun initialize(
    context: Context,
    config: AgentRuntimeConfig,
    onDone: (String) -> Unit,
  ) {
    // Configure the cloud session even when a key has not been saved yet. The user can add or
    // replace a credential from Settings while this chat screen remains alive; execution reads the
    // latest encrypted value for every turn and reports a specific missing-key error when needed.
    model = config.model
    systemInstruction = config.systemInstruction.orEmpty()
    toolExecutionContext =
      ToolExecutionContext(taskId = config.taskId, actionChannel = config.actionChannel)
    synchronized(conversationItems) { conversationItems.clear() }
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
        var turnStartIndex = -1
        try {
          val imageDataUrls =
            request.attachments.filterIsInstance<Attachment.ImageBitmap>().map { image ->
              image.bitmap.toJpegDataUrl()
            }
          val userItem =
            OpenAiInputJson.message(
              OpenAiConversationMessage(
                role = "user",
                text = request.query,
                imageDataUrls = imageDataUrls,
              )
            )

          synchronized(conversationItems) {
            turnStartIndex = conversationItems.size
            conversationItems.add(userItem)
          }

          val availableTools = agentTools.getAvailableTools()
          toolExecutionContext?.let { executionContext ->
            toolDispatcher.setupExecutionContext(availableTools, executionContext)
          }
          val toolManager = ToolManager(agentTools.getLiteRtToolProviders())
          val openAiTools = OpenAiToolJson.fromLiteRtDescriptions(toolManager.getToolsDescription())
          val visibleText = StringBuilder()
          var completedTurn = false

          toolLoop@ for (round in 0 until MAX_TOOL_ROUNDS) {
            val requestItems = synchronized(conversationItems) { conversationItems.toList() }
            val response =
              apiClient.streamResponse(
                apiKey = apiKey,
                request =
                  OpenAiResponseRequest(
                    model = currentModel.name,
                    instructions = systemInstruction,
                    inputItems = requestItems,
                    safetyIdentifier = credentialsRepository.getOrCreateSafetyIdentifier(),
                    tools = openAiTools,
                  ),
                onTextDelta = { delta ->
                  visibleText.append(delta)
                  trySend(AgentEvent.StreamToken(token = delta, done = false))
                },
              )

            val outputItems =
              response.outputItems.ifEmpty {
                if (response.text.isEmpty()) emptyList()
                else
                  listOf(
                    OpenAiInputJson.message(
                      OpenAiConversationMessage(role = "assistant", text = response.text)
                    )
                  )
              }
            synchronized(conversationItems) { conversationItems.addAll(outputItems) }

            val functionCalls = response.outputItems.mapNotNull(::parseFunctionCall)
            if (functionCalls.isEmpty()) {
              completedTurn = true
              break@toolLoop
            }

            functionCalls.forEach { functionCall ->
              val output = executeToolCall(toolManager, functionCall)
              synchronized(conversationItems) {
                conversationItems.add(
                  OpenAiInputJson.functionCallOutput(
                    callId = functionCall.callId,
                    output = output,
                  )
                )
              }
            }
          }

          if (!completedTurn) {
            throw IllegalStateException(
              "Jarvis stopped after $MAX_TOOL_ROUNDS tool steps to prevent a runaway loop."
            )
          }

          val finalText = visibleText.toString()
          trySend(AgentEvent.StreamToken(token = "", done = true))
          trySend(AgentEvent.LoopTerminated(finalResponse = finalText))
        } catch (cancelled: CancellationException) {
          removeFailedTurn(turnStartIndex)
          trySend(AgentEvent.LoopCancelled)
          throw cancelled
        } catch (error: Exception) {
          removeFailedTurn(turnStartIndex)
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
    synchronized(conversationItems) {
      conversationItems.clear()
      conversationItems.addAll(
        messages.map { message ->
          OpenAiInputJson.message(
            OpenAiConversationMessage(
              role =
                when (message.role) {
                  AgentConversationRole.USER -> "user"
                  AgentConversationRole.ASSISTANT -> "assistant"
                },
              text = message.content,
            )
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
    toolExecutionContext = null
    synchronized(conversationItems) { conversationItems.clear() }
    onDone()
  }

  private suspend fun executeToolCall(
    toolManager: ToolManager,
    functionCall: OpenAiFunctionCall,
  ): String {
    return try {
      val arguments = JsonParser.parseString(functionCall.arguments).asJsonObject
      val result =
        toolDispatcher.dispatchManualCall(
          toolManager = toolManager,
          functionName = functionCall.name,
          arguments = arguments,
        )
      if (result.isJsonPrimitive && result.asJsonPrimitive.isString) result.asString
      else result.toString()
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      "{\"error\":\"Tool execution failed.\"}"
    }
  }

  private fun parseFunctionCall(item: JsonObject): OpenAiFunctionCall? {
    if (item["type"]?.jsonPrimitive?.contentOrNull != "function_call") return null
    val callId = item["call_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val arguments = item["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (callId.isBlank() || name.isBlank()) return null
    return OpenAiFunctionCall(callId = callId, name = name, arguments = arguments)
  }

  private fun removeFailedTurn(turnStartIndex: Int) {
    if (turnStartIndex < 0) return
    synchronized(conversationItems) {
      if (turnStartIndex <= conversationItems.size) {
        conversationItems.subList(turnStartIndex, conversationItems.size).clear()
      }
    }
  }

  private fun Bitmap.toJpegDataUrl(): String {
    val bytes = ByteArrayOutputStream()
    check(compress(Bitmap.CompressFormat.JPEG, 90, bytes)) { "Could not prepare the image." }
    return "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(bytes.toByteArray())}"
  }
}
