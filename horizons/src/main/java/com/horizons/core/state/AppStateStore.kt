package com.horizons.core.state

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for persisted app state — credentials, toggles,
 * picker selections.
 *
 * Fixes the phantom-save bug from the old RouterPanel.KeyRow: composables
 * collect [snapshot] and write via [put]; they NEVER hold a local
 * `remember { mutableStateOf(stored) }` because that diverges from the
 * actual store on the next read.
 *
 * Backed by EncryptedSharedPreferences so credentials don't sit in plaintext.
 */
class AppStateStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _snapshot = MutableStateFlow(loadAll())
    val snapshot: StateFlow<Map<String, String>> = _snapshot.asStateFlow()

    fun get(key: String): String? = _snapshot.value[key]
    fun has(key: String): Boolean = !get(key).isNullOrBlank()

    fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        _snapshot.value = _snapshot.value + (key to value)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
        _snapshot.value = _snapshot.value - key
    }

    private fun loadAll(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    companion object {
        private const val FILE = "horizons_app_state"

        // Well-known keys. Keep this list short and reviewed.
        const val KEY_NEXA_TOKEN = "nexa.token"
        const val KEY_PREFER_VOXSHERPA = "tts.prefer_system"
        const val KEY_LAST_SCREENSHOT = "screen.last_path"
    }
}
