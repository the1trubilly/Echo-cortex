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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object SelfAdbPairingNotification {
  fun showCodePrompt(context: Context, requestId: String, error: String = ""): Boolean {
    if (!canNotify(context)) return false
    createChannel(context)
    val replyIntent =
      Intent(context, SelfAdbPairingReceiver::class.java).apply {
        action = ACTION_SUBMIT_PAIRING_CODE
        putExtra(EXTRA_REQUEST_ID, requestId)
      }
    val replyPendingIntent =
      PendingIntent.getBroadcast(
        context,
        requestId.hashCode(),
        replyIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
      )
    val remoteInput =
      RemoteInput.Builder(REMOTE_INPUT_PAIRING_CODE)
        .setLabel(context.getString(R.string.self_adb_pairing_code_hint))
        .setAllowFreeFormInput(true)
        .build()
    val replyAction =
      NotificationCompat.Action.Builder(
          android.R.drawable.ic_menu_send,
          context.getString(R.string.self_adb_enter_pairing_code),
          replyPendingIntent,
        )
        .addRemoteInput(remoteInput)
        .setAllowGeneratedReplies(false)
        .build()
    val settingsIntent =
      Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
      )
    val settingsPendingIntent =
      PendingIntent.getActivity(
        context,
        requestId.hashCode() xor SETTINGS_REQUEST_MASK,
        settingsIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val body =
      error.ifBlank {
        context.getString(R.string.self_adb_pairing_notification_description)
      }
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle(context.getString(R.string.self_adb_pairing_notification_title))
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(settingsPendingIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setOngoing(true)
        .setOnlyAlertOnce(error.isNotBlank())
        .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
        .addAction(replyAction)
        .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    return true
  }

  fun showPairingInProgress(context: Context) {
    if (!canNotify(context)) return
    createChannel(context)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle(context.getString(R.string.self_adb_pairing_in_progress))
        .setContentText(context.getString(R.string.self_adb_pairing_code_received))
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }

  fun showResult(context: Context, result: SelfAdbConnectionResult) {
    if (!canNotify(context)) return
    createChannel(context)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(
          if (result.succeeded) android.R.drawable.stat_sys_data_bluetooth
          else android.R.drawable.stat_notify_error
        )
        .setContentTitle(
          context.getString(
            if (result.succeeded) R.string.self_adb_pairing_succeeded
            else R.string.self_adb_pairing_failed
          )
        )
        .setContentText(result.message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(result.message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setOngoing(false)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
  }

  private fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

  private fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.self_adb_pairing_channel_name),
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = context.getString(R.string.self_adb_pairing_channel_description)
        lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
      }
    )
  }

  const val ACTION_SUBMIT_PAIRING_CODE =
    "com.google.ai.edge.gallery.action.SUBMIT_SELF_ADB_PAIRING_CODE"
  const val EXTRA_REQUEST_ID = "self_adb_pairing_request_id"
  const val REMOTE_INPUT_PAIRING_CODE = "self_adb_pairing_code"
  private const val CHANNEL_ID = "jarvis_self_adb_pairing"
  private const val NOTIFICATION_ID = 0x4A4152
  private const val SETTINGS_REQUEST_MASK = 0x5E771A65
  private const val NOTIFICATION_TIMEOUT_MS = 3 * 60_000L
}

/** Receives the six-digit direct reply. The code remains memory-only and is never logged/stored. */
class SelfAdbPairingReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != SelfAdbPairingNotification.ACTION_SUBMIT_PAIRING_CODE) return
    val requestId =
      intent.getStringExtra(SelfAdbPairingNotification.EXTRA_REQUEST_ID).orEmpty()
    val pairingCode =
      RemoteInput.getResultsFromIntent(intent)
        ?.getCharSequence(SelfAdbPairingNotification.REMOTE_INPUT_PAIRING_CODE)
        ?.toString()
        ?.trim()
        .orEmpty()
    if (requestId.isBlank()) return
    if (!Regex("^[0-9]{6}$").matches(pairingCode)) {
      SelfAdbPairingNotification.showCodePrompt(
        context,
        requestId,
        context.getString(R.string.self_adb_pairing_code_invalid),
      )
      return
    }

    // Replace the direct-reply notification immediately so Android does not retain/display the code.
    SelfAdbPairingNotification.showPairingInProgress(context)
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        val result =
          SelfAdbPairingEngine(
              runner = AndroidTermuxCommandRunner(context),
              endpointDiscovery = AndroidSelfAdbEndpointDiscovery(context),
              expectedFingerprint = Build.FINGERPRINT,
            )
            .pairWithCode(pairingCode)
        SelfAdbPairingRequestRegistry.complete(requestId, result)
        SelfAdbPairingNotification.showResult(context, result)
      } finally {
        pendingResult.finish()
      }
    }
  }
}
