package com.ekam.baton.core.network.security

import kotlinx.coroutines.runBlocking
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

interface AgentSecurityConfigProvider {
    suspend fun getSecurityConfig(agentId: String): AgentSecurityDetails?
}

data class AgentSecurityDetails(
    val securityMode: String,
    val clientPrivateKeyBase64: String?,
    val clientPrivateKeyIvBase64: String?,
    val peerPublicKeyHex: String?,
    val certificatePins: List<String>
)

class SecurityInterceptor(
    private val securityManager: ConnectionSecurityManager,
    private val configProvider: AgentSecurityConfigProvider
) : Interceptor {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val agentId = request.header("X-Baton-Agent-Id") ?: return chain.proceed(request)

        // Fetch security details from provider (using runBlocking since intercept is synchronous)
        val details = runBlocking {
            configProvider.getSecurityConfig(agentId)
        } ?: return chain.proceed(request)

        val mode = details.securityMode

        // 1. Dynamic Certificate Pinning Verification (for modes 2-4)
        if (mode != "standard" && details.certificatePins.isNotEmpty()) {
            val connection = chain.connection()
            val handshake = connection?.handshake()
            if (handshake != null) {
                val hostname = request.url.host
                val pinnerBuilder = CertificatePinner.Builder()
                for (pin in details.certificatePins) {
                    // Make sure pin has the format "sha256/..."
                    val formattedPin = if (pin.startsWith("sha256/")) pin else "sha256/$pin"
                    pinnerBuilder.add(hostname, formattedPin)
                }
                val pinner = pinnerBuilder.build()
                try {
                    pinner.check(hostname, handshake.peerCertificates)
                } catch (e: Exception) {
                    throw IOException("Certificate pinning verification failed for $hostname: ${e.localizedMessage}", e)
                }
            }
        }

        if (mode == "standard") {
            return chain.proceed(request)
        }

        // Encryption is active for Secured (mode 2), Signed (mode 3), Sovereign (mode 4)
        val privKeyBase64 = details.clientPrivateKeyBase64
        val privKeyIvBase64 = details.clientPrivateKeyIvBase64
        val peerPubKeyHex = details.peerPublicKeyHex

        if (privKeyBase64 == null || privKeyIvBase64 == null || peerPubKeyHex == null) {
            throw IOException("Security keys not configured for agent in mode $mode")
        }

        // Derive shared secret
        val privateKey = securityManager.decryptPrivateKey(privKeyBase64, privKeyIvBase64)
        val sharedSecret = securityManager.deriveSharedSecret(privateKey, peerPubKeyHex)

        // Read request body to encrypt
        val rawBody = request.body
        val rawPlaintext = if (rawBody != null) {
            val buffer = okio.Buffer()
            rawBody.writeTo(buffer)
            buffer.readUtf8()
        } else {
            ""
        }

        if (rawPlaintext.isBlank()) {
            return chain.proceed(request)
        }

        // Always generate a nonce and timestamp for encrypted requests
        val timestamp = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString().replace("-", "")

        // Encrypt request body
        val encrypted = securityManager.encryptPayload(rawPlaintext, sharedSecret, nonce, timestamp)
        val encryptedJson = JSONObject().apply {
            put("ciphertext", encrypted.ciphertextBase64)
            put("iv", encrypted.ivBase64)
        }.toString()

        var newRequestBuilder = request.newBuilder()
            .post(encryptedJson.toRequestBody(jsonMediaType))
            .addHeader("X-Baton-Timestamp", timestamp.toString())
            .addHeader("X-Baton-Nonce", nonce)

        // If in Signed or Sovereign mode, add replay signature headers
        if (mode == "signed" || mode == "sovereign") {
            val signature = securityManager.computeSignature(
                timestamp,
                nonce,
                encrypted.ciphertextBase64,
                sharedSecret
            )
            newRequestBuilder = newRequestBuilder.addHeader("X-Baton-Signature", signature)
        }

        val encryptedRequest = newRequestBuilder.build()
        val response = chain.proceed(encryptedRequest)

        if (!response.isSuccessful) {
            privateKey.fill(0)
            sharedSecret.fill(0)
            return response
        }

        // Decrypt response body
        val responseBodyString = response.body?.string() ?: ""
        if (responseBodyString.isBlank()) {
            privateKey.fill(0)
            sharedSecret.fill(0)
            return response
        }

        val decryptedPlaintext = try {
            val jsonResponse = JSONObject(responseBodyString)
            val ciphertext = jsonResponse.getString("ciphertext")
            val iv = jsonResponse.getString("iv")
            securityManager.decryptPayload(ciphertext, iv, sharedSecret, nonce, timestamp)
        } catch (e: Exception) {
            throw IOException("Failed to decrypt response payload: ${e.localizedMessage}", e)
        } finally {
            privateKey.fill(0)
            sharedSecret.fill(0)
        }

        val newResponseBody = decryptedPlaintext.toResponseBody(response.body?.contentType())
        return response.newBuilder()
            .body(newResponseBody)
            .build()
    }
}
