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

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.gallery.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TermuxBridgeDeviceTest {
  @Test
  fun approvedBridge_returnsTerminalOutputAndFindsAdbBinary() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val status = AndroidTermuxEnvironment(context).status()
    assumeTrue("Termux is not installed on this device", status.installed)
    assumeTrue("RUN_COMMAND permission has not been granted to Jarvis", status.runCommandPermissionGranted)

    context.packageManager
      .getLaunchIntentForPackage(TermuxRunCommandContract.PACKAGE_NAME)
      ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
      ?.let(context::startActivity)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    val scenario = ActivityScenario.launch(MainActivity::class.java)
    var activity: MainActivity? = null
    scenario.onActivity { launchedActivity -> activity = launchedActivity }
    val result =
      try {
        AndroidTermuxCommandRunner(requireNotNull(activity)).run(
          "printf 'JARVIS_TERMUX_DEVICE_OK\\n'; " +
            "printf 'TMUX_SESSION=%s\\n' \"\$(tmux display-message -p '#S')\"; " +
            "command -v adb; adb version | head -n 1",
          timeoutMs = 20_000L,
        )
      } finally {
        scenario.close()
      }

    val diagnostic =
      "exit=${result.exitCode}; stdout=${result.stdout}; stderr=${result.stderr}; " +
        "internal=${result.internalErrorMessage}"
    assertTrue(diagnostic, result.succeeded)
    assertEquals(0, result.exitCode)
    assertTrue(result.stdout.contains("JARVIS_TERMUX_DEVICE_OK"))
    assertTrue(result.stdout.contains("TMUX_SESSION=android-jarvis"))
    assertTrue(result.stdout.contains("/adb"))
    assertTrue(result.stdout.contains("Android Debug Bridge version"))
  }

  @Test
  fun nativeAdbNsd_discoversThisPhonesConnectEndpoint() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val endpoints = AndroidSelfAdbEndpointDiscovery(context).discoverConnectEndpoints()
    assertTrue(
      "Android NSD did not discover this phone's local wireless-debugging connect endpoint.",
      endpoints.isNotEmpty(),
    )
  }
}
