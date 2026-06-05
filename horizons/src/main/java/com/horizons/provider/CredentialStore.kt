package com.horizons.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed credential storage. Keys never leave the device.
 *
 * Known well-known keys (any string works, these are conventions):
 *   nexa.token       — Nexa hub coin (for `nexa pull` use, not SDK runtime)
 *   hf.token         — Hugging Face access token (for gated repos)
 *   openrouter.key   — OpenRouter API key (sk-or-v1-...)
 *   vertex.key       — GCP Vertex AI key
 *   anthropic.key    — Anthropic API key
 *   gemini.key       — AI Studio Gemini key
 */
class CredentialStore(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            STORE_NAME,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun get(key: String): String? = prefs.getString(key, null)

    fun delete(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun keys(): List<String> = prefs.all.keys.toList().sorted()

    fun has(key: String): Boolean = !prefs.getString(key, null).isNullOrBlank()

    private companion object { const val STORE_NAME = "horizons_creds" }
}
