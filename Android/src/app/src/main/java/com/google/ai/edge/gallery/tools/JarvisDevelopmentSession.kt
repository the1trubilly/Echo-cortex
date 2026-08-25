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

package com.google.ai.edge.gallery.tools

import android.content.Context

/** Durable audit anchor for one counterpart-development cycle. */
internal data class JarvisDevelopmentSession(
  val id: String,
  val goal: String,
  val branch: String,
  val baselineCommit: String,
  val startedAtEpochMs: Long,
  val verifiedCommit: String = "",
  val backupApkPath: String = "",
  val counterpartPackage: String = "",
  val changedPaths: List<String> = emptyList(),
)

internal interface JarvisDevelopmentSessionStore {
  fun read(): JarvisDevelopmentSession?

  fun write(session: JarvisDevelopmentSession)

  fun clear()
}

internal class AndroidJarvisDevelopmentSessionStore(context: Context) :
  JarvisDevelopmentSessionStore {
  private val preferences =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override fun read(): JarvisDevelopmentSession? {
    val id = preferences.getString(KEY_ID, null).orEmpty()
    val baseline = preferences.getString(KEY_BASELINE, null).orEmpty()
    if (id.isBlank() || baseline.isBlank()) return null
    return JarvisDevelopmentSession(
      id = id,
      goal = preferences.getString(KEY_GOAL, "").orEmpty(),
      branch = preferences.getString(KEY_BRANCH, "").orEmpty(),
      baselineCommit = baseline,
      startedAtEpochMs = preferences.getLong(KEY_STARTED_AT, 0L),
      verifiedCommit = preferences.getString(KEY_VERIFIED_COMMIT, "").orEmpty(),
      backupApkPath = preferences.getString(KEY_BACKUP_APK, "").orEmpty(),
      counterpartPackage = preferences.getString(KEY_COUNTERPART_PACKAGE, "").orEmpty(),
      changedPaths =
        preferences
          .getString(KEY_CHANGED_PATHS, "")
          .orEmpty()
          .lineSequence()
          .map(String::trim)
          .filter(String::isNotBlank)
          .toList(),
    )
  }

  override fun write(session: JarvisDevelopmentSession) {
    preferences
      .edit()
      .putString(KEY_ID, session.id)
      .putString(KEY_GOAL, session.goal)
      .putString(KEY_BRANCH, session.branch)
      .putString(KEY_BASELINE, session.baselineCommit)
      .putLong(KEY_STARTED_AT, session.startedAtEpochMs)
      .putString(KEY_VERIFIED_COMMIT, session.verifiedCommit)
      .putString(KEY_BACKUP_APK, session.backupApkPath)
      .putString(KEY_COUNTERPART_PACKAGE, session.counterpartPackage)
      .putString(KEY_CHANGED_PATHS, session.changedPaths.joinToString("\n"))
      .apply()
  }

  override fun clear() {
    preferences.edit().clear().apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "jarvis_development_session"
    const val KEY_ID = "id"
    const val KEY_GOAL = "goal"
    const val KEY_BRANCH = "branch"
    const val KEY_BASELINE = "baseline"
    const val KEY_STARTED_AT = "started_at"
    const val KEY_VERIFIED_COMMIT = "verified_commit"
    const val KEY_BACKUP_APK = "backup_apk"
    const val KEY_COUNTERPART_PACKAGE = "counterpart_package"
    const val KEY_CHANGED_PATHS = "changed_paths"
  }
}
