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
          currentSessionId = "device-test-new-session",
        )
      )
    assertTrue(recall.message, recall.verified)
    assertTrue(recall.contextForModel.contains("Greenwood, Delaware"))
    assertEquals(1, recall.artifactIds.size)
    val broadRecall =
      runtime.recall(
        CortexRecallRequest(
          query = "Hey Echo what do you remember about me",
          currentSessionId = "device-test-new-session",
        )
      )
    assertTrue(broadRecall.message, broadRecall.verified)
    assertTrue(broadRecall.contextForModel.contains("Greenwood, Delaware"))
    assertEquals(1, broadRecall.artifactIds.size)
    val retrievalFiles =
      retrievalDirectory.listFiles().orEmpty().filter { file -> file.name !in retrievalNamesBefore }
    assertEquals(2, retrievalFiles.size)
    retrievalFiles.forEach { file ->
      assertTrue("document_type: memory_cycle_retrieval_receipt" in file.readText())
      assertTrue("verified: true" in file.readText())
    }

    val after = runtime.status.value
    assertEquals(before.verifiedExchanges + 1, after.verifiedExchanges)
    assertEquals(before.verifiedArtifacts + 2, after.verifiedArtifacts)
    assertEquals(before.verifiedRecalls + 2, after.verifiedRecalls)
    val reopenedIndex = CortexIndexDatabase(context)
    try {
      assertEquals(after.verifiedExchanges, reopenedIndex.counts().exchanges)
      assertEquals(after.verifiedArtifacts, reopenedIndex.counts().artifacts)
      assertEquals(after.verifiedRecalls, reopenedIndex.counts().retrievals)
    } finally {
      reopenedIndex.close()
    }
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
