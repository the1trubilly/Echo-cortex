/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CortexCognitiveFieldTest {
  @Test
  fun boundedField_spreadsFromQuestionToAnswer_thenRunsDeterministicPhysicsAndDmr() {
    val candidates =
      buildList {
        add(
          candidate(
            id = "location-question",
            source = CortexSourceKind.OTHER_AGENT,
            session = "interview",
            capturedAt = 100,
            content = "Where are you based?",
          )
        )
        add(
          candidate(
            id = "location-answer",
            source = CortexSourceKind.USER_STATED,
            session = "interview",
            capturedAt = 200,
            content = "Greenwood, Delaware.",
          )
        )
        add(
          candidate(
            id = "workshop",
            source = CortexSourceKind.USER_STATED,
            session = "workshop-chat",
            capturedAt = 300,
            content = "I want Infinite Workshop to help ideas become tools that create tools.",
          )
        )
        repeat(70) { index ->
          add(
            candidate(
              id = "noise-$index",
              source = CortexSourceKind.USER_STATED,
              session = "noise-$index",
              capturedAt = 1_000L + index,
              content = "Unrelated bounded candidate $index.",
            )
          )
        }
      }
    val first =
      CortexCognitiveField().activate(
        query = "Where do I live?",
        candidates = candidates,
        intent = CortexRecallIntent.FOCUSED,
        requestedArtifacts = 8,
        requestedContextChars = 9_000,
      )
    val second =
      CortexCognitiveField().activate(
        query = "Where do I live?",
        candidates = candidates,
        intent = CortexRecallIntent.FOCUSED,
        requestedArtifacts = 8,
        requestedContextChars = 9_000,
      )

    assertEquals(36, first.candidates.size)
    assertEquals(3, first.trace.size)
    assertEquals(5, first.profile.outputMemories)
    assertEquals(3_600, first.profile.modelContextChars)
    assertTrue(first.profile.candidatePool <= 64)
    assertTrue(first.profile.ticks <= 5)
    assertTrue(first.profile.outputMemories <= 8)
    assertTrue(first.profile.modelContextChars <= 5_200)
    assertTrue(first.directSeedCount >= 1)
    val location = first.candidates.single { it.artifactId == "location-answer" }
    assertTrue(location.physicsActivation > 0.0)
    assertTrue(location.navigationBasis.contains("associative spread"))
    assertTrue(location.forceSummary.contains("gravity="))
    assertTrue(location.nearestRelated.any { it.artifactId == "location-question" })
    assertEquals(
      first.candidates.map { it.artifactId to it.physicsActivation },
      second.candidates.map { it.artifactId to it.physicsActivation },
    )

    val selected =
      CortexRecallEngine.select(
        candidatesNewestFirst = first.candidates,
        query = "Where do I live?",
        maxArtifacts = first.profile.outputMemories,
        maxContextChars = first.profile.modelContextChars,
      )
    assertTrue(selected.any { it.candidate.artifactId == "location-answer" })
    val context = CortexRecallEngine.buildModelContext(selected)
    assertTrue(context.contains("verified schema-13 retrieval"))
    assertTrue(context.contains("memory_handle"))
    assertTrue(context.contains("inform_only"))
    assertTrue(context.contains("nearest_related"))

    val receipt =
      CortexMarkdownCodec.encodeRetrievalReceipt(
          receiptId = "receipt",
          sessionId = "new-chat",
          queryHash = "hash",
          recalledAtEpochMs = 123,
          candidateCount = first.candidates.size,
          intent = CortexRecallIntent.FOCUSED,
          field = first,
          selected = selected,
        )
        .toString(Charsets.UTF_8)
    assertTrue(receipt.contains("threadkeeper_schema: 13"))
    assertTrue(receipt.contains("physics_ticks: 3"))
    assertTrue(receipt.contains("authority_ceiling: inform_only"))
    assertTrue(receipt.contains("## Bounded physics trace"))
  }

  private fun candidate(
    id: String,
    source: CortexSourceKind,
    session: String,
    capturedAt: Long,
    content: String,
  ): ReadableRecallCandidate =
    ReadableRecallCandidate(
      artifactId = id,
      exchangeId = "exchange-$id",
      sessionId = session,
      sourceKind = source,
      capturedAtEpochMs = capturedAt,
      exactContent = content,
    )
}
