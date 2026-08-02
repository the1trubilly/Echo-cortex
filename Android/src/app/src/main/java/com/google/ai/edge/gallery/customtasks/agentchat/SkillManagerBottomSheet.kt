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

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.GalleryEvent
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.common.clearFocusOnKeyboardDismiss
import com.google.ai.edge.gallery.data.AgentSkillsURLs
import com.google.ai.edge.gallery.data.MAX_RECOMMENDED_SKILL_COUNT
import com.google.ai.edge.gallery.firebaseAnalytics
import com.google.ai.edge.gallery.proto.Skill
import com.google.ai.edge.gallery.tools.getSkillSecretKey
import com.google.ai.edge.gallery.ui.common.FloatingBanner
import com.google.ai.edge.gallery.ui.common.BaseGalleryWebViewClient
import com.google.ai.edge.gallery.ui.common.GalleryWebView
import com.google.ai.edge.gallery.ui.theme.customColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AddSkillOptionType {
  FeaturedList,
  RemoteUrl,
  LocalImport,
  ManualInput,
  ViewCommunitySkills,
}

private data class AddSkillOption(
  val type: AddSkillOptionType,
  @StringRes val titleResId: Int,
  @StringRes val descriptionResId: Int,
  val icon: ImageVector,
)

private val ADD_SKILL_OPTIONS =
  listOf(
    AddSkillOption(
      type = AddSkillOptionType.RemoteUrl,
      titleResId = R.string.add_skill_option_url_title,
      descriptionResId = R.string.add_skill_option_url_description,
      icon = Icons.Rounded.Link,
    ),
    AddSkillOption(
      type = AddSkillOptionType.LocalImport,
      titleResId = R.string.add_skill_option_local_title,
      descriptionResId = R.string.add_skill_option_local_description,
      icon = Icons.Outlined.DriveFolderUpload,
    ),
    AddSkillOption(
      type = AddSkillOptionType.ViewCommunitySkills,
      titleResId = R.string.add_skill_option_view_community_skills_title,
      descriptionResId = R.string.add_skill_option_view_community_skills_description,
      icon = Icons.AutoMirrored.Outlined.OpenInNew,
    ),
  )

val BUTTON_CONTENT_PADDING = PaddingValues(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 2.dp)

private const val TAG = "AGSkillManagerBottomSheet"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SkillManagerBottomSheet(
  agentTools: AgentTools,
  skillManagerViewModel: SkillManagerViewModel,
  onDismiss: (selectedSkillsChanged: Boolean) -> Unit,
) {
  val uiState by skillManagerViewModel.uiState.collectAsState()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  var showAddSkillFromUrlDialog by remember { mutableStateOf(false) }
  var showAddSkillFromLocalImportDialog by remember { mutableStateOf(false) }
  var showAddSkillFromFeaturedListBottomSheet by remember { mutableStateOf(false) }
  var showCommunitySkillsBottomSheet by remember { mutableStateOf(false) }
  var showAddOrEditSkillBottomSheet by remember { mutableStateOf(false) }
  var showAddSkillOptionsSheet by remember { mutableStateOf(false) }
  var showDeleteSkillDialog by remember { mutableStateOf(false) }
  var showJsSkillTesterBottomSheet by remember { mutableStateOf(false) }
  var showSecretEditorDialog by remember { mutableStateOf(false) }
  var showDisclaimerDialog by remember { mutableStateOf(false) }
  var skillToDeleteName by remember { mutableStateOf("") }
  var skillToTest by remember { mutableStateOf<Skill?>(null) }
  var addSkillOptionTypeToConfirm by remember { mutableStateOf<AddSkillOptionType?>(null) }
  var skillToEditIndex by remember { mutableIntStateOf(-1) }
  var searchQuery by remember { mutableStateOf("") }
  var savedSelectedSkillsNamesAndDescriptions by remember { mutableStateOf("") }
  var filteredSkills by remember { mutableStateOf(uiState.skills) }
  val listState = rememberLazyListState()
  val uriHandler = LocalUriHandler.current

  // Additional states for multi-select and section collapsing
  var hasDeterminedExpansionStates by remember { mutableStateOf(false) }
  var isBuiltInExpanded by remember { mutableStateOf(false) }
  var isCustomExpanded by remember { mutableStateOf(true) }
  var inMultiSelectMode by remember { mutableStateOf(false) }
  val selectedCustomSkillNames = remember { mutableStateListOf<String>() }
  var previousSearchQuery by remember { mutableStateOf(searchQuery) }

  var showSkillLimitBanner by remember { mutableStateOf(false) }
  val selectedSkillsCount by remember {
    derivedStateOf { uiState.skills.count { it.skill.selected } }
  }

  LaunchedEffect(selectedSkillsCount) {
    if (selectedSkillsCount > MAX_RECOMMENDED_SKILL_COUNT) {
      showSkillLimitBanner = true
    }
  }

  LaunchedEffect(showSkillLimitBanner) {
    if (showSkillLimitBanner) {
      delay(3000) // 3 seconds
      showSkillLimitBanner = false
    }
  }

  LaunchedEffect(uiState.skills, searchQuery, uiState.loading) {
    if (!uiState.loading && !hasDeterminedExpansionStates) {
      val hasCustomSkills = uiState.skills.any { !it.skill.builtIn }
      isBuiltInExpanded = !hasCustomSkills
      hasDeterminedExpansionStates = true
    }

    val trimmedQuery = searchQuery.trim().lowercase()
    filteredSkills =
      if (trimmedQuery.isBlank()) {
        uiState.skills
      } else {
        uiState.skills.filter { skillState ->
          val skill = skillState.skill
          (skill.name?.lowercase()?.contains(trimmedQuery) == true) ||
            (skill.description?.lowercase()?.contains(trimmedQuery) == true)
        }
      }

    if (searchQuery != previousSearchQuery) {
      if (searchQuery.isNotEmpty()) {
        isBuiltInExpanded = true
        isCustomExpanded = true
      }
      if (filteredSkills.isNotEmpty()) {
        listState.scrollToItem(0)
      }
      previousSearchQuery = searchQuery
    }
  }

  LaunchedEffect(Unit) {
    savedSelectedSkillsNamesAndDescriptions =
      skillManagerViewModel.getSelectedSkillsNamesAndDescriptions()
  }

  ModalBottomSheet(
    onDismissRequest = {
      onDismiss(
        savedSelectedSkillsNamesAndDescriptions !=
          skillManagerViewModel.getSelectedSkillsNamesAndDescriptions()
      )
    },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Spinner when loading.
      if (uiState.loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 3.dp,
            modifier = Modifier.padding(end = 8.dp).size(24.dp),
          )
        }
      }
      // Loaded content.
      else {
        val focusManager = LocalFocusManager.current

        Column(
          modifier =
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).fillMaxSize().pointerInput(
              Unit
            ) {
              detectTapGestures(onTap = { focusManager.clearFocus() })
            }
        ) {
          // Title or Multi-Select Context Bar.
          if (inMultiSelectMode) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(
                onClick = {
                  inMultiSelectMode = false
                  selectedCustomSkillNames.clear()
                }
              ) {
                Icon(
                  Icons.Rounded.Close,
                  contentDescription = stringResource(R.string.cd_close_icon),
                )
              }
              Text(
                pluralStringResource(
                  R.plurals.selected_custom_skills_count,
                  selectedCustomSkillNames.size,
                  selectedCustomSkillNames.size,
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
              )
              IconButton(
                modifier = Modifier.padding(end = 3.dp),
                onClick = {
                  if (selectedCustomSkillNames.isNotEmpty()) {
                    showDeleteSkillDialog = true
                  }
                },
              ) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  stringResource(R.string.manage_skills),
                  style = MaterialTheme.typography.titleLarge,
                )
                Text(
                  stringResource(R.string.manage_skills_description),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                modifier = Modifier.padding(end = 3.dp),
                onClick = {
                  scope.launch {
                    sheetState.hide()
                    onDismiss(
                      savedSelectedSkillsNamesAndDescriptions !=
                        skillManagerViewModel.getSelectedSkillsNamesAndDescriptions()
                    )
                  }
                },
              ) {
                Icon(
                  Icons.Rounded.Close,
                  contentDescription = stringResource(R.string.cd_close_icon),
                )
              }
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
              Modifier.padding(top = 8.dp, bottom = if (searchQuery.isEmpty()) 8.dp else 18.dp)
                .height(IntrinsicSize.Min),
          ) {
            // Search bar.
            TextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              modifier = Modifier.weight(1f).clearFocusOnKeyboardDismiss(),
              shape = CircleShape,
              placeholder = { Text(stringResource(R.string.search_skill)) },
              leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
              trailingIcon = {
                if (searchQuery.trim().isNotEmpty()) {
                  IconButton(onClick = { searchQuery = "" }) {
                    Icon(Icons.Outlined.Cancel, contentDescription = null)
                  }
                }
              },
              singleLine = true,
              colors =
                TextFieldDefaults.colors(
                  focusedIndicatorColor = Color.Transparent,
                  unfocusedIndicatorColor = Color.Transparent,
                  disabledIndicatorColor = Color.Transparent,
                ),
            )

            // Button to add skill.
            Box(
              modifier =
                Modifier.fillMaxHeight()
                  .aspectRatio(1f)
                  .clip(CircleShape)
                  .clickable {
                    searchQuery = ""
                    showAddSkillOptionsSheet = true
                  }
                  .background(MaterialTheme.colorScheme.primary),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.cd_add_icon),
                tint = MaterialTheme.colorScheme.onPrimary,
              )
            }
          }

          AnimatedVisibility(visible = searchQuery.isEmpty()) {
            Row(
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
              // Skill count.
              Text(
                pluralStringResource(
                  R.plurals.skills_count,
                  uiState.skills.size,
                  uiState.skills.size,
                ),
                style = MaterialTheme.typography.labelLarge,
              )

              // Select all / Deselect all.
              Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                  onClick = { skillManagerViewModel.setAllSkillsSelected(selected = true) }
                ) {
                  Text(stringResource(R.string.turn_on_all))
                }
                TextButton(
                  onClick = { skillManagerViewModel.setAllSkillsSelected(selected = false) }
                ) {
                  Text(stringResource(R.string.turn_off_all))
                }
              }
            }
          }

          // Content.
          //
          // Disable over-scroll "stretch" effect.
          CompositionLocalProvider(LocalOverscrollFactory provides null) {
            val builtInSkills =
              remember(filteredSkills) { filteredSkills.filter { it.skill.builtIn } }
            val customSkills =
              remember(filteredSkills) { filteredSkills.filter { !it.skill.builtIn } }

            Box(modifier = Modifier.weight(1f)) {
              LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                if (builtInSkills.isNotEmpty()) {
                  item(key = "built_in_header") {
                    Row(
                      modifier =
                        Modifier.fillMaxWidth()
                          .clip(shape = RoundedCornerShape(20.dp))
                          .clickable { isBuiltInExpanded = !isBuiltInExpanded }
                          .padding(vertical = 12.dp, horizontal = 16.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        stringResource(R.string.built_in_skills_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                      )
                      Icon(
                        imageVector =
                          if (isBuiltInExpanded) {
                            Icons.Rounded.ExpandLess
                          } else {
                            Icons.Rounded.ExpandMore
                          },
                        contentDescription =
                          if (isBuiltInExpanded) {
                            stringResource(R.string.cd_collapse_icon)
                          } else {
                            stringResource(R.string.cd_expand_icon)
                          },
                      )
                    }
                  }
                  if (isBuiltInExpanded) {
                    items(builtInSkills, key = { it.skill.name }) { skillState ->
                      SkillItemRow(
                        skillState = skillState,
                        inMultiSelectMode = inMultiSelectMode,
                        isSelectedForDeletion = false,
                        onSelectionCheckedChange = {},
                        onLongClick = {},
                        onSkillEnabledChange = { newCheckedState ->
                          skillManagerViewModel.setSkillSelected(skillState, newCheckedState)
                        },
                        onViewClick = {
                          skillToEditIndex = uiState.skills.indexOf(skillState)
                          showAddOrEditSkillBottomSheet = true
                        },
                        onSecretClick = {
                          skillToEditIndex = uiState.skills.indexOf(skillState)
                          showSecretEditorDialog = true
                        },
                        onDeleteClick = {
                          skillToDeleteName = skillState.skill.name
                          showDeleteSkillDialog = true
                        },
                        uriHandler = uriHandler,
                      )
                    }
                  }
                }

                if (customSkills.isNotEmpty()) {
                  item(key = "custom_header") {
                    Row(
                      modifier =
                        Modifier.fillMaxWidth()
                          .clip(shape = RoundedCornerShape(20.dp))
                          .clickable { isCustomExpanded = !isCustomExpanded }
                          .padding(vertical = 12.dp, horizontal = 16.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Text(
                        stringResource(R.string.custom_skills_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                      )
                      Icon(
                        imageVector =
                          if (isCustomExpanded) {
                            Icons.Rounded.ExpandLess
                          } else {
                            Icons.Rounded.ExpandMore
                          },
                        contentDescription =
                          if (isCustomExpanded) {
                            stringResource(R.string.cd_collapse_icon)
                          } else {
                            stringResource(R.string.cd_expand_icon)
                          },
                      )
                    }
                  }
                  if (isCustomExpanded) {
                    items(customSkills, key = { it.skill.name }) { skillState ->
                      SkillItemRow(
                        skillState = skillState,
                        inMultiSelectMode = inMultiSelectMode,
                        isSelectedForDeletion =
                          selectedCustomSkillNames.contains(skillState.skill.name),
                        onSelectionCheckedChange = { checked ->
                          if (checked) {
                            selectedCustomSkillNames.add(skillState.skill.name)
                          } else {
                            selectedCustomSkillNames.remove(skillState.skill.name)
                            if (selectedCustomSkillNames.isEmpty()) inMultiSelectMode = false
                          }
                        },
                        onLongClick = {
                          if (!inMultiSelectMode) {
                            inMultiSelectMode = true
                            selectedCustomSkillNames.add(skillState.skill.name)
                          }
                        },
                        onSkillEnabledChange = { newCheckedState ->
                          skillManagerViewModel.setSkillSelected(skillState, newCheckedState)
                        },
                        onViewClick = {
                          skillToEditIndex = uiState.skills.indexOf(skillState)
                          showAddOrEditSkillBottomSheet = true
                        },
                        onSecretClick = {
                          skillToEditIndex = uiState.skills.indexOf(skillState)
                          showSecretEditorDialog = true
                        },
                        onDeleteClick = {
                          skillToDeleteName = skillState.skill.name
                          showDeleteSkillDialog = true
                        },
                        uriHandler = uriHandler,
                      )
                    }
                  }
                }
              }

              FloatingBanner(
                visible = showSkillLimitBanner,
                text =
                  stringResource(
                    R.string.skill_limit_warning,
                    pluralStringResource(
                      R.plurals.skills_count,
                      MAX_RECOMMENDED_SKILL_COUNT,
                      MAX_RECOMMENDED_SKILL_COUNT,
                    ),
                  ),
                modifier = Modifier.align(Alignment.TopCenter),
              )
            }
          }
        }
      }
    }
  }

  if (showDeleteSkillDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteSkillDialog = false },
      title = {
        Text(
          if (inMultiSelectMode) stringResource(R.string.delete_selected_skills_title)
          else stringResource(R.string.delete_skill_dialog_title)
        )
      },
      text = {
        Text(
          if (inMultiSelectMode)
            pluralStringResource(
              R.plurals.delete_selected_skills_content,
              selectedCustomSkillNames.size,
              selectedCustomSkillNames.size,
            )
          else stringResource(R.string.delete_skill_dialog_content)
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (inMultiSelectMode) {
              skillManagerViewModel.deleteSkills(selectedCustomSkillNames.toSet())
              inMultiSelectMode = false
              selectedCustomSkillNames.clear()
            } else {
              skillManagerViewModel.deleteSkill(name = skillToDeleteName)
            }
            showDeleteSkillDialog = false
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.customColors.errorTextColor,
              contentColor = Color.White,
            ),
        ) {
          Text(stringResource(R.string.delete))
        }
      },
      dismissButton = {
        OutlinedButton(onClick = { showDeleteSkillDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  if (showAddSkillFromUrlDialog) {
    AddSkillFromUrlDialog(
      skillManagerViewModel = skillManagerViewModel,
      onDismissRequest = { showAddSkillFromUrlDialog = false },
      onSuccess = { scrollToBottomOfList(scope, listState) },
    )
  }

  if (showAddSkillFromFeaturedListBottomSheet) {
    AddSkillFromFeatureListBottomSheet(
      skillManagerViewModel = skillManagerViewModel,
      onDismiss = { showAddSkillFromFeaturedListBottomSheet = false },
      onSkillAdded = { scrollToBottomOfList(scope, listState) },
    )
  }

  if (showAddSkillFromLocalImportDialog) {
    AddSkillFromLocalImportDialog(
      skillManagerViewModel = skillManagerViewModel,
      onDismissRequest = { showAddSkillFromLocalImportDialog = false },
      onSuccess = { scrollToBottomOfList(scope, listState) },
    )
  }

  if (showAddOrEditSkillBottomSheet) {
    AddOrEditSkillBottomSheet(
      skillManagerViewModel = skillManagerViewModel,
      skillIndex = if (skillToEditIndex != -1) skillToEditIndex else uiState.skills.size,
      onDismiss = {
        showAddOrEditSkillBottomSheet = false
        skillToEditIndex = -1
      },
      onSuccess = {
        scrollToBottomOfList(scope, listState)
        skillToEditIndex = -1
      },
    )
  }

  if (showAddSkillOptionsSheet) {
    AddSkillOptionsBottomSheet(
      onDismiss = { showAddSkillOptionsSheet = false },
      onOptionSelected = { option ->
        skillManagerViewModel.setValidationError(null)
        addSkillOptionTypeToConfirm = option.type
        when (option.type) {
          AddSkillOptionType.FeaturedList -> {
            showAddSkillFromFeaturedListBottomSheet = true
          }
          AddSkillOptionType.RemoteUrl -> {
            showAddSkillFromUrlDialog = true
          }
          AddSkillOptionType.LocalImport -> {
            showDisclaimerDialog = true
          }
          AddSkillOptionType.ViewCommunitySkills -> {
            showDisclaimerDialog = true
          }
          else -> {}
        }
        showAddSkillOptionsSheet = false
      },
    )
  }

  if (showJsSkillTesterBottomSheet) {
    skillToTest?.let { skill ->
      SkillTesterBottomSheet(
        agentTools = agentTools,
        skill = skill,
        onDismiss = { showJsSkillTesterBottomSheet = false },
      )
    }
  }

  if (showSecretEditorDialog) {
    val skillState = uiState.skills.getOrNull(skillToEditIndex)
    skillState?.let {
      var curSecret by remember {
        mutableStateOf(
          skillManagerViewModel.skillManager.dataStoreRepository.readSecret(
            getSkillSecretKey(skillName = it.skill.name)
          ) ?: ""
        )
      }
      SecretEditorDialog(
        title = stringResource(R.string.edit_secret),
        fieldLabel = skillState.skill.requireSecretDescription,
        value = curSecret,
        onValueChange = { curSecret = it },
        onDone = {
          skillManagerViewModel.skillManager.dataStoreRepository.saveSecret(
            key = getSkillSecretKey(skillName = it.skill.name),
            value = curSecret,
          )
          showSecretEditorDialog = false
        },
        onDismiss = { showSecretEditorDialog = false },
      )
    }
  }

  if (showDisclaimerDialog) {
    AddSkillDisclaimerDialog(
      onDismiss = {
        showDisclaimerDialog = false
        addSkillOptionTypeToConfirm = null
      },
      onConfirm = {
        when (addSkillOptionTypeToConfirm) {
          AddSkillOptionType.LocalImport -> showAddSkillFromLocalImportDialog = true
          AddSkillOptionType.ViewCommunitySkills -> showCommunitySkillsBottomSheet = true
          else -> {}
        }
        showDisclaimerDialog = false
        addSkillOptionTypeToConfirm = null
      },
    )
  }

  if (showCommunitySkillsBottomSheet) {
    ViewCommunitySkillsBottomSheet(
      skillManagerViewModel = skillManagerViewModel,
      onDismiss = { showCommunitySkillsBottomSheet = false },
      onSkillAdded = { scrollToBottomOfList(scope, listState) },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewCommunitySkillsBottomSheet(
  skillManagerViewModel: SkillManagerViewModel,
  onDismiss: () -> Unit,
  onSkillAdded: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val context = androidx.compose.ui.platform.LocalContext.current
  var webView by remember { mutableStateOf<WebView?>(null) }
  var installingSkillName by remember { mutableStateOf<String?>(null) }
  var installedSkillName by remember { mutableStateOf<String?>(null) }
  var installError by remember { mutableStateOf<String?>(null) }
  var selectedCatalogUrl by remember { mutableStateOf(AgentSkillsURLs.DISCUSSIONS) }
  val webViewClient =
    remember(context) {
      CommunitySkillsWebViewClient(
        context = context,
        onInstallRequested = { request ->
          val skillName = getCatalogSkillDisplayName(request)
          if (skillName == null) {
            installError = context.getString(R.string.skill_install_invalid_source)
          } else if (installingSkillName == null) {
            installingSkillName = skillName
            installedSkillName = null
            installError = null
            skillManagerViewModel.validateAndAddSkillFromUrl(
              url = request.skillUrl,
              onSuccess = {
                installingSkillName = null
                installedSkillName = skillName
                webView?.evaluateJavascript(markSkillInstalledScript(request.buttonKey), null)
                onSkillAdded()
              },
              onValidationError = { error ->
                installingSkillName = null
                installError = error
                webView?.evaluateJavascript(markSkillInstallFailedScript(request.buttonKey), null)
              },
            )
          }
        },
      )
    }

  BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Header section with title, description, and close button.
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.add_skill_option_view_community_skills_title),
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            stringResource(R.string.community_skills_browser_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          modifier = Modifier.padding(end = 3.dp),
          onClick = {
            scope.launch {
              sheetState.hide()
              onDismiss()
            }
          },
        ) {
          Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_icon))
        }
      }

      installingSkillName?.let { skillName ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          Text(
            text = stringResource(R.string.skill_installing, skillName),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      installedSkillName?.let { skillName ->
        Text(
          text = stringResource(R.string.skill_installed, skillName),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
      installError?.let { error ->
        Text(
          text = error,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (selectedCatalogUrl == AgentSkillsURLs.DISCUSSIONS) {
          FilledTonalButton(onClick = {}) {
            Text(stringResource(R.string.community_skills_tab))
          }
        } else {
          OutlinedButton(
            onClick = {
              selectedCatalogUrl = AgentSkillsURLs.DISCUSSIONS
              webView?.loadUrl(selectedCatalogUrl)
            }
          ) {
            Text(stringResource(R.string.community_skills_tab))
          }
        }
        if (selectedCatalogUrl == AgentSkillsURLs.FEATURED) {
          FilledTonalButton(onClick = {}) {
            Text(stringResource(R.string.featured_skills_tab))
          }
        } else {
          OutlinedButton(
            onClick = {
              selectedCatalogUrl = AgentSkillsURLs.FEATURED
              webView?.loadUrl(selectedCatalogUrl)
            }
          ) {
            Text(stringResource(R.string.featured_skills_tab))
          }
        }
      }

      GalleryWebView(
        modifier = Modifier.fillMaxWidth().weight(1f),
        initialUrl = selectedCatalogUrl,
        preventParentScrolling = true,
        customWebViewClient = webViewClient,
        onWebViewCreated = { createdWebView ->
          webView = createdWebView
          createdWebView.addJavascriptInterface(
            CommunitySkillInstallerBridge { skillUrl, buttonKey, displayName ->
              createdWebView.post {
                webViewClient.requestInstall(
                  CatalogSkillInstallRequest(
                    catalogPageUrl = createdWebView.url.orEmpty(),
                    skillUrl = skillUrl,
                    buttonKey = buttonKey,
                    displayName = displayName,
                  )
                )
              }
            },
            "AndroidJarvisSkills",
          )
        },
      )
    }
  }
}

private const val INSTALL_SKILL_SCHEME = "jarvis-install"
private const val OFFICIAL_FEATURED_SKILL_PATH = "/google-ai-edge/gallery/tree/main/skills/featured/"
private const val OFFICIAL_DISCUSSION_PATH = "/google-ai-edge/gallery/discussions/"

private data class CatalogSkillInstallRequest(
  val catalogPageUrl: String,
  val skillUrl: String,
  val buttonKey: String,
  val displayName: String,
)

private fun getOfficialFeaturedSkillName(url: String): String? {
  val uri = Uri.parse(url)
  if (uri.scheme != "https" || uri.host != "github.com") return null
  val segments = uri.pathSegments
  if (
    segments.size != 7 ||
      segments[0] != "google-ai-edge" ||
      segments[1] != "gallery" ||
      segments[2] != "tree" ||
      segments[3] != "main" ||
      segments[4] != "skills" ||
      segments[5] != "featured"
  ) {
    return null
  }
  return segments[6].takeIf { it.isNotBlank() }
}

private fun isOfficialCommunityCatalogPage(url: String): Boolean {
  val uri = Uri.parse(url)
  if (uri.scheme != "https" || uri.host != "github.com") return false
  val segments = uri.pathSegments
  if (
    segments.size < 4 ||
      segments[0] != "google-ai-edge" ||
      segments[1] != "gallery" ||
      segments[2] != "discussions"
  ) {
    return false
  }
  return segments[3].toIntOrNull() != null ||
    (segments.size >= 5 && segments[3] == "categories" && segments[4] == "skills")
}

private fun getCatalogSkillDisplayName(request: CatalogSkillInstallRequest): String? {
  getOfficialFeaturedSkillName(request.skillUrl)?.let { featuredSkillName ->
    return featuredSkillName.takeIf {
      request.catalogPageUrl.startsWith(AgentSkillsURLs.FEATURED)
    }
  }
  if (!isOfficialCommunityCatalogPage(request.catalogPageUrl)) return null

  val skillUri = Uri.parse(request.skillUrl)
  if (skillUri.scheme != "https" || skillUri.host.isNullOrBlank()) return null
  if (isOfficialCommunityCatalogPage(request.skillUrl)) return null

  val requestedName = request.displayName.trim().replace(Regex("\\s+"), " ").take(80)
  return requestedName.takeIf { it.isNotBlank() }
    ?: skillUri.lastPathSegment?.takeIf { it.isNotBlank() }
    ?: skillUri.host
}

private fun markSkillInstalledScript(buttonKey: String): String {
  val safeKey = buttonKey.replace("\\", "\\\\").replace("'", "\\'")
  return """
    (function() {
      var button = document.querySelector("button[data-jarvis-skill='$safeKey']");
      if (button) {
        button.textContent = '\u2713';
        button.disabled = true;
        button.setAttribute('aria-label', 'Skill installed');
      }
    })();
  """
    .trimIndent()
}

private fun markSkillInstallFailedScript(buttonKey: String): String {
  val safeKey = buttonKey.replace("\\", "\\\\").replace("'", "\\'")
  return """
    (function() {
      var button = document.querySelector("button[data-jarvis-skill='$safeKey']");
      if (button) {
        button.textContent = '+';
        button.disabled = false;
      }
    })();
  """
    .trimIndent()
}

private class CommunitySkillsWebViewClient(
  context: Context,
  private val onInstallRequested: (CatalogSkillInstallRequest) -> Unit,
) : BaseGalleryWebViewClient(context = context) {
  fun requestInstall(request: CatalogSkillInstallRequest) {
    onInstallRequested(request)
  }

  override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    val uri = request?.url ?: return false
    if (uri.scheme == INSTALL_SKILL_SCHEME) {
      val skillUrl = uri.getQueryParameter("url")
      val buttonKey = uri.getQueryParameter("key")
      val displayName = uri.getQueryParameter("name")
      if (skillUrl != null && buttonKey != null && displayName != null) {
        onInstallRequested(
          CatalogSkillInstallRequest(
            catalogPageUrl = view?.url.orEmpty(),
            skillUrl = skillUrl,
            buttonKey = buttonKey,
            displayName = displayName,
          )
        )
      }
      return true
    }
    return false
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    if (url?.startsWith(AgentSkillsURLs.FEATURED) == true) {
      view?.evaluateJavascript(INJECT_FEATURED_INSTALL_BUTTONS_SCRIPT, null)
    } else if (isOfficialCommunityCatalogPage(url.orEmpty())) {
      view?.evaluateJavascript(INJECT_COMMUNITY_INSTALL_BUTTONS_SCRIPT, null)
    }
  }
}

private class CommunitySkillInstallerBridge(
  private val onInstallRequested: (String, String, String) -> Unit
) {
  @JavascriptInterface
  fun install(url: String, buttonKey: String, displayName: String) {
    onInstallRequested(url, buttonKey, displayName)
  }
}

private val INJECT_FEATURED_INSTALL_BUTTONS_SCRIPT =
  """
    (function() {
      var pathPrefix = '$OFFICIAL_FEATURED_SKILL_PATH';
      function decorateSkillLinks() {
        var links = document.querySelectorAll("a[href^='" + pathPrefix + "']");
        links.forEach(function(link) {
          var href = (link.getAttribute('href') || '').split('?')[0].replace(/\/$/, '');
          var skillName = href.substring(pathPrefix.length);
          if (!skillName || skillName.indexOf('/') !== -1) return;

          var row = link.closest('[role="row"]') || link.closest('li') || link.parentElement;
          if (!row || row.querySelector("button[data-jarvis-skill='" + skillName + "']")) return;

          var button = document.createElement('button');
          button.type = 'button';
          button.textContent = '+';
          button.setAttribute('data-jarvis-skill', skillName);
          button.setAttribute('aria-label', 'Install ' + skillName);
          button.style.cssText =
            'margin-left:12px;min-width:34px;height:30px;border:1px solid #39ff14;' +
            'border-radius:15px;background:#071007;color:#39ff14;font-size:22px;' +
            'font-weight:700;line-height:24px;cursor:pointer;z-index:1000;';
          button.addEventListener('click', function(event) {
            event.preventDefault();
            event.stopImmediatePropagation();
            event.stopPropagation();
            var skillUrl = 'https://github.com' + href;
            if (window.AndroidJarvisSkills && window.AndroidJarvisSkills.install) {
              window.AndroidJarvisSkills.install(skillUrl, skillName, skillName);
            } else {
              window.location.href =
                '$INSTALL_SKILL_SCHEME://skill?url=' + encodeURIComponent(skillUrl) +
                '&key=' + encodeURIComponent(skillName) +
                '&name=' + encodeURIComponent(skillName);
            }
          });

          var target = row.querySelector('[role="gridcell"]:last-child') || row;
          target.appendChild(button);
        });
      }

      decorateSkillLinks();
      if (!window.__jarvisSkillObserver) {
        window.__jarvisSkillObserver = new MutationObserver(decorateSkillLinks);
        window.__jarvisSkillObserver.observe(document.body, { childList: true, subtree: true });
      }
    })();
  """
    .trimIndent()

private val INJECT_COMMUNITY_INSTALL_BUTTONS_SCRIPT =
  """
    (function() {
      var discussionPathPrefix = '$OFFICIAL_DISCUSSION_PATH';

      function normalizeHeading(text) {
        return (text || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
      }

      function validSkillUrl(href, discussionUrl) {
        try {
          var url = new URL(href, discussionUrl);
          if (url.protocol !== 'https:') return null;
          if (
            url.hostname === 'github.com' &&
            url.pathname.indexOf('$OFFICIAL_DISCUSSION_PATH') === 0
          ) return null;
          return url.href.replace(/\/$/, '');
        } catch (error) {
          return null;
        }
      }

      function firstLinkAfterHeading(body, wantedHeading, discussionUrl) {
        var headings = body.querySelectorAll('h1,h2,h3,h4,h5,h6,strong');
        for (var index = 0; index < headings.length; index++) {
          var heading = headings[index];
          if (normalizeHeading(heading.textContent) !== wantedHeading) continue;

          var sectionStart = heading.closest('p') || heading;
          var sibling = sectionStart.nextElementSibling;
          while (sibling) {
            if (/^H[1-6]$/.test(sibling.tagName)) break;
            var anchor = sibling.matches('a[href]') ? sibling : sibling.querySelector('a[href]');
            if (anchor) {
              var skillUrl = validSkillUrl(anchor.getAttribute('href'), discussionUrl);
              if (skillUrl) return skillUrl;
            }
            sibling = sibling.nextElementSibling;
          }
        }
        return null;
      }

      function extractSkillUrl(html, discussionUrl) {
        var parsed = new DOMParser().parseFromString(html, 'text/html');
        var bodies = Array.prototype.slice.call(parsed.querySelectorAll('.markdown-body'));
        var body = bodies.find(function(candidate) {
          return /skill\s+webhost\s+path/i.test(candidate.textContent || '');
        }) || bodies[0];
        if (!body) return null;

        var webhost = firstLinkAfterHeading(body, 'skill webhost path', discussionUrl);
        if (webhost) return webhost;

        var directSkillFile = Array.prototype.slice.call(body.querySelectorAll('a[href]')).find(
          function(anchor) {
            try {
              return new URL(anchor.getAttribute('href'), discussionUrl).pathname.endsWith('/SKILL.md');
            } catch (error) {
              return false;
            }
          }
        );
        if (directSkillFile) {
          return validSkillUrl(directSkillFile.getAttribute('href'), discussionUrl);
        }

        return firstLinkAfterHeading(body, 'skill source repository', discussionUrl);
      }

      function requestInstall(skillUrl, buttonKey, displayName) {
        if (window.AndroidJarvisSkills && window.AndroidJarvisSkills.install) {
          window.AndroidJarvisSkills.install(skillUrl, buttonKey, displayName);
        } else {
          window.location.href =
            '$INSTALL_SKILL_SCHEME://skill?url=' + encodeURIComponent(skillUrl) +
            '&key=' + encodeURIComponent(buttonKey) +
            '&name=' + encodeURIComponent(displayName);
        }
      }

      function decorateDiscussionLinks() {
        var links = document.querySelectorAll("a[href*='" + discussionPathPrefix + "']");
        links.forEach(function(link) {
          var linkUrl;
          try {
            linkUrl = new URL(link.getAttribute('href'), window.location.href);
          } catch (error) {
            return;
          }
          if (linkUrl.hostname !== 'github.com') return;
          var href = linkUrl.pathname.replace(/\/$/, '');
          var match = href.match(/^\/google-ai-edge\/gallery\/discussions\/(\d+)$/);
          if (!match) return;

          var displayName = (link.textContent || '').replace(/\s+/g, ' ').trim();
          if (!displayName || /^\d+$/.test(displayName)) return;
          var titleContainer = link.closest('h2,h3,h4') || link.parentElement;
          if (!titleContainer) return;

          var buttonKey = 'discussion-' + match[1];
          if (document.querySelector("button[data-jarvis-skill='" + buttonKey + "']")) return;

          var button = document.createElement('button');
          button.type = 'button';
          button.textContent = '+';
          button.setAttribute('data-jarvis-skill', buttonKey);
          button.setAttribute('aria-label', 'Install ' + displayName);
          button.style.cssText =
            'margin-left:12px;min-width:34px;height:30px;border:1px solid #39ff14;' +
            'border-radius:15px;background:#071007;color:#39ff14;font-size:22px;' +
            'font-weight:700;line-height:24px;cursor:pointer;z-index:1000;';
          button.addEventListener('click', function(event) {
            event.preventDefault();
            event.stopImmediatePropagation();
            event.stopPropagation();
            if (button.disabled) return;
            button.disabled = true;
            button.textContent = '\u2026';

            var discussionUrl = 'https://github.com' + href;
            fetch(discussionUrl, { credentials: 'same-origin' })
              .then(function(response) {
                if (!response.ok) throw new Error('Could not open the community skill page.');
                return response.text();
              })
              .then(function(html) {
                var skillUrl = extractSkillUrl(html, discussionUrl);
                if (!skillUrl) throw new Error('This post does not declare an installable skill URL.');
                requestInstall(skillUrl, buttonKey, displayName);
              })
              .catch(function(error) {
                button.disabled = false;
                button.textContent = '+';
                window.location.href = discussionUrl;
              });
          });

          titleContainer.appendChild(button);
        });
      }

      decorateDiscussionLinks();
      if (!window.__jarvisCommunitySkillObserver) {
        window.__jarvisCommunitySkillObserver = new MutationObserver(decorateDiscussionLinks);
        window.__jarvisCommunitySkillObserver.observe(document.body, {
          childList: true,
          subtree: true
        });
      }
    })();
  """
    .trimIndent()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SkillItemRow(
  skillState: SkillState,
  inMultiSelectMode: Boolean,
  isSelectedForDeletion: Boolean,
  onSelectionCheckedChange: (Boolean) -> Unit,
  onLongClick: () -> Unit,
  onSkillEnabledChange: (Boolean) -> Unit,
  onViewClick: () -> Unit,
  onSecretClick: () -> Unit,
  onDeleteClick: () -> Unit,
  uriHandler: androidx.compose.ui.platform.UriHandler,
) {
  val skill = skillState.skill
  val isCustom = !skill.builtIn

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .then(if (inMultiSelectMode && skill.builtIn) Modifier.alpha(0.5f) else Modifier)
        .clip(shape = RoundedCornerShape(20.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .then(
          if (isCustom) {
            Modifier.combinedClickable(
              onClick = {
                if (inMultiSelectMode) {
                  onSelectionCheckedChange(!isSelectedForDeletion)
                }
              },
              onLongClick = onLongClick,
            )
          } else Modifier
        )
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (inMultiSelectMode && isCustom) {
      Checkbox(
        checked = isSelectedForDeletion,
        onCheckedChange = onSelectionCheckedChange,
        modifier = Modifier.padding(end = 12.dp),
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Name and description.
        Column(
          modifier = Modifier.weight(1f).padding(top = 2.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            val hasHomepage = !skill.homepage.isBlank()
            val textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)

            if (hasHomepage) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  skill.name,
                  style =
                    textStyle.copy(
                      color = MaterialTheme.colorScheme.primary,
                      textDecoration = TextDecoration.Underline,
                    ),
                  color = MaterialTheme.customColors.linkColor,
                  modifier = Modifier.clickable { uriHandler.openUri(skill.homepage) },
                )
                Icon(
                  Icons.AutoMirrored.Outlined.OpenInNew,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                  tint = MaterialTheme.customColors.linkColor,
                )
              }
            } else {
              Text(skill.name, style = textStyle)
            }
          }
          Text(
            (skill.description ?: "").replace("\n", " "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        // Select switch.
        Switch(
          checked = skill.selected,
          onCheckedChange = onSkillEnabledChange,
          modifier =
            Modifier.offset(y = (-4).dp).semantics { contentDescription = "Toggle ${skill.name}" },
          enabled = !inMultiSelectMode,
        )
      }

      // Buttons row.
      AnimatedVisibility(visible = !inMultiSelectMode) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Start,
          modifier = Modifier.padding(top = 8.dp),
        ) {
          // Edit.
          FilledTonalButton(
            onClick = onViewClick,
            modifier = Modifier.height(32.dp).padding(end = 8.dp),
            contentPadding = BUTTON_CONTENT_PADDING,
          ) {
            Icon(
              Icons.Outlined.RemoveRedEye,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
            Text(
              stringResource(R.string.view),
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.padding(start = 4.dp),
            )
          }

          if (skill.requireSecret) {
            // Edit secret.
            FilledTonalButton(
              onClick = onSecretClick,
              modifier = Modifier.height(32.dp).padding(end = 8.dp),
              contentPadding = BUTTON_CONTENT_PADDING,
            ) {
              Icon(
                Icons.Outlined.VpnKey,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Text(
                stringResource(R.string.secret),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp),
              )
            }
          }

          // Buttons only for non-builtin skills.
          if (!skill.builtIn) {
            // Delete.
            OutlinedButton(
              onClick = onDeleteClick,
              modifier = Modifier.height(32.dp),
              contentPadding = BUTTON_CONTENT_PADDING,
            ) {
              Icon(
                Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
              Text(
                stringResource(R.string.delete),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp),
              )
            }
          }
        }
      }
    }
  }
}

private fun scrollToBottomOfList(scope: CoroutineScope, listState: LazyListState) {
  scope.launch {
    // Scroll to the bottom of the LazyColumn.
    delay(300)
    if (listState.layoutInfo.totalItemsCount > 0) {
      listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSkillOptionsBottomSheet(
  onDismiss: () -> Unit,
  onOptionSelected: (AddSkillOption) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
      Text(
        stringResource(R.string.add_skill),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 16.dp).padding(horizontal = 16.dp),
      )
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ADD_SKILL_OPTIONS.forEach { option ->
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  onOptionSelected(option)
                  firebaseAnalytics?.logEvent(
                    GalleryEvent.BUTTON_CLICKED.id,
                    Bundle().apply {
                      putString("event_type", "agent_skills_add_skill")
                      putString("button_id", option.type.toString())
                    },
                  )
                  onDismiss()
                }
                .padding(horizontal = 16.dp, vertical = 8.dp)
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              // Row for Icon and Title
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 4.dp),
              ) {
                Icon(option.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(stringResource(option.titleResId), style = MaterialTheme.typography.bodyLarge)
              }
              // Description Text
              Text(
                stringResource(option.descriptionResId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp),
              )
            }
          }
        }
      }
    }
  }
}
