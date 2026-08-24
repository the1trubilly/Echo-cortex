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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisControlCenterBottomSheet(
  onDismiss: () -> Unit,
  onSettingsClick: () -> Unit,
  onSkillsClick: () -> Unit,
  onMcpClick: () -> Unit,
  onTerminalClick: () -> Unit,
  onModelsClick: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.jarvis_control_center_title),
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = stringResource(R.string.jarvis_control_center_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = {
            scope.launch {
              sheetState.hide()
              onDismiss()
            }
          }
        ) {
          Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_icon))
        }
      }

      ControlCenterItem(
        icon = Icons.Rounded.Settings,
        title = stringResource(R.string.drawer_settings_label),
        description = stringResource(R.string.jarvis_settings_description),
        onClick = onSettingsClick,
      )
      ControlCenterItem(
        icon = Icons.AutoMirrored.Outlined.ListAlt,
        title = stringResource(R.string.manage_skills),
        description = stringResource(R.string.jarvis_skills_description),
        onClick = onSkillsClick,
      )
      ControlCenterItem(
        icon = Icons.Outlined.VpnKey,
        title = stringResource(R.string.manage_mcp_servers),
        description = stringResource(R.string.jarvis_mcp_description),
        onClick = onMcpClick,
      )
      ControlCenterItem(
        icon = Icons.Outlined.Terminal,
        title = stringResource(R.string.termux_setup_title),
        description = stringResource(R.string.jarvis_terminal_description),
        onClick = onTerminalClick,
      )
      ControlCenterItem(
        icon = Icons.Outlined.Cloud,
        title = stringResource(R.string.model_manager),
        description = stringResource(R.string.drawer_models_description),
        onClick = onModelsClick,
      )
    }
  }
}

@Composable
private fun ControlCenterItem(
  icon: ImageVector,
  title: String,
  description: String,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .background(
          color = MaterialTheme.colorScheme.surfaceContainerLowest,
          shape = RoundedCornerShape(20.dp),
        )
        .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(26.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
