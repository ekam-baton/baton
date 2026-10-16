package com.ekam.baton.core.network.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

open class VaultRepository constructor(
    private val okHttpClient: OkHttpClient? = null,
    private val tunnelEndpointValidator: com.ekam.baton.core.network.tunnel.TunnelEndpointValidator? = null
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    open suspend fun uploadVault(endpointUrl: String, authHeader: String?, clientId: String, vaultData: String): Result<Unit> = withContext(Dispatchers.IO) {
        val validator = tunnelEndpointValidator ?: return@withContext Result.failure(IllegalStateException("Validator not initialized"))
        val client = okHttpClient ?: return@withContext Result.failure(IllegalStateException("Client not initialized"))
        if (!validator.isUrlSafe(endpointUrl)) {
            return@withContext Result.failure(Exception("SSRF Protection: Endpoint resolves to a private or reserved address."))
        }
        try {
            val json = JSONObject().apply {
                put("client_id", clientId)
                put("vault_data", vaultData)
            }
            val requestBody = json.toString().toRequestBody(jsonMediaType)
            
            val url = if (endpointUrl.endsWith("/")) "${endpointUrl}admin/api/vault" else "$endpointUrl/admin/api/vault"
            
            val requestBuilder = Request.Builder().url(url).post(requestBody)
            authHeader?.let { requestBuilder.addHeader("Authorization", it) }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to upload vault: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun downloadVault(endpointUrl: String, authHeader: String?, clientId: String): Result<String> = withContext(Dispatchers.IO) {
        val validator = tunnelEndpointValidator ?: return@withContext Result.failure(IllegalStateException("Validator not initialized"))
        val client = okHttpClient ?: return@withContext Result.failure(IllegalStateException("Client not initialized"))
        if (!validator.isUrlSafe(endpointUrl)) {
            return@withContext Result.failure(Exception("SSRF Protection: Endpoint resolves to a private or reserved address."))
        }
        try {
            val url = if (endpointUrl.endsWith("/")) "${endpointUrl}admin/api/vault/$clientId" else "$endpointUrl/admin/api/vault/$clientId"
            val requestBuilder = Request.Builder().url(url).get()
            authHeader?.let { requestBuilder.addHeader("Authorization", it) }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        Result.success(json.getString("vault_data"))
                    } else {
                        Result.failure(Exception("Empty response body"))
                    }
                } else if (response.code == 404) {
                    Result.failure(Exception("No vault found for this client"))
                } else {
                    Result.failure(Exception("Failed to download vault: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
