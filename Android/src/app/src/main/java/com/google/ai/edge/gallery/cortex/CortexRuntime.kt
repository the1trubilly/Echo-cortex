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

package com.google.ai.edge.gallery.cortex

/** The provenance labels defined by ThreadKeeper 2.99's capture-exchange contract. */
enum class CortexSourceKind {
  USER_STATED,
  OTHER_AGENT,
}

/** A completed conversational exchange ready for durable Cortex capture. */
data class CortexExchangeCaptureRequest(
  val sessionId: String,
  val taskId: String,
  val modelName: String,
  val userMessage: String,
  val assistantResponse: String,
  val completedAtEpochMs: Long,
)

/** Receipt for a capture attempt. A receipt is never evidence of success unless [verified] is true. */
data class CortexCaptureReceipt(
  val exchangeId: String,
  val verified: Boolean,
  val message: String,
)

/** A bounded automatic-recall request evaluated before an Agent Chat turn. */
data class CortexRecallRequest(
  val query: String,
  val currentSessionId: String,
  val maxArtifacts: Int = 5,
  val maxContextChars: Int = 5_200,
)

/** Verified memory context for the internal agent boundary; external-transmission policy is separate. */
data class CortexRecallPacket(
  val contextForModel: String,
  val artifactIds: List<String>,
  val receiptId: String,
  val verified: Boolean,
  val message: String,
)

/** Typed app-level boundary for Cortex. Main builds bind this to a no-op; Alpha binds it natively. */
interface CortexRuntime {
  suspend fun captureExchange(request: CortexExchangeCaptureRequest): CortexCaptureReceipt

  suspend fun recall(request: CortexRecallRequest): CortexRecallPacket
}

/** Deliberately inert implementation used outside the isolated Jarvis Alpha build. */
object NoOpCortexRuntime : CortexRuntime {
  override suspend fun captureExchange(
    request: CortexExchangeCaptureRequest
  ): CortexCaptureReceipt =
    CortexCaptureReceipt(
      exchangeId = "",
      verified = false,
      message = "Cortex is disabled for this build.",
    )

  override suspend fun recall(request: CortexRecallRequest): CortexRecallPacket =
    CortexRecallPacket(
      contextForModel = "",
      artifactIds = emptyList(),
      receiptId = "",
      verified = false,
      message = "Cortex is disabled for this build.",
    )
}
