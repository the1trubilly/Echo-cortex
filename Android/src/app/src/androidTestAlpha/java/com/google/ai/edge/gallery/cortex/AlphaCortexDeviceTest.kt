/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlphaCortexDeviceTest {
  @Test
  fun capturesAndRecallsBillyAndJarvisAsVerifiedMarkdownArtifacts() = runBlocking {
    // Use a disposable cache-rooted context, never Billy's canonical Alpha or Main data.
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val context = TemporaryCortexContext(targetContext)
    val runtime = AlphaCortexRuntime.get(context)
    val before = runtime.status.value
    val turnsDirectory = File(context.filesDir, "cortex-vault/turns")
    val receiptsDirectory = File(context.filesDir, "cortex-vault/receipts")
    val retrievalDirectory = File(context.filesDir, "cortex-vault/retrieval-receipts")
    val turnNamesBefore = turnsDirectory.listFiles()?.map(File::getName)?.toSet().orEmpty()
    val receiptNamesBefore = receiptsDirectory.listFiles()?.map(File::getName)?.toSet().orEmpty()
    val billyExact =
      "Device-test Billy turn\nI live in Greenwood, Delaware.\nwith Markdown: **exact**"
    val jarvisExact = "Device-test Jarvis reply\nwith Unicode: 🧠"

    val receipt =
      runtime.captureExchange(
        CortexExchangeCaptureRequest(
          sessionId = "device-test-session",
          taskId = "llm_agent_chat",
          modelName = "device-test-model",
          userMessage = billyExact,
          assistantResponse = jarvisExact,
          completedAtEpochMs = System.currentTimeMillis(),
        )
      )

    assertTrue(receipt.message, receipt.verified)
    val newTurnFiles =
      turnsDirectory.listFiles().orEmpty().filter { file -> file.name !in turnNamesBefore }
    val newReceiptFiles =
      receiptsDirectory.listFiles().orEmpty().filter { file -> file.name !in receiptNamesBefore }
    assertEquals(2, newTurnFiles.size)
    assertEquals(1, newReceiptFiles.size)

    val contentBySource =
      newTurnFiles.associate { file ->
        val document = file.readBytes()
        val header = document.toString(Charsets.UTF_8).substringBefore("---\n\n")
        val source =
          when {
            "source_kind: USER_STATED" in header -> CortexSourceKind.USER_STATED
            "source_kind: OTHER_AGENT" in header -> CortexSourceKind.OTHER_AGENT
            else -> error("Missing ThreadKeeper provenance in ${file.name}")
          }
        source to CortexMarkdownCodec.decodeExactContent(document).toString(Charsets.UTF_8)
      }
    assertEquals(billyExact, contentBySource[CortexSourceKind.USER_STATED])
    assertEquals(jarvisExact, contentBySource[CortexSourceKind.OTHER_AGENT])
    assertTrue("verified: true" in newReceiptFiles.single().readText())

    val retrievalNamesBefore =
      retrievalDirectory.listFiles()?.map(File::getName)?.toSet().orEmpty()
    val recall =
      runtime.recall(
        CortexRecallRequest(
          query = "Where do I live?",
          // The chat UI may recycle a session ID across a surface reset. Prior verified turns in
          // the same logical session must remain retrievable; the current turn is not captured yet.
          currentSessionId = "device-test-session",
        )
      )
    assertTrue(recall.message, recall.verified)
    assertTrue(recall.contextForModel.contains("Greenwood, Delaware"))
    assertEquals(1, recall.artifactIds.size)
    val broadRecall =
      runtime.recall(
        CortexRecallRequest(
          query = "Hey Echo what do you remember about me",
          currentSessionId = "device-test-session",
        )
      )
    assertTrue(broadRecall.message, broadRecall.verified)
    assertTrue(broadRecall.contextForModel.contains("Greenwood, Delaware"))
    assertEquals(1, broadRecall.artifactIds.size)
    val memoryTestRecall =
      runtime.recall(
        CortexRecallRequest(
          query = "Tell me something about me from the memory test",
          currentSessionId = "a-new-user-visible-chat",
        )
      )
    assertTrue(memoryTestRecall.message, memoryTestRecall.verified)
    assertTrue(memoryTestRecall.contextForModel.contains("Greenwood, Delaware"))
    assertEquals(1, memoryTestRecall.artifactIds.size)
    val synthesisMemories =
      listOf(
        "device-synthesis-autonomy" to
          "I want Jarvis to become an autonomous collaborator that can improve its own app.",
        "device-synthesis-workshop" to
          "Infinite Workshop is a framework for turning ideas into tools that build more tools.",
        "device-synthesis-consciousness" to
          "I care about consciousness, simulation theory, meaning-making, and helping people.",
      )
    synthesisMemories.forEachIndexed { index, (sessionId, memory) ->
      val synthesisCapture =
        runtime.captureExchange(
          CortexExchangeCaptureRequest(
            sessionId = sessionId,
            taskId = "llm_agent_chat",
            modelName = "device-test-model",
            userMessage = memory,
            assistantResponse = "Saved synthesis memory ${index + 1}.",
            completedAtEpochMs = System.currentTimeMillis() + index + 1,
          )
        )
      assertTrue(synthesisCapture.message, synthesisCapture.verified)
    }
    val synthesisRecall =
      runtime.recall(
        CortexRecallRequest(
          query =
            "Across our conversations, synthesize how Jarvis, Infinite Workshop, " +
              "consciousness, and autonomy fit together",
          currentSessionId = "device-synthesis-new-chat",
        )
      )
    assertTrue(synthesisRecall.message, synthesisRecall.verified)
    assertTrue(synthesisRecall.contextForModel.contains("autonomous collaborator"))
    assertTrue(synthesisRecall.contextForModel.contains("Infinite Workshop"))
    assertTrue(synthesisRecall.contextForModel.contains("consciousness"))
    assertTrue(synthesisRecall.artifactIds.size >= 3)
    val retrievalFiles =
      retrievalDirectory.listFiles().orEmpty().filter { file -> file.name !in retrievalNamesBefore }
    assertEquals(4, retrievalFiles.size)
    retrievalFiles.forEach { file ->
      val receiptText = file.readText()
      assertTrue("document_type: memory_cycle_retrieval_receipt" in receiptText)
      assertTrue("threadkeeper_schema: 13" in receiptText)
      assertTrue("authority_ceiling: inform_only" in receiptText)
      assertTrue("physics_ticks: 3" in receiptText)
      assertTrue("## Bounded physics trace" in receiptText)
      assertTrue("verified: true" in receiptText)
    }

    val after = runtime.status.value
    assertEquals(before.verifiedExchanges + 4, after.verifiedExchanges)
    assertEquals(before.verifiedArtifacts + 8, after.verifiedArtifacts)
    assertEquals(before.verifiedRecalls + 4, after.verifiedRecalls)
    val reopenedIndex = CortexIndexDatabase(context)
    try {
      assertEquals(after.verifiedExchanges, reopenedIndex.counts().exchanges)
      assertEquals(after.verifiedArtifacts, reopenedIndex.counts().artifacts)
      assertEquals(after.verifiedRecalls, reopenedIndex.counts().retrievals)
      val db = reopenedIndex.readableDatabase
      assertTrue(countRows(db, "concepts") > 0)
      assertTrue(countRows(db, "artifact_concepts") >= after.verifiedArtifacts)
      assertTrue(countRows(db, "concept_edges") > 0)
      assertEquals(0, countRows(db, "artifacts", "cognitive_indexed = 0"))
      assertEquals(0, countRows(db, "artifacts", "authority_ceiling != 'inform_only'"))
    } finally {
      reopenedIndex.close()
    }
  }

  private fun countRows(db: SQLiteDatabase, table: String, where: String? = null): Int =
    db.rawQuery(
        "SELECT COUNT(*) FROM $table${if (where == null) "" else " WHERE $where"}",
        null,
      )
      .use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
      }

  private class TemporaryCortexContext(base: Context) : ContextWrapper(base) {
    private val identifier = UUID.randomUUID().toString()
    private val root = File(base.cacheDir, "cortex-device-test-$identifier").apply { mkdirs() }
    private val files = File(root, "files").apply { mkdirs() }
    private val databases = File(root, "databases").apply { mkdirs() }

    override fun getApplicationContext(): Context? = null

    override fun getFilesDir(): File = files

    override fun getDatabasePath(name: String): File = File(databases, name)

    override fun openOrCreateDatabase(
      name: String,
      mode: Int,
      factory: SQLiteDatabase.CursorFactory?,
    ): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)

    override fun openOrCreateDatabase(
      name: String,
      mode: Int,
      factory: SQLiteDatabase.CursorFactory?,
      errorHandler: DatabaseErrorHandler?,
    ): SQLiteDatabase =
      if (errorHandler == null) {
        openOrCreateDatabase(name, mode, factory)
      } else {
        SQLiteDatabase.openOrCreateDatabase(
          getDatabasePath(name).absolutePath,
          factory,
          errorHandler,
        )
      }

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
      baseContext.getSharedPreferences("cortex_device_test_${identifier}_$name", mode)
  }
}
