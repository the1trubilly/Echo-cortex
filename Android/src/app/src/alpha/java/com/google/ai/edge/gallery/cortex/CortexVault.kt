/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class StoredVaultDocument(
  val location: String,
  val documentSha256: String,
  val byteCount: Int,
)

/** Writes canonical files either to Alpha-private storage or a user-selected document tree. */
internal class CortexVault(private val context: Context) {
  private val preferences =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun configuredTreeUri(): Uri? =
    preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

  fun displayLabel(): String =
    configuredTreeUri()?.lastPathSegment?.substringAfterLast(':')?.ifBlank { null }
      ?: "App-private Jarvis Alpha vault"

  fun setTreeUri(uri: Uri) {
    preferences.edit().putString(KEY_TREE_URI, uri.toString()).apply()
  }

  fun usePrivateVault() {
    preferences.edit().remove(KEY_TREE_URI).apply()
  }

  fun writeVerified(
    category: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
  ): StoredVaultDocument {
    val safeCategory = sanitize(category)
    val safeFileName = sanitize(fileName)
    return configuredTreeUri()?.let { treeUri ->
      writeDocumentTree(treeUri, safeCategory, safeFileName, mimeType, bytes)
    } ?: writePrivate(safeCategory, safeFileName, bytes)
  }

  fun readVerified(location: String, expectedDocumentSha256: String): ByteArray {
    val bytes =
      if (location.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(location))?.use { stream ->
          stream.readBytes()
        } ?: error("The selected Cortex document is no longer readable.")
      } else {
        File(location).readBytes()
      }
    require(CortexHashing.sha256(bytes) == expectedDocumentSha256) {
      "Cortex document hash verification failed."
    }
    return bytes
  }

  private fun writePrivate(
    category: String,
    fileName: String,
    bytes: ByteArray,
  ): StoredVaultDocument {
    val directory = File(context.filesDir, "$PRIVATE_ROOT/$category").apply { mkdirs() }
    require(directory.isDirectory) { "Could not create Alpha vault directory." }
    val destination = File(directory, fileName)
    val temporary = File(directory, ".$fileName.${System.nanoTime()}.tmp")
    FileOutputStream(temporary).use { stream ->
      stream.write(bytes)
      stream.flush()
      stream.fd.sync()
    }
    require(temporary.readBytes().contentEquals(bytes)) {
      "Alpha vault temporary-file verification failed."
    }
    try {
      Files.move(
        temporary.toPath(),
        destination.toPath(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    } catch (_: AtomicMoveNotSupportedException) {
      Files.move(
        temporary.toPath(),
        destination.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
      )
    }
    val readBack = destination.readBytes()
    require(readBack.contentEquals(bytes)) { "Alpha vault readback verification failed." }
    return StoredVaultDocument(
      location = destination.absolutePath,
      documentSha256 = CortexHashing.sha256(readBack),
      byteCount = readBack.size,
    )
  }

  private fun writeDocumentTree(
    treeUri: Uri,
    category: String,
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
  ): StoredVaultDocument {
    val selectedRoot =
      DocumentFile.fromTreeUri(context, treeUri)
        ?: error("The selected Alpha vault is no longer available.")
    val alphaRoot =
      selectedRoot.findFile(EXTERNAL_ROOT)
        ?: selectedRoot.createDirectory(EXTERNAL_ROOT)
        ?: error("Could not create the Jarvis Alpha Cortex directory.")
    val directory =
      alphaRoot.findFile(category)
        ?: alphaRoot.createDirectory(category)
        ?: error("Could not create the $category vault directory.")
    val document =
      directory.createFile(mimeType, fileName)
        ?: error("Could not create $fileName in the selected Alpha vault.")
    context.contentResolver.openOutputStream(document.uri, "wt")?.use { stream ->
      stream.write(bytes)
      stream.flush()
    } ?: error("Could not write $fileName in the selected Alpha vault.")
    val readBack =
      context.contentResolver.openInputStream(document.uri)?.use { stream -> stream.readBytes() }
        ?: error("Could not verify $fileName in the selected Alpha vault.")
    if (!readBack.contentEquals(bytes)) {
      document.delete()
      error("Selected-vault readback verification failed for $fileName.")
    }
    return StoredVaultDocument(
      location = document.uri.toString(),
      documentSha256 = CortexHashing.sha256(readBack),
      byteCount = readBack.size,
    )
  }

  private fun sanitize(value: String): String {
    val sanitized = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
    require(sanitized.isNotBlank() && sanitized != "." && sanitized != "..") {
      "Invalid Alpha vault path component."
    }
    return sanitized
  }

  private companion object {
    const val PREFERENCES_NAME = "jarvis_alpha_cortex"
    const val KEY_TREE_URI = "vault_tree_uri"
    const val PRIVATE_ROOT = "cortex-vault"
    const val EXTERNAL_ROOT = "Jarvis Alpha Cortex"
  }
}
