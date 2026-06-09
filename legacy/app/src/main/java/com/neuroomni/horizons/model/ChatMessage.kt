package com.neuroomni.horizons.model

/** Who authored a chat line. */
enum class ChatRole { User, Assistant }

data class ChatMessage(
    val role: ChatRole,
    val text: String,
)
