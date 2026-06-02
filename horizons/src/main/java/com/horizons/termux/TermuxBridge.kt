package com.horizons.termux

import android.content.Context

class TermuxBridge(private val context: Context) {
    fun run(command: String): String {
        // Phase 7: send to Termux via com.termux.RUN_COMMAND intent (requires Termux permission grant).
        // Used by Bash-mode STT, dispatcher's CLI lane, and the on-device Terminal panel.
        TODO("Phase 7")
    }
}
