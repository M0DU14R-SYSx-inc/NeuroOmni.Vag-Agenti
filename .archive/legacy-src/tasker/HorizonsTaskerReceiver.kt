package com.horizons.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.horizons.HorizonsApplication
import com.horizons.model.EdgeModelFactory
import com.horizons.model.EdgeModelImporter
import kotlinx.coroutines.launch
import java.io.File

/**
 * Exported BroadcastReceiver that lets Tasker / Termux / `adb shell am broadcast`
 * drive Horizons without UI taps.
 *
 * Actions (all under namespace com.horizons.action):
 *   RELOAD_ENGINE         — kicks off engine reload
 *   IMPORT_FOLDER         — copies the 14 model files from a SAF tree URI
 *                           extras: tree_uri (String, content://...)
 *   SEND_PROMPT           — runs prompt through the current engine, replies via result broadcast
 *                           extras: text (String)
 *   GET_STATUS            — returns engine state via result broadcast
 *
 * Optional extra on every action:
 *   output_file (String, absolute path) — append result to this file in addition
 *                                          to broadcasting it
 *
 * Every action also emits com.horizons.action.RESULT with extras:
 *   source_action  — the action that produced this result
 *   body           — text payload
 *
 * Tasker listens via Profile > Event > System > Intent Received, action
 * `com.horizons.action.RESULT`.
 */
class HorizonsTaskerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as HorizonsApplication
        val action = intent.action ?: return
        val outputFile = intent.getStringExtra(EXTRA_OUTPUT_FILE)

        fun emit(body: String) {
            Log.i(TAG, "$action -> $body")
            context.sendBroadcast(
                Intent(ACTION_RESULT)
                    .putExtra(EXTRA_SOURCE_ACTION, action)
                    .putExtra(EXTRA_BODY, body)
            )
            outputFile?.let { path ->
                runCatching { File(path).appendText(body + "\n") }
                    .onFailure { Log.e(TAG, "output_file write failed: ${it.message}") }
            }
        }

        when (action) {
            ACTION_RELOAD_ENGINE -> {
                app.reloadEngineAsync()
                emit("reload triggered (poll GET_STATUS for ready state)")
            }

            ACTION_IMPORT_FOLDER -> {
                val treeStr = intent.getStringExtra(EXTRA_TREE_URI)
                if (treeStr.isNullOrBlank()) {
                    emit("error: missing extra tree_uri")
                    return
                }
                val pending = goAsync()
                app.scope.launch {
                    runCatching { EdgeModelImporter.importFromTree(context, Uri.parse(treeStr)) }
                        .fold(
                            { result ->
                                result.fold(
                                    { r ->
                                        val total = r.copied.size + r.missing.size
                                        emit("import ok: ${r.copied.size}/$total staged, ${r.missing.size} missing, candidates=${r.candidates}")
                                        if (r.missing.isEmpty()) {
                                            app.reloadEngine()
                                            emit("engine reloaded: ${app.engine().backendTag} (status=${app.engineStatus.value})")
                                        }
                                    },
                                    { emit("import error: ${it.javaClass.simpleName}: ${it.message}") }
                                )
                            },
                            { emit("import threw: ${it.message}") }
                        )
                    pending.finish()
                }
            }

            ACTION_SEND_PROMPT -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                if (text.isNullOrBlank()) {
                    emit("error: missing extra text")
                    return
                }
                val pending = goAsync()
                app.scope.launch {
                    val sb = StringBuilder()
                    runCatching {
                        app.engine().generateStream(text).collect { sb.append(it) }
                    }.onFailure { sb.append("\n[error] ${it.javaClass.simpleName}: ${it.message}") }
                    emit("prompt reply (${app.engine().backendTag}): ${sb.toString().take(4096)}")
                    pending.finish()
                }
            }

            ACTION_GET_STATUS -> {
                val staged = EdgeModelFactory.installedModelDir(context) != null
                emit(
                    "backend=${app.engine().backendTag} " +
                        "status=${app.engineStatus.value} " +
                        "error=${app.engineError.value ?: "-"} " +
                        "staged=$staged"
                )
            }

            else -> Log.w(TAG, "unknown action: $action")
        }
    }

    companion object {
        private const val TAG = "HorizonsTasker"
        const val ACTION_RELOAD_ENGINE = "com.horizons.action.RELOAD_ENGINE"
        const val ACTION_IMPORT_FOLDER = "com.horizons.action.IMPORT_FOLDER"
        const val ACTION_SEND_PROMPT = "com.horizons.action.SEND_PROMPT"
        const val ACTION_GET_STATUS = "com.horizons.action.GET_STATUS"
        const val ACTION_RESULT = "com.horizons.action.RESULT"
        const val EXTRA_TREE_URI = "tree_uri"
        const val EXTRA_TEXT = "text"
        const val EXTRA_OUTPUT_FILE = "output_file"
        const val EXTRA_SOURCE_ACTION = "source_action"
        const val EXTRA_BODY = "body"
    }
}
