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
    selected: List<SelectedRecallArtifact>,
  ): ByteArray =
    buildString {
        appendLine("---")
        appendLine("threadkeeper_schema: 11")
        appendLine("document_type: memory_cycle_retrieval_receipt")
        appendLine("receipt_id: ${yaml(receiptId)}")
        appendLine("session_id: ${yaml(sessionId)}")
        appendLine("query_sha256: $queryHash")
        appendLine("recalled_at: ${yaml(Instant.ofEpochMilli(recalledAtEpochMs).toString())}")
        appendLine("candidate_count: $candidateCount")
        appendLine("selected_count: ${selected.size}")
        appendLine("verified: true")
        appendLine("---")
        appendLine()
        appendLine("# Native Cortex retrieval receipt")
        appendLine()
        appendLine("This receipt records why bounded memory artifacts surfaced. Retrieval is navigation")
        appendLine("evidence only; frequency and activation never promote a claim's truth status.")
        appendLine()
        selected.forEachIndexed { index, artifact ->
          appendLine(
            "${index + 1}. `${artifact.candidate.artifactId}` | " +
              "`${artifact.candidate.sourceKind.name}` | ${artifact.whySurfaced}"
          )
        }
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
