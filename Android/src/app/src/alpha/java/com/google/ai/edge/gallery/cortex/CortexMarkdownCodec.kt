/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.cortex

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

internal object CortexHashing {
  fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
      "%02x".format(byte)
    }

  fun sha256(text: String): String = sha256(text.toByteArray(StandardCharsets.UTF_8))
}

internal object CortexMarkdownCodec {
  private const val CONTENT_MARKER = "---\n\n"

  fun encodeTurn(
    artifactId: String,
    exchangeId: String,
    sessionId: String,
    taskId: String,
    modelName: String,
    sourceKind: CortexSourceKind,
    capturedAtEpochMs: Long,
    content: String,
  ): ByteArray {
    val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
    val header =
      buildString {
        appendLine("---")
        appendLine("threadkeeper_schema: 11")
        appendLine("document_type: conversation_turn")
        appendLine("artifact_id: ${yaml(artifactId)}")
        appendLine("exchange_id: ${yaml(exchangeId)}")
        appendLine("session_id: ${yaml(sessionId)}")
        appendLine("task_id: ${yaml(taskId)}")
        appendLine("model: ${yaml(modelName)}")
        appendLine("source_kind: ${sourceKind.name}")
        appendLine("captured_at: ${yaml(Instant.ofEpochMilli(capturedAtEpochMs).toString())}")
        appendLine("content_encoding: utf-8")
        appendLine("content_bytes: ${contentBytes.size}")
        appendLine("content_sha256: ${CortexHashing.sha256(contentBytes)}")
        append(CONTENT_MARKER)
      }
    return header.toByteArray(StandardCharsets.UTF_8) + contentBytes
  }

  fun decodeExactContent(document: ByteArray): ByteArray {
    val marker = CONTENT_MARKER.toByteArray(StandardCharsets.UTF_8)
    val markerIndex = document.indexOf(marker)
    require(markerIndex >= 0) { "Cortex Markdown content marker is missing." }
    return document.copyOfRange(markerIndex + marker.size, document.size)
  }

  fun encodeExchangeReceipt(
    exchangeId: String,
    sessionId: String,
    modelName: String,
    capturedAtEpochMs: Long,
    userArtifactId: String,
    userLocation: String,
    userContentHash: String,
    assistantArtifactId: String,
    assistantLocation: String,
    assistantContentHash: String,
  ): ByteArray =
    buildString {
        appendLine("---")
        appendLine("threadkeeper_schema: 11")
        appendLine("document_type: capture_exchange_receipt")
        appendLine("exchange_id: ${yaml(exchangeId)}")
        appendLine("session_id: ${yaml(sessionId)}")
        appendLine("model: ${yaml(modelName)}")
        appendLine("captured_at: ${yaml(Instant.ofEpochMilli(capturedAtEpochMs).toString())}")
        appendLine("verified: true")
        appendLine("user_artifact_id: ${yaml(userArtifactId)}")
        appendLine("user_location: ${yaml(userLocation)}")
        appendLine("user_content_sha256: $userContentHash")
        appendLine("assistant_artifact_id: ${yaml(assistantArtifactId)}")
        appendLine("assistant_location: ${yaml(assistantLocation)}")
        appendLine("assistant_content_sha256: $assistantContentHash")
        appendLine("---")
        appendLine()
        appendLine("# Verified Jarvis exchange")
        appendLine()
        appendLine("Billy's exact turn and Jarvis's completed reply are stored separately.")
        appendLine("This receipt is the commit marker; association strength never changes source truth.")
      }
      .toByteArray(StandardCharsets.UTF_8)

  fun encodeImportCollection(
    sourceHash: String,
    collectionName: String,
    entryCount: Int,
    canonicalJson: String,
  ): ByteArray =
    buildString {
        appendLine("---")
        appendLine("threadkeeper_schema: 11")
        appendLine("document_type: imported_collection_snapshot")
        appendLine("source_sha256: $sourceHash")
        appendLine("collection: ${yaml(collectionName)}")
        appendLine("entry_count: $entryCount")
        appendLine("canonical_json_sha256: ${CortexHashing.sha256(canonicalJson)}")
        appendLine("---")
        appendLine()
        appendLine("# ThreadKeeper collection: $collectionName")
        appendLine()
        appendLine("The exact imported source file is retained separately. This semantic snapshot is")
        appendLine("stored as Markdown so the Alpha vault remains directly inspectable in Obsidian.")
        appendLine()
        appendLine("```json")
        appendLine(canonicalJson)
        appendLine("```")
      }
      .toByteArray(StandardCharsets.UTF_8)

  fun encodeImportReceipt(
    sourceHash: String,
    archiveLocation: String,
    collectionCount: Int,
    entryCount: Int,
    importedAtEpochMs: Long,
  ): ByteArray =
    buildString {
        appendLine("---")
        appendLine("threadkeeper_schema: 11")
        appendLine("document_type: schema_11_import_receipt")
        appendLine("source_sha256: $sourceHash")
        appendLine("source_archive: ${yaml(archiveLocation)}")
        appendLine("collection_count: $collectionCount")
        appendLine("entry_count: $entryCount")
        appendLine("imported_at: ${yaml(Instant.ofEpochMilli(importedAtEpochMs).toString())}")
        appendLine("source_was_modified: false")
        appendLine("verified: true")
        appendLine("---")
        appendLine()
        appendLine("# ThreadKeeper 2.99 import receipt")
        appendLine()
        appendLine("The selected copy was read-only, SHA-256 verified, and archived in Jarvis Alpha.")
        appendLine("No Main application data was read, cleared, overwritten, or promoted.")
      }
      .toByteArray(StandardCharsets.UTF_8)

  fun encodeRetrievalReceipt(
    receiptId: String,
    sessionId: String,
    queryHash: String,
    recalledAtEpochMs: Long,
    candidateCount: Int,
    intent: CortexRecallIntent,
    field: CortexCognitiveFieldResult,
    selected: List<SelectedRecallArtifact>,
  ): ByteArray =
    buildString {
        appendLine("---")
        appendLine("threadkeeper_schema: 13")
        appendLine("document_type: memory_cycle_retrieval_receipt")
        appendLine("receipt_id: ${yaml(receiptId)}")
        appendLine("session_id: ${yaml(sessionId)}")
        appendLine("query_sha256: $queryHash")
        appendLine("recalled_at: ${yaml(Instant.ofEpochMilli(recalledAtEpochMs).toString())}")
        appendLine("candidate_count: $candidateCount")
        appendLine("selected_count: ${selected.size}")
        appendLine("retrieval_intent: ${intent.name.lowercase()}")
        appendLine("normalizer_version: ${CortexMemoryNormalizer.VERSION}")
        appendLine("physics_candidate_pool: ${field.profile.candidatePool}")
        appendLine("physics_ticks: ${field.profile.ticks}")
        appendLine("physics_degraded: ${field.degraded}")
        appendLine("physics_operation_ms: ${field.operationMs}")
        appendLine("authority_ceiling: inform_only")
        appendLine("verified: true")
        appendLine("---")
        appendLine()
        appendLine("# Native Cortex schema-13 retrieval receipt")
        appendLine()
        appendLine("This receipt records why bounded memory artifacts surfaced. Retrieval is navigation")
        appendLine("evidence only; frequency and activation never promote a claim's truth status.")
        appendLine()
        selected.forEachIndexed { index, artifact ->
          appendLine(
            "${index + 1}. `${artifact.candidate.artifactId}` | " +
              "`${artifact.candidate.sourceKind.name}` | lod `${artifact.detailLevel}` | " +
              "activation `${artifact.candidate.physicsActivation}` | " +
              "mass `${artifact.candidate.physicsMass}` | ${artifact.whySurfaced}"
          )
          appendLine("   - forces: ${artifact.candidate.forceSummary}")
          appendLine(
            "   - temporal: ${artifact.candidate.temporalStatus}; " +
              "statement: ${artifact.candidate.statementKind}; " +
              "correction_cue: ${artifact.candidate.correctionCue}"
          )
          artifact.candidate.typedRelations
            .filter { relation -> relation.relationType == "POSSIBLY_CORRECTS" }
            .take(3)
            .forEach { relation ->
              appendLine(
                "   - typed_relation: ${relation.direction} ${relation.relationType} " +
                  "`${relation.otherArtifactId}`; confidence=${relation.confidence}; " +
                  "status=${relation.confirmationStatus}; authority=${relation.authorityCeiling}"
              )
              appendLine("     evidence: ${relation.evidenceBasis}")
            }
          appendLine("   - authority: inform_only")
        }
        appendLine()
        appendLine("## Bounded physics trace")
        appendLine()
        field.trace.forEach { tick ->
          appendLine(
            "- tick ${tick.tick}: " +
              tick.top.joinToString(" | ") { (artifactId, activation) ->
                "`${artifactId.take(12)}`=$activation"
              }
          )
        }
        appendLine()
        appendLine(
          "Activation, mass, spread, and forces are navigation facts only; they do not change " +
            "truth, confirmation, privacy, authority, or permission."
        )
      }
      .toByteArray(StandardCharsets.UTF_8)

  fun encodeNormalizationSidecar(
    artifactId: String,
    sourceKind: CortexSourceKind,
    sourceContentHash: String,
    sourceLocation: String,
    sourceDocumentHash: String,
    capturedAtEpochMs: Long,
    normalizedAtEpochMs: Long,
    metadata: CortexNormalizationMetadata,
  ): ByteArray =
    buildString {
        appendLine("---")
        appendLine("threadkeeper_schema: 13")
        appendLine("document_type: memory_normalization_sidecar")
        appendLine("normalizer_version: ${metadata.version}")
        appendLine("source_artifact_id: ${yaml(artifactId)}")
        appendLine("source_kind: ${sourceKind.name}")
        appendLine("source_content_sha256: $sourceContentHash")
        appendLine("source_document_sha256: $sourceDocumentHash")
        appendLine("source_location: ${yaml(sourceLocation)}")
        appendLine("observed_at: ${yaml(Instant.ofEpochMilli(capturedAtEpochMs).toString())}")
        appendLine("recorded_at: ${yaml(Instant.ofEpochMilli(capturedAtEpochMs).toString())}")
        appendLine("normalized_at: ${yaml(Instant.ofEpochMilli(normalizedAtEpochMs).toString())}")
        appendLine("statement_kind: ${metadata.statementKind}")
        appendLine("temporal_status: ${metadata.temporalStatus}")
        appendLine("modality: ${metadata.modality}")
        appendLine("correction_cue: ${metadata.correctionCue}")
        appendLine("projection_sha256: ${metadata.projectionHash}")
        appendLine("origin_class: derived_index")
        appendLine("origin_trust: derived")
        appendLine("confirmation_status: UNCONFIRMED")
        appendLine("authority_ceiling: inform_only")
        appendLine("source_was_modified: false")
        appendLine("verified: true")
        appendLine("---")
        appendLine()
        appendLine("# ${metadata.title.replace("#", "\\#")}")
        appendLine()
        appendLine("> [!warning] Derived navigation sidecar")
        appendLine("> This file does not replace, summarize, confirm, or supersede its exact source.")
        appendLine("> Delete and rebuild it at any time; the source Markdown and hashes remain truth.")
        appendLine()
        appendLine("## Memory handle")
        appendLine()
        appendLine("- Statement kind: `${metadata.statementKind}`")
        appendLine("- Temporal cue: `${metadata.temporalStatus}`")
        appendLine("- Modality: `${metadata.modality}`")
        appendLine("- Possible correction cue: `${metadata.correctionCue}`")
        appendLine("- Authority ceiling: `inform_only`")
        appendLine()
        appendLine("## Concept hooks")
        appendLine()
        if (metadata.conceptTerms.isEmpty()) {
          appendLine("- None derived.")
        } else {
          metadata.conceptTerms.forEach { concept ->
            appendLine("- [[${concept.replace('_', ' ')}]]")
          }
        }
        appendLine()
        appendLine("## Epistemic boundary")
        appendLine()
        appendLine("This projection is deterministic and unconfirmed. A correction cue is a routing")
        appendLine("hypothesis only; conflict adjudication must preserve all competing exact evidence.")
      }
      .toByteArray(StandardCharsets.UTF_8)

  private fun yaml(value: String): String =
    buildString {
      append('"')
      value.forEach { character ->
        when (character) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          else -> append(character)
        }
      }
      append('"')
    }

  private fun ByteArray.indexOf(needle: ByteArray): Int {
    if (needle.isEmpty()) return 0
    for (start in 0..size - needle.size) {
      var matches = true
      for (offset in needle.indices) {
        if (this[start + offset] != needle[offset]) {
          matches = false
          break
        }
      }
      if (matches) return start
    }
    return -1
  }
}
