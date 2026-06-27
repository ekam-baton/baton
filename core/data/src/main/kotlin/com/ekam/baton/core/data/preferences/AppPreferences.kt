package com.ekam.baton.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

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
    }

    val userEmail: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_EMAIL] ?: ""
    }

    val userPhone: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[USER_PHONE] ?: ""
    }

    val isRegistered: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_REGISTERED] ?: false
    }

    val trialStartTime: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[TRIAL_START_TIME] ?: 0L
    }

    val isPremiumUnlocked: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_PREMIUM_UNLOCKED] ?: false
    }

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

    suspend fun setPremiumUnlocked(unlocked: Boolean) {
        context.dataStore.edit { preferences -> preferences[IS_PREMIUM_UNLOCKED] = unlocked }
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { preferences -> preferences[BACKEND_URL] = url }
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
            preferences[USER_EMAIL] = email
            preferences[USER_PHONE] = phone
            preferences[IS_REGISTERED] = true
            preferences[TRIAL_START_TIME] = System.currentTimeMillis()
            preferences[IS_PREMIUM_UNLOCKED] = false
        }
    }

    suspend fun clearRegistration() {
        context.dataStore.edit { preferences ->
            preferences[USER_EMAIL] = ""
            preferences[USER_PHONE] = ""
            preferences[IS_REGISTERED] = false
            preferences[TRIAL_START_TIME] = 0L
            preferences[IS_PREMIUM_UNLOCKED] = false
        }
    }
}
