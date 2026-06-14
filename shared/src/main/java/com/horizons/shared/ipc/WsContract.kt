package com.horizons.shared.ipc

import kotlinx.serialization.json.Json

object WsContract {
    const val VERSION = 1
    const val DEFAULT_PORT = 47821
    const val HEARTBEAT_INTERVAL_MS = 5_000L
    const val HEARTBEAT_MISS_LIMIT = 3
    const val IMAGE_TRANSFER_DIR = "horizons_ipc_images"

    /**
     * Canonical codec for the WsMessage hierarchy. Both :horizons and :watchdog
     * encode/decode through this so the polymorphic `type` discriminator and
     * default handling stay identical on both ends of the socket.
     */
    val JSON: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serialize any WsMessage to a wire string (polymorphic on the sealed type). */
    fun encode(message: WsMessage): String = JSON.encodeToString(WsMessage.serializer(), message)

    /** Parse a wire string back to a WsMessage. */
    fun decode(text: String): WsMessage = JSON.decodeFromString(WsMessage.serializer(), text)
}
