package com.horizons.shared.ipc

object WsContract {
    const val VERSION = 1
    const val DEFAULT_PORT = 47821
    const val HEARTBEAT_INTERVAL_MS = 5_000L
    const val HEARTBEAT_MISS_LIMIT = 3
    const val IMAGE_TRANSFER_DIR = "horizons_ipc_images"
}
