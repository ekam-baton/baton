package com.ekam.baton.core.network.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthInterceptor constructor(
    private val tokenStore: TokenStore,
    private val oauthFlowManager: OAuthFlowManager
    // In a real app, we'd need a way to look up the OAuthConfig for the agent here.
    // For now, we assume OAuthConfig can be fetched via repository or is passed somehow.
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        
        // We use a custom header to pass the agent ID down to the interceptor
        val agentId = request.header("X-Baton-Agent-Id")

        if (agentId != null) {
            // Remove the internal header so it doesn't leak
            request = request.newBuilder().removeHeader("X-Baton-Agent-Id").build()

            // Check if token is valid
            if (tokenStore.isTokenValid(agentId)) {
                val token = tokenStore.getAccessToken(agentId)
                if (token != null) {
                    request = request.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
            } else {
                // Token is expired or missing. Try to refresh if we have a refresh token.
                val refreshToken = tokenStore.getRefreshToken(agentId)
                if (refreshToken != null) {
                    // Synchronously refresh token. We use runBlocking since intercept is called on a background thread.
                    // Note: We need the OAuthConfig to refresh. For now, we will throw an exception
                    // if we can't refresh, since we need the OAuthConfig which is stored in AgentEntity.
                    // This will be handled in the UI/ViewModel layer catching the AuthException.
                    
                    // In a complete implementation, we'd inject AgentRepository here to get the config.
                    // For this step, if it's expired we just throw an exception to trigger re-auth.
                    throw IOException("Authentication expired. Please re-authenticate.")
                } else {
                    throw IOException("Authentication required. Please authenticate.")
                }
            }
        }

        val response = chain.proceed(request)

        // If server still returns 401, token is invalid
        if (response.code == 401) {
            if (agentId != null) {
                tokenStore.clearTokens(agentId)
                throw IOException("Authentication expired (401). Please re-authenticate.")
            }
        }

        return response
    }
}
