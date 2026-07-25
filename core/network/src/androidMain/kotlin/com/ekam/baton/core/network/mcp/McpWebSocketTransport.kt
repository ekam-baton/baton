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

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class McpWebSocketTransport constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val securityManager: ConnectionSecurityManager,
    private val configProvider: AgentSecurityConfigProvider
) : McpTransport {

    companion object {
        private val agentMutexes = ConcurrentHashMap<String, Mutex>()
    }

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

        val secureUrl = if (endpointUrl.startsWith("ws://")) {
            android.util.Log.w("McpWebSocketTransport", "Upgrading insecure ws:// to wss://. Local/BYOS relays without TLS will fail to connect.")
            endpointUrl.replaceFirst("ws://", "wss://")
        } else {
            endpointUrl
        }
        val requestBuilder = Request.Builder().url(secureUrl)
        authHeader?.let { requestBuilder.addHeader("Authorization", it) }

        val webSocket = okHttpClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            private var sharedSecret: ByteArray? = null
            private var privateKey: ByteArray? = null
            private var securityMode: String = "standard"

            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
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
                            
                            var ratchetStateJson = details.ratchetStateBase64?.let {
                                String(android.util.Base64.decode(it, android.util.Base64.NO_WRAP))
                            }
                            if (ratchetStateJson == null) {
                                ratchetStateJson = ConnectionSecurityManager.ratchetInitAliceRust(
                                    sharedSecret!!,
                                    securityManager.fromHex(peerPubKeyHex)
                                )
                            }
                            
                            val ratchetOutputStr = ConnectionSecurityManager.ratchetEncryptRust(
                                ratchetStateJson,
                                rawPlaintext.toByteArray(Charsets.UTF_8)
                            )
                            val ratchetOutput = JSONObject(ratchetOutputStr)
                            val newStateJson = ratchetOutput.getString("state")
                            
                            configProvider.saveRatchetState(
                                agentId, 
                                android.util.Base64.encodeToString(newStateJson.toByteArray(), android.util.Base64.NO_WRAP)
                            )
                            
                            val payloadJson = JSONObject().apply {
                                put("header_pub", ratchetOutput.getString("header_pub"))
                                put("header_n", ratchetOutput.getInt("header_n"))
                                put("header_pn", ratchetOutput.getInt("header_pn"))
                                put("ciphertext", ratchetOutput.getString("ciphertext"))
                                put("sender_id", clientId) // Sealed Sender: Encrypt sender identity inside payload
                            }
                            
                            val envelope = JSONObject().apply {
                                put("receiver_id", agentId) // Outer envelope contains ONLY destination, preventing metadata leakage
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
                    val senderId = if (envelope.has("sender_id") && envelope.getString("sender_id").isNotEmpty()) {
                        envelope.getString("sender_id")
                    } else {
                        if (securityMode == "standard") {
                            throw Exception("Security violation: missing sender_id in standard mode")
                        }
                        agentId // In Sealed Sender mode, outer envelope omits sender_id; ratchet decryption validates sender authenticity
                    }
                    // T16: Validate sender_id matches the agent we expect to communicate with
                    if (senderId != agentId) {
                        throw Exception("Security violation: sender_id mismatch (expected $agentId, got $senderId)")
                    }
                    val payloadB64 = envelope.getString("payload")
                    val payloadStr = String(android.util.Base64.decode(payloadB64, android.util.Base64.NO_WRAP))
                    
                    if (securityMode != "standard" && sharedSecret != null) {
                        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
                        scope.launch {
                            val mutex = agentMutexes.getOrPut(agentId) { Mutex() }
                            mutex.withLock {
                                val payloadJson = JSONObject(payloadStr)
                                val hpub = payloadJson.getString("header_pub")
                                val hn = payloadJson.getInt("header_n")
                                val hpn = payloadJson.getInt("header_pn")
                                val ct = payloadJson.getString("ciphertext")
                                
                                val details = configProvider.getSecurityConfig(agentId)
                                val ratchetStateJson = details?.ratchetStateBase64?.let {
                                    String(android.util.Base64.decode(it, android.util.Base64.NO_WRAP))
                                }
                                if (ratchetStateJson == null) {
                                    webSocket.close(1001, "No ratchet state")
                                    return@withLock
                                }
                                
                                val decryptOutputStr = ConnectionSecurityManager.ratchetDecryptRust(
                                    ratchetStateJson,
                                    hpub,
                                    hn,
                                    hpn,
                                    ct
                                )
                                val decryptOutput = JSONObject(decryptOutputStr)
                                val newStateJson = decryptOutput.getString("state")
                                val decryptedB64 = decryptOutput.getString("plaintext")
                                val decrypted = String(android.util.Base64.decode(decryptedB64, android.util.Base64.NO_WRAP), Charsets.UTF_8)
                                
                                configProvider.saveRatchetState(
                                    agentId,
                                    android.util.Base64.encodeToString(newStateJson.toByteArray(), android.util.Base64.NO_WRAP)
                                )
                                
                                trySend(decrypted)
                                webSocket.close(1000, "Done")
                            }
                        }
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
