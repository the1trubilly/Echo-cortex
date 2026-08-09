/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.cortex.AlphaCortexRuntime
import kotlinx.coroutines.launch

/** Alpha-only Cortex and ThreadKeeper vault controls. */
@Composable
internal fun CortexSettingsSection() {
  val context = LocalContext.current
  val runtime = remember { AlphaCortexRuntime.get(context) }
  val status by runtime.status.collectAsState()
  val scope = rememberCoroutineScope()
  var importMessage by remember { mutableStateOf("") }
  var importInProgress by remember { mutableStateOf(false) }

  val directoryPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        runCatching {
          context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
          )
          runtime.setVaultTree(uri)
          importMessage = "Vault location saved. New Cortex files will be written there."
        }.onFailure { error ->
          importMessage = "Could not use that folder: ${error.message ?: "permission denied"}"
        }
      }
    }

  val importPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        importInProgress = true
        importMessage = "Inspecting a read-only schema-11 copy..."
        scope.launch {
          val result = runtime.importSchema11Copy(uri)
          importMessage = result.message
          importInProgress = false
        }
      }
    }

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "Cortex / ThreadKeeper vault (Alpha)",
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
    )
    Text(
      text =
        "Jarvis saves Billy's exact turn and his own completed reply as separate Markdown " +
          "memories. Before each new turn, Jarvis automatically retrieves a small, verified " +
          "memory packet from prior sessions. SQLite is only a rebuildable index.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = "Vault: ${status.vaultLabel}",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      text =
        "${status.verifiedExchanges} verified exchanges | " +
          "${status.verifiedArtifacts} turn files | ${status.verifiedRecalls} recalls | " +
          "${status.verifiedImports} imports",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { directoryPicker.launch(null) }) { Text("Choose vault folder") }
      if (status.usingSelectedFolder) {
        OutlinedButton(
          onClick = {
            runtime.usePrivateVault()
            importMessage = "Future memories will use the app-private Alpha vault."
          }
        ) {
          Text("Use private vault")
        }
      }
    }
    OutlinedButton(
      enabled = !importInProgress,
      onClick = { importPicker.launch(arrayOf("application/json", "text/plain")) },
    ) {
      Text(if (importInProgress) "Importing..." else "Import ThreadKeeper schema-11 copy")
    }
    if (importMessage.isNotBlank() || status.lastOperation != "Ready") {
      Text(
        text = importMessage.ifBlank { status.lastOperation },
        style = MaterialTheme.typography.bodySmall,
        color =
          if (status.healthy) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.error,
      )
    }
  }
}
