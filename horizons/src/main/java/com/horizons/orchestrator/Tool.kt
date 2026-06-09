package com.horizons.orchestrator

import kotlinx.coroutines.flow.Flow

interface Tool {
    val id: String
    val displayName: String
    fun run(prompt: String, imagePath: String? = null): Flow<String>
}
