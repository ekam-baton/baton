package com.ekam.baton.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "baton_preferences")

open class AppPreferences constructor(
    private val _context: Context? = null
) {
    private val context: Context get() = _context ?: throw IllegalStateException("Context not available")

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
        val CLIENT_ID = stringPreferencesKey("client_id")
        val CONSENT_TIMESTAMP = longPreferencesKey("consent_timestamp")
        val POLICY_VERSION = stringPreferencesKey("policy_version")
        val HAS_SEEN_WALKTHROUGH = booleanPreferencesKey("has_seen_walkthrough")
        val REGION = stringPreferencesKey("region")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val CLOUD_SYNC_ACTIVE = booleanPreferencesKey("cloud_sync_active")
        val CLOUD_SYNC_MEMORIES = booleanPreferencesKey("cloud_sync_memories")
        val CLOUD_SYNC_KEYS = booleanPreferencesKey("cloud_sync_keys")
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        val STEALTH_MODE_ENABLED = booleanPreferencesKey("stealth_mode_enabled")
        val DURESS_PIN = stringPreferencesKey("duress_pin")
    }

    private val securePrefs = try {
        if (_context == null) null else {
            val masterKey = MasterKey.Builder(_context, "baton_app_prefs_master_key")
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                _context,
                "secure_baton_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    } catch (e: Exception) {
        if (_context == null) null else {
            e.printStackTrace()
            try {
                _context.deleteSharedPreferences("secure_baton_prefs")
            } catch (ignored: Exception) {}
            try {
                val file = java.io.File(_context.filesDir.parent, "shared_prefs/secure_baton_prefs.xml")
                if (file.exists()) file.delete()
                val bakFile = java.io.File(_context.filesDir.parent, "shared_prefs/secure_baton_prefs.xml.bak")
                if (bakFile.exists()) bakFile.delete()
                val bakFile2 = java.io.File(_context.filesDir.parent, "shared_prefs/secure_baton_prefs.bak")
                if (bakFile2.exists()) bakFile2.delete()
            } catch (ignored: Exception) {}

            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                keyStore.deleteEntry("baton_app_prefs_master_key")
            } catch (ignored: Exception) {}

            try {
                val freshMasterKey = MasterKey.Builder(_context, "baton_app_prefs_master_key")
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    _context,
                    "secure_baton_prefs",
                    freshMasterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (fallbackEx: Exception) {
                fallbackEx.printStackTrace()
                null
            }
        }
    }

    private val _jwtSecretFlow = MutableStateFlow(securePrefs?.getString("jwt_secret", "") ?: "")
    private val _refreshTokenFlow = MutableStateFlow(securePrefs?.getString("refresh_token", "") ?: "")
    private val _isPremiumUnlockedFlow = MutableStateFlow(securePrefs?.getBoolean("is_premium_unlocked", false) ?: false)

    init {
        if (_context != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val legacyJwt = context.dataStore.data.map { it[JWT_SECRET] }.firstOrNull()
                if (!legacyJwt.isNullOrBlank() && securePrefs?.getString("jwt_secret", "") == "") {
                    setJwtSecret(legacyJwt)
                }
                val legacyPremium = context.dataStore.data.map { it[IS_PREMIUM_UNLOCKED] }.firstOrNull()
                if (legacyPremium != null && !(securePrefs?.getBoolean("is_premium_unlocked", false) ?: false)) {
                    setPremiumUnlocked(legacyPremium)
                }
            }
        }
    }

    private val _userEmailFlow = MutableStateFlow(securePrefs?.getString("user_email", "") ?: "")
    private val _userPhoneFlow = MutableStateFlow(securePrefs?.getString("user_phone", "") ?: "")

    val userEmail: Flow<String> = _userEmailFlow

    val userPhone: Flow<String> = _userPhoneFlow

    open val isRegistered: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(false) else context.dataStore.data.map { preferences ->
        preferences[IS_REGISTERED] ?: false
    }

    open val trialStartTime: Flow<Long> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(0L) else context.dataStore.data.map { preferences ->
        preferences[TRIAL_START_TIME] ?: 0L
    }

    open val isPremiumUnlocked: Flow<Boolean> = _isPremiumUnlockedFlow

    open val themeMode: Flow<String> get() = if (_context == null) kotlinx.coroutines.flow.flowOf("dark") else context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "dark"
    }

    open val defaultTokenLimit: Flow<Int> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(4096) else context.dataStore.data.map { preferences ->
        preferences[DEFAULT_TOKEN_LIMIT] ?: 4096
    }

    open val enableHapticFeedback: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(true) else context.dataStore.data.map { preferences ->
        preferences[ENABLE_HAPTIC_FEEDBACK] ?: true
    }

    open val tunnelStatusMap: Flow<Map<String, String>> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(emptyMap()) else context.dataStore.data.map { preferences ->
        val jsonString = preferences[TUNNEL_STATUS_MAP] ?: "{}"
        try {
            kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    open val accentColor: Flow<Long> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(0xFFECEFF4) else context.dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR] ?: 0xFFECEFF4 // Default tertiary color (Cool White)
    }

    open val fontSize: Flow<String> get() = if (_context == null) kotlinx.coroutines.flow.flowOf("medium") else context.dataStore.data.map { preferences ->
        preferences[FONT_SIZE] ?: "medium"
    }

    open val appLockEnabled: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(false) else context.dataStore.data.map { preferences ->
        preferences[APP_LOCK_ENABLED] ?: false
    }

    open val autoExtractFacts: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(true) else context.dataStore.data.map { preferences ->
        preferences[AUTO_EXTRACT_FACTS] ?: true
    }

    open val autoGenerateEpisodes: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(true) else context.dataStore.data.map { preferences ->
        preferences[AUTO_GENERATE_EPISODES] ?: true
    }

    open val memoryRetentionDays: Flow<Int> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(30) else context.dataStore.data.map { preferences ->
        preferences[MEMORY_RETENTION_DAYS] ?: 30
    }

    open val backendUrl: Flow<String> get() = if (_context == null) kotlinx.coroutines.flow.flowOf("https://api.baton.com/") else combine(
        context.dataStore.data.map { it[PIPELINE_MODE] ?: "MANAGED" },
        context.dataStore.data.map { it[REGION] ?: "Global" },
        context.dataStore.data.map { it[BACKEND_URL] ?: "https://api.baton.com/" }
    ) { mode, region, url ->
        if (mode == "MANAGED") {
            when (region) {
                "India" -> "https://in.relay.baton.com/"
                "US" -> "https://us.relay.baton.com/"
                "UK/EU" -> "https://eu.relay.baton.com/"
                "UAE" -> "https://ae.relay.baton.com/"
                "South Africa" -> "https://za.relay.baton.com/"
                "Brazil" -> "https://br.relay.baton.com/"
                else -> "https://global.relay.baton.com/"
            }
        } else {
            url
        }
    }

    open val jwtSecret: Flow<String> get() = _jwtSecretFlow
    val refreshToken: Flow<String> = _refreshTokenFlow

    var qtspPassword: CharArray?
        get() = securePrefs?.getString("qtsp_password", null)?.toCharArray()
        set(value) {
            if (value == null) {
                securePrefs?.edit()?.remove("qtsp_password")?.commit()
            } else {
                securePrefs?.edit()?.putString("qtsp_password", String(value))?.commit()
                value.fill('\u0000')
            }
        }

    open val allowLocalNetworkAgents: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(false) else context.dataStore.data.map { preferences ->
        preferences[ALLOW_LOCAL_NETWORK_AGENTS] ?: false
    }

    open val pipelineMode: Flow<String> get() = if (_context == null) kotlinx.coroutines.flow.flowOf("MANAGED") else context.dataStore.data.map { preferences ->
        preferences[PIPELINE_MODE] ?: "MANAGED"
    }

    open val clientId: Flow<String> get() = if (_context == null) kotlinx.coroutines.flow.flowOf("") else context.dataStore.data.map { preferences ->
        preferences[CLIENT_ID] ?: ""
    }

    open val consentTimestamp: Flow<Long> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(0L) else context.dataStore.data.map { preferences ->
        preferences[CONSENT_TIMESTAMP] ?: 0L
    }

    open val policyVersion: Flow<String> get() = if (_context == null) kotlinx.coroutines.flow.flowOf("") else context.dataStore.data.map { preferences ->
        preferences[POLICY_VERSION] ?: ""
    }

    open val hasSeenWalkthrough: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(false) else context.dataStore.data.map { preferences ->
        preferences[HAS_SEEN_WALKTHROUGH] ?: false
    }

    open val region: Flow<String> get() = if (_context == null) kotlinx.coroutines.flow.flowOf("Global") else context.dataStore.data.map { preferences ->
        preferences[REGION] ?: "Global"
    }

    open suspend fun setHasSeenWalkthrough(seen: Boolean) {
        _context?.dataStore?.edit { preferences -> preferences[HAS_SEEN_WALKTHROUGH] = seen }
    }

    open suspend fun setClientId(id: String) {
        _context?.dataStore?.edit { preferences -> preferences[CLIENT_ID] = id }
    }

    open suspend fun setThemeMode(mode: String) {
        _context?.dataStore?.edit { preferences -> preferences[THEME_MODE] = mode }
    }

    open suspend fun setAccentColor(color: Long) {
        _context?.dataStore?.edit { preferences -> preferences[ACCENT_COLOR] = color }
    }

    open suspend fun setFontSize(size: String) {
        _context?.dataStore?.edit { preferences -> preferences[FONT_SIZE] = size }
    }

    open suspend fun setAppLockEnabled(enabled: Boolean) {
        _context?.dataStore?.edit { preferences -> preferences[APP_LOCK_ENABLED] = enabled }
    }

    open suspend fun setAutoExtractFacts(enabled: Boolean) {
        _context?.dataStore?.edit { preferences -> preferences[AUTO_EXTRACT_FACTS] = enabled }
    }

    open suspend fun setAutoGenerateEpisodes(enabled: Boolean) {
        _context?.dataStore?.edit { preferences -> preferences[AUTO_GENERATE_EPISODES] = enabled }
    }

    open suspend fun setMemoryRetentionDays(days: Int) {
        _context?.dataStore?.edit { preferences -> preferences[MEMORY_RETENTION_DAYS] = days }
    }

    open suspend fun setDefaultTokenLimit(limit: Int) {
        _context?.dataStore?.edit { preferences -> preferences[DEFAULT_TOKEN_LIMIT] = limit }
    }

    open suspend fun setEnableHapticFeedback(enable: Boolean) {
        _context?.dataStore?.edit { preferences -> preferences[ENABLE_HAPTIC_FEEDBACK] = enable }
    }

    open suspend fun setTunnelStatusMap(statusMap: Map<String, String>) {
        val jsonString = kotlinx.serialization.json.Json.encodeToString(statusMap)
        _context?.dataStore?.edit { preferences -> preferences[TUNNEL_STATUS_MAP] = jsonString }
    }

    open suspend fun setTrialStartTime(time: Long) {
        _context?.dataStore?.edit { preferences -> preferences[TRIAL_START_TIME] = time }
    }

    open suspend fun setJwtSecret(secret: String) {
        securePrefs?.edit()?.putString("jwt_secret", secret)?.commit()
        _jwtSecretFlow.value = secret
        // Clear from plaintext datastore if it was previously stored there
        _context?.dataStore?.edit { preferences -> preferences.remove(JWT_SECRET) }
    }

    open suspend fun setRefreshToken(token: String) {
        securePrefs?.edit()?.putString("refresh_token", token)?.commit()
        _refreshTokenFlow.value = token
        _context?.dataStore?.edit { preferences -> preferences.remove(REFRESH_TOKEN) }
    }

    open suspend fun setPremiumUnlocked(unlocked: Boolean) {
        securePrefs?.edit()?.putBoolean("is_premium_unlocked", unlocked)?.commit()
        _isPremiumUnlockedFlow.value = unlocked
        // Clear from plaintext datastore if it was previously stored there
        _context?.dataStore?.edit { preferences -> preferences.remove(IS_PREMIUM_UNLOCKED) }
    }

    open suspend fun setBackendUrl(url: String) {
        _context?.dataStore?.edit { preferences -> preferences[BACKEND_URL] = url }
    }

    open suspend fun setAllowLocalNetworkAgents(allow: Boolean) {
        _context?.dataStore?.edit { preferences -> preferences[ALLOW_LOCAL_NETWORK_AGENTS] = allow }
    }

    open suspend fun setPipelineMode(mode: String) {
        _context?.dataStore?.edit { preferences -> preferences[PIPELINE_MODE] = mode }
    }

    open val keyboardShortcuts: Flow<List<KeyboardShortcut>> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(defaultShortcuts()) else context.dataStore.data.map { preferences ->
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

    open suspend fun setKeyboardShortcuts(shortcuts: List<KeyboardShortcut>) {
        val jsonString = kotlinx.serialization.json.Json.encodeToString(shortcuts)
        _context?.dataStore?.edit { preferences ->
            preferences[KEYBOARD_SHORTCUTS] = jsonString
        }
    }

    suspend fun registerUser(email: String, phone: String, consentTimestamp: Long = 0L, policyVersion: String = "", region: String = "Global") {
        securePrefs?.edit()
            ?.putString("user_email", email)
            ?.putString("user_phone", phone)
            ?.apply()
        _userEmailFlow.value = email
        _userPhoneFlow.value = phone

        context?.dataStore?.edit { preferences ->
            // Security Fix: Do not store PII in plaintext DataStore
            preferences.remove(USER_EMAIL)
            preferences.remove(USER_PHONE)
            preferences[IS_REGISTERED] = true
            preferences[TRIAL_START_TIME] = System.currentTimeMillis()
            preferences[CONSENT_TIMESTAMP] = consentTimestamp
            preferences[POLICY_VERSION] = policyVersion
            preferences[REGION] = region
            preferences.remove(IS_PREMIUM_UNLOCKED)
        }
        setPremiumUnlocked(false)
    }

    open suspend fun clearRegistration() {
        securePrefs?.edit()
            ?.remove("user_email")
            ?.remove("user_phone")
            ?.apply()
        _userEmailFlow.value = ""
        _userPhoneFlow.value = ""

        _context?.dataStore?.edit { preferences ->
            preferences.remove(USER_EMAIL)
            preferences.remove(USER_PHONE)
            preferences[IS_REGISTERED] = false
            preferences[TRIAL_START_TIME] = 0L
            preferences.remove(IS_PREMIUM_UNLOCKED)
        }
        setPremiumUnlocked(false)
    }

    open val cloudSyncActive: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(true) else context.dataStore.data.map { preferences ->
        preferences[CLOUD_SYNC_ACTIVE] ?: true
    }

    open val cloudSyncMemories: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(true) else context.dataStore.data.map { preferences ->
        preferences[CLOUD_SYNC_MEMORIES] ?: true
    }

    open val cloudSyncKeys: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(false) else context.dataStore.data.map { preferences ->
        preferences[CLOUD_SYNC_KEYS] ?: false
    }

    open val lastSyncTimestamp: Flow<Long> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(0L) else context.dataStore.data.map { preferences ->
        preferences[LAST_SYNC_TIMESTAMP] ?: 0L
    }

    open suspend fun setCloudSyncActive(active: Boolean) {
        _context?.dataStore?.edit { preferences ->
            preferences[CLOUD_SYNC_ACTIVE] = active
        }
    }

    open suspend fun setCloudSyncMemories(sync: Boolean) {
        _context?.dataStore?.edit { preferences ->
            preferences[CLOUD_SYNC_MEMORIES] = sync
        }
    }

    open suspend fun setCloudSyncKeys(sync: Boolean) {
        _context?.dataStore?.edit { preferences ->
            preferences[CLOUD_SYNC_KEYS] = sync
        }
    }

    open suspend fun setLastSyncTimestamp(timestamp: Long) {
        _context?.dataStore?.edit { preferences ->
            preferences[LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    open val stealthModeEnabled: Flow<Boolean> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(false) else context.dataStore.data.map { preferences ->
        preferences[STEALTH_MODE_ENABLED] ?: false
    }

    open suspend fun setStealthModeEnabled(enabled: Boolean) {
        _context?.dataStore?.edit { preferences ->
            preferences[STEALTH_MODE_ENABLED] = enabled
        }
    }

    open val duressPin: Flow<String?> get() = if (_context == null) kotlinx.coroutines.flow.flowOf(null) else context.dataStore.data.map { preferences ->
        preferences[DURESS_PIN]
    }

    open suspend fun setDuressPin(pin: String?) {
        _context?.dataStore?.edit { preferences ->
            if (pin.isNullOrBlank()) {
                preferences.remove(DURESS_PIN)
            } else {
                preferences[DURESS_PIN] = pin
            }
        }
    }
}
