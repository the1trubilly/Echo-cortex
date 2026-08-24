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

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Receives the one-shot PendingIntent result populated by Termux's RunCommandService. */
class TermuxCommandResultService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val executionId =
      intent?.getIntExtra(TermuxRunCommandContract.EXTRA_JARVIS_EXECUTION_ID, -1) ?: -1
    val result = intent?.getBundleExtra(TermuxRunCommandContract.EXTRA_PLUGIN_RESULT_BUNDLE)
    if (executionId >= 0 && result != null) {
      TermuxCommandResultRegistry.complete(executionId, result)
    }
    stopSelf(startId)
    return START_NOT_STICKY
  }
}
