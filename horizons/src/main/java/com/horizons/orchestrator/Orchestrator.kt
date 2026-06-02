package com.horizons.orchestrator

import kotlinx.coroutines.flow.Flow

class Orchestrator(private val dispatcher: Dispatcher) {
    fun run(sttText: String, mode: SttMode, screenshotPath: String? = null, forcedToolId: String? = null): Flow<String> {
        TODO("Phase 5: route by mode: Dictation = passthrough; MetaPrompt = VLM.buildMetaPrompt then optional tool; BashCommand = VLM.buildMetaPrompt(BashCommand) then inject. Strict single-brain. On NPU stall flag THERMAL_THROTTLE/NPU_STALL to Watchdog and switch to failover-tool.")
    }
}
