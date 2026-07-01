package com.ekam.baton.core.network.mcp

import com.ekam.baton.core.network.security.AgentSecurityConfigProvider
import com.ekam.baton.core.network.security.ConnectionSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class McpWebSocketTransport constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val securityManager: ConnectionSecurityManager,
    private val configProvider: AgentSecurityConfigProvider
) : McpTransport {

    override suspend fun initialize(endpointUrl: String, authHeader: String?): Result<JsonObject> {
        return Result.success(JsonObject(emptyMap())) 
    }

    override suspend fun listTools(endpointUrl: String, authHeader: String?): Result<List<McpTool>> {
        return Result.success(emptyList())
    }

    override suspend fun ping(endpointUrl: String): Boolean {
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val request = Request.Builder().url(endpointUrl).build()
        val webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                deferred.complete(true)
                webSocket.close(1000, "Ping complete")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                deferred.complete(false)
            }
        })
        return try {
            kotlinx.coroutines.withTimeout(3000) {
                deferred.await()
            }
        } catch (e: Exception) {
            webSocket.close(1001, "Timeout")
            false
        }
    }

    override suspend fun uploadFile(
        endpointUrl: String,
        authHeader: String?,
        uri: String,
        context: android.content.Context
    ): Result<String> {
        return Result.failure(Exception("File upload over WebSocket not yet implemented"))
    }

    override fun callTool(
        endpointUrl: String,
        authHeader: String?,
        toolName: String,
        arguments: JsonObject
    ): Flow<String> = callbackFlow {
        val toolCallParams = McpToolCallParams(name = toolName, arguments = arguments)
        val requestPayload = McpRequest(
            id = java.util.UUID.randomUUID().toString(),
            method = "tools/call",
            params = json.encodeToJsonElement(McpToolCallParams.serializer(), toolCallParams) as JsonObject
        )
        val rawPlaintext = json.encodeToString(requestPayload)

        val uri = android.net.Uri.parse(endpointUrl)
        val agentId = uri.getQueryParameter("agent") ?: "unknown"
        val clientId = uri.lastPathSegment ?: "unknown"

        val requestBuilder = Request.Builder().url(endpointUrl)
        authHeader?.let { requestBuilder.addHeader("Authorization", it) }

        val webSocket = okHttpClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            private var sharedSecret: ByteArray? = null
            private var privateKey: ByteArray? = null
            private var securityMode: String = "standard"

            override fun onOpen(webSocket: WebSocket, response: Response) {
                GlobalScope.launch(Dispatchers.IO) {
                    val details = configProvider.getSecurityConfig(agentId)
                    if (details != null && details.securityMode != "standard") {
                        securityMode = details.securityMode
                        val privKeyBase64 = details.clientPrivateKeyBase64
                        val privKeyIvBase64 = details.clientPrivateKeyIvBase64
                        val peerPubKeyHex = details.peerPublicKeyHex
                        
                        if (privKeyBase64 != null && privKeyIvBase64 != null && peerPubKeyHex != null) {
                            privateKey = securityManager.decryptPrivateKey(privKeyBase64, privKeyIvBase64)
                            sharedSecret = securityManager.deriveSharedSecret(privateKey!!, peerPubKeyHex)
                            
                            val timestamp = System.currentTimeMillis()
                            val nonce = java.util.UUID.randomUUID().toString().replace("-", "")
                            
                            val encrypted = securityManager.encryptPayload(rawPlaintext, sharedSecret!!, nonce, timestamp)
                            
                            val payloadJson = JSONObject().apply {
                                put("timestamp", timestamp.toString())
                                put("nonce", nonce)
                                put("ciphertext", encrypted.ciphertextBase64)
                                put("iv", encrypted.ivBase64)
                                if (securityMode == "signed" || securityMode == "sovereign") {
                                    put("signature", securityManager.computeSignature(timestamp, nonce, encrypted.ciphertextBase64, sharedSecret!!))
                                }
                            }
                            
                            val envelope = JSONObject().apply {
                                put("sender_id", clientId)
                                put("receiver_id", agentId)
                                val b64Payload = android.util.Base64.encodeToString(payloadJson.toString().toByteArray(), android.util.Base64.NO_WRAP)
                                put("payload", b64Payload)
                            }
                            
                            webSocket.send(envelope.toString())
                        } else {
                            close(Exception("Security keys not configured"))
                        }
                    } else {
                        val envelope = JSONObject().apply {
                            put("sender_id", clientId)
                            put("receiver_id", agentId)
                            val b64Payload = android.util.Base64.encodeToString(rawPlaintext.toByteArray(), android.util.Base64.NO_WRAP)
                            put("payload", b64Payload)
                        }
                        webSocket.send(envelope.toString())
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val envelope = JSONObject(text)
                    val senderId = envelope.optString("sender_id")
                    // T16: Validate sender_id matches the agent we expect to communicate with
                    if (senderId != agentId) {
                        throw Exception("Security violation: sender_id mismatch (expected $agentId, got $senderId)")
                    }
                    val payloadB64 = envelope.getString("payload")
                    val payloadStr = String(android.util.Base64.decode(payloadB64, android.util.Base64.NO_WRAP))
                    
                    if (securityMode != "standard" && sharedSecret != null) {
                        val payloadJson = JSONObject(payloadStr)
                        val ct = payloadJson.getString("ciphertext")
                        val iv = payloadJson.getString("iv")
                        val ts = payloadJson.getString("timestamp").toLong()
                        val nonce = payloadJson.getString("nonce")
                        
                        val decrypted = securityManager.decryptPayload(ct, iv, sharedSecret!!, nonce, ts)
                        trySend(decrypted)
                        webSocket.close(1000, "Done")
                    } else {
                        trySend(payloadStr)
                        webSocket.close(1000, "Done")
                    }
                } catch (e: Exception) {
                    close(e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })

        awaitClose {
            webSocket.cancel()
        }
    }
}
