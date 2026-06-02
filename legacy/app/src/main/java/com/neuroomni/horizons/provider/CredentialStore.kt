package com.neuroomni.horizons.provider

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed storage for provider endpoint configs (Security §12: "Anthropic API
 * key: Android Keystore", "No keys in build artifacts. No keys in logs.").
 *
 * API keys, base URLs, and model strings are persisted in an
 * [EncryptedSharedPreferences] whose master key is hardware-backed on the Razr's
 * Snapdragon 8 Elite. If the encrypted store can't be created (e.g. a stripped-down
 * emulator), it degrades to an in-memory map so the app still runs — keys just don't
 * survive a restart. Nothing here is ever written to logcat.
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.onFailure { Log.w(TAG, "EncryptedSharedPreferences unavailable; using in-memory store") }
        .getOrNull()

    /** Volatile fallback so the app is usable even when the encrypted store fails. */
    private val memory = mutableMapOf<String, String>()

    fun load(provider: ProviderId): EndpointConfig {
        val defaults = EndpointConfig.defaultFor(provider)
        return EndpointConfig(
            provider = provider,
            apiKey = get(key(provider, KEY_API), ""),
            baseUrl = get(key(provider, KEY_BASE_URL), defaults.baseUrl),
            modelString = get(key(provider, KEY_MODEL), defaults.modelString),
            maxTokens = get(key(provider, KEY_MAX_TOKENS), defaults.maxTokens.toString())
                .toIntOrNull() ?: defaults.maxTokens,
        )
    }

    fun save(config: EndpointConfig) {
        put(key(config.provider, KEY_API), config.apiKey)
        put(key(config.provider, KEY_BASE_URL), config.baseUrl)
        put(key(config.provider, KEY_MODEL), config.modelString)
        put(key(config.provider, KEY_MAX_TOKENS), config.maxTokens.toString())
    }

    /** The provider toggle Derek last selected (Spec §7 logs the active provider). */
    var activeProvider: ProviderId
        get() = ProviderId.fromName(get(KEY_ACTIVE, ProviderId.Edge.name))
        set(value) = put(KEY_ACTIVE, value.name)

    /**
     * The universal Nexa SDK key for the on-device OmniNeural runtime. Kept here in
     * the encrypted store (Architecture §12) rather than baked into the APK, so one
     * generic Nexa build works for everyone and the key never ships in an artifact.
     */
    var nexaToken: String
        get() = get(KEY_NEXA_TOKEN, "")
        set(value) = put(KEY_NEXA_TOKEN, value)

    private fun get(k: String, default: String): String =
        prefs?.getString(k, default) ?: memory[k] ?: default

    private fun put(k: String, v: String) {
        if (prefs != null) prefs.edit().putString(k, v).apply() else memory[k] = v
    }

    private fun key(provider: ProviderId, field: String) = "${provider.name}.$field"

    private companion object {
        const val TAG = "CredentialStore"
        const val PREFS_NAME = "horizons_provider_credentials"
        const val KEY_API = "apiKey"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_MODEL = "modelString"
        const val KEY_MAX_TOKENS = "maxTokens"
        const val KEY_ACTIVE = "activeProvider"
        const val KEY_NEXA_TOKEN = "nexaToken"
    }
}
