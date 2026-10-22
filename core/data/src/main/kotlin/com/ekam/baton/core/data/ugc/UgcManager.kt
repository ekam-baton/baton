package com.ekam.baton.core.data.ugc

import android.content.Context
import android.util.Log
import com.ekam.baton.core.data.db.BatonDatabase
import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.data.preferences.SessionManager
import com.ekam.baton.core.data.repository.WipeDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID

private const val TAG = "UgcManager"

enum class ReportReason(val label: String) {
    HARASSMENT("Harassment or Bullying"),
    HATE_SPEECH("Hate Speech or Discrimination"),
    SEXUALLY_EXPLICIT("Sexually Explicit Content"),
    VIOLENCE_SELF_HARM("Violence or Self-Harm"),
    SPAM_SCAM("Spam or Scams"),
    OTHER("Other Objectionable Content")
}

data class ContentReport(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val messageId: String? = null,
    val agentId: String,
    val reason: ReportReason,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class SafetyCheckResult(
    val isSafe: Boolean,
    val flaggedCategory: String? = null,
    val explanation: String? = null
)

interface UgcManager {
    suspend fun reportContent(report: ContentReport): Boolean
    suspend fun blockAgent(agentId: String): Boolean
    suspend fun unblockAgent(agentId: String): Boolean
    suspend fun setAgentBlocked(agentId: String, blocked: Boolean): Boolean
    fun isAgentBlocked(agentId: String): Flow<Boolean>
    fun getBlockedAgentIds(): Flow<Set<String>>
    fun checkContentSafety(content: String): SafetyCheckResult
    suspend fun deleteAccountAndWipeData(): Boolean
}

class UgcManagerImpl(
    private val context: Context,
    private val database: BatonDatabase,
    private val sessionManager: SessionManager,
    private val appPreferences: AppPreferences,
    private val wipeDataManager: WipeDataManager,
    private val httpClient: OkHttpClient
) : UgcManager {

    private val _blockedAgents = MutableStateFlow<Set<String>>(emptySet())
    val blockedAgents: Flow<Set<String>> = _blockedAgents.asStateFlow()

    private val blockedAgentsKey = "ugc_blocked_agents_set"

    init {
        // Load initially blocked agents from preferences/DB
        loadBlockedAgents()
    }

    private fun loadBlockedAgents() {
        try {
            val prefs = context.getSharedPreferences("baton_ugc_prefs", Context.MODE_PRIVATE)
            val savedSet = prefs.getStringSet(blockedAgentsKey, emptySet()) ?: emptySet()
            _blockedAgents.value = savedSet
        } catch (e: Exception) {
            Log.e(TAG, "Error loading blocked agents", e)
        }
    }

    private fun persistBlockedAgents(newSet: Set<String>) {
        try {
            val prefs = context.getSharedPreferences("baton_ugc_prefs", Context.MODE_PRIVATE)
            prefs.edit().putStringSet(blockedAgentsKey, newSet).apply()
            _blockedAgents.value = newSet
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting blocked agents", e)
        }
    }

    override suspend fun reportContent(report: ContentReport): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.w(TAG, "UGC Report submitted: ID=${report.id}, reason=${report.reason.name}, agent=${report.agentId}, details=${report.details}")
            
            // 1. Persist report locally in Audit Log / Store report store
            val prefs = context.getSharedPreferences("baton_ugc_reports", Context.MODE_PRIVATE)
            val jsonReport = JSONObject().apply {
                put("id", report.id)
                put("conversationId", report.conversationId)
                put("messageId", report.messageId ?: "")
                put("agentId", report.agentId)
                put("reason", report.reason.name)
                put("details", report.details)
                put("timestamp", report.timestamp)
            }.toString()
            prefs.edit().putString("report_${report.id}", jsonReport).apply()

            // 2. Dispatch to backend if backend URL configured
            val backendUrl = appPreferences.backendUrl.first()
            if (backendUrl.isNotBlank()) {
                val reportEndpoint = if (backendUrl.endsWith("/")) "${backendUrl}ugc/report" else "$backendUrl/ugc/report"
                val token = appPreferences.jwtSecret.first()
                val body = jsonReport.toRequestBody("application/json".toMediaTypeOrNull())
                val reqBuilder = Request.Builder()
                    .url(reportEndpoint)
                    .post(body)
                if (token.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $token")
                }
                try {
                    httpClient.newCall(reqBuilder.build()).execute().use { /* auto-closed */ }
                } catch (netEx: Exception) {
                    Log.w(TAG, "Backend report dispatch failed (cached locally): ${netEx.message}")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record content report", e)
            false
        }
    }

    override suspend fun blockAgent(agentId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _blockedAgents.value.toMutableSet()
        current.add(agentId)
        persistBlockedAgents(current)
        try {
            database.agentDao().updateBlockedStatus(agentId, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update agent blocked status in DB", e)
        }
        true
    }

    override suspend fun unblockAgent(agentId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _blockedAgents.value.toMutableSet()
        current.remove(agentId)
        persistBlockedAgents(current)
        try {
            database.agentDao().updateBlockedStatus(agentId, false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update agent unblocked status in DB", e)
        }
        true
    }

    override suspend fun setAgentBlocked(agentId: String, blocked: Boolean): Boolean {
        return if (blocked) blockAgent(agentId) else unblockAgent(agentId)
    }

    override fun isAgentBlocked(agentId: String): Flow<Boolean> {
        return _blockedAgents.map { it.contains(agentId) }
    }

    override fun getBlockedAgentIds(): Flow<Set<String>> {
        return blockedAgents
    }

    override fun checkContentSafety(content: String): SafetyCheckResult {
        val lower = content.lowercase()

        // 1. Self-Harm & Suicide prevention heuristics
        val selfHarmKeywords = listOf("how to commit suicide", "kill myself", "ways to harm myself", "suicide method")
        if (selfHarmKeywords.any { lower.contains(it) }) {
            return SafetyCheckResult(
                isSafe = false,
                flaggedCategory = "SELF_HARM",
                explanation = "Content flagged for potential self-harm indicators."
            )
        }

        // 2. Severe CSAM / Child safety guardrails
        val childSafetyKeywords = listOf("csam", "underage explicit", "child sexual")
        if (childSafetyKeywords.any { lower.contains(it) }) {
            return SafetyCheckResult(
                isSafe = false,
                flaggedCategory = "CHILD_SAFETY",
                explanation = "Content flagged for zero-tolerance child safety violation."
            )
        }

        // 3. Dangerous weapons / explosives synthesis instructions
        val dangerousWeapons = listOf("how to make a pipe bomb", "synthesize ricin", "weaponize anthrax")
        if (dangerousWeapons.any { lower.contains(it) }) {
            return SafetyCheckResult(
                isSafe = false,
                flaggedCategory = "DANGEROUS_MATERIAL",
                explanation = "Content flagged for dangerous weapons or substance instructions."
            )
        }

        return SafetyCheckResult(isSafe = true)
    }

    override suspend fun deleteAccountAndWipeData(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Executing Apple Guideline 5.1.1(v) complete account deletion & wipe...")

            // 1. Notify remote backend to delete account if registered
            val backendUrl = appPreferences.backendUrl.first()
            val token = appPreferences.jwtSecret.first()
            if (backendUrl.isNotBlank() && token.isNotBlank()) {
                val deleteEndpoint = if (backendUrl.endsWith("/")) "${backendUrl}auth/delete-account" else "$backendUrl/auth/delete-account"
                val request = Request.Builder()
                    .url(deleteEndpoint)
                    .delete()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                try {
                    httpClient.newCall(request).execute().use { /* auto-closed */ }
                    Log.i(TAG, "Backend account deletion successful")
                } catch (netEx: Exception) {
                    Log.w(TAG, "Backend account deletion call failed (continuing local purge): ${netEx.message}")
                }
            }

            // 2. Wipe SQLCipher database and all tables
            wipeDataManager.wipeAllData()

            // 3. Clear SessionManager registration & login state
            sessionManager.clearRegistration()
            sessionManager.setLoggedIn(false)

            // 4. Wipe AppPreferences & auth tokens
            appPreferences.clearAllPreferences()

            // 5. Purge Android Keystore master keys
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val aliases = keyStore.aliases()
                while (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    if (alias.startsWith("baton_") || alias.contains("master_key")) {
                        keyStore.deleteEntry(alias)
                    }
                }
            } catch (ksEx: Exception) {
                Log.w(TAG, "KeyStore alias deletion warning: ${ksEx.message}")
            }

            // 6. Clear UGC blocked agents & reports
            context.getSharedPreferences("baton_ugc_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("baton_ugc_reports", Context.MODE_PRIVATE).edit().clear().apply()

            Log.i(TAG, "Account deletion and complete device purge completed successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed during account deletion", e)
            false
        }
    }
}
