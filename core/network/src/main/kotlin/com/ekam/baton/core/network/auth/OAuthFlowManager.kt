package com.ekam.baton.core.network.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long, // seconds
    @SerialName("refresh_token") val refreshToken: String? = null
)

@Singleton
class OAuthFlowManager @Inject constructor(
    private val json: Json,
    private val tokenStore: TokenStore
) {
    private val authClient = OkHttpClient.Builder().build()
    // In-memory store for pending auth flows (agentId -> Triple<Verifier, State, Config>)
    private val pendingFlows = ConcurrentHashMap<String, Triple<String, String, OAuthConfig>>()

    fun getAgentIdByState(state: String): String? {
        return pendingFlows.entries.firstOrNull { it.value.second == state }?.key
    }

    fun getConfigByState(state: String): OAuthConfig? {
        return pendingFlows.entries.firstOrNull { it.value.second == state }?.value?.third
    }

    fun buildAuthUrlAndStartFlow(agentId: String, config: OAuthConfig): String {
        val verifier = PkceGenerator.generateCodeVerifier()
        val challenge = PkceGenerator.generateCodeChallenge(verifier)
        val state = PkceGenerator.generateState()

        pendingFlows[agentId] = Triple(verifier, state, config)

        val uriBuilder = Uri.parse(config.authorizationUrl).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("scope", config.scopes.joinToString(" "))
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")

        return uriBuilder.build().toString()
    }

    fun launchAuthBrowser(context: Context, authUrl: String) {
        val customTabsIntent = CustomTabsIntent.Builder().build()
        try {
            customTabsIntent.launchUrl(context, Uri.parse(authUrl))
        } catch (e: Exception) {
            // Fallback if no browser supports Custom Tabs
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    suspend fun handleCallback(
        agentId: String,
        config: OAuthConfig,
        code: String,
        returnedState: String
    ): Result<TokenResponse> {
        val flowData = pendingFlows.remove(agentId)
            ?: return Result.failure(IllegalStateException("No pending flow for this agent"))

        val verifier = flowData.first
        val savedState = flowData.second

        if (savedState != returnedState) {
            return Result.failure(IllegalStateException("State mismatch. Possible CSRF attack."))
        }

        return exchangeCodeForToken(agentId, config, code, verifier)
    }

    private suspend fun exchangeCodeForToken(
        agentId: String,
        config: OAuthConfig,
        code: String,
        verifier: String
    ): Result<TokenResponse> {
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", config.redirectUri)
            .add("client_id", config.clientId)
            .add("code_verifier", verifier)
            .build()

        val request = Request.Builder()
            .url(config.tokenUrl)
            .post(formBody)
            .build()

        return try {
            val response = authClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(IOException("Token exchange failed: ${response.code} ${response.body?.string()}"))
            }

            val bodyStr = response.body?.string() ?: return Result.failure(IOException("Empty response body"))
            val tokenResponse = json.decodeFromString<TokenResponse>(bodyStr)

            // Save to TokenStore
            val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)
            tokenStore.saveTokens(agentId, tokenResponse.accessToken, tokenResponse.refreshToken, expiresAt)

            Result.success(tokenResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(agentId: String, config: OAuthConfig): Result<String> {
        val refreshToken = tokenStore.getRefreshToken(agentId)
            ?: return Result.failure(IllegalStateException("No refresh token available"))

        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", config.clientId)
            .build()

        val request = Request.Builder()
            .url(config.tokenUrl)
            .post(formBody)
            .build()

        return try {
            val response = authClient.newCall(request).execute()
            if (!response.isSuccessful) {
                tokenStore.clearTokens(agentId)
                return Result.failure(IOException("Token refresh failed: ${response.code}"))
            }

            val bodyStr = response.body?.string() ?: return Result.failure(IOException("Empty response body"))
            val tokenResponse = json.decodeFromString<TokenResponse>(bodyStr)

            val newRefreshToken = tokenResponse.refreshToken ?: refreshToken
            val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000)

            tokenStore.saveTokens(agentId, tokenResponse.accessToken, newRefreshToken, expiresAt)

            Result.success(tokenResponse.accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
