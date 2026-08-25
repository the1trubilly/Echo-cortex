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
  val conceptTerms: List<String>,
  val normalization: CortexNormalizationMetadata,
  val normalizationSidecar: StoredVaultDocument,
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
  val recallTerms: String,
  val durablePersonalScore: Int,
  val statementKind: String,
  val temporalStatus: String,
  val modality: String,
  val correctionCue: Boolean,
)

internal data class UnindexedRecallArtifact(
  val artifactId: String,
  val sourceKind: CortexSourceKind,
  val contentHash: String,
  val markdownLocation: String,
  val documentHash: String,
  val capturedAtEpochMs: Long,
  val normalizationVersion: Int,
)

internal data class CortexArtifactRelation(
  val sourceArtifactId: String,
  val targetArtifactId: String,
  val relationType: String,
  val strength: Double,
  val confidence: Double,
  val evidenceBasis: String,
  val confirmationStatus: String,
  val authorityCeiling: String,
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
        cognitive_indexed INTEGER NOT NULL DEFAULT 0 CHECK (cognitive_indexed IN (0, 1)),
        origin_class TEXT NOT NULL DEFAULT 'unknown',
        origin_id TEXT NOT NULL DEFAULT '',
        origin_trust TEXT NOT NULL DEFAULT 'derived',
        authority_ceiling TEXT NOT NULL DEFAULT 'inform_only',
        privacy_scope TEXT NOT NULL DEFAULT 'general',
        disclosure_policy TEXT NOT NULL DEFAULT 'safe_with_user',
        quarantine_status TEXT NOT NULL DEFAULT 'none',
        retrieval_eligible INTEGER NOT NULL DEFAULT 1 CHECK (retrieval_eligible IN (0, 1)),
        memory_state TEXT NOT NULL DEFAULT 'active',
        observed_at_ms INTEGER NOT NULL DEFAULT 0,
        recorded_at_ms INTEGER NOT NULL DEFAULT 0,
        normalization_version INTEGER NOT NULL DEFAULT 0,
        normalization_sidecar_location TEXT NOT NULL DEFAULT '',
        normalization_sidecar_sha256 TEXT NOT NULL DEFAULT '',
        normalization_projection_sha256 TEXT NOT NULL DEFAULT '',
        statement_kind TEXT NOT NULL DEFAULT 'unclassified',
        temporal_status TEXT NOT NULL DEFAULT 'unspecified',
        modality TEXT NOT NULL DEFAULT 'unknown',
        correction_cue INTEGER NOT NULL DEFAULT 0 CHECK (correction_cue IN (0, 1)),
        verified INTEGER NOT NULL CHECK (verified IN (0, 1))
      )
      """.trimIndent()
    )
    createArtifactSearchTable(db)
    createCognitiveTables(db)
    createNormalizationTables(db)
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
    if (oldVersion < 5) {
      // Schema-13 governance and graph data are a disposable index over canonical Markdown.
      // Migration never rewrites or promotes the exact source artifacts.
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN cognitive_indexed INTEGER NOT NULL DEFAULT 0 " +
          "CHECK (cognitive_indexed IN (0, 1))"
      )
      db.execSQL("ALTER TABLE artifacts ADD COLUMN origin_class TEXT NOT NULL DEFAULT 'unknown'")
      db.execSQL("ALTER TABLE artifacts ADD COLUMN origin_id TEXT NOT NULL DEFAULT ''")
      db.execSQL("ALTER TABLE artifacts ADD COLUMN origin_trust TEXT NOT NULL DEFAULT 'derived'")
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN authority_ceiling TEXT NOT NULL DEFAULT 'inform_only'"
      )
      db.execSQL("ALTER TABLE artifacts ADD COLUMN privacy_scope TEXT NOT NULL DEFAULT 'general'")
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN disclosure_policy TEXT NOT NULL DEFAULT 'safe_with_user'"
      )
      db.execSQL("ALTER TABLE artifacts ADD COLUMN quarantine_status TEXT NOT NULL DEFAULT 'none'")
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN retrieval_eligible INTEGER NOT NULL DEFAULT 1 " +
          "CHECK (retrieval_eligible IN (0, 1))"
      )
      db.execSQL("ALTER TABLE artifacts ADD COLUMN memory_state TEXT NOT NULL DEFAULT 'active'")
      db.execSQL("ALTER TABLE artifacts ADD COLUMN observed_at_ms INTEGER NOT NULL DEFAULT 0")
      db.execSQL("ALTER TABLE artifacts ADD COLUMN recorded_at_ms INTEGER NOT NULL DEFAULT 0")
      db.execSQL(
        """
        UPDATE artifacts
        SET origin_class = CASE source_kind WHEN 'USER_STATED' THEN 'user' ELSE 'agent' END,
            origin_id = CASE source_kind WHEN 'USER_STATED' THEN 'Billy' ELSE 'Jarvis' END,
            origin_trust = CASE source_kind WHEN 'USER_STATED' THEN 'user' ELSE 'derived' END,
            observed_at_ms = COALESCE(
              (SELECT captured_at_ms FROM exchanges WHERE exchanges.exchange_id = artifacts.exchange_id),
              0
            ),
            recorded_at_ms = COALESCE(
              (SELECT captured_at_ms FROM exchanges WHERE exchanges.exchange_id = artifacts.exchange_id),
              0
            ),
            cognitive_indexed = 0
        """.trimIndent()
      )
      createCognitiveTables(db)
    }
    if (oldVersion < 6) {
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN normalization_version INTEGER NOT NULL DEFAULT 0"
      )
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN normalization_sidecar_location TEXT NOT NULL DEFAULT ''"
      )
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN normalization_sidecar_sha256 TEXT NOT NULL DEFAULT ''"
      )
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN normalization_projection_sha256 TEXT NOT NULL DEFAULT ''"
      )
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN statement_kind TEXT NOT NULL DEFAULT 'unclassified'"
      )
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN temporal_status TEXT NOT NULL DEFAULT 'unspecified'"
      )
      db.execSQL("ALTER TABLE artifacts ADD COLUMN modality TEXT NOT NULL DEFAULT 'unknown'")
      db.execSQL(
        "ALTER TABLE artifacts ADD COLUMN correction_cue INTEGER NOT NULL DEFAULT 0 " +
          "CHECK (correction_cue IN (0, 1))"
      )
      createNormalizationTables(db)
      // Derived transformation state is rebuilt. Exact source Markdown is untouched.
      db.execSQL("UPDATE artifacts SET normalization_version = 0")
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
            put("cognitive_indexed", 1)
            put(
              "origin_class",
              if (artifact.sourceKind == CortexSourceKind.USER_STATED) "user" else "agent",
            )
            put(
              "origin_id",
              if (artifact.sourceKind == CortexSourceKind.USER_STATED) "Billy" else "Jarvis",
            )
            put(
              "origin_trust",
              if (artifact.sourceKind == CortexSourceKind.USER_STATED) "user" else "derived",
            )
            put("authority_ceiling", "inform_only")
            put("privacy_scope", "general")
            put("disclosure_policy", "safe_with_user")
            put("quarantine_status", "none")
            put("retrieval_eligible", 1)
            put("memory_state", "active")
            put("observed_at_ms", request.completedAtEpochMs)
            put("recorded_at_ms", request.completedAtEpochMs)
            put("normalization_version", artifact.normalization.version)
            put("normalization_sidecar_location", artifact.normalizationSidecar.location)
            put(
              "normalization_sidecar_sha256",
              artifact.normalizationSidecar.documentSha256,
            )
            put("normalization_projection_sha256", artifact.normalization.projectionHash)
            put("statement_kind", artifact.normalization.statementKind)
            put("temporal_status", artifact.normalization.temporalStatus)
            put("modality", artifact.normalization.modality)
            put("correction_cue", if (artifact.normalization.correctionCue) 1 else 0)
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
        indexCognitiveMetadata(
          db = this,
          artifactId = artifact.artifactId,
          conceptTerms = artifact.conceptTerms,
          capturedAtEpochMs = request.completedAtEpochMs,
        )
        recordNormalizationReceipt(
          db = this,
          artifactId = artifact.artifactId,
          sourceContentHash = artifact.contentHash,
          normalization = artifact.normalization,
          sidecar = artifact.normalizationSidecar,
          normalizedAtEpochMs = request.completedAtEpochMs,
        )
        indexTypedRelations(
          db = this,
          artifactId = artifact.artifactId,
          capturedAtEpochMs = request.completedAtEpochMs,
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
        SELECT a.artifact_id, a.source_kind, a.content_sha256, a.markdown_location,
               a.document_sha256, e.captured_at_ms, a.normalization_version
        FROM artifacts a
        JOIN exchanges e ON e.exchange_id = a.exchange_id
        WHERE a.verified = 1 AND (
          a.recall_indexed = 0 OR a.cognitive_indexed = 0 OR
          a.normalization_version < ${CortexMemoryNormalizer.VERSION}
        )
        ORDER BY a.rowid ${if (newestFirst) "DESC" else "ASC"}
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
                capturedAtEpochMs = cursor.getLong(5),
                normalizationVersion = cursor.getInt(6),
              )
            )
          }
        }
      }

  fun updateRecallMetadata(
    artifactId: String,
    metadata: CortexRecallIndexMetadata,
    sourceContentHash: String,
    capturedAtEpochMs: Long,
    normalization: CortexNormalizationMetadata,
    normalizationSidecar: StoredVaultDocument,
    normalizedAtEpochMs: Long,
  ) {
    writableDatabase.inTransaction {
      val needsCognitiveIndex =
        rawQuery(
            "SELECT cognitive_indexed FROM artifacts WHERE artifact_id = ? AND verified = 1",
            arrayOf(artifactId),
          )
          .use { cursor ->
            require(cursor.moveToFirst()) { "Recall metadata target is missing or unverified." }
            cursor.getInt(0) == 0
          }
      val updated =
        update(
          "artifacts",
          ContentValues().apply {
            put("recall_terms", metadata.normalizedTerms)
            put("durable_personal_score", metadata.durablePersonalScore)
            put("recall_indexed", 1)
            put("cognitive_indexed", 1)
            put("normalization_version", normalization.version)
            put("normalization_sidecar_location", normalizationSidecar.location)
            put("normalization_sidecar_sha256", normalizationSidecar.documentSha256)
            put("normalization_projection_sha256", normalization.projectionHash)
            put("statement_kind", normalization.statementKind)
            put("temporal_status", normalization.temporalStatus)
            put("modality", normalization.modality)
            put("correction_cue", if (normalization.correctionCue) 1 else 0)
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
      if (needsCognitiveIndex) {
        indexCognitiveMetadata(
          db = this,
          artifactId = artifactId,
          conceptTerms = metadata.conceptTerms,
          capturedAtEpochMs = capturedAtEpochMs,
        )
      }
      recordNormalizationReceipt(
        db = this,
        artifactId = artifactId,
        sourceContentHash = sourceContentHash,
        normalization = normalization,
        sidecar = normalizationSidecar,
        normalizedAtEpochMs = normalizedAtEpochMs,
      )
      indexTypedRelations(
        db = this,
        artifactId = artifactId,
        capturedAtEpochMs = capturedAtEpochMs,
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
                   e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256,
                   a.recall_terms, a.durable_personal_score, a.statement_kind,
                   a.temporal_status, a.modality, a.correction_cue
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
                   e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256,
                   a.recall_terms, a.durable_personal_score, a.statement_kind,
                   a.temporal_status, a.modality, a.correction_cue
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
                   e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256,
                   a.recall_terms, a.durable_personal_score, a.statement_kind,
                   a.temporal_status, a.modality, a.correction_cue
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
                 e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256,
                 a.recall_terms, a.durable_personal_score, a.statement_kind,
                 a.temporal_status, a.modality, a.correction_cue
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

  /**
   * Expands direct artifacts through the persistent, rebuildable concept lattice. Edges are
   * explicitly derived and inform-only; callers still verify every returned Markdown artifact.
   */
  fun associativeConceptCandidates(
    seedArtifactIds: List<String>,
    limit: Int,
  ): List<IndexedRecallCandidate> {
    val seeds = seedArtifactIds.distinct().take(MAX_ASSOCIATIVE_SEEDS)
    if (seeds.isEmpty()) return emptyList()
    val safeLimit = limit.coerceIn(1, MAX_ASSOCIATIVE_CANDIDATES)
    val placeholders = seeds.joinToString(",") { "?" }
    return queryRecallCandidates(
      sql =
        """
        WITH seed_concepts AS (
          SELECT concept_id, MAX(weight) AS seed_weight
          FROM artifact_concepts
          WHERE artifact_id IN ($placeholders)
          GROUP BY concept_id
        ),
        neighbor_concepts AS (
          SELECT
            CASE
              WHEN ce.source_concept_id = sc.concept_id THEN ce.target_concept_id
              ELSE ce.source_concept_id
            END AS concept_id,
            SUM(ce.strength * sc.seed_weight) AS graph_score
          FROM seed_concepts sc
          JOIN concept_edges ce
            ON ce.source_concept_id = sc.concept_id OR ce.target_concept_id = sc.concept_id
          GROUP BY
            CASE
              WHEN ce.source_concept_id = sc.concept_id THEN ce.target_concept_id
              ELSE ce.source_concept_id
            END
        )
        SELECT a.artifact_id, a.exchange_id, e.session_id, a.source_kind,
               e.captured_at_ms, a.content_sha256, a.markdown_location, a.document_sha256,
               a.recall_terms, a.durable_personal_score, a.statement_kind,
               a.temporal_status, a.modality, a.correction_cue
        FROM neighbor_concepts nc
        JOIN artifact_concepts ac ON ac.concept_id = nc.concept_id
        JOIN artifacts a ON a.artifact_id = ac.artifact_id
        JOIN exchanges e ON e.exchange_id = a.exchange_id
        WHERE a.artifact_id NOT IN ($placeholders)
          AND a.verified = 1 AND e.verified = 1
          AND a.retrieval_eligible = 1 AND a.memory_state = 'active'
        GROUP BY a.artifact_id
        ORDER BY SUM(nc.graph_score * ac.weight) DESC, e.captured_at_ms DESC
        LIMIT ?
        """.trimIndent(),
      args = (seeds + seeds + safeLimit.toString()).toTypedArray(),
    )
  }

  fun typedRelationsForArtifacts(artifactIds: List<String>): List<CortexArtifactRelation> {
    val ids = artifactIds.distinct().take(MAX_RECALL_CANDIDATES)
    if (ids.isEmpty()) return emptyList()
    val placeholders = ids.joinToString(",") { "?" }
    return readableDatabase
      .rawQuery(
        """
        SELECT source_artifact_id, target_artifact_id, relation_type, strength, confidence,
               evidence_basis, confirmation_status, authority_ceiling
        FROM artifact_relations
        WHERE source_artifact_id IN ($placeholders) AND target_artifact_id IN ($placeholders)
        ORDER BY strength DESC, updated_at_ms DESC
        LIMIT $MAX_TYPED_RELATIONS_PER_FIELD
        """.trimIndent(),
        (ids + ids).toTypedArray(),
      )
      .use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(
              CortexArtifactRelation(
                sourceArtifactId = cursor.getString(0),
                targetArtifactId = cursor.getString(1),
                relationType = cursor.getString(2),
                strength = cursor.getDouble(3),
                confidence = cursor.getDouble(4),
                evidenceBasis = cursor.getString(5),
                confirmationStatus = cursor.getString(6),
                authorityCeiling = cursor.getString(7),
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

  private fun createArtifactSearchTable(db: SQLiteDatabase) {
    db.execSQL(
      "CREATE VIRTUAL TABLE IF NOT EXISTS artifact_search USING fts4(artifact_id, recall_terms)"
    )
  }

  private fun createCognitiveTables(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS concepts (
        concept_id TEXT PRIMARY KEY,
        label TEXT NOT NULL,
        normalized_label TEXT NOT NULL UNIQUE,
        concept_type TEXT NOT NULL,
        origin_class TEXT NOT NULL DEFAULT 'derived_index',
        origin_trust TEXT NOT NULL DEFAULT 'derived',
        authority_ceiling TEXT NOT NULL DEFAULT 'inform_only',
        privacy_scope TEXT NOT NULL DEFAULT 'general',
        disclosure_policy TEXT NOT NULL DEFAULT 'safe_with_user',
        created_at_ms INTEGER NOT NULL,
        updated_at_ms INTEGER NOT NULL
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS artifact_concepts (
        artifact_id TEXT NOT NULL REFERENCES artifacts(artifact_id) ON DELETE CASCADE,
        concept_id TEXT NOT NULL REFERENCES concepts(concept_id) ON DELETE CASCADE,
        weight REAL NOT NULL,
        provenance TEXT NOT NULL DEFAULT 'DERIVED_LEXICAL',
        confirmation_status TEXT NOT NULL DEFAULT 'UNCONFIRMED',
        PRIMARY KEY (artifact_id, concept_id)
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS concept_edges (
        source_concept_id TEXT NOT NULL REFERENCES concepts(concept_id) ON DELETE CASCADE,
        target_concept_id TEXT NOT NULL REFERENCES concepts(concept_id) ON DELETE CASCADE,
        relation_type TEXT NOT NULL DEFAULT 'co_occurs_with',
        strength REAL NOT NULL,
        confidence REAL NOT NULL,
        support_count INTEGER NOT NULL,
        source_kind TEXT NOT NULL DEFAULT 'MODEL_INFERRED',
        confirmation_status TEXT NOT NULL DEFAULT 'UNCONFIRMED',
        authority_ceiling TEXT NOT NULL DEFAULT 'inform_only',
        updated_at_ms INTEGER NOT NULL,
        PRIMARY KEY (source_concept_id, target_concept_id, relation_type)
      )
      """.trimIndent()
    )
    db.execSQL(
      "CREATE INDEX IF NOT EXISTS idx_artifact_concepts_concept ON artifact_concepts(concept_id)"
    )
    db.execSQL(
      "CREATE INDEX IF NOT EXISTS idx_concept_edges_target ON concept_edges(target_concept_id)"
    )
  }

  private fun createNormalizationTables(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS normalization_receipts (
        artifact_id TEXT NOT NULL REFERENCES artifacts(artifact_id) ON DELETE CASCADE,
        normalizer_version INTEGER NOT NULL,
        source_content_sha256 TEXT NOT NULL,
        projection_sha256 TEXT NOT NULL,
        sidecar_location TEXT NOT NULL,
        sidecar_document_sha256 TEXT NOT NULL,
        normalized_at_ms INTEGER NOT NULL,
        source_was_modified INTEGER NOT NULL DEFAULT 0 CHECK (source_was_modified IN (0, 1)),
        verified INTEGER NOT NULL CHECK (verified IN (0, 1)),
        PRIMARY KEY (artifact_id, normalizer_version)
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS artifact_relations (
        relation_id TEXT PRIMARY KEY,
        source_artifact_id TEXT NOT NULL REFERENCES artifacts(artifact_id) ON DELETE CASCADE,
        target_artifact_id TEXT NOT NULL REFERENCES artifacts(artifact_id) ON DELETE CASCADE,
        relation_type TEXT NOT NULL,
        strength REAL NOT NULL,
        confidence REAL NOT NULL,
        evidence_basis TEXT NOT NULL,
        source_kind TEXT NOT NULL DEFAULT 'MODEL_INFERRED',
        confirmation_status TEXT NOT NULL DEFAULT 'UNCONFIRMED',
        independence_state TEXT NOT NULL DEFAULT 'unknown',
        authority_ceiling TEXT NOT NULL DEFAULT 'inform_only',
        created_at_ms INTEGER NOT NULL,
        updated_at_ms INTEGER NOT NULL,
        UNIQUE (source_artifact_id, target_artifact_id, relation_type)
      )
      """.trimIndent()
    )
    db.execSQL(
      "CREATE INDEX IF NOT EXISTS idx_artifact_relations_source " +
        "ON artifact_relations(source_artifact_id)"
    )
    db.execSQL(
      "CREATE INDEX IF NOT EXISTS idx_artifact_relations_target " +
        "ON artifact_relations(target_artifact_id)"
    )
  }

  private fun recordNormalizationReceipt(
    db: SQLiteDatabase,
    artifactId: String,
    sourceContentHash: String,
    normalization: CortexNormalizationMetadata,
    sidecar: StoredVaultDocument,
    normalizedAtEpochMs: Long,
  ) {
    db.insertWithOnConflict(
      "normalization_receipts",
      null,
      ContentValues().apply {
        put("artifact_id", artifactId)
        put("normalizer_version", normalization.version)
        put("source_content_sha256", sourceContentHash)
        put("projection_sha256", normalization.projectionHash)
        put("sidecar_location", sidecar.location)
        put("sidecar_document_sha256", sidecar.documentSha256)
        put("normalized_at_ms", normalizedAtEpochMs)
        put("source_was_modified", 0)
        put("verified", 1)
      },
      SQLiteDatabase.CONFLICT_REPLACE,
    )
  }

  /**
   * Builds provisional evidence-backed routes. A POSSIBLY_CORRECTS edge never changes either
   * artifact's active state; adjudication is deliberately a separate future operation.
   */
  private fun indexTypedRelations(
    db: SQLiteDatabase,
    artifactId: String,
    capturedAtEpochMs: Long,
  ) {
    data class Neighbor(
      val artifactId: String,
      val sourceKind: CortexSourceKind,
      val exchangeId: String,
      val sessionId: String,
      val capturedAt: Long,
      val correctionCue: Boolean,
      val overlap: Int,
      val concepts: String,
    )

    val current =
      db.rawQuery(
          """
          SELECT a.source_kind, a.exchange_id, e.session_id, a.correction_cue
          FROM artifacts a JOIN exchanges e ON e.exchange_id = a.exchange_id
          WHERE a.artifact_id = ? AND a.verified = 1
          """.trimIndent(),
          arrayOf(artifactId),
        )
        .use { cursor ->
          require(cursor.moveToFirst()) { "Typed-relation source artifact is missing." }
          arrayOf(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3))
        }
    val currentSource = CortexSourceKind.valueOf(current[0])
    val currentExchange = current[1]
    val currentSession = current[2]
    val currentCorrection = current[3] == "1"
    val neighbors =
      db.rawQuery(
          """
          SELECT a.artifact_id, a.source_kind, a.exchange_id, e.session_id, e.captured_at_ms,
                 a.correction_cue, COUNT(*) AS overlap_count,
                 GROUP_CONCAT(c.normalized_label, ',') AS shared_concepts
          FROM artifact_concepts mine
          JOIN artifact_concepts other ON other.concept_id = mine.concept_id
          JOIN concepts c ON c.concept_id = mine.concept_id
          JOIN artifacts a ON a.artifact_id = other.artifact_id
          JOIN exchanges e ON e.exchange_id = a.exchange_id
          WHERE mine.artifact_id = ? AND other.artifact_id != ?
            AND a.verified = 1 AND a.memory_state = 'active'
          GROUP BY a.artifact_id
          ORDER BY overlap_count DESC, e.captured_at_ms DESC
          LIMIT $MAX_TYPED_RELATION_NEIGHBORS
          """.trimIndent(),
          arrayOf(artifactId, artifactId),
        )
        .use { cursor ->
          buildList {
            while (cursor.moveToNext()) {
              add(
                Neighbor(
                  artifactId = cursor.getString(0),
                  sourceKind = CortexSourceKind.valueOf(cursor.getString(1)),
                  exchangeId = cursor.getString(2),
                  sessionId = cursor.getString(3),
                  capturedAt = cursor.getLong(4),
                  correctionCue = cursor.getInt(5) == 1,
                  overlap = cursor.getInt(6),
                  concepts = cursor.getString(7).orEmpty().split(',').distinct().take(5).joinToString(", "),
                )
              )
            }
          }
        }

    db.delete(
      "artifact_relations",
      "source_artifact_id = ? OR target_artifact_id = ?",
      arrayOf(artifactId, artifactId),
    )
    neighbors.forEach { neighbor ->
      val sameExchange = currentExchange == neighbor.exchangeId
      val sameSession = currentSession == neighbor.sessionId
      val currentCanCorrect =
        currentCorrection &&
          currentSource == CortexSourceKind.USER_STATED &&
          neighbor.sourceKind == CortexSourceKind.USER_STATED &&
          capturedAtEpochMs >= neighbor.capturedAt &&
          neighbor.overlap >= MIN_CORRECTION_CONCEPT_OVERLAP
      val neighborCanCorrect =
        neighbor.correctionCue &&
          neighbor.sourceKind == CortexSourceKind.USER_STATED &&
          currentSource == CortexSourceKind.USER_STATED &&
          neighbor.capturedAt >= capturedAtEpochMs &&
          neighbor.overlap >= MIN_CORRECTION_CONCEPT_OVERLAP
      val relation =
        when {
          currentCanCorrect ->
            RelationDraft(
              sourceId = artifactId,
              targetId = neighbor.artifactId,
              type = "POSSIBLY_CORRECTS",
              strength = 0.84,
              confidence = (0.48 + neighbor.overlap * 0.05).coerceAtMost(0.73),
              evidence =
                "newer USER_STATED artifact has an explicit correction cue; shared concepts: " +
                  neighbor.concepts,
              independence = "unknown",
            )
          neighborCanCorrect ->
            RelationDraft(
              sourceId = neighbor.artifactId,
              targetId = artifactId,
              type = "POSSIBLY_CORRECTS",
              strength = 0.84,
              confidence = (0.48 + neighbor.overlap * 0.05).coerceAtMost(0.73),
              evidence =
                "newer USER_STATED artifact has an explicit correction cue; shared concepts: " +
                  neighbor.concepts,
              independence = "unknown",
            )
          sameExchange -> {
            val assistantIsCurrent = currentSource == CortexSourceKind.OTHER_AGENT
            RelationDraft(
              sourceId = if (assistantIsCurrent) artifactId else neighbor.artifactId,
              targetId = if (assistantIsCurrent) neighbor.artifactId else artifactId,
              type = "SAME_EXCHANGE_CONTEXT",
              strength = 0.76,
              confidence = 1.0,
              evidence = "host-indexed artifacts share the exact exchange id",
              independence = "post_exposure_agreement",
            )
          }
          sameSession ->
            RelationDraft(
              sourceId = artifactId,
              targetId = neighbor.artifactId,
              type = "SAME_SESSION_CONTEXT",
              strength = 0.58,
              confidence = 1.0,
              evidence = "host-indexed artifacts share the exact session id",
              independence = "unknown",
            )
          neighbor.overlap >= MIN_SEMANTIC_CONCEPT_OVERLAP ->
            RelationDraft(
              sourceId = artifactId,
              targetId = neighbor.artifactId,
              type = "SEMANTIC_OVERLAP",
              strength = (0.34 + neighbor.overlap * 0.06).coerceAtMost(0.76),
              confidence = (0.38 + neighbor.overlap * 0.04).coerceAtMost(0.70),
              evidence = "derived shared concepts: ${neighbor.concepts}",
              independence = "unknown",
            )
          else -> null
        } ?: return@forEach
      val relationId =
        "relation_${CortexHashing.sha256("${relation.sourceId}|${relation.targetId}|${relation.type}").take(28)}"
      db.insertWithOnConflict(
        "artifact_relations",
        null,
        ContentValues().apply {
          put("relation_id", relationId)
          put("source_artifact_id", relation.sourceId)
          put("target_artifact_id", relation.targetId)
          put("relation_type", relation.type)
          put("strength", relation.strength)
          put("confidence", relation.confidence)
          put("evidence_basis", relation.evidence)
          put("source_kind", "MODEL_INFERRED")
          put("confirmation_status", "UNCONFIRMED")
          put("independence_state", relation.independence)
          put("authority_ceiling", "inform_only")
          put("created_at_ms", maxOf(capturedAtEpochMs, neighbor.capturedAt))
          put("updated_at_ms", maxOf(capturedAtEpochMs, neighbor.capturedAt))
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
  }

  private data class RelationDraft(
    val sourceId: String,
    val targetId: String,
    val type: String,
    val strength: Double,
    val confidence: Double,
    val evidence: String,
    val independence: String,
  )

  private fun indexCognitiveMetadata(
    db: SQLiteDatabase,
    artifactId: String,
    conceptTerms: List<String>,
    capturedAtEpochMs: Long,
  ) {
    val normalizedConcepts =
      conceptTerms
        .map { term ->
          term
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
            .trim('_', '-')
        }
        .filter { term -> term.length in 2..80 }
        .distinct()
        .take(MAX_CONCEPTS_PER_ARTIFACT)
    if (normalizedConcepts.isEmpty()) return

    db.delete("artifact_concepts", "artifact_id = ?", arrayOf(artifactId))
    val conceptIds =
      normalizedConcepts.mapIndexed { rank, normalized ->
        val conceptId = "concept_${CortexHashing.sha256(normalized).take(24)}"
        db.insertWithOnConflict(
          "concepts",
          null,
          ContentValues().apply {
            put("concept_id", conceptId)
            put("label", normalized.replace('_', ' '))
            put("normalized_label", normalized)
            put("concept_type", if ('_' in normalized) "phrase" else "lexical")
            put("origin_class", "derived_index")
            put("origin_trust", "derived")
            put("authority_ceiling", "inform_only")
            put("privacy_scope", "general")
            put("disclosure_policy", "safe_with_user")
            put("created_at_ms", capturedAtEpochMs)
            put("updated_at_ms", capturedAtEpochMs)
          },
          SQLiteDatabase.CONFLICT_IGNORE,
        )
        db.update(
          "concepts",
          ContentValues().apply { put("updated_at_ms", capturedAtEpochMs) },
          "concept_id = ?",
          arrayOf(conceptId),
        )
        db.insertWithOnConflict(
          "artifact_concepts",
          null,
          ContentValues().apply {
            put("artifact_id", artifactId)
            put("concept_id", conceptId)
            put("weight", (1.0 - rank * 0.035).coerceAtLeast(0.48))
            put("provenance", "DERIVED_LEXICAL")
            put("confirmation_status", "UNCONFIRMED")
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
        conceptId
      }

    conceptIds.sorted().take(MAX_EDGE_CONCEPTS_PER_ARTIFACT).forEachIndexed { leftIndex, left ->
      conceptIds
        .sorted()
        .take(MAX_EDGE_CONCEPTS_PER_ARTIFACT)
        .drop(leftIndex + 1)
        .forEach { right ->
          val support =
            db.rawQuery(
                """
                SELECT support_count FROM concept_edges
                WHERE source_concept_id = ? AND target_concept_id = ?
                  AND relation_type = 'co_occurs_with'
                """.trimIndent(),
                arrayOf(left, right),
              )
              .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
          val newSupport = support + 1
          val values =
            ContentValues().apply {
              put("source_concept_id", left)
              put("target_concept_id", right)
              put("relation_type", "co_occurs_with")
              put("strength", (0.22 + (newSupport - 1).coerceAtMost(7) * 0.08).coerceAtMost(0.78))
              put("confidence", (0.42 + (newSupport - 1).coerceAtMost(7) * 0.06).coerceAtMost(0.84))
              put("support_count", newSupport)
              put("source_kind", "MODEL_INFERRED")
              put("confirmation_status", "UNCONFIRMED")
              put("authority_ceiling", "inform_only")
              put("updated_at_ms", capturedAtEpochMs)
            }
          if (support == 0) {
            db.insertOrThrow("concept_edges", null, values)
          } else {
            db.update(
              "concept_edges",
              values,
              "source_concept_id = ? AND target_concept_id = ? AND relation_type = ?",
              arrayOf(left, right, "co_occurs_with"),
            )
          }
        }
    }
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
              recallTerms = cursor.getString(8),
              durablePersonalScore = cursor.getInt(9),
              statementKind = cursor.getString(10),
              temporalStatus = cursor.getString(11),
              modality = cursor.getString(12),
              correctionCue = cursor.getInt(13) == 1,
            )
          )
        }
      }
    }

  private companion object {
    const val DATABASE_NAME = "jarvis_alpha_cortex.db"
    const val DATABASE_VERSION = 6
    const val MAX_RECALL_CANDIDATES = 64
    const val MAX_INDEX_BACKFILL_BATCH = 256
    const val MAX_DURABLE_PERSONAL_CHARS = 8_000
    const val MAX_SYNTHESIS_DURABLE_SEEDS = 16
    const val MAX_SYNTHESIS_LEXICAL_SEEDS = 40
    const val MAX_SEEDS_PER_TERM = 8
    const val MAX_ASSOCIATIVE_SEEDS = 12
    const val MAX_ASSOCIATIVE_CANDIDATES = 16
    const val MAX_CONCEPTS_PER_ARTIFACT = 16
    const val MAX_EDGE_CONCEPTS_PER_ARTIFACT = 12
    const val MAX_TYPED_RELATION_NEIGHBORS = 24
    const val MAX_TYPED_RELATIONS_PER_FIELD = 96
    const val MIN_CORRECTION_CONCEPT_OVERLAP = 2
    const val MIN_SEMANTIC_CONCEPT_OVERLAP = 2
    val SAFE_SEARCH_TERM = Regex("^[\\p{L}\\p{N}_-]{2,64}$")
  }
}
