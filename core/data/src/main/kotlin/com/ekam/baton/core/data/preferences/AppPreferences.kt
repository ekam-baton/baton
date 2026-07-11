package com.ekam.baton.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "baton_preferences")

class AppPreferences constructor(
    private val context: Context
) {
    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = longPreferencesKey("accent_color")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val AUTO_EXTRACT_FACTS = booleanPreferencesKey("auto_extract_facts")
        val AUTO_GENERATE_EPISODES = booleanPreferencesKey("auto_generate_episodes")
        val MEMORY_RETENTION_DAYS = intPreferencesKey("memory_retention_days")
        val DEFAULT_TOKEN_LIMIT = intPreferencesKey("default_token_limit")
        val ENABLE_HAPTIC_FEEDBACK = booleanPreferencesKey("enable_haptic_feedback")
        val TUNNEL_STATUS_MAP = stringPreferencesKey("tunnel_status_map")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val IS_REGISTERED = booleanPreferencesKey("is_registered")
        val TRIAL_START_TIME = longPreferencesKey("trial_start_time")
        val IS_PREMIUM_UNLOCKED = booleanPreferencesKey("is_premium_unlocked")
        val KEYBOARD_SHORTCUTS = stringPreferencesKey("keyboard_shortcuts")
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val JWT_SECRET = stringPreferencesKey("jwt_secret")
        val ALLOW_LOCAL_NETWORK_AGENTS = booleanPreferencesKey("allow_local_network_agents")
        val PIPELINE_MODE = stringPreferencesKey("pipeline_mode")
    }

    private val securePrefs = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_baton_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            context.deleteSharedPreferences("secure_baton_prefs")
        } catch (ignored: Exception) {}
        try {
            val file = java.io.File(context.filesDir.parent, "shared_prefs/secure_baton_prefs.xml")
            if (file.exists()) file.delete()
            val bakFile = java.io.File(context.filesDir.parent, "shared_prefs/secure_baton_prefs.xml.bak")
            if (bakFile.exists()) bakFile.delete()
            val bakFile2 = java.io.File(context.filesDir.parent, "shared_prefs/secure_baton_prefs.bak")
            if (bakFile2.exists()) bakFile2.delete()
        } catch (ignored: Exception) {}

        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (ignored: Exception) {}

        try {
            val freshMasterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secure_baton_prefs",
                freshMasterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (fallbackEx: Exception) {
            fallbackEx.printStackTrace()
            throw SecurityException("CRITICAL: Failed to initialize EncryptedSharedPreferences for AppPreferences.", fallbackEx)
        }
    }

    private val _jwtSecretFlow = MutableStateFlow(securePrefs.getString("jwt_secret", "") ?: "")
    private val _isPremiumUnlockedFlow = MutableStateFlow(securePrefs.getBoolean("is_premium_unlocked", false))

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val legacyJwt = context.dataStore.data.map { it[JWT_SECRET] }.firstOrNull()
            if (!legacyJwt.isNullOrBlank() && securePrefs.getString("jwt_secret", "") == "") {
                setJwtSecret(legacyJwt)
            }
            val legacyPremium = context.dataStore.data.map { it[IS_PREMIUM_UNLOCKED] }.firstOrNull()
            if (legacyPremium != null && !securePrefs.getBoolean("is_premium_unlocked", false)) {
                setPremiumUnlocked(legacyPremium)
            }
        }
    }

    val userEmail: Flow<String> = context.dataStore.data.map { "" }

    val userPhone: Flow<String> = context.dataStore.data.map { "" }

    val isRegistered: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_REGISTERED] ?: false
    }

    val trialStartTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[TRIAL_START_TIME] ?: 0L
    }

    val isPremiumUnlocked: Flow<Boolean> = _isPremiumUnlockedFlow

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "dark"
    }

    val defaultTokenLimit: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[DEFAULT_TOKEN_LIMIT] ?: 4096
    }

    val enableHapticFeedback: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENABLE_HAPTIC_FEEDBACK] ?: true
    }

    val tunnelStatusMap: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[TUNNEL_STATUS_MAP] ?: "{}"
        try {
            kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    val accentColor: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR] ?: 0xFFECEFF4 // Default tertiary color (Cool White)
    }

    val fontSize: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[FONT_SIZE] ?: "medium"
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_ENABLED] ?: false
    }

    val autoExtractFacts: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_EXTRACT_FACTS] ?: true
    }

    val autoGenerateEpisodes: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_GENERATE_EPISODES] ?: true
    }

    val memoryRetentionDays: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MEMORY_RETENTION_DAYS] ?: 30
    }

    val backendUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[BACKEND_URL] ?: "http://10.0.2.2:8080/"
    }

    val jwtSecret: Flow<String> = _jwtSecretFlow

    var qtspPassword: CharArray?
        get() = securePrefs.getString("qtsp_password", null)?.toCharArray()
        set(value) {
            if (value == null) {
                securePrefs.edit().remove("qtsp_password").apply()
            } else {
                securePrefs.edit().putString("qtsp_password", String(value)).apply()
            }
        }

    val allowLocalNetworkAgents: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ALLOW_LOCAL_NETWORK_AGENTS] ?: false
    }

    val pipelineMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PIPELINE_MODE] ?: "MANAGED"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[THEME_MODE] = mode }
    }

    suspend fun setAccentColor(color: Long) {
        context.dataStore.edit { preferences -> preferences[ACCENT_COLOR] = color }
    }

    suspend fun setFontSize(size: String) {
        context.dataStore.edit { preferences -> preferences[FONT_SIZE] = size }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setAutoExtractFacts(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_EXTRACT_FACTS] = enabled }
    }

    suspend fun setAutoGenerateEpisodes(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[AUTO_GENERATE_EPISODES] = enabled }
    }

    suspend fun setMemoryRetentionDays(days: Int) {
        context.dataStore.edit { preferences -> preferences[MEMORY_RETENTION_DAYS] = days }
    }

    suspend fun setDefaultTokenLimit(limit: Int) {
        context.dataStore.edit { preferences -> preferences[DEFAULT_TOKEN_LIMIT] = limit }
    }

    suspend fun setEnableHapticFeedback(enable: Boolean) {
        context.dataStore.edit { preferences -> preferences[ENABLE_HAPTIC_FEEDBACK] = enable }
    }

    suspend fun setTunnelStatusMap(statusMap: Map<String, String>) {
        val jsonString = kotlinx.serialization.json.Json.encodeToString(statusMap)
        context.dataStore.edit { preferences -> preferences[TUNNEL_STATUS_MAP] = jsonString }
    }

    suspend fun setTrialStartTime(time: Long) {
        context.dataStore.edit { preferences -> preferences[TRIAL_START_TIME] = time }
    }

    suspend fun setJwtSecret(secret: String) {
        securePrefs.edit().putString("jwt_secret", secret).apply()
        _jwtSecretFlow.value = secret
        // Clear from plaintext datastore if it was previously stored there
        context.dataStore.edit { preferences -> preferences.remove(JWT_SECRET) }
    }

    suspend fun setPremiumUnlocked(unlocked: Boolean) {
        securePrefs.edit().putBoolean("is_premium_unlocked", unlocked).apply()
        _isPremiumUnlockedFlow.value = unlocked
        // Clear from plaintext datastore if it was previously stored there
        context.dataStore.edit { preferences -> preferences.remove(IS_PREMIUM_UNLOCKED) }
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { preferences -> preferences[BACKEND_URL] = url }
    }

    suspend fun setAllowLocalNetworkAgents(allow: Boolean) {
        context.dataStore.edit { preferences -> preferences[ALLOW_LOCAL_NETWORK_AGENTS] = allow }
    }

    suspend fun setPipelineMode(mode: String) {
        context.dataStore.edit { preferences -> preferences[PIPELINE_MODE] = mode }
    }

    val keyboardShortcuts: Flow<List<KeyboardShortcut>> = context.dataStore.data.map { preferences ->
        val jsonString = preferences[KEYBOARD_SHORTCUTS]
        if (jsonString.isNullOrBlank()) {
            defaultShortcuts()
        } else {
            try {
                kotlinx.serialization.json.Json.decodeFromString<List<KeyboardShortcut>>(jsonString)
            } catch (e: Exception) {
                defaultShortcuts()
            }
        }
    }

    private fun defaultShortcuts(): List<KeyboardShortcut> = listOf(
        KeyboardShortcut(label = "Code", textToInsert = "```\n\n```", isImmediate = false),
        KeyboardShortcut(label = "Status", textToInsert = "/status", isImmediate = true),
        KeyboardShortcut(label = "Clear", textToInsert = "/clear", isImmediate = true),
        KeyboardShortcut(label = "Help", textToInsert = "/help", isImmediate = true)
    )

    suspend fun setKeyboardShortcuts(shortcuts: List<KeyboardShortcut>) {
        val jsonString = kotlinx.serialization.json.Json.encodeToString(shortcuts)
        context.dataStore.edit { preferences ->
            preferences[KEYBOARD_SHORTCUTS] = jsonString
        }
    }

    suspend fun registerUser(email: String, phone: String) {
        context.dataStore.edit { preferences ->
            // Security Fix: Do not store PII in plaintext DataStore
            preferences.remove(USER_EMAIL)
            preferences.remove(USER_PHONE)
            preferences[IS_REGISTERED] = true
            preferences[TRIAL_START_TIME] = System.currentTimeMillis()
            preferences.remove(IS_PREMIUM_UNLOCKED)
        }
        setPremiumUnlocked(false)
    }

    suspend fun clearRegistration() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_EMAIL)
            preferences.remove(USER_PHONE)
            preferences[IS_REGISTERED] = false
            preferences[TRIAL_START_TIME] = 0L
            preferences.remove(IS_PREMIUM_UNLOCKED)
        }
        setPremiumUnlocked(false)
    }
}
