/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class IndexedArtifact(
  val artifactId: String,
  val sourceKind: CortexSourceKind,
  val contentHash: String,
  val contentBytes: Int,
  val markdownLocation: String,
  val documentHash: String,
)

internal data class IndexedImportCollection(
  val name: String,
  val entryCount: Int,
  val markdownLocation: String,
  val documentHash: String,
)

internal data class CortexIndexCounts(
  val exchanges: Int,
  val artifacts: Int,
  val imports: Int,
  val retrievals: Int,
)

internal data class IndexedRecallCandidate(
  val artifactId: String,
  val exchangeId: String,
  val sessionId: String,
  val sourceKind: CortexSourceKind,
  val capturedAtEpochMs: Long,
  val contentHash: String,
  val markdownLocation: String,
  val documentHash: String,
)

/** Rebuildable native index; canonical source truth remains in the Markdown vault. */
internal class CortexIndexDatabase(context: Context) :
  SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

  init {
    context.getDatabasePath(DATABASE_NAME).parentFile?.mkdirs()
  }

  override fun onConfigure(db: SQLiteDatabase) {
    super.onConfigure(db)
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE exchanges (
        exchange_id TEXT PRIMARY KEY,
        session_id TEXT NOT NULL,
        task_id TEXT NOT NULL,
        model_name TEXT NOT NULL,
        captured_at_ms INTEGER NOT NULL,
        receipt_location TEXT NOT NULL,
        receipt_document_sha256 TEXT NOT NULL,
        verified INTEGER NOT NULL CHECK (verified IN (0, 1))
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE artifacts (
        artifact_id TEXT PRIMARY KEY,
        exchange_id TEXT NOT NULL REFERENCES exchanges(exchange_id) ON DELETE CASCADE,
        source_kind TEXT NOT NULL,
        content_sha256 TEXT NOT NULL,
        content_bytes INTEGER NOT NULL,
        markdown_location TEXT NOT NULL,
        document_sha256 TEXT NOT NULL,
        verified INTEGER NOT NULL CHECK (verified IN (0, 1))
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE import_receipts (
        source_sha256 TEXT PRIMARY KEY,
        schema_version INTEGER NOT NULL,
        imported_at_ms INTEGER NOT NULL,
        archive_location TEXT NOT NULL,
        receipt_location TEXT NOT NULL,
        collection_count INTEGER NOT NULL,
        entry_count INTEGER NOT NULL,
        verified INTEGER NOT NULL CHECK (verified IN (0, 1))
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE import_collections (
        source_sha256 TEXT NOT NULL REFERENCES import_receipts(source_sha256) ON DELETE CASCADE,
        collection_name TEXT NOT NULL,
        entry_count INTEGER NOT NULL,
        markdown_location TEXT NOT NULL,
        document_sha256 TEXT NOT NULL,
        PRIMARY KEY (source_sha256, collection_name)
      )
      """.trimIndent()
    )
    createRetrievalReceiptsTable(db)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) createRetrievalReceiptsTable(db)
    require(newVersion <= DATABASE_VERSION) {
      "No Cortex database migration is defined from $oldVersion to $newVersion."
    }
  }

  fun recordExchange(
    request: CortexExchangeCaptureRequest,
    exchangeId: String,
    receipt: StoredVaultDocument,
    artifacts: List<IndexedArtifact>,
  ) {
    writableDatabase.inTransaction {
      insertOrThrow(
        "exchanges",
        null,
        ContentValues().apply {
          put("exchange_id", exchangeId)
          put("session_id", request.sessionId)
          put("task_id", request.taskId)
          put("model_name", request.modelName)
          put("captured_at_ms", request.completedAtEpochMs)
          put("receipt_location", receipt.location)
          put("receipt_document_sha256", receipt.documentSha256)
          put("verified", 1)
        },
      )
      artifacts.forEach { artifact ->
        insertOrThrow(
          "artifacts",
          null,
          ContentValues().apply {
            put("artifact_id", artifact.artifactId)
            put("exchange_id", exchangeId)
            put("source_kind", artifact.sourceKind.name)
            put("content_sha256", artifact.contentHash)
            put("content_bytes", artifact.contentBytes)
            put("markdown_location", artifact.markdownLocation)
            put("document_sha256", artifact.documentHash)
            put("verified", 1)
          },
        )
      }
    }
  }

  fun hasImport(sourceHash: String): Boolean =
    readableDatabase
      .rawQuery(
        "SELECT 1 FROM import_receipts WHERE source_sha256 = ? LIMIT 1",
        arrayOf(sourceHash),
      )
      .use { cursor -> cursor.moveToFirst() }

  fun recordImport(
    sourceHash: String,
    importedAtEpochMs: Long,
    archive: StoredVaultDocument,
    receipt: StoredVaultDocument,
    collections: List<IndexedImportCollection>,
  ) {
    writableDatabase.inTransaction {
      insertOrThrow(
        "import_receipts",
        null,
        ContentValues().apply {
          put("source_sha256", sourceHash)
          put("schema_version", 11)
          put("imported_at_ms", importedAtEpochMs)
          put("archive_location", archive.location)
          put("receipt_location", receipt.location)
          put("collection_count", collections.size)
          put("entry_count", collections.sumOf { it.entryCount })
          put("verified", 1)
        },
      )
      collections.forEach { collection ->
        insertOrThrow(
          "import_collections",
          null,
          ContentValues().apply {
            put("source_sha256", sourceHash)
            put("collection_name", collection.name)
            put("entry_count", collection.entryCount)
            put("markdown_location", collection.markdownLocation)
            put("document_sha256", collection.documentHash)
          },
        )
      }
    }
  }

  fun recentRecallCandidates(
    excludedSessionId: String,
    limit: Int,
  ): List<IndexedRecallCandidate> {
    val safeLimit = limit.coerceIn(1, 256)
    return readableDatabase
      .rawQuery(
        """
        SELECT a.artifact_id, a.exchange_id, e.session_id, a.source_kind,
               e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256
        FROM artifacts a
        JOIN exchanges e ON e.exchange_id = a.exchange_id
        WHERE a.verified = 1 AND e.verified = 1 AND e.session_id != ?
        ORDER BY e.captured_at_ms DESC,
                 CASE a.source_kind WHEN 'USER_STATED' THEN 0 ELSE 1 END
        LIMIT ?
        """.trimIndent(),
        arrayOf(excludedSessionId, safeLimit.toString()),
      )
      .use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(
              IndexedRecallCandidate(
                artifactId = cursor.getString(0),
                exchangeId = cursor.getString(1),
                sessionId = cursor.getString(2),
                sourceKind = CortexSourceKind.valueOf(cursor.getString(3)),
                capturedAtEpochMs = cursor.getLong(4),
                contentHash = cursor.getString(5),
                markdownLocation = cursor.getString(6),
                documentHash = cursor.getString(7),
              )
            )
          }
        }
      }
  }

  fun recordRetrieval(
    receiptId: String,
    sessionId: String,
    queryHash: String,
    recalledAtEpochMs: Long,
    receipt: StoredVaultDocument,
    artifactIdsJson: String,
  ) {
    writableDatabase.insertOrThrow(
      "retrieval_receipts",
      null,
      ContentValues().apply {
        put("receipt_id", receiptId)
        put("session_id", sessionId)
        put("query_sha256", queryHash)
        put("recalled_at_ms", recalledAtEpochMs)
        put("receipt_location", receipt.location)
        put("receipt_document_sha256", receipt.documentSha256)
        put("artifact_ids_json", artifactIdsJson)
        put("verified", 1)
      },
    )
  }

  fun counts(): CortexIndexCounts =
    CortexIndexCounts(
      exchanges = count("exchanges"),
      artifacts = count("artifacts"),
      imports = count("import_receipts"),
      retrievals = count("retrieval_receipts"),
    )

  private fun count(table: String): Int =
    readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    }

  private inline fun SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try {
      block()
      setTransactionSuccessful()
    } finally {
      endTransaction()
    }
  }

  private fun createRetrievalReceiptsTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS retrieval_receipts (
        receipt_id TEXT PRIMARY KEY,
        session_id TEXT NOT NULL,
        query_sha256 TEXT NOT NULL,
        recalled_at_ms INTEGER NOT NULL,
        receipt_location TEXT NOT NULL,
        receipt_document_sha256 TEXT NOT NULL,
        artifact_ids_json TEXT NOT NULL,
        verified INTEGER NOT NULL CHECK (verified IN (0, 1))
      )
      """.trimIndent()
    )
  }

  private companion object {
    const val DATABASE_NAME = "jarvis_alpha_cortex.db"
    const val DATABASE_VERSION = 2
  }
}
