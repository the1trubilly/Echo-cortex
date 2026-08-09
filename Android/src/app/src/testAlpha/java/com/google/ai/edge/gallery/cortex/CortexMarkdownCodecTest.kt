/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CortexMarkdownCodecTest {
  @Test
  fun turnDocumentPreservesExactUtf8Content() {
    val exact = "Billy said --- literally.\n\nUnicode: 🧠\nNo trailing newline"
    val document =
      CortexMarkdownCodec.encodeTurn(
        artifactId = "artifact-user",
        exchangeId = "exchange-1",
        sessionId = "session-1",
        taskId = "llm_agent_chat",
        modelName = "GPT test",
        sourceKind = CortexSourceKind.USER_STATED,
        capturedAtEpochMs = 1_700_000_000_000,
        content = exact,
      )

    assertArrayEquals(exact.toByteArray(Charsets.UTF_8), CortexMarkdownCodec.decodeExactContent(document))
    assertEquals(
      CortexHashing.sha256(exact),
      CortexHashing.sha256(CortexMarkdownCodec.decodeExactContent(document)),
    )
  }

  @Test
  fun schema11InspectionAcceptsRawAndWrappedCopies() {
    val database =
      """
      {
        "schemaVersion": 11,
        "records": [{"id":"r1"}],
        "artifacts": {"a1":{"kind":"USER_STATED"}},
        "events": []
      }
      """.trimIndent()
    val raw = Schema11Importer.inspect(database.toByteArray())
    val wrapped =
      Schema11Importer.inspect(
        """{"format":"threadkeeper-transfer","database":$database}""".toByteArray()
      )

    assertEquals(listOf("artifacts", "events", "records"), raw.collections.map { it.name })
    assertEquals(2, raw.totalEntries)
    assertEquals(raw.collections.map { it.name }, wrapped.collections.map { it.name })
  }

  @Test
  fun schema11InspectionRejectsOtherSchemasAndSecrets() {
    assertThrows(IllegalArgumentException::class.java) {
      Schema11Importer.inspect("""{"schemaVersion":10,"records":[]}""".toByteArray())
    }
    assertThrows(IllegalStateException::class.java) {
      Schema11Importer.inspect(
        """{"schemaVersion":11,"settings":{"api_key":"do-not-import"}}""".toByteArray()
      )
    }
  }
}
