/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.customtasks.agentchat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.tools.AndroidSelfAdbConnectionProvider
import com.google.ai.edge.gallery.tools.AndroidTermuxCommandRunner
import com.google.ai.edge.gallery.tools.AndroidTermuxEnvironment
import com.google.ai.edge.gallery.tools.AndroidTerminalApprovalModeStore
import com.google.ai.edge.gallery.tools.TerminalApprovalMode
import com.google.ai.edge.gallery.tools.TermuxRunCommandContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxSetupBottomSheet(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val environment = remember(context) { AndroidTermuxEnvironment(context) }
  val runner = remember(context) { AndroidTermuxCommandRunner(context) }
  val selfAdbConnectionProvider =
    remember(context) { AndroidSelfAdbConnectionProvider(context, runner) }
  val approvalModeStore = remember(context) { AndroidTerminalApprovalModeStore(context) }
  var refreshKey by remember { mutableIntStateOf(0) }
  val status = remember(refreshKey) { environment.status() }
  var copied by remember { mutableStateOf(false) }
  var testing by remember { mutableStateOf(false) }
  var testResult by remember { mutableStateOf("") }
  var pairingSelfAdb by remember { mutableStateOf(false) }
  var selfAdbResult by remember { mutableStateOf("") }
  var approvalMode by remember { mutableStateOf(approvalModeStore.getMode()) }
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      refreshKey += 1
    }

  fun beginSelfAdbPairing() {
    pairingSelfAdb = true
    selfAdbResult = ""
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          try {
            selfAdbConnectionProvider.getOrPair()
          } catch (error: Exception) {
            null
          }
        }
      selfAdbResult =
        result?.message ?: context.getString(R.string.self_adb_pairing_failed_to_start)
      pairingSelfAdb = false
    }
  }

  val selfAdbPermissionsLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      results ->
      if (results.values.all { granted -> granted }) {
        beginSelfAdbPairing()
      } else {
        selfAdbResult = context.getString(R.string.self_adb_permissions_required)
      }
    }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.termux_setup_title),
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = stringResource(R.string.termux_setup_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_icon))
        }
      }

      SetupStatusRow(
        label = stringResource(R.string.termux_app_status),
        value =
          if (status.installed) {
            stringResource(R.string.termux_installed_version, status.versionName)
          } else {
            stringResource(R.string.termux_not_installed)
          },
      )
      SetupStatusRow(
        label = stringResource(R.string.termux_permission_status),
        value =
          if (status.runCommandPermissionGranted) {
            stringResource(R.string.termux_permission_granted)
          } else {
            stringResource(R.string.termux_permission_not_granted)
          },
      )

      if (!status.runCommandPermissionGranted) {
        Button(
          onClick = {
            permissionLauncher.launch(TermuxRunCommandContract.RUN_COMMAND_PERMISSION)
          },
          enabled = status.installed,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(stringResource(R.string.termux_grant_permission))
        }
      }

      HorizontalDivider()
      Text(
        text = stringResource(R.string.termux_approval_mode_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      ApprovalModeOption(
        selected = approvalMode == TerminalApprovalMode.EVERY_COMMAND,
        title = stringResource(R.string.termux_approval_every_command),
        description = stringResource(R.string.termux_approval_every_command_description),
        onClick = {
          approvalMode = TerminalApprovalMode.EVERY_COMMAND
          approvalModeStore.setMode(approvalMode)
        },
      )
      ApprovalModeOption(
        selected = approvalMode == TerminalApprovalMode.DANGEROUS_COMMANDS_ONLY,
        title = stringResource(R.string.termux_approval_dangerous_only),
        description = stringResource(R.string.termux_approval_dangerous_only_description),
        onClick = {
          approvalMode = TerminalApprovalMode.DANGEROUS_COMMANDS_ONLY
          approvalModeStore.setMode(approvalMode)
        },
      )

      HorizontalDivider()
      Text(
        text = stringResource(R.string.termux_setup_step_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = stringResource(R.string.termux_setup_step_description),
        style = MaterialTheme.typography.bodyMedium,
      )
      OutlinedButton(
        onClick = {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          clipboard.setPrimaryClip(ClipData.newPlainText("Android Jarvis Termux setup", SETUP_COMMAND))
          copied = true
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          if (copied) stringResource(R.string.termux_setup_copied)
          else stringResource(R.string.termux_copy_setup_command)
        )
      }
      Button(
        onClick = {
          context.packageManager
            .getLaunchIntentForPackage(TermuxRunCommandContract.PACKAGE_NAME)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
        },
        enabled = status.installed,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.termux_open_app))
      }

      Text(
        text = stringResource(R.string.termux_security_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = stringResource(R.string.termux_adb_pairing_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      HorizontalDivider()
      Text(
        text = stringResource(R.string.self_adb_setup_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = stringResource(R.string.self_adb_setup_description),
        style = MaterialTheme.typography.bodyMedium,
      )
      Button(
        onClick = {
          val missingPermissions =
            buildList {
              if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                  ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                  ) != PackageManager.PERMISSION_GRANTED
              ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
              }
              if (
                Build.VERSION.SDK_INT >= 37 &&
                  ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_LOCAL_NETWORK,
                  ) != PackageManager.PERMISSION_GRANTED
              ) {
                add(Manifest.permission.ACCESS_LOCAL_NETWORK)
              }
            }
          if (missingPermissions.isEmpty()) {
            beginSelfAdbPairing()
          } else {
            selfAdbPermissionsLauncher.launch(missingPermissions.toTypedArray())
          }
        },
        enabled =
          status.installed && status.runCommandPermissionGranted && !pairingSelfAdb && !testing,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          if (pairingSelfAdb) stringResource(R.string.self_adb_pairing_waiting)
          else stringResource(R.string.self_adb_connect_button)
        )
      }
      if (selfAdbResult.isNotBlank()) {
        Text(
          text = selfAdbResult,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      OutlinedButton(
        onClick = {
          testing = true
          testResult = ""
          scope.launch {
            val result =
              withContext(Dispatchers.IO) {
                try {
                  runner.run(TEST_COMMAND, timeoutMs = 15_000L)
                } catch (_: Exception) {
                  null
                }
              }
            testResult =
              when {
                result == null -> context.getString(R.string.termux_test_failed_to_start)
                result.succeeded -> result.stdout.trim().ifBlank { "Terminal connected." }
                else ->
                  listOf(result.stderr, result.internalErrorMessage)
                    .filter(String::isNotBlank)
                    .joinToString("\n")
                    .ifBlank { context.getString(R.string.termux_test_failed) }
              }
            testing = false
          }
        },
        enabled = status.installed && status.runCommandPermissionGranted && !testing,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          if (testing) stringResource(R.string.termux_testing)
          else stringResource(R.string.termux_test_connection)
        )
      }
      if (testResult.isNotBlank()) {
        Text(
          text = testResult,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
        )
      }
    }
  }
}

@Composable
private fun ApprovalModeOption(
  selected: Boolean,
  title: String,
  description: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    RadioButton(selected = selected, onClick = null)
    Column(modifier = Modifier.padding(start = 8.dp)) {
      Text(text = title, style = MaterialTheme.typography.labelLarge)
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun SetupStatusRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label, style = MaterialTheme.typography.labelLarge)
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.primary,
    )
  }
}

private const val SETUP_COMMAND =
  "mkdir -p ~/.termux; touch ~/.termux/termux.properties; " +
    "if grep -q '^allow-external-apps=' ~/.termux/termux.properties; then " +
    "sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' " +
    "~/.termux/termux.properties; else printf '\\nallow-external-apps=true\\n' >> " +
    "~/.termux/termux.properties; fi; pkg install tmux android-tools"

private const val TEST_COMMAND =
  "printf 'JARVIS_TERMUX_OK\\n'; printf 'TMUX_SESSION=%s\\n' \"\$(tmux display-message -p '#S')\"; " +
    "if command -v adb >/dev/null 2>&1; then " +
    "printf 'ADB_INSTALLED\\n'; adb version | head -n 1; else printf 'ADB_NOT_INSTALLED\\n'; fi"
