/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import android.content.Context
import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AlphaCortexStatus(
  val vaultLabel: String,
  val usingSelectedFolder: Boolean,
  val verifiedExchanges: Int,
  val verifiedArtifacts: Int,
  val verifiedImports: Int,
  val lastOperation: String,
  val healthy: Boolean,
)

data class Schema11ImportResult(
  val verified: Boolean,
  val alreadyImported: Boolean,
  val message: String,
)

/** Native Kotlin Cortex implementation compiled only into Jarvis Alpha. */
class AlphaCortexRuntime private constructor(context: Context) : CortexRuntime {
  private val appContext = context.applicationContext ?: context
  private val vault = CortexVault(appContext)
  private val index = CortexIndexDatabase(appContext)
  private val mutationMutex = Mutex()
  private val _status = MutableStateFlow(readStatus("Ready", healthy = true))
  val status: StateFlow<AlphaCortexStatus> = _status.asStateFlow()

  override suspend fun captureExchange(
    request: CortexExchangeCaptureRequest
  ): CortexCaptureReceipt =
    withContext(Dispatchers.IO) {
      mutationMutex.withLock {
        val exchangeId = UUID.randomUUID().toString()
        try {
          require(request.userMessage.isNotEmpty()) { "Billy's completed turn is empty." }
          require(request.assistantResponse.isNotEmpty()) { "Jarvis's completed reply is empty." }

          val userArtifactId = UUID.randomUUID().toString()
          val assistantArtifactId = UUID.randomUUID().toString()
          val userContentHash = CortexHashing.sha256(request.userMessage)
          val assistantContentHash = CortexHashing.sha256(request.assistantResponse)
          val timestamp = request.completedAtEpochMs

          val userBytes =
            CortexMarkdownCodec.encodeTurn(
              artifactId = userArtifactId,
              exchangeId = exchangeId,
              sessionId = request.sessionId,
              taskId = request.taskId,
              modelName = request.modelName,
              sourceKind = CortexSourceKind.USER_STATED,
              capturedAtEpochMs = timestamp,
              content = request.userMessage,
            )
          require(
            CortexHashing.sha256(CortexMarkdownCodec.decodeExactContent(userBytes)) ==
              userContentHash
          ) {
            "Billy artifact exact-content verification failed before write."
          }
          val userDocument =
            vault.writeVerified(
              category = "turns",
              fileName = "${timestamp}_${userArtifactId}_billy.md",
              mimeType = "text/markdown",
              bytes = userBytes,
            )

          val assistantBytes =
            CortexMarkdownCodec.encodeTurn(
              artifactId = assistantArtifactId,
              exchangeId = exchangeId,
              sessionId = request.sessionId,
              taskId = request.taskId,
              modelName = request.modelName,
              sourceKind = CortexSourceKind.OTHER_AGENT,
              capturedAtEpochMs = timestamp,
              content = request.assistantResponse,
            )
          require(
            CortexHashing.sha256(CortexMarkdownCodec.decodeExactContent(assistantBytes)) ==
              assistantContentHash
          ) {
            "Jarvis artifact exact-content verification failed before write."
          }
          val assistantDocument =
            vault.writeVerified(
              category = "turns",
              fileName = "${timestamp}_${assistantArtifactId}_jarvis.md",
              mimeType = "text/markdown",
              bytes = assistantBytes,
            )

          val receiptBytes =
            CortexMarkdownCodec.encodeExchangeReceipt(
              exchangeId = exchangeId,
              sessionId = request.sessionId,
              modelName = request.modelName,
              capturedAtEpochMs = timestamp,
              userArtifactId = userArtifactId,
              userLocation = userDocument.location,
              userContentHash = userContentHash,
              assistantArtifactId = assistantArtifactId,
              assistantLocation = assistantDocument.location,
              assistantContentHash = assistantContentHash,
            )
          val receiptDocument =
            vault.writeVerified(
              category = "receipts",
              fileName = "${timestamp}_${exchangeId}_exchange.md",
              mimeType = "text/markdown",
              bytes = receiptBytes,
            )

          index.recordExchange(
            request = request,
            exchangeId = exchangeId,
            receipt = receiptDocument,
            artifacts =
              listOf(
                IndexedArtifact(
                  artifactId = userArtifactId,
                  sourceKind = CortexSourceKind.USER_STATED,
                  contentHash = userContentHash,
                  contentBytes = request.userMessage.toByteArray(Charsets.UTF_8).size,
                  markdownLocation = userDocument.location,
                  documentHash = userDocument.documentSha256,
                ),
                IndexedArtifact(
                  artifactId = assistantArtifactId,
                  sourceKind = CortexSourceKind.OTHER_AGENT,
                  contentHash = assistantContentHash,
                  contentBytes = request.assistantResponse.toByteArray(Charsets.UTF_8).size,
                  markdownLocation = assistantDocument.location,
                  documentHash = assistantDocument.documentSha256,
                ),
              ),
          )
          _status.value = readStatus("Last exchange verified", healthy = true)
          CortexCaptureReceipt(
            exchangeId = exchangeId,
            verified = true,
            message = "Billy and Jarvis artifacts verified; receipt committed.",
          )
        } catch (error: Exception) {
          _status.value =
            readStatus(
              "Capture failed: ${error.message ?: error.javaClass.simpleName}",
              healthy = false,
            )
          CortexCaptureReceipt(
            exchangeId = exchangeId,
            verified = false,
            message = error.message ?: "Cortex capture failed.",
          )
        }
      }
    }

  fun setVaultTree(uri: Uri) {
    vault.setTreeUri(uri)
    _status.value = readStatus("Selected vault folder saved", healthy = true)
  }

  fun usePrivateVault() {
    vault.usePrivateVault()
    _status.value = readStatus("Using app-private Alpha vault", healthy = true)
  }

  suspend fun importSchema11Copy(sourceUri: Uri): Schema11ImportResult =
    withContext(Dispatchers.IO) {
      mutationMutex.withLock {
        try {
          val sourceBytes =
            appContext.contentResolver.openInputStream(sourceUri)?.use { stream ->
              stream.readBytes()
            } ?: error("Could not read the selected ThreadKeeper copy.")
          val sourceHash = CortexHashing.sha256(sourceBytes)
          if (index.hasImport(sourceHash)) {
            _status.value = readStatus("Schema-11 copy was already imported", healthy = true)
            return@withLock Schema11ImportResult(
              verified = true,
              alreadyImported = true,
              message = "This exact ThreadKeeper copy was already imported.",
            )
          }

          val inspection = Schema11Importer.inspect(sourceBytes)
          val shortHash = sourceHash.take(16)
          val importedAt = System.currentTimeMillis()
          val archive =
            vault.writeVerified(
              category = "imports",
              fileName = "threadkeeper_schema11_${shortHash}.json",
              mimeType = "application/json",
              bytes = sourceBytes,
            )
          val indexedCollections =
            inspection.collections.mapIndexed { indexNumber, collection ->
              val collectionMarkdown =
                CortexMarkdownCodec.encodeImportCollection(
                  sourceHash = sourceHash,
                  collectionName = collection.name,
                  entryCount = collection.entryCount,
                  canonicalJson = collection.element.toString(),
                )
              val stored =
                vault.writeVerified(
                  category = "imports",
                  fileName =
                    "threadkeeper_${shortHash}_${indexNumber.toString().padStart(2, '0')}_" +
                      "${sanitizeCollectionName(collection.name)}.md",
                  mimeType = "text/markdown",
                  bytes = collectionMarkdown,
                )
              IndexedImportCollection(
                name = collection.name,
                entryCount = collection.entryCount,
                markdownLocation = stored.location,
                documentHash = stored.documentSha256,
              )
            }
          val receiptBytes =
            CortexMarkdownCodec.encodeImportReceipt(
              sourceHash = sourceHash,
              archiveLocation = archive.location,
              collectionCount = indexedCollections.size,
              entryCount = inspection.totalEntries,
              importedAtEpochMs = importedAt,
            )
          val receipt =
            vault.writeVerified(
              category = "receipts",
              fileName = "${importedAt}_${shortHash}_schema11_import.md",
              mimeType = "text/markdown",
              bytes = receiptBytes,
            )
          index.recordImport(
            sourceHash = sourceHash,
            importedAtEpochMs = importedAt,
            archive = archive,
            receipt = receipt,
            collections = indexedCollections,
          )
          _status.value = readStatus("Schema-11 import verified", healthy = true)
          Schema11ImportResult(
            verified = true,
            alreadyImported = false,
            message =
              "Imported ${indexedCollections.size} collections and " +
                "${inspection.totalEntries} entries from a verified copy.",
          )
        } catch (error: Exception) {
          val message = error.message ?: "ThreadKeeper schema-11 import failed."
          _status.value = readStatus("Import failed: $message", healthy = false)
          Schema11ImportResult(
            verified = false,
            alreadyImported = false,
            message = message,
          )
        }
      }
    }

  private fun readStatus(lastOperation: String, healthy: Boolean): AlphaCortexStatus {
    val counts = index.counts()
    return AlphaCortexStatus(
      vaultLabel = vault.displayLabel(),
      usingSelectedFolder = vault.configuredTreeUri() != null,
      verifiedExchanges = counts.exchanges,
      verifiedArtifacts = counts.artifacts,
      verifiedImports = counts.imports,
      lastOperation = lastOperation,
      healthy = healthy,
    )
  }

  private fun sanitizeCollectionName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "collection" }

  companion object {
    @Volatile private var instance: AlphaCortexRuntime? = null

    fun get(context: Context): AlphaCortexRuntime =
      instance
        ?: synchronized(this) {
          instance ?: AlphaCortexRuntime(context).also { created -> instance = created }
        }
  }
}
