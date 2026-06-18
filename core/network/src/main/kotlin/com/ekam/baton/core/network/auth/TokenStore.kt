package com.ekam.baton.core.network.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "baton_token_store",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(agentId: String, accessToken: String, refreshToken: String?, expiresAt: Long) {
        sharedPrefs.edit().apply {
            putString("${agentId}_access_token", accessToken)
            if (refreshToken != null) {
                putString("${agentId}_refresh_token", refreshToken)
            } else {
                remove("${agentId}_refresh_token")
            }
            putLong("${agentId}_expires_at", expiresAt)
            apply()
        }
    }

    fun getAccessToken(agentId: String): String? {
        return sharedPrefs.getString("${agentId}_access_token", null)
    }

    fun getRefreshToken(agentId: String): String? {
        return sharedPrefs.getString("${agentId}_refresh_token", null)
    }

    fun isTokenValid(agentId: String): Boolean {
        val expiresAt = sharedPrefs.getLong("${agentId}_expires_at", 0L)
        if (expiresAt == 0L) return false
        // 60-second buffer
        return expiresAt > (System.currentTimeMillis() + 60_000)
    }

    fun clearTokens(agentId: String) {
        sharedPrefs.edit().apply {
            remove("${agentId}_access_token")
            remove("${agentId}_refresh_token")
            remove("${agentId}_expires_at")
            apply()
        }
    }
}
