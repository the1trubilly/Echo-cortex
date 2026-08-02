package com.google.ai.edge.gallery.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenAiApiClientTest {
  @Test
  fun requestPreservesPromptHistoryImagesAndPrivacyContract() {
    val encoded =
      OpenAiRequestJson.encode(
        OpenAiResponseRequest(
          model = "gpt-5.6-terra",
          instructions = "System first, personality second.",
          messages =
            listOf(
              OpenAiConversationMessage(
                role = "user",
                text = "What is here?",
                imageDataUrls = listOf("data:image/jpeg;base64,abc"),
              ),
              OpenAiConversationMessage(role = "assistant", text = "A test image."),
            ),
          safetyIdentifier = "random-install-id",
        )
      )

    val root = Json.parseToJsonElement(encoded).jsonObject
    assertEquals("gpt-5.6-terra", root.getValue("model").jsonPrimitive.content)
    assertEquals(
      "System first, personality second.",
      root.getValue("instructions").jsonPrimitive.content,
    )
    assertTrue(root.getValue("stream").jsonPrimitive.boolean)
    assertFalse(root.getValue("store").jsonPrimitive.boolean)
    assertEquals(
      "random-install-id",
      root.getValue("safety_identifier").jsonPrimitive.content,
    )
    assertEquals(
      "medium",
      root.getValue("reasoning").jsonObject.getValue("effort").jsonPrimitive.content,
    )

    val input = root.getValue("input").jsonArray
    assertEquals("user", input[0].jsonObject.getValue("role").jsonPrimitive.content)
    val userContent = input[0].jsonObject.getValue("content").jsonArray
    assertEquals("input_text", userContent[0].jsonObject.getValue("type").jsonPrimitive.content)
    assertEquals("input_image", userContent[1].jsonObject.getValue("type").jsonPrimitive.content)
    assertEquals("assistant", input[1].jsonObject.getValue("role").jsonPrimitive.content)
    assertEquals("A test image.", input[1].jsonObject.getValue("content").jsonPrimitive.content)
  }

  @Test
  fun chatLatestDoesNotSendUnsupportedReasoningSetting() {
    val root =
      Json.parseToJsonElement(
          OpenAiRequestJson.encode(
            OpenAiResponseRequest(
              model = "chat-latest",
              instructions = "",
              messages = listOf(OpenAiConversationMessage(role = "user", text = "Hello")),
              safetyIdentifier = "install-id",
            )
          )
        )
        .jsonObject

    assertFalse(root.containsKey("reasoning"))
    assertFalse(root.containsKey("instructions"))
  }

  @Test
  fun streamingEventsExposeOnlyTextAndTerminalState() {
    assertEquals(
      OpenAiStreamEvent.TextDelta("Hello"),
      OpenAiSseParser.parse("""{"type":"response.output_text.delta","delta":"Hello"}"""),
    )
    assertEquals(
      OpenAiStreamEvent.Completed,
      OpenAiSseParser.parse("""{"type":"response.completed","response":{"id":"r_1"}}"""),
    )
    assertEquals(
      OpenAiStreamEvent.Error("No access"),
      OpenAiSseParser.parse(
        """{"type":"response.failed","response":{"error":{"message":"No access"}}}"""
      ),
    )
  }
}
