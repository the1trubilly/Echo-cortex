/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CortexMemoryNormalizerTest {
  @Test
  fun correctionSidecar_isDerivedAndCorrectionPairStaysVisible() {
    val olderText = "My favorite lighthouse color is blue."
    val newerText = "Actually, my favorite lighthouse color is green now, not blue."
    val olderMetadata =
      CortexRecallEngine.indexMetadata(olderText, CortexSourceKind.USER_STATED)
    val newerMetadata =
      CortexRecallEngine.indexMetadata(newerText, CortexSourceKind.USER_STATED)
    val olderNormalization =
      CortexMemoryNormalizer.normalize(
        olderText,
        CortexSourceKind.USER_STATED,
        olderMetadata,
      )
    val newerNormalization =
      CortexMemoryNormalizer.normalize(
        newerText,
        CortexSourceKind.USER_STATED,
        newerMetadata,
      )

    assertFalse(olderNormalization.correctionCue)
    assertTrue(newerNormalization.correctionCue)
    assertEquals("correction_candidate", newerNormalization.statementKind)
    assertEquals("current_time_cue", newerNormalization.temporalStatus)
    val sidecar =
      CortexMarkdownCodec.encodeNormalizationSidecar(
          artifactId = "newer",
          sourceKind = CortexSourceKind.USER_STATED,
          sourceContentHash = CortexHashing.sha256(newerText),
          sourceLocation = "turns/newer.md",
          sourceDocumentHash = "document-hash",
          capturedAtEpochMs = 200,
          normalizedAtEpochMs = 201,
          metadata = newerNormalization,
        )
        .toString(Charsets.UTF_8)
    assertTrue(sidecar.contains("threadkeeper_schema: 13"))
    assertTrue(sidecar.contains("document_type: memory_normalization_sidecar"))
    assertTrue(sidecar.contains("source_was_modified: false"))
    assertTrue(sidecar.contains("authority_ceiling: inform_only"))
    assertTrue(sidecar.contains("correction_cue: true"))

    val relationFromNewer =
      CortexTypedRelation(
        otherArtifactId = "older",
        direction = "outgoing",
        relationType = "POSSIBLY_CORRECTS",
        strength = 0.84,
        confidence = 0.63,
        evidenceBasis = "explicit correction cue; shared concepts: favorite, lighthouse, color",
        confirmationStatus = "UNCONFIRMED",
        authorityCeiling = "inform_only",
      )
    val relationFromOlder = relationFromNewer.copy(otherArtifactId = "newer", direction = "incoming")
    val newer =
      candidate(
        id = "newer",
        capturedAt = 200,
        content = newerText,
        normalization = newerNormalization,
        relation = relationFromNewer,
      )
    val older =
      candidate(
        id = "older",
        capturedAt = 100,
        content = olderText,
        normalization = olderNormalization,
        relation = relationFromOlder,
      )
    val field =
      CortexCognitiveField().activate(
        query = "What is my favorite lighthouse color?",
        candidates = listOf(newer, older),
        intent = CortexRecallIntent.FOCUSED,
        requestedArtifacts = 5,
        requestedContextChars = 3_600,
      )
    assertEquals(3, field.trace.size)
    assertTrue(
      field.candidates
        .flatMap(ReadableRecallCandidate::nearestRelated)
        .any { neighbor -> neighbor.basis.contains("POSSIBLY_CORRECTS") }
    )

    val selected =
      CortexRecallEngine.select(
        candidatesNewestFirst = field.candidates,
        query = "What is my favorite lighthouse color?",
        maxArtifacts = 5,
        maxContextChars = 3_600,
      )
    assertEquals(setOf("newer", "older"), selected.map { it.candidate.artifactId }.toSet())
    val context = CortexRecallEngine.buildModelContext(selected)
    assertTrue(context.contains(olderText))
    assertTrue(context.contains(newerText))
    assertTrue(context.contains("POSSIBLY_CORRECTS"))
    assertTrue(context.contains("not an adjudication"))
    assertTrue(context.contains("current_time_cue"))
  }

  private fun candidate(
    id: String,
    capturedAt: Long,
    content: String,
    normalization: CortexNormalizationMetadata,
    relation: CortexTypedRelation,
  ): ReadableRecallCandidate =
    ReadableRecallCandidate(
      artifactId = id,
      exchangeId = "exchange-$id",
      sessionId = "session-$id",
      sourceKind = CortexSourceKind.USER_STATED,
      capturedAtEpochMs = capturedAt,
      exactContent = content,
      indexedTerms = CortexRecallEngine.normalizedQueryTerms(content),
      indexedDurablePersonalScore = 160,
      statementKind = normalization.statementKind,
      temporalStatus = normalization.temporalStatus,
      modality = normalization.modality,
      correctionCue = normalization.correctionCue,
      typedRelations = listOf(relation),
    )
}
