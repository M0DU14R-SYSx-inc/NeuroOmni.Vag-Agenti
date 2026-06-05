package com.horizons.termux

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Outbound bridge into the Termux app via the `com.termux.RUN_COMMAND` intent
 * (see https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).
 *
 * `run(...)` fires a shell command at Termux's [RunCommandService], waits for the
 * result via a one-shot PendingIntent + programmatically-registered receiver,
 * and surfaces stdout/stderr/exit_code (or a Result.failure if Termux isn't
 * installed / the user hasn't allowed external apps in Termux properties).
 *
 * Used by Bash-mode STT, the dispatcher's CLI lane, and the on-device Terminal panel.
 */
class TermuxBridge(private val context: Context) {

    /**
     * Run [command] under `bash -c` inside Termux.
     *
     * @param command       the shell line (passed as `-c` arg to bash).
     * @param args          extra args appended after `-c <command>`. Rarely needed.
     * @param workdir       optional working directory (Termux-side absolute path).
     * @param timeoutMs     how long to wait for Termux to broadcast a result back.
     */
    suspend fun run(
        command: String,
        args: List<String> = emptyList(),
        workdir: String? = null,
        timeoutMs: Long = 60_000,
    ): Result<TermuxOutput> {
        val app = context.applicationContext
        val resultAction = "com.horizons.termux.RESULT." + UUID.randomUUID().toString()

        return try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context, intent: Intent) {
                            // Termux nests the actual result extras under a Bundle keyed
                            // "result" (top-level extras are the PendingIntent's own).
                            val bundle: Bundle = intent.getBundleExtra("result") ?: intent.extras ?: Bundle()
                            val stdout = bundle.getString("result_stdout") ?: ""
                            val stderr = bundle.getString("result_stderr") ?: ""
                            val exit = bundle.getInt("result_exit_code", -1)
                            val errCode = bundle.getInt("result_err", 0)
                            val errMsg = bundle.getString("result_errmsg")

                            runCatching { app.unregisterReceiver(this) }

                            if (cont.isActive) {
                                if (errCode != 0 && errMsg != null && stdout.isEmpty() && stderr.isEmpty()) {
                                    cont.resume(
                                        Result.failure(
                                            IllegalStateException("Termux reported err=$errCode: $errMsg")
                                        )
                                    )
                                } else {
                                    cont.resume(Result.success(TermuxOutput(stdout, stderr, exit)))
                                }
                            }
                        }
                    }

                    val filter = IntentFilter(resultAction)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        app.registerReceiver(receiver, filter)
                    }

                    cont.invokeOnCancellation {
                        runCatching { app.unregisterReceiver(receiver) }
                    }

                    // PendingIntent that Termux's RunCommandService will fire back.
                    // Must target our own package; use a unique action so PendingIntent
                    // identity is per-call (avoids FLAG_UPDATE_CURRENT collisions).
                    val callbackIntent = Intent(resultAction).setPackage(app.packageName)
                    val piFlags = PendingIntent.FLAG_ONE_SHOT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            PendingIntent.FLAG_MUTABLE else 0)
                    val pendingIntent = PendingIntent.getBroadcast(
                        app, 0, callbackIntent, piFlags
                    )

                    val runIntent = Intent().apply {
                        component = ComponentName(
                            "com.termux",
                            "com.termux.app.RunCommandService"
                        )
                        action = ACTION_RUN_COMMAND
                        putExtra(EXTRA_COMMAND_PATH, DEFAULT_SHELL)
                        val allArgs = arrayOf("-c", command) + args.toTypedArray()
                        putExtra(EXTRA_ARGUMENTS, allArgs)
                        workdir?.let { putExtra(EXTRA_WORKDIR, it) }
                        putExtra(EXTRA_BACKGROUND, true)
                        putExtra(EXTRA_SESSION_ACTION, "0") // start FG session, don't show
                        putExtra(EXTRA_RESULT_PENDING_INTENT, pendingIntent)
                    }

                    try {
                        // RunCommandService is a Service, not an Activity. Use startService
                        // (or startForegroundService on O+) — see Termux wiki.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            app.startForegroundService(runIntent)
                        } else {
                            app.startService(runIntent)
                        }
                    } catch (e: ActivityNotFoundException) {
                        runCatching { app.unregisterReceiver(receiver) }
                        if (cont.isActive) {
                            cont.resume(
                                Result.failure(
                                    IllegalStateException(
                                        "Termux is not installed (RunCommandService not found). " +
                                            "Install Termux from F-Droid and enable `allow-external-apps=true` " +
                                            "in ~/.termux/termux.properties.",
                                        e
                                    )
                                )
                            )
                        }
                    } catch (e: SecurityException) {
                        runCatching { app.unregisterReceiver(receiver) }
                        if (cont.isActive) {
                            cont.resume(
                                Result.failure(
                                    IllegalStateException(
                                        "Termux refused the RUN_COMMAND intent. Set " +
                                            "`allow-external-apps=true` in ~/.termux/termux.properties " +
                                            "and grant com.termux.permission.RUN_COMMAND to this app.",
                                        e
                                    )
                                )
                            )
                        }
                    } catch (e: IllegalStateException) {
                        // e.g. background-start restrictions on O+.
                        runCatching { app.unregisterReceiver(receiver) }
                        if (cont.isActive) {
                            cont.resume(
                                Result.failure(
                                    IllegalStateException(
                                        "Failed to start Termux RunCommandService: ${e.message}", e
                                    )
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Termux dispatch failed", e)
                        runCatching { app.unregisterReceiver(receiver) }
                        if (cont.isActive) cont.resume(Result.failure(e))
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(
                IllegalStateException("Termux command timed out after ${timeoutMs}ms: $command", e)
            )
        }
    }

    companion object {
        private const val TAG = "TermuxBridge"
        const val DEFAULT_SHELL = "/data/data/com.termux/files/usr/bin/bash"

        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        private const val EXTRA_RESULT_PENDING_INTENT = "com.termux.RUN_COMMAND_RESULT_PENDING_INTENT"
    }
}

data class TermuxOutput(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)
