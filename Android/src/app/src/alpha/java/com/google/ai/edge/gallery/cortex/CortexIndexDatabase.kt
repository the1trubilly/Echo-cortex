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
  val recallTerms: String,
  val durablePersonalScore: Int,
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

internal data class UnindexedRecallArtifact(
  val artifactId: String,
  val sourceKind: CortexSourceKind,
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
        recall_terms TEXT NOT NULL DEFAULT '',
        durable_personal_score INTEGER NOT NULL DEFAULT 0,
        recall_indexed INTEGER NOT NULL DEFAULT 0 CHECK (recall_indexed IN (0, 1)),
        verified INTEGER NOT NULL CHECK (verified IN (0, 1))
      )
      """.trimIndent()
    )
    createArtifactSearchTable(db)
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
    if (oldVersion < 3) {
      db.execSQL("ALTER TABLE artifacts ADD COLUMN recall_terms TEXT NOT NULL DEFAULT ''")
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN durable_personal_score INTEGER NOT NULL DEFAULT 0"
      )
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN recall_indexed INTEGER NOT NULL DEFAULT 0 " +
          "CHECK (recall_indexed IN (0, 1))"
      )
      createArtifactSearchTable(db)
    }
    if (oldVersion < 4) {
      // Search metadata is derived and rebuildable. Re-run classification when its rules change;
      // canonical Markdown and its verification hashes are never rewritten by this migration.
      db.execSQL(
        "UPDATE artifacts SET recall_terms = '', durable_personal_score = 0, recall_indexed = 0"
      )
      db.delete("artifact_search", null, null)
    }
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
            put("recall_terms", artifact.recallTerms)
            put("durable_personal_score", artifact.durablePersonalScore)
            put("recall_indexed", 1)
            put("verified", 1)
          },
        )
        insertOrThrow(
          "artifact_search",
          null,
          ContentValues().apply {
            put("artifact_id", artifact.artifactId)
            put("recall_terms", artifact.recallTerms)
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

  fun unindexedRecallArtifacts(
    limit: Int,
    newestFirst: Boolean,
  ): List<UnindexedRecallArtifact> =
    readableDatabase
      .rawQuery(
        """
        SELECT artifact_id, source_kind, content_sha256, markdown_location, document_sha256
        FROM artifacts
        WHERE verified = 1 AND recall_indexed = 0
        ORDER BY rowid ${if (newestFirst) "DESC" else "ASC"}
        LIMIT ?
        """.trimIndent(),
        arrayOf(limit.coerceIn(1, MAX_INDEX_BACKFILL_BATCH).toString()),
      )
      .use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(
              UnindexedRecallArtifact(
                artifactId = cursor.getString(0),
                sourceKind = CortexSourceKind.valueOf(cursor.getString(1)),
                contentHash = cursor.getString(2),
                markdownLocation = cursor.getString(3),
                documentHash = cursor.getString(4),
              )
            )
          }
        }
      }

  fun updateRecallMetadata(
    artifactId: String,
    metadata: CortexRecallIndexMetadata,
  ) {
    writableDatabase.inTransaction {
      val updated =
        update(
          "artifacts",
          ContentValues().apply {
            put("recall_terms", metadata.normalizedTerms)
            put("durable_personal_score", metadata.durablePersonalScore)
            put("recall_indexed", 1)
          },
          "artifact_id = ? AND verified = 1",
          arrayOf(artifactId),
        )
      require(updated == 1) { "Recall metadata target is missing or unverified." }
      delete("artifact_search", "artifact_id = ?", arrayOf(artifactId))
      insertOrThrow(
        "artifact_search",
        null,
        ContentValues().apply {
          put("artifact_id", artifactId)
          put("recall_terms", metadata.normalizedTerms)
        },
      )
    }
  }

  /** Whole-index retrieval produces a bounded local field; recency is an input, not a wall. */
  fun recallCandidates(
    queryTerms: Set<String>,
    intent: CortexRecallIntent,
    limit: Int,
  ): List<IndexedRecallCandidate> {
    val safeLimit = limit.coerceIn(1, MAX_RECALL_CANDIDATES)
    if (intent == CortexRecallIntent.BROAD_PERSONAL) {
      return queryRecallCandidates(
          sql =
            """
            SELECT a.artifact_id, a.exchange_id, e.session_id, a.source_kind,
                   e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256
            FROM artifacts a
            JOIN exchanges e ON e.exchange_id = a.exchange_id
            WHERE a.verified = 1 AND e.verified = 1
              AND a.source_kind = 'USER_STATED' AND a.durable_personal_score > 0
              AND a.content_bytes <= $MAX_DURABLE_PERSONAL_CHARS
            ORDER BY a.durable_personal_score DESC, e.captured_at_ms DESC
            LIMIT ?
            """.trimIndent(),
          args = arrayOf(safeLimit.toString()),
        )
        .sortedByDescending(IndexedRecallCandidate::capturedAtEpochMs)
    }

    val safeTerms = queryTerms.filter { term -> SAFE_SEARCH_TERM.matches(term) }.take(12)
    val durableSeeds =
      if (intent == CortexRecallIntent.SYNTHESIS) {
        queryRecallCandidates(
          sql =
            """
            SELECT a.artifact_id, a.exchange_id, e.session_id, a.source_kind,
                   e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256
            FROM artifacts a
            JOIN exchanges e ON e.exchange_id = a.exchange_id
            WHERE a.verified = 1 AND e.verified = 1
              AND a.source_kind = 'USER_STATED' AND a.durable_personal_score > 0
              AND a.content_bytes <= $MAX_DURABLE_PERSONAL_CHARS
            ORDER BY a.durable_personal_score DESC, e.captured_at_ms DESC
            LIMIT ?
            """.trimIndent(),
          args = arrayOf(MAX_SYNTHESIS_DURABLE_SEEDS.toString()),
        )
      } else {
        emptyList()
      }
    if (safeTerms.isEmpty()) return durableSeeds.take(safeLimit)

    // Query each cue separately and merge round-robin. A common word such as "Jarvis" must not
    // crowd a rarer cue such as "consciousness" or "workshop" out of the bounded candidate field.
    val seedBuckets =
      safeTerms.map { term ->
        queryRecallCandidates(
          sql =
            """
            SELECT a.artifact_id, a.exchange_id, e.session_id, a.source_kind,
                   e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256
            FROM artifact_search
            JOIN artifacts a ON a.artifact_id = artifact_search.artifact_id
            JOIN exchanges e ON e.exchange_id = a.exchange_id
            WHERE artifact_search MATCH ? AND a.verified = 1 AND e.verified = 1
            ORDER BY a.durable_personal_score DESC, e.captured_at_ms DESC
            LIMIT ?
            """.trimIndent(),
          args = arrayOf(term, MAX_SEEDS_PER_TERM.toString()),
        )
      }
    val seedBudget =
      if (intent == CortexRecallIntent.SYNTHESIS) MAX_SYNTHESIS_LEXICAL_SEEDS else safeLimit
    val seeds =
      (0 until MAX_SEEDS_PER_TERM)
        .flatMap { rank -> seedBuckets.mapNotNull { bucket -> bucket.getOrNull(rank) } }
        .distinctBy(IndexedRecallCandidate::artifactId)
        .take(seedBudget)
    if (seeds.isEmpty()) return durableSeeds.take(safeLimit)

    val sessionIds = seeds.map(IndexedRecallCandidate::sessionId).distinct().take(12)
    val placeholders = sessionIds.joinToString(",") { "?" }
    val related =
      queryRecallCandidates(
        sql =
          """
          SELECT a.artifact_id, a.exchange_id, e.session_id, a.source_kind,
                 e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256
          FROM artifacts a
          JOIN exchanges e ON e.exchange_id = a.exchange_id
          WHERE a.verified = 1 AND e.verified = 1 AND e.session_id IN ($placeholders)
          ORDER BY e.captured_at_ms DESC,
                   CASE a.source_kind WHEN 'USER_STATED' THEN 0 ELSE 1 END
          LIMIT ?
          """.trimIndent(),
        args = (sessionIds + safeLimit.toString()).toTypedArray(),
      )
    return (durableSeeds + seeds + related)
      .distinctBy(IndexedRecallCandidate::artifactId)
      .take(safeLimit)
      .sortedByDescending(IndexedRecallCandidate::capturedAtEpochMs)
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

  private fun createArtifactSearchTable(db: SQLiteDatabase) {
    db.execSQL(
      "CREATE VIRTUAL TABLE IF NOT EXISTS artifact_search USING fts4(artifact_id, recall_terms)"
    )
  }

  private fun queryRecallCandidates(
    sql: String,
    args: Array<String>,
  ): List<IndexedRecallCandidate> =
    readableDatabase.rawQuery(sql, args).use { cursor ->
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

  private companion object {
    const val DATABASE_NAME = "jarvis_alpha_cortex.db"
    const val DATABASE_VERSION = 4
    const val MAX_RECALL_CANDIDATES = 64
    const val MAX_INDEX_BACKFILL_BATCH = 256
    const val MAX_DURABLE_PERSONAL_CHARS = 8_000
    const val MAX_SYNTHESIS_DURABLE_SEEDS = 16
    const val MAX_SYNTHESIS_LEXICAL_SEEDS = 40
    const val MAX_SEEDS_PER_TERM = 8
    val SAFE_SEARCH_TERM = Regex("^[\\p{L}\\p{N}_-]{2,64}$")
  }
}
