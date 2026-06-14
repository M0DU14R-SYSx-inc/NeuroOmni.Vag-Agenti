package com.horizons.core.log

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Append-only JSONL interaction logger.
 *
 * Writes one JSON object per line to `<filesDir>/logs/YYYY-MM-DD.jsonl` (UTC day boundary).
 * All IO runs on [Dispatchers.IO]. A [Mutex] guards file writes so concurrent callers cannot
 * interleave bytes mid-line.
 *
 * Schema (per line):
 *   ts         ISO-8601 UTC timestamp (e.g. "2026-06-05T14:23:51.123Z")
 *   session_id String
 *   kind       "prompt" | "response" | "tool_call" | "error"
 *   backend    String?  (which model backend, e.g. "nexa", "onnx", "anthropic")
 *   model      String?  (specific model id)
 *   content    String?  (prompt text, response text, tool args+result, or error message)
 *   tokens     Int?     (response token count)
 *   latency_ms Long?    (response latency)
 *   error      String?  (stack trace for errors)
 */
class InteractionLogger(private val context: Context) {

    private val writeMutex = Mutex()

    private val dayFormatter: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private val tsFormatter: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun logsDir(): File {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun fileForToday(): File = File(logsDir(), "${dayFormatter.format(Date())}.jsonl")

    private fun nowIso(): String = tsFormatter.format(Date())

    suspend fun logPrompt(
        sessionId: String,
        backend: String,
        model: String?,
        content: String,
    ) {
        val obj = baseObject("prompt", sessionId).apply {
            put("backend", backend)
            put("model", model ?: JSONObject.NULL)
            put("content", content)
            put("tokens", JSONObject.NULL)
            put("latency_ms", JSONObject.NULL)
            put("error", JSONObject.NULL)
        }
        append(obj)
    }

    suspend fun logResponse(
        sessionId: String,
        backend: String,
        content: String,
        tokens: Int?,
        latencyMs: Long?,
        cacheCreationTokens: Int? = null,
        cacheReadTokens: Int? = null,
    ) {
        val obj = baseObject("response", sessionId).apply {
            put("backend", backend)
            put("model", JSONObject.NULL)
            put("content", content)
            put("tokens", tokens ?: JSONObject.NULL)
            put("latency_ms", latencyMs ?: JSONObject.NULL)
            put("error", JSONObject.NULL)
            put("cache_creation_tokens", cacheCreationTokens ?: JSONObject.NULL)
            put("cache_read_tokens", cacheReadTokens ?: JSONObject.NULL)
        }
        append(obj)
    }

    suspend fun logToolCall(
        sessionId: String,
        toolId: String,
        args: String,
        result: String?,
    ) {
        val payload = JSONObject().apply {
            put("tool_id", toolId)
            put("args", args)
            put("result", result ?: JSONObject.NULL)
        }
        val obj = baseObject("tool_call", sessionId).apply {
            put("backend", JSONObject.NULL)
            put("model", JSONObject.NULL)
            put("content", payload.toString())
            put("tokens", JSONObject.NULL)
            put("latency_ms", JSONObject.NULL)
            put("error", JSONObject.NULL)
        }
        append(obj)
    }

    suspend fun logError(
        sessionId: String,
        backend: String?,
        error: Throwable,
    ) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val obj = baseObject("error", sessionId).apply {
            put("backend", backend ?: JSONObject.NULL)
            put("model", JSONObject.NULL)
            put("content", error.message ?: error.javaClass.simpleName)
            put("tokens", JSONObject.NULL)
            put("latency_ms", JSONObject.NULL)
            put("error", sw.toString())
        }
        append(obj)
    }

    private fun baseObject(kind: String, sessionId: String): JSONObject = JSONObject().apply {
        put("ts", nowIso())
        put("session_id", sessionId)
        put("kind", kind)
    }

    private suspend fun append(obj: JSONObject) = withContext(Dispatchers.IO) {
        val line = obj.toString() + "\n"
        writeMutex.withLock {
            val file = fileForToday()
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            file.appendBytes(line.toByteArray(Charsets.UTF_8))
        }
    }

    /** Returns the last [n] lines from today's log file (oldest first), or empty if none. */
    suspend fun tail(n: Int = 50): List<String> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val file = fileForToday()
            if (!file.exists()) return@withLock emptyList()
            val all = file.readLines(Charsets.UTF_8)
            if (all.size <= n) all else all.subList(all.size - n, all.size)
        }
    }

    /** Deletes log files whose YYYY-MM-DD name is older than [days] days from now (UTC). */
    suspend fun purgeOlderThan(days: Int) = withContext(Dispatchers.IO) {
        require(days >= 0) { "days must be non-negative" }
        writeMutex.withLock {
            val cutoffMs = System.currentTimeMillis() - days.toLong() * 24L * 60L * 60L * 1000L
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }
            val dir = logsDir()
            dir.listFiles()?.forEach { f ->
                if (!f.isFile || !f.name.endsWith(".jsonl")) return@forEach
                val stem = f.name.removeSuffix(".jsonl")
                val parsed = try {
                    parser.parse(stem)
                } catch (_: Exception) {
                    null
                }
                if (parsed != null && parsed.time < cutoffMs) {
                    f.delete()
                }
            }
        }
    }
}
