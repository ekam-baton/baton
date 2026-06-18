package com.ekam.baton.core.network.tunnel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TunnelEndpointValidator @Inject constructor(
    private val json: Json
) {
    // We create a custom OkHttpClient with a 10s timeout just for validation
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun validateEndpoint(urlString: String): TunnelValidationResult = withContext(Dispatchers.IO) {
        // a. Parse URL
        if (!urlString.startsWith("https://") && !urlString.startsWith("http://")) {
            return@withContext TunnelValidationResult(Status.INVALID_URL, null, null, "URL must start with http:// or https://")
        }

        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            return@withContext TunnelValidationResult(Status.INVALID_URL, null, null, "Malformed URL")
        }

        val baseUrl = urlString.removeSuffix("/")

        // b. Check if hostname is valid (tunnel)
        val host = url.host
        val isTunnel = host.endsWith(".trycloudflare.com") || host.endsWith(".ngrok.io") || 
                host.endsWith(".ngrok-free.app") || host.endsWith(".bore.pub") || host == "localhost"

        // c. Send GET to URL
        val getRequest = Request.Builder()
            .url(baseUrl)
            .get()
            .build()

        try {
            client.newCall(getRequest).execute().use { response ->
                if (!response.isSuccessful && response.code != 404 && response.code != 405) {
                    // Sometimes root path returns 404 or 405 Method Not Allowed on MCP servers, which is still REACHABLE.
                    // Let's just be lenient if we got a response at all, it's reachable.
                }
            }
        } catch (e: Exception) {
            return@withContext TunnelValidationResult(Status.UNREACHABLE, null, null, e.localizedMessage)
        }

        // d. Check for MCP capability - send POST to base URL (JSON-RPC)
        // Some servers expect it at /, some at /message
        val initializePayload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", UUID.randomUUID().toString())
            put("method", "initialize")
            put("params", buildJsonObject {
                put("protocolVersion", "2024-11-05")
                put("capabilities", buildJsonObject {})
                put("clientInfo", buildJsonObject {
                    put("name", "baton-validator")
                    put("version", "1.0")
                })
            })
        }

        val requestBody = initializePayload.toString().toRequestBody("application/json".toMediaType())
        val postRequest = Request.Builder()
            .url(baseUrl)
            .post(requestBody)
            .build()

        try {
            client.newCall(postRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    try {
                        val jsonResponse = json.parseToJsonElement(bodyString).jsonObject
                        if (jsonResponse.containsKey("jsonrpc") && jsonResponse["jsonrpc"]?.toString()?.replace("\"", "") == "2.0") {
                            // Valid MCP
                            return@withContext TunnelValidationResult(Status.VALID, host, emptyList(), null)
                        }
                    } catch (e: Exception) {
                        // Not JSON-RPC
                    }
                }
            }
        } catch (e: Exception) {
            // Error on POST
        }

        // e. If HTTP reachable but no MCP
        return@withContext TunnelValidationResult(Status.REACHABLE_NO_MCP, null, null, "HTTP reachable but no MCP detected")
    }
}
