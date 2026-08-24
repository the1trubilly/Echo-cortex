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
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

data class SelfAdbConnectionResult(
  val succeeded: Boolean,
  val serial: String = "",
  val message: String,
)

interface SelfAdbConnectionProvider {
  suspend fun checkConnection(): SelfAdbConnectionResult

  suspend fun getOrPair(
    actionChannel: SendChannel<ToolAction>? = null
  ): SelfAdbConnectionResult
}

/** Test/default seam for callers that already manage their own ADB target. */
internal object ExistingAdbConnectionProvider : SelfAdbConnectionProvider {
  override suspend fun checkConnection(): SelfAdbConnectionResult =
    SelfAdbConnectionResult(succeeded = true, message = "ADB target managed by caller.")

  override suspend fun getOrPair(
    actionChannel: SendChannel<ToolAction>?
  ): SelfAdbConnectionResult = checkConnection()
}

/** Coordinates the user-approved notification handoff and resumes the waiting agent tool call. */
class AndroidSelfAdbConnectionProvider(
  private val context: Context,
  private val runner: TermuxCommandRunner,
) : SelfAdbConnectionProvider {
  private val engine =
    SelfAdbPairingEngine(
      runner = runner,
      endpointDiscovery = AndroidSelfAdbEndpointDiscovery(context),
      expectedFingerprint = Build.FINGERPRINT,
    )

  override suspend fun checkConnection(): SelfAdbConnectionResult =
    engine.findVerifiedConnection()
      ?: SelfAdbConnectionResult(
        succeeded = false,
        message = "Jarvis is not connected to this phone through its Termux ADB server.",
      )

  override suspend fun getOrPair(
    actionChannel: SendChannel<ToolAction>?
  ): SelfAdbConnectionResult {
    engine.findVerifiedConnection()?.let { return it }

    if (!ensureNotificationPermission(actionChannel)) {
      return SelfAdbConnectionResult(
        succeeded = false,
        message = "Notification permission is required to enter the temporary pairing code.",
      )
    }
    if (!ensureLocalNetworkPermission(actionChannel)) {
      return SelfAdbConnectionResult(
        succeeded = false,
        message =
          "Local network permission is required to discover this phone's wireless ADB endpoint.",
      )
    }

    // Warm Termux while Jarvis is foregrounded. Android 16 may reject a cold cross-app service
    // start after Settings has taken the foreground.
    val warmup = runner.run("true", timeoutMs = 15_000L)
    if (!warmup.succeeded) {
      return SelfAdbConnectionResult(
        succeeded = false,
        message =
          warmup.internalErrorMessage.ifBlank {
            "Open Termux once, return to Jarvis, and start self-ADB pairing again."
          },
      )
    }

    val requestId = UUID.randomUUID().toString()
    val deferred = SelfAdbPairingRequestRegistry.register(requestId)
    if (!SelfAdbPairingNotification.showCodePrompt(context, requestId)) {
      SelfAdbPairingRequestRegistry.unregister(requestId)
      return SelfAdbConnectionResult(
        succeeded = false,
        message = "Jarvis could not post the pairing-code notification.",
      )
    }

    actionChannel?.send(
      SkillProgressToolAction(
        label = "Waiting for self-ADB pairing code",
        inProgress = true,
        addItemTitle = "Self-ADB pairing",
        addItemDescription =
          "Tap Pair device with pairing code in Wireless debugging, then enter the six digits " +
            "in the Jarvis notification. If Android shows Developer options instead, tap " +
            "Wireless debugging first.",
      )
    )
    openDeveloperSettings(context)

    val result = withTimeoutOrNull(PAIRING_FLOW_TIMEOUT_MS) { deferred.await() }
    SelfAdbPairingRequestRegistry.unregister(requestId)
    val resolved =
      result
        ?: SelfAdbConnectionResult(
          succeeded = false,
          message = "Self-ADB pairing timed out. Generate a new pairing code and try again.",
        )
    actionChannel?.send(
      SkillProgressToolAction(
        label = if (resolved.succeeded) "Self-ADB connected" else "Self-ADB pairing failed",
        inProgress = false,
        addItemTitle = "Self-ADB pairing result",
        addItemDescription = resolved.message,
      )
    )
    return resolved
  }

  private suspend fun ensureNotificationPermission(
    actionChannel: SendChannel<ToolAction>?
  ): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    if (
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    ) {
      return true
    }
    if (actionChannel == null) return false
    val permission = RequestPermissionToolAction(permission = Manifest.permission.POST_NOTIFICATIONS)
    actionChannel.send(permission)
    return permission.result.await()
  }

  private suspend fun ensureLocalNetworkPermission(
    actionChannel: SendChannel<ToolAction>?
  ): Boolean {
    if (Build.VERSION.SDK_INT < 37) return true
    if (
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED
    ) {
      return true
    }
    if (actionChannel == null) return false
    val permission = RequestPermissionToolAction(permission = Manifest.permission.ACCESS_LOCAL_NETWORK)
    actionChannel.send(permission)
    return permission.result.await()
  }

  companion object {
    fun openDeveloperSettings(context: Context) {
      for (intent in wirelessDebuggingSettingsIntents()) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) continue
        try {
          context.startActivity(intent)
          return
        } catch (_: ActivityNotFoundException) {
          // Try the next supported route.
        } catch (_: SecurityException) {
          // Vendor Settings apps may expose an intent filter but reject external callers.
        }
      }
    }

    private const val PAIRING_FLOW_TIMEOUT_MS = 3 * 60_000L
  }
}

/** Ordered from the shortest supported route to the universal Developer-options fallback. */
internal fun wirelessDebuggingSettingsIntents(): List<Intent> {
  val wirelessDebuggingTile =
    ComponentName(
      SETTINGS_PACKAGE_NAME,
      "$SETTINGS_PACKAGE_NAME.development.qstile.DevelopmentTiles\$WirelessDebugging",
    )
  return listOf(
    Intent(ACTION_WIRELESS_DEBUGGING_SETTINGS),
    Intent(ACTION_QS_TILE_PREFERENCES).putExtra(Intent.EXTRA_COMPONENT_NAME, wirelessDebuggingTile),
    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(
      EXTRA_FRAGMENT_ARG_KEY,
      WIRELESS_DEBUGGING_PREFERENCE_KEY,
    ),
  )
}

private const val ACTION_WIRELESS_DEBUGGING_SETTINGS =
  "android.settings.WIRELESS_DEBUGGING_SETTINGS"
private const val ACTION_QS_TILE_PREFERENCES =
  "android.service.quicksettings.action.QS_TILE_PREFERENCES"
private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
private const val SETTINGS_PACKAGE_NAME = "com.android.settings"
private const val WIRELESS_DEBUGGING_PREFERENCE_KEY = "toggle_adb_wireless"

internal interface SelfAdbEndpointDiscovery {
  suspend fun discoverPairingEndpoints(): List<String>

  suspend fun discoverConnectEndpoints(): List<String>
}

/** Uses Android NSD instead of optional `adb mdns` and Termux `ip` commands. */
internal class AndroidSelfAdbEndpointDiscovery(context: Context) : SelfAdbEndpointDiscovery {
  private val appContext = context.applicationContext
  private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
  private val connectivityManager =
    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  private val callbackExecutor = ContextCompat.getMainExecutor(appContext)

  override suspend fun discoverPairingEndpoints(): List<String> =
    discoverEndpoints(ADB_PAIRING_SERVICE_TYPE)

  override suspend fun discoverConnectEndpoints(): List<String> =
    discoverEndpoints(ADB_CONNECT_SERVICE_TYPE)

  private suspend fun discoverEndpoints(serviceType: String): List<String> {
    val localAddresses = localInterfaceAddresses()
    if (localAddresses.isEmpty()) return emptyList()
    val endpoints = mutableListOf<String>()
    for (discoveredService in discoverServiceInfos(serviceType)) {
      val resolvedService = resolveService(discoveredService) ?: continue
      for (address in resolvedAddresses(resolvedService)) {
        if (address in localAddresses) {
          endpoints += formatEndpoint(address, resolvedService.port)
        }
      }
    }
    return endpoints.distinct()
  }

  private suspend fun discoverServiceInfos(serviceType: String): List<NsdServiceInfo> {
    val found = mutableListOf<NsdServiceInfo>()
    val started = AtomicBoolean(false)
    val stopped = CompletableDeferred<Unit>()
    val listener =
      object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
          started.set(true)
        }

        override fun onServiceFound(service: NsdServiceInfo) {
          synchronized(found) { found += service }
        }

        override fun onServiceLost(service: NsdServiceInfo) = Unit

        override fun onDiscoveryStopped(serviceType: String) {
          stopped.complete(Unit)
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
          stopped.complete(Unit)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
          stopped.complete(Unit)
        }
      }

    try {
      nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
      delay(NSD_DISCOVERY_WINDOW_MS)
      if (started.get()) {
        try {
          nsdManager.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
          stopped.complete(Unit)
        }
      }
      withTimeoutOrNull(NSD_STOP_TIMEOUT_MS) { stopped.await() }
    } catch (_: Exception) {
      if (started.get()) {
        try {
          nsdManager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
          // Nothing remains to stop.
        }
      }
    }
    return synchronized(found) { found.toList() }
  }

  private suspend fun resolveService(service: NsdServiceInfo): NsdServiceInfo? {
    val resolved = CompletableDeferred<NsdServiceInfo?>()
    val listener =
      object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
          resolved.complete(null)
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
          resolved.complete(serviceInfo)
        }
      }
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        nsdManager.resolveService(service, callbackExecutor, listener)
      } else {
        @Suppress("DEPRECATION") nsdManager.resolveService(service, listener)
      }
    } catch (_: Exception) {
      return null
    }
    return withTimeoutOrNull(NSD_RESOLVE_TIMEOUT_MS) { resolved.await() }
  }

  private fun localInterfaceAddresses(): Set<InetAddress> =
    connectivityManager.allNetworks
      .asSequence()
      .mapNotNull(connectivityManager::getLinkProperties)
      .flatMap { properties -> properties.linkAddresses.asSequence() }
      .map { linkAddress -> linkAddress.address }
      .filterIsInstance<Inet4Address>()
      .filterNot { address -> address.isLoopbackAddress || address.isLinkLocalAddress }
      .toSet()

  private fun resolvedAddresses(service: NsdServiceInfo): List<InetAddress> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      service.hostAddresses
    } else {
      @Suppress("DEPRECATION") listOfNotNull(service.host)
    }

  private fun formatEndpoint(address: InetAddress, port: Int): String =
    "${address.hostAddress?.substringBefore('%')}:$port"

  private companion object {
    const val ADB_PAIRING_SERVICE_TYPE = "_adb-tls-pairing._tcp"
    const val ADB_CONNECT_SERVICE_TYPE = "_adb-tls-connect._tcp"
    const val NSD_DISCOVERY_WINDOW_MS = 1_500L
    const val NSD_STOP_TIMEOUT_MS = 1_000L
    const val NSD_RESOLVE_TIMEOUT_MS = 3_000L
  }
}

/** Performs only local endpoint discovery, pairing, connection, and device identity verification. */
internal class SelfAdbPairingEngine(
  private val runner: TermuxCommandRunner,
  private val endpointDiscovery: SelfAdbEndpointDiscovery,
  private val expectedFingerprint: String,
) {
  suspend fun findVerifiedConnection(): SelfAdbConnectionResult? {
    verifiedConnectedDevice()?.let { return it }
    repeat(CONNECT_DISCOVERY_ATTEMPTS) {
      for (endpoint in endpointDiscovery.discoverConnectEndpoints()) {
        runner.run("adb connect ${endpoint.shellSingleQuote()}", timeoutMs = DISCOVERY_TIMEOUT_MS)
        verifySerial(endpoint)?.let { return it }
      }
      if (it < CONNECT_DISCOVERY_ATTEMPTS - 1) delay(DISCOVERY_DELAY_MS)
    }
    return null
  }

  suspend fun pairWithCode(pairingCode: String): SelfAdbConnectionResult {
    if (!PAIRING_CODE.matches(pairingCode)) {
      return SelfAdbConnectionResult(
        succeeded = false,
        message = "The pairing code must contain exactly six digits.",
      )
    }

    val pairingEndpoint =
      pollForPairingEndpoint()
        ?: return SelfAdbConnectionResult(
          succeeded = false,
          message =
            "Jarvis could not find this phone's temporary pairing endpoint. Keep the Pair device " +
              "with pairing code screen open and try again.",
        )
    val pairResult =
      runner.run(
        "printf '%s\\n' $pairingCode | adb pair ${pairingEndpoint.shellSingleQuote()}",
        timeoutMs = PAIR_COMMAND_TIMEOUT_MS,
      )
    if (!pairResult.succeeded) {
      return SelfAdbConnectionResult(
        succeeded = false,
        message =
          pairResult.stderr.trim().ifBlank {
            pairResult.stdout.trim().ifBlank { "ADB rejected the temporary pairing code." }
          },
      )
    }

    repeat(CONNECT_AFTER_PAIR_ATTEMPTS) {
      findVerifiedConnection()?.let { return it }
      delay(DISCOVERY_DELAY_MS)
    }
    return SelfAdbConnectionResult(
      succeeded = false,
      message =
        "Pairing succeeded, but Jarvis could not verify a connection to this same phone. Keep " +
          "Wireless debugging enabled and retry the approved ADB action.",
    )
  }

  private suspend fun verifiedConnectedDevice(): SelfAdbConnectionResult? {
    val devices = runner.run("adb devices", timeoutMs = DISCOVERY_TIMEOUT_MS)
    if (!devices.succeeded) return null
    for (serial in SelfAdbEndpointParser.parseConnectedSerials(devices.stdout)) {
      verifySerial(serial)?.let { return it }
    }
    return null
  }

  private suspend fun verifySerial(serial: String): SelfAdbConnectionResult? {
    if (!SAFE_ADB_SERIAL.matches(serial)) return null
    val fingerprint =
      runner.run(
        "adb -s ${serial.shellSingleQuote()} shell getprop ro.build.fingerprint",
        timeoutMs = DISCOVERY_TIMEOUT_MS,
      )
    if (!fingerprint.succeeded || fingerprint.stdout.trim() != expectedFingerprint) return null
    return SelfAdbConnectionResult(
      succeeded = true,
      serial = serial,
      message = "Jarvis verified a wireless ADB connection to this phone.",
    )
  }

  private suspend fun pollForPairingEndpoint(): String? {
    repeat(PAIR_DISCOVERY_ATTEMPTS) {
      val endpoints = endpointDiscovery.discoverPairingEndpoints()
      if (endpoints.size == 1) return endpoints.single()
      if (endpoints.size > 1) return null
      delay(DISCOVERY_DELAY_MS)
    }
    return null
  }

  private companion object {
    val PAIRING_CODE = Regex("^[0-9]{6}$")
    val SAFE_ADB_SERIAL = Regex("^[A-Za-z0-9._:%\\-\\[\\]]+$")
    const val DISCOVERY_TIMEOUT_MS = 15_000L
    const val PAIR_COMMAND_TIMEOUT_MS = 30_000L
    const val DISCOVERY_DELAY_MS = 750L
    const val PAIR_DISCOVERY_ATTEMPTS = 16
    const val CONNECT_DISCOVERY_ATTEMPTS = 2
    const val CONNECT_AFTER_PAIR_ATTEMPTS = 8
  }
}

internal object SelfAdbEndpointParser {
  fun parseConnectedSerials(output: String): List<String> =
    output
      .lineSequence()
      .map(String::trim)
      .filter { line -> line.isNotBlank() && !line.startsWith("List of devices") }
      .mapNotNull { line ->
        val columns = line.split(Regex("\\s+"))
        columns.firstOrNull()?.takeIf { columns.getOrNull(1) == "device" }
      }
      .filter { serial -> SAFE_SERIAL.matches(serial) }
      .toList()

  private val SAFE_SERIAL = Regex("^[A-Za-z0-9._:%\\-\\[\\]]+$")
}

internal object SelfAdbPairingRequestRegistry {
  private val pending = ConcurrentHashMap<String, CompletableDeferred<SelfAdbConnectionResult>>()

  fun register(requestId: String): CompletableDeferred<SelfAdbConnectionResult> =
    CompletableDeferred<SelfAdbConnectionResult>().also { deferred -> pending[requestId] = deferred }

  fun complete(requestId: String, result: SelfAdbConnectionResult) {
    pending.remove(requestId)?.complete(result)
  }

  fun unregister(requestId: String) {
    pending.remove(requestId)?.cancel()
  }
}

private fun String.shellSingleQuote(): String = "'" + replace("'", "'\\''") + "'"
