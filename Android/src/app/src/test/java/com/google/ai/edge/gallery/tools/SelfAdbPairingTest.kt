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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfAdbPairingTest {
  @Test
  fun connectedSerials_includeOnlyDevicesInReadyState() {
    val output =
      """
        List of devices attached
        192.168.50.17:41234 device product:e3q model:SM_S928U
        192.168.50.18:41235 unauthorized
        emulator-5554 offline
      """.trimIndent()

    assertEquals(
      listOf("192.168.50.17:41234"),
      SelfAdbEndpointParser.parseConnectedSerials(output),
    )
  }

  @Test
  fun existingConnection_isAcceptedOnlyAfterFingerprintMatches() = runBlocking {
    val runner = PairingRunner(expectedFingerprint = "samsung/test/fingerprint")
    val discovery = FakeEndpointDiscovery()
    val engine =
      SelfAdbPairingEngine(
        runner,
        endpointDiscovery = discovery,
        expectedFingerprint = "samsung/test/fingerprint",
      )

    val result = engine.findVerifiedConnection()

    assertTrue(result?.succeeded == true)
    assertEquals("192.168.50.17:41234", result?.serial)
    assertTrue(runner.commands.any { "shell getprop ro.build.fingerprint" in it })
  }

  @Test
  fun pairing_discoversPairsAndThenVerifiesThisPhone() = runBlocking {
    val runner = PairingRunner(expectedFingerprint = "samsung/test/fingerprint")
    runner.connected = false
    val discovery = FakeEndpointDiscovery()
    val engine =
      SelfAdbPairingEngine(
        runner,
        endpointDiscovery = discovery,
        expectedFingerprint = "samsung/test/fingerprint",
      )

    val result = engine.pairWithCode("123456")

    assertTrue(result.succeeded)
    assertEquals("192.168.50.17:41234", result.serial)
    assertTrue(runner.commands.any { "adb pair '192.168.50.17:37123'" in it })
    assertTrue(runner.commands.any { "adb -s '192.168.50.17:41234'" in it })
    assertTrue(discovery.pairingDiscoveryCalled)
    assertTrue(discovery.connectDiscoveryCalled)
  }

  @Test
  fun invalidPairingCode_neverReachesTermux() = runBlocking {
    val runner = PairingRunner(expectedFingerprint = "samsung/test/fingerprint")
    val engine =
      SelfAdbPairingEngine(
        runner,
        endpointDiscovery = FakeEndpointDiscovery(),
        expectedFingerprint = "samsung/test/fingerprint",
      )

    val result = engine.pairWithCode("12 3456")

    assertFalse(result.succeeded)
    assertTrue(runner.commands.isEmpty())
  }

  private class PairingRunner(private val expectedFingerprint: String) : TermuxCommandRunner {
    val commands = mutableListOf<String>()
    var connected = true

    override suspend fun run(command: String, timeoutMs: Long): TermuxCommandResult {
      commands += command
      val stdout =
        when {
          command == "adb devices" ->
            if (connected) {
              "List of devices attached\n192.168.50.17:41234 device\n"
            } else {
              "List of devices attached\n\n"
            }
          "shell getprop ro.build.fingerprint" in command -> "$expectedFingerprint\n"
          "adb pair" in command -> {
            "Successfully paired to 192.168.50.17:37123\n"
          }
          "adb connect" in command -> {
            connected = true
            "connected to 192.168.50.17:41234\n"
          }
          else -> ""
        }
      return TermuxCommandResult(
        exitCode = 0,
        stdout = stdout,
        stderr = "",
        internalErrorCode = -1,
        internalErrorMessage = "",
        stdoutTruncated = false,
        stderrTruncated = false,
      )
    }
  }

  private class FakeEndpointDiscovery : SelfAdbEndpointDiscovery {
    var pairingDiscoveryCalled = false
    var connectDiscoveryCalled = false

    override suspend fun discoverPairingEndpoints(): List<String> {
      pairingDiscoveryCalled = true
      return listOf("192.168.50.17:37123")
    }

    override suspend fun discoverConnectEndpoints(): List<String> {
      connectDiscoveryCalled = true
      return listOf("192.168.50.17:41234")
    }
  }
}
