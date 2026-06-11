package com.horizons.watchdog.telemetry

import com.horizons.shared.ipc.FailureType

class DeviceTelemetry {
    private val failureCounts = mutableMapOf<FailureType, Int>()

    fun record(type: FailureType) {
        failureCounts[type] = (failureCounts[type] ?: 0) + 1
    }

    fun readThermals(): Thermals {
        // Phase 9: read /sys/class/thermal/thermal_zone*/{type,temp}; map NPU/GPU/CPU zones for Snapdragon 8 Elite.
        // Target: ~90 t/s, ceiling ~38 C.
        return Thermals(null, null, null)
    }

    data class Thermals(val npuC: Float?, val gpuC: Float?, val cpuC: Float?)
}
