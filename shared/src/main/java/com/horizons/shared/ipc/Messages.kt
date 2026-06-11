package com.horizons.shared.ipc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface WsMessage { val ts: Long }

@Serializable
@SerialName("hello")
data class Hello(val role: String, val pid: Int, override val ts: Long) : WsMessage

@Serializable
@SerialName("heartbeat")
data class Heartbeat(val seq: Long, override val ts: Long) : WsMessage

@Serializable
@SerialName("telemetry")
data class Telemetry(
    val npuTempC: Float?,
    val gpuTempC: Float?,
    val cpuTempC: Float?,
    val tokensPerSec: Float?,
    val memUsedMb: Int?,
    override val ts: Long
) : WsMessage

@Serializable
@SerialName("failure")
data class FailureFlag(val type: FailureType, val note: String?, override val ts: Long) : WsMessage

@Serializable
enum class FailureType {
    CONTEXT_DEGRADATION,
    SPECIFICATION_DRIFT,
    SYCOPHANTIC_CONFIRMATION,
    SILENT_FAILURE,
    TOOL_SELECTION_ERROR,
    CASCADING_FAILURE,
    THERMAL_THROTTLE,
    NPU_STALL,
    NATIVE_CRASH
}

@Serializable
@SerialName("cmd_hotswap")
data class HotSwap(val target: Placement, override val ts: Long) : WsMessage

@Serializable
enum class Placement { NPU, GPU, CPU, CLOUD_FAILOVER }

@Serializable
@SerialName("cmd_restart")
data class RestartSession(val reason: String, override val ts: Long) : WsMessage

@Serializable
@SerialName("image_ref")
data class ImageRef(val fileUri: String, val purpose: String, override val ts: Long) : WsMessage

// ─────────────────────────────────────────────────────────────────────────────
// M2.3 — Horizons ⇄ Watchdog request/response protocol.
//
// The types above are the supervisory channel (telemetry, fallback control).
// The types below are the request/response channel: Horizons is the sole client,
// Watchdog the server. Every request carries a `requestId` so streamed/async
// responses can be correlated over the single WS connection. All implement
// WsMessage and round-trip via WsContract.JSON.
// ─────────────────────────────────────────────────────────────────────────────

// ── Inbound: Horizons → Watchdog ────────────────────────────────────────────
// (Hello, above, is the handshake — Watchdog answers with Welcome.)

@Serializable
@SerialName("chat_request")
data class ChatRequest(
    val requestId: String,
    val prompt: String,
    val backendId: String? = null,
    val systemPrompt: String? = null,
    val imageUri: String? = null,
    val stream: Boolean = true,
    override val ts: Long
) : WsMessage

@Serializable
@SerialName("key_get")
data class KeyGet(val requestId: String, val key: String, override val ts: Long) : WsMessage

@Serializable
@SerialName("key_set")
data class KeySet(val requestId: String, val key: String, val value: String, override val ts: Long) : WsMessage

@Serializable
@SerialName("download_request")
data class DownloadRequest(val requestId: String, val modelId: String, override val ts: Long) : WsMessage

@Serializable
@SerialName("telemetry_ping")
data class TelemetryPing(val seq: Long, override val ts: Long) : WsMessage

@Serializable
@SerialName("shutdown")
data class Shutdown(val reason: String? = null, override val ts: Long) : WsMessage

// ── Outbound: Watchdog → Horizons ───────────────────────────────────────────

@Serializable
@SerialName("welcome")
data class Welcome(val version: Int, val watchdogPid: Int, override val ts: Long) : WsMessage

@Serializable
@SerialName("chat_stream_token")
data class ChatStreamToken(val requestId: String, val token: String, override val ts: Long) : WsMessage

@Serializable
@SerialName("chat_complete")
data class ChatComplete(
    val requestId: String,
    val fullText: String? = null,
    val finishReason: String? = null,
    override val ts: Long
) : WsMessage

@Serializable
@SerialName("chat_error")
data class ChatError(val requestId: String, val message: String, override val ts: Long) : WsMessage

@Serializable
@SerialName("key_value")
data class KeyValue(val requestId: String, val key: String, val value: String? = null, override val ts: Long) : WsMessage

@Serializable
@SerialName("download_progress")
data class DownloadProgress(
    val requestId: String,
    val modelId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    override val ts: Long
) : WsMessage

@Serializable
@SerialName("download_complete")
data class DownloadComplete(val requestId: String, val modelId: String, val path: String, override val ts: Long) : WsMessage

@Serializable
@SerialName("download_error")
data class DownloadError(val requestId: String, val modelId: String, val message: String, override val ts: Long) : WsMessage

@Serializable
@SerialName("pong")
data class Pong(val seq: Long, override val ts: Long) : WsMessage
