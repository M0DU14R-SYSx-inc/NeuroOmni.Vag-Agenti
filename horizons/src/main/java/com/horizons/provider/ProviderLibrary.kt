package com.horizons.provider

import android.content.Context

class ProviderLibrary(private val context: Context) {
    fun load(): List<NamedBackend> {
        TODO("Phase 6: read persisted JSON list of named backends from app files dir.")
    }

    fun save(entries: List<NamedBackend>) {
        TODO("Phase 6: write JSON list back.")
    }

    fun failoverTarget(): NamedBackend? = load().firstOrNull { it.isFailoverTarget }
}
