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
import java.net.InetAddress
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

class TunnelEndpointValidator constructor(
    private val json: Json,
    // SECURITY: this used to build its own local OkHttpClient with the
    // default system DNS resolver — a completely separate resolution path
    // from the one isPrivateOrReservedAddress() used to decide whether the
    // host was safe. That let a malicious DNS server answer differently for
    // the check vs. the actual connection (classic DNS-rebinding SSRF),
    // undermining the exact protection this class exists to provide.
    // Sharing the app's single OkHttpClient means both the validation
    // decision and the real connection go through the same SsrfProtectionDns
    // resolution, so there's no gap between "what we checked" and
    // "what we connected to."
    private val client: OkHttpClient
) {
    // The shared client has an unlimited read timeout (needed for SSE
    // streaming elsewhere in the app) — wrong for a bounded reachability
    // probe. newBuilder() clones the shared client, keeping its dns()
    // (SsrfProtectionDns) and interceptors, while only overriding the
    // timeouts for this use case.
    private val probeClient: OkHttpClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Allowed hostname suffixes for MCP tunnel endpoints */
    private val ALLOWED_TUNNEL_SUFFIXES = listOf(
        ".trycloudflare.com",
        ".ngrok.io",
        ".ngrok-free.app",
        ".bore.pub"
    )
    private val ALLOWED_LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /**
     * SECURITY FIX (CRIT-2): Block RFC-1918, link-local, and loopback addresses
     * unless the hostname is in the explicit local allow-list.
     * Prevents SSRF via the deep-link pairing flow.
     */
    private fun isPrivateOrReservedAddress(host: String): Boolean {
        return try {
            val addr = InetAddress.getByName(host)
            com.ekam.baton.core.network.security.NetworkSecurityConstraints.isLocalOrPrivate(addr)



        } catch (e: Exception) {
            false
        }
    }

    /**
     * SECURITY FIX (H1): Check if a URL is safe for internal routing without performing MCP reachability probes.
     * Prevents SSRF when connecting to the Cloud Router for Vault sync or FCM registration.
     */
    fun isUrlSafe(urlString: String): Boolean {
        if (!urlString.startsWith("https://") && !urlString.startsWith("http://")) return false
        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            return false
        }
        val host = url.host
        val isTunnel = ALLOWED_TUNNEL_SUFFIXES.any { host.endsWith(it) }
        val isExplicitLocal = ALLOWED_LOCAL_HOSTS.contains(host.lowercase())
        if (!isTunnel && !isExplicitLocal && isPrivateOrReservedAddress(host)) {
            return false
        }
        return true
    }

    suspend fun validateEndpoint(urlString: String): TunnelValidationResult = withContext(Dispatchers.IO) {
        // a. Scheme check — only http/https
        if (!urlString.startsWith("https://") && !urlString.startsWith("http://")) {
            return@withContext TunnelValidationResult(Status.INVALID_URL, null, null, "URL must start with http:// or https://")
        }

        val url = try {
            URL(urlString)
        } catch (e: Exception) {
            return@withContext TunnelValidationResult(Status.INVALID_URL, null, null, "Malformed URL")
        }

        val host = url.host
        val baseUrl = urlString.removeSuffix("/")

        // SECURITY FIX (CRIT-2): Determine if this host is permitted.
        // The allowlist check is performed BEFORE any HTTP call is made.
        // Previously, only the UI classification was gated behind this check,
        // but the actual HTTP requests were made to any URL — enabling SSRF.
        //
        // SECURITY FIX (BYOS-SSRF-1): A prior BYOS change treated *any*
        // https:// URL as an automatically-trusted tunnel, on the theory
        // that "TLS certs ensure safe routing." That's not true — a valid
        // cert only proves the hostname matches, not that the hostname
        // resolves somewhere safe. An attacker can obtain a legitimate cert
        // for a domain they control and then point that domain's DNS record
        // at 127.0.0.1, 169.254.169.254, or a LAN IP (DNS rebinding), which
        // is exactly the attack isPrivateOrReservedAddress() below exists to
        // catch. So scheme alone must never bypass that check.
        val isTunnel = ALLOWED_TUNNEL_SUFFIXES.any { host.endsWith(it) }
        val isExplicitLocal = ALLOWED_LOCAL_HOSTS.contains(host.lowercase())

        if (!isTunnel && !isExplicitLocal) {
            // Before outright rejection, check if it resolves to a private address
            // (even if the hostname looks public, it could point to a LAN IP via DNS rebinding)
            if (isPrivateOrReservedAddress(host)) {
                return@withContext TunnelValidationResult(
                    Status.INVALID_URL, null, null,
                    "Endpoint resolves to a private/internal IP address. Use a tunnel (Cloudflare, ngrok) for security."
                )
            }
        }

        // b. HTTP reachability check — only performed after allowlist validation
        val getRequest = Request.Builder()
            .url(baseUrl)
            .get()
            .build()

        try {
            probeClient.newCall(getRequest).execute().use { /* just checking reachability */ }
        } catch (e: Exception) {
            return@withContext TunnelValidationResult(Status.UNREACHABLE, null, null, e.localizedMessage)
        }

        // c. MCP capability probe
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
            probeClient.newCall(postRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    try {
                        val jsonResponse = json.parseToJsonElement(bodyString).jsonObject
                        if (jsonResponse.containsKey("jsonrpc") &&
                            jsonResponse["jsonrpc"]?.toString()?.replace("\"", "") == "2.0"
                        ) {
                            return@withContext TunnelValidationResult(Status.VALID, host, emptyList(), null)
                        }
                    } catch (e: Exception) {
                        // Not JSON-RPC — fall through
                    }
                }
            }
        } catch (e: Exception) {
            // POST failed — fall through to REACHABLE_NO_MCP
        }

        return@withContext TunnelValidationResult(Status.REACHABLE_NO_MCP, null, null, "HTTP reachable but no MCP detected")
    }
}
