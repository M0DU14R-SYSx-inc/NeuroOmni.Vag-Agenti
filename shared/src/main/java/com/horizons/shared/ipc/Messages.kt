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
