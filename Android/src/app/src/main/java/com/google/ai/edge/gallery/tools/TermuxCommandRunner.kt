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

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Public Termux RUN_COMMAND contract supported by Termux 0.109 and newer. */
internal object TermuxRunCommandContract {
  const val PACKAGE_NAME = "com.termux"
  const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
  const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
  const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
  const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
  const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
  const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
  const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
  const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
  const val EXTRA_COMMAND_DESCRIPTION = "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION"
  const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
  const val EXTRA_PLUGIN_RESULT_BUNDLE = "result"
  const val EXTRA_STDOUT = "stdout"
  const val EXTRA_STDOUT_ORIGINAL_LENGTH = "stdout_original_length"
  const val EXTRA_STDERR = "stderr"
  const val EXTRA_STDERR_ORIGINAL_LENGTH = "stderr_original_length"
  const val EXTRA_EXIT_CODE = "exitCode"
  const val EXTRA_ERROR_CODE = "err"
  const val EXTRA_ERROR_MESSAGE = "errmsg"
  const val EXTRA_JARVIS_EXECUTION_ID =
    "com.google.ai.edge.gallery.extra.TERMUX_EXECUTION_ID"
  const val BASH_PATH = "$PREFIX/bin/bash"
  const val HOME_PATH = "~/"
}

data class TermuxEnvironmentStatus(
  val installed: Boolean,
  val versionName: String,
  val runCommandPermissionGranted: Boolean,
)

interface TermuxEnvironment {
  fun status(): TermuxEnvironmentStatus
}

class AndroidTermuxEnvironment(private val context: Context) : TermuxEnvironment {
  override fun status(): TermuxEnvironmentStatus {
    val packageInfo =
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.packageManager.getPackageInfo(
            TermuxRunCommandContract.PACKAGE_NAME,
            PackageManager.PackageInfoFlags.of(0),
          )
        } else {
          @Suppress("DEPRECATION")
          context.packageManager.getPackageInfo(TermuxRunCommandContract.PACKAGE_NAME, 0)
        }
      } catch (_: PackageManager.NameNotFoundException) {
        null
      }
    return TermuxEnvironmentStatus(
      installed = packageInfo != null,
      versionName = packageInfo?.versionName.orEmpty(),
      runCommandPermissionGranted =
        ContextCompat.checkSelfPermission(
          context,
          TermuxRunCommandContract.RUN_COMMAND_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED,
    )
  }
}

data class TermuxCommandResult(
  val exitCode: Int?,
  val stdout: String,
  val stderr: String,
  val internalErrorCode: Int,
  val internalErrorMessage: String,
  val stdoutTruncated: Boolean,
  val stderrTruncated: Boolean,
  val timedOut: Boolean = false,
) {
  val succeeded: Boolean
    get() = !timedOut && internalErrorCode == Activity.RESULT_OK && exitCode == 0

  companion object {
    internal fun fromBundle(bundle: Bundle): TermuxCommandResult {
      val stdout = bundle.getString(TermuxRunCommandContract.EXTRA_STDOUT).orEmpty()
      val stderr = bundle.getString(TermuxRunCommandContract.EXTRA_STDERR).orEmpty()
      val stdoutOriginalLength =
        bundle.getString(TermuxRunCommandContract.EXTRA_STDOUT_ORIGINAL_LENGTH)?.toIntOrNull()
          ?: stdout.length
      val stderrOriginalLength =
        bundle.getString(TermuxRunCommandContract.EXTRA_STDERR_ORIGINAL_LENGTH)?.toIntOrNull()
          ?: stderr.length
      return TermuxCommandResult(
        exitCode =
          if (bundle.containsKey(TermuxRunCommandContract.EXTRA_EXIT_CODE)) {
            bundle.getInt(TermuxRunCommandContract.EXTRA_EXIT_CODE)
          } else {
            null
          },
        stdout = stdout.limitForModel(),
        stderr = stderr.limitForModel(),
        internalErrorCode =
          bundle.getInt(TermuxRunCommandContract.EXTRA_ERROR_CODE, Activity.RESULT_CANCELED),
        internalErrorMessage =
          bundle.getString(TermuxRunCommandContract.EXTRA_ERROR_MESSAGE).orEmpty().limitForModel(),
        stdoutTruncated = stdoutOriginalLength > stdout.length || stdout.length > MAX_MODEL_CHARS,
        stderrTruncated = stderrOriginalLength > stderr.length || stderr.length > MAX_MODEL_CHARS,
      )
    }

    internal fun timedOut(): TermuxCommandResult =
      TermuxCommandResult(
        exitCode = null,
        stdout = "",
        stderr = "",
        internalErrorCode = Activity.RESULT_CANCELED,
        internalErrorMessage =
          "Timed out waiting for Termux. Confirm allow-external-apps=true in Termux and try again.",
        stdoutTruncated = false,
        stderrTruncated = false,
        timedOut = true,
      )

    internal fun launchFailed(message: String): TermuxCommandResult =
      TermuxCommandResult(
        exitCode = null,
        stdout = "",
        stderr = "",
        internalErrorCode = Activity.RESULT_CANCELED,
        internalErrorMessage = message,
        stdoutTruncated = false,
        stderrTruncated = false,
      )
  }
}

interface TermuxCommandRunner {
  suspend fun run(command: String, timeoutMs: Long = DEFAULT_TERMUX_TIMEOUT_MS): TermuxCommandResult
}

class AndroidTermuxCommandRunner(context: Context) : TermuxCommandRunner {
  private val launchContext = context
  private val appContext = context.applicationContext

  override suspend fun run(command: String, timeoutMs: Long): TermuxCommandResult {
    val executionId = TermuxCommandResultRegistry.nextExecutionId()
    val tmuxCommand = buildTmuxCommand(command = command, executionId = executionId)
    val deferred = TermuxCommandResultRegistry.register(executionId)
    val resultIntent =
      Intent(appContext, TermuxCommandResultService::class.java).putExtra(
        TermuxRunCommandContract.EXTRA_JARVIS_EXECUTION_ID,
        executionId,
      )
    val resultPendingIntent =
      PendingIntent.getService(
        appContext,
        executionId,
        resultIntent,
        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE,
      )
    val commandIntent =
      Intent(TermuxRunCommandContract.ACTION_RUN_COMMAND).apply {
        component =
          ComponentName(
            TermuxRunCommandContract.PACKAGE_NAME,
            TermuxRunCommandContract.RUN_COMMAND_SERVICE,
          )
        putExtra(TermuxRunCommandContract.EXTRA_COMMAND_PATH, TermuxRunCommandContract.BASH_PATH)
        putExtra(TermuxRunCommandContract.EXTRA_ARGUMENTS, arrayOf("-lc", tmuxCommand))
        putExtra(TermuxRunCommandContract.EXTRA_WORKDIR, TermuxRunCommandContract.HOME_PATH)
        putExtra(TermuxRunCommandContract.EXTRA_BACKGROUND, true)
        putExtra(TermuxRunCommandContract.EXTRA_COMMAND_LABEL, "Android Jarvis")
        putExtra(
          TermuxRunCommandContract.EXTRA_COMMAND_DESCRIPTION,
          "Command approved once in Android Jarvis.",
        )
        putExtra(TermuxRunCommandContract.EXTRA_PENDING_INTENT, resultPendingIntent)
      }

    return try {
      launchContext.startService(commandIntent)
        ?: throw IllegalStateException("Termux RunCommandService was not available.")
      val resultBundle = withTimeout(timeoutMs) { deferred.await() }
      TermuxCommandResult.fromBundle(resultBundle)
    } catch (_: TimeoutCancellationException) {
      TermuxCommandResult.timedOut()
    } catch (error: IllegalStateException) {
      val message = error.message.orEmpty()
      TermuxCommandResult.launchFailed(
        if ("background" in message.lowercase()) {
          "Android could not start Termux while it was inactive. Open Termux once, return to " +
            "Jarvis, and approve the command again."
        } else {
          message.ifBlank { "Termux RunCommandService could not start." }
        }
      )
    } finally {
      TermuxCommandResultRegistry.unregister(executionId)
    }
  }
}

/**
 * Wraps an approved command in a persistent tmux session while preserving its output and exit code.
 *
 * The outer shell only prepares the isolated command files and waits for completion. The approved
 * command itself always runs in a tmux window inside [TERMUX_TMUX_SESSION].
 */
internal fun buildTmuxCommand(command: String, executionId: Int): String {
  val quotedCommand = command.shellSingleQuote()
  return """
    set -u
    if ! command -v tmux >/dev/null 2>&1; then
      printf '%s\n' 'tmux is not installed in Termux. Run: pkg install tmux' >&2
      exit 127
    fi
    jarvis_dir="${'$'}PREFIX/tmp/android-jarvis"
    mkdir -p "${'$'}jarvis_dir"
    jarvis_base="${'$'}jarvis_dir/run-$executionId-${'$'}${'$'}"
    jarvis_script="${'$'}jarvis_base.sh"
    jarvis_stdout="${'$'}jarvis_base.out"
    jarvis_stderr="${'$'}jarvis_base.err"
    jarvis_exit="${'$'}jarvis_base.exit"
    cleanup_jarvis_run() {
      rm -f "${'$'}jarvis_script" "${'$'}jarvis_stdout" "${'$'}jarvis_stderr" "${'$'}jarvis_exit"
    }
    trap cleanup_jarvis_run EXIT
    printf '%s\n' $quotedCommand > "${'$'}jarvis_script"
    chmod 700 "${'$'}jarvis_script"
    tmux has-session -t '$TERMUX_TMUX_SESSION' 2>/dev/null || \
      tmux new-session -d -s '$TERMUX_TMUX_SESSION' -c "${'$'}HOME" -n home
    tmux new-window -d -t '$TERMUX_TMUX_SESSION' -n 'jarvis-$executionId' \
      "bash \"${'$'}jarvis_script\" >\"${'$'}jarvis_stdout\" 2>\"${'$'}jarvis_stderr\"; printf '%s' \${'$'}? >\"${'$'}jarvis_exit\""
    while [ ! -f "${'$'}jarvis_exit" ]; do sleep 0.05; done
    cat "${'$'}jarvis_stdout"
    cat "${'$'}jarvis_stderr" >&2
    jarvis_status="${'$'}(cat "${'$'}jarvis_exit")"
    exit "${'$'}jarvis_status"
  """.trimIndent()
}

private fun String.shellSingleQuote(): String = "'" + replace("'", "'\\''") + "'"

internal object TermuxCommandResultRegistry {
  private val nextId = AtomicInteger(10_000)
  private val pending = ConcurrentHashMap<Int, CompletableDeferred<Bundle>>()

  fun nextExecutionId(): Int = nextId.incrementAndGet()

  fun register(executionId: Int): CompletableDeferred<Bundle> =
    CompletableDeferred<Bundle>().also { deferred -> pending[executionId] = deferred }

  fun complete(executionId: Int, result: Bundle) {
    pending.remove(executionId)?.complete(result)
  }

  fun unregister(executionId: Int) {
    pending.remove(executionId)?.cancel()
  }
}

private const val PREFIX = "\$PREFIX"
const val DEFAULT_TERMUX_TIMEOUT_MS = 60_000L
internal const val TERMUX_TMUX_SESSION = "android-jarvis"
private const val MAX_MODEL_CHARS = 16_000

private fun String.limitForModel(): String {
  if (length <= MAX_MODEL_CHARS) return this
  val retainedAtEachEnd = (MAX_MODEL_CHARS - TRUNCATION_MARKER.length) / 2
  return take(retainedAtEachEnd) + TRUNCATION_MARKER + takeLast(retainedAtEachEnd)
}

private const val TRUNCATION_MARKER = "\n... output truncated by Android Jarvis ...\n"
