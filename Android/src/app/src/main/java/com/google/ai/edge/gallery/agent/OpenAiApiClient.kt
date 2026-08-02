package com.google.ai.edge.gallery.agent

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val RESPONSES_URL = "https://api.openai.com/v1/responses"

data class OpenAiConversationMessage(
  val role: String,
  val text: String,
  val imageDataUrls: List<String> = emptyList(),
)

data class OpenAiResponseRequest(
  val model: String,
  val instructions: String,
  val messages: List<OpenAiConversationMessage>,
  val safetyIdentifier: String,
)

internal object OpenAiRequestJson {
  fun encode(request: OpenAiResponseRequest): String =
    buildJsonObject {
        put("model", request.model)
        if (request.instructions.isNotBlank()) put("instructions", request.instructions)
        put("stream", true)
        // The app owns conversation persistence; do not ask OpenAI to store response state.
        put("store", false)
        put("safety_identifier", request.safetyIdentifier)
        if (request.model.startsWith("gpt-5.6")) {
          putJsonObject("reasoning") { put("effort", "medium") }
        }
        putJsonArray("input") {
          request.messages.forEach { message -> add(encodeMessage(message)) }
        }
      }
      .toString()

  private fun encodeMessage(message: OpenAiConversationMessage): JsonObject =
    buildJsonObject {
      put("role", message.role)
      if (message.imageDataUrls.isEmpty()) {
        put("content", message.text)
      } else {
        put(
          "content",
          buildJsonArray {
            if (message.text.isNotEmpty()) {
              add(buildJsonObject {
                put("type", "input_text")
                put("text", message.text)
              })
            }
            message.imageDataUrls.forEach { imageDataUrl ->
              add(buildJsonObject {
                put("type", "input_image")
                put("image_url", imageDataUrl)
              })
            }
          },
        )
      }
    }
}

internal sealed interface OpenAiStreamEvent {
  data class TextDelta(val text: String) : OpenAiStreamEvent

  data object Completed : OpenAiStreamEvent

  data class Error(val message: String) : OpenAiStreamEvent

  data object Ignored : OpenAiStreamEvent
}

internal object OpenAiSseParser {
  private val json = Json { ignoreUnknownKeys = true }

  fun parse(data: String): OpenAiStreamEvent {
    if (data == "[DONE]") return OpenAiStreamEvent.Completed
    val event =
      try {
        json.parseToJsonElement(data).jsonObject
      } catch (_: Exception) {
        return OpenAiStreamEvent.Error("OpenAI returned an unreadable streaming response.")
      }

    return when (event["type"]?.jsonPrimitive?.contentOrNull) {
      "response.output_text.delta",
      "response.refusal.delta" ->
        OpenAiStreamEvent.TextDelta(event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty())
      "response.completed" -> OpenAiStreamEvent.Completed
      "response.failed" ->
        OpenAiStreamEvent.Error(
          event["response"]
            ?.jsonObject
            ?.get("error")
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull ?: "OpenAI could not complete this response."
        )
      "response.incomplete" ->
        OpenAiStreamEvent.Error("OpenAI stopped before completing the response.")
      "error" ->
        OpenAiStreamEvent.Error(
          event["message"]?.jsonPrimitive?.contentOrNull ?: "OpenAI returned an error."
        )
      else -> OpenAiStreamEvent.Ignored
    }
  }
}

class OpenAiApiException(val statusCode: Int, message: String) : IOException(message)

@Singleton
class OpenAiApiClient @Inject constructor() {
  @Volatile private var activeConnection: HttpURLConnection? = null

  suspend fun streamResponse(
    apiKey: String,
    request: OpenAiResponseRequest,
    onTextDelta: (String) -> Unit,
  ): String =
    withContext(Dispatchers.IO) {
      val connection =
        (URL(RESPONSES_URL).openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 30_000
          readTimeout = 180_000
          doOutput = true
          instanceFollowRedirects = false
          setRequestProperty("Authorization", "Bearer $apiKey")
          setRequestProperty("Content-Type", "application/json")
          setRequestProperty("Accept", "text/event-stream")
        }
      activeConnection = connection

      try {
        val body = OpenAiRequestJson.encode(request).toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }

        val statusCode = connection.responseCode
        if (statusCode !in 200..299) {
          val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
          throw OpenAiApiException(statusCode, readableHttpError(statusCode, errorBody))
        }

        val finalText = StringBuilder()
        var completed = false
        val eventData = mutableListOf<String>()

        fun handleEvent() {
          if (eventData.isEmpty()) return
          when (val event = OpenAiSseParser.parse(eventData.joinToString("\n"))) {
            is OpenAiStreamEvent.TextDelta -> {
              if (event.text.isNotEmpty()) {
                finalText.append(event.text)
                onTextDelta(event.text)
              }
            }
            OpenAiStreamEvent.Completed -> completed = true
            is OpenAiStreamEvent.Error -> throw IOException(event.message)
            OpenAiStreamEvent.Ignored -> Unit
          }
          eventData.clear()
        }

        connection.inputStream.bufferedReader().use { reader ->
          while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) {
              handleEvent()
              if (completed) break
            } else if (line.startsWith("data:")) {
              eventData.add(line.removePrefix("data:").trimStart())
            }
          }
        }
        handleEvent()

        if (!completed) throw IOException("The OpenAI connection ended before the response finished.")
        finalText.toString()
      } finally {
        activeConnection = null
        connection.disconnect()
      }
    }

  fun cancel() {
    activeConnection?.disconnect()
    activeConnection = null
  }

  private fun readableHttpError(statusCode: Int, body: String): String {
    val apiMessage =
      try {
        Json.parseToJsonElement(body)
          .jsonObject["error"]
          ?.jsonObject
          ?.get("message")
          ?.jsonPrimitive
          ?.contentOrNull
      } catch (_: Exception) {
        null
      }

    return when (statusCode) {
      401 -> "OpenAI rejected the API key. Replace it in Settings and try again."
      403 -> "This OpenAI API key does not have permission to use that model."
      404 -> "That OpenAI model is not available to this API key."
      429 -> "OpenAI's usage, billing, or rate limit was reached. Check the API account and try again."
      else -> apiMessage ?: "OpenAI returned HTTP $statusCode."
    }
  }
}
