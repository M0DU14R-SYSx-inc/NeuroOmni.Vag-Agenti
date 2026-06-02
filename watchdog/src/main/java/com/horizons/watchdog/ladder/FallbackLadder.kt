package com.horizons.watchdog.ladder

import com.horizons.shared.ipc.Placement

class FallbackLadder {
    fun next(current: Placement, reason: String): Placement = when (current) {
        Placement.NPU -> Placement.GPU
        Placement.GPU -> Placement.CPU
        Placement.CPU -> Placement.CLOUD_FAILOVER
        Placement.CLOUD_FAILOVER -> Placement.CLOUD_FAILOVER
    }
}
