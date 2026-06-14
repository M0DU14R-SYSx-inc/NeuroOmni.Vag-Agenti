package com.horizons.provider

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * Persisted list of user-saved named backends. JSON file in app filesDir.
 * Used by Router panel CRUD UI + Orchestrator failover selection.
 */
class ProviderLibrary(private val context: Context) {
    private val file: File by lazy {
        File(context.filesDir, "provider_library.json").apply {
            parentFile?.mkdirs()
        }
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun load(): List<NamedBackend> = runCatching {
        if (!file.exists()) emptyList()
        else json.decodeFromString<List<NamedBackend>>(file.readText())
    }.getOrElse { emptyList() }

    fun save(entries: List<NamedBackend>) {
        runCatching { file.writeText(json.encodeToString(entries)) }
    }

    fun add(entry: NamedBackend) {
        save(load().filterNot { it.id == entry.id } + entry)
    }

    fun update(entry: NamedBackend) = add(entry) // upsert

    fun delete(id: String) {
        save(load().filterNot { it.id == id })
    }

    fun failoverTarget(): NamedBackend? = load().firstOrNull { it.isFailoverTarget }

    fun byId(id: String): NamedBackend? = load().firstOrNull { it.id == id }
}
