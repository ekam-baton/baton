package com.ekam.baton.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "baton_preferences")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
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
        preferences[ACCENT_COLOR] ?: 0xFF9D65FF // Default tertiary color
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
}
