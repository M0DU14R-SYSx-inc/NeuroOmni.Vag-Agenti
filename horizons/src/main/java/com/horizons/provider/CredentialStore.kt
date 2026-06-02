package com.horizons.provider

import android.content.Context

class CredentialStore(private val context: Context) {
    fun put(key: String, value: String) {
        TODO("Phase 6: Keystore-backed EncryptedSharedPreferences write.")
    }
    fun get(key: String): String? {
        TODO("Phase 6: Keystore-backed read.")
    }
    fun delete(key: String) {
        TODO("Phase 6: remove key.")
    }
    fun keys(): List<String> {
        TODO("Phase 6: list all stored credential keys.")
    }
}
