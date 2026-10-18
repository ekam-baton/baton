package com.ekam.baton.core.network.mcp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

import kotlinx.serialization.Serializable

private val jsonParser = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
data class AttachmentDto(
    val mimeType: String,
    val dataBase64: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val fileId: String? = null,
    val uri: String? = null
)

@Serializable
data class PairRequest(
    val public_key_hex: String,
    val x25519_public_key: String
)

@Serializable
data class PairResponse(
    val status: String,
    val message: String,
    val relay_url: String? = null,
    val relay_token: String? = null,
    val agent_owner_id: String? = null,
    val agent_owner_name: String? = null,
    val x25519_public_key: String? = null,
    val ed25519_public_key: String? = null
)

data class PairResult(
    val relayUrl: String?,
    val relayToken: String?,
    val ownerId: String?,
    val ownerName: String?,
    val x25519PublicKey: String?,
    val ed25519PublicKey: String?
)

open class McpMessageSender constructor(
    private val connectionManager: McpConnectionManager,
    private val authorizationManager: ToolAuthorizationManager,
    private val context: android.content.Context?,
    private val httpClient: okhttp3.OkHttpClient
) {
    /**
     * @param agentId The ID of the agent to route to.
     * @param endpointUrl The base URL of the MCP server.
     * @param authHeader Optional authorization header.
     * @param conversationHistory Previous messages in the conversation mapped to McpMessageDto.
     * @param newUserMessage The new user prompt to append.
     */
    open suspend fun sendUserMessage(
        agentId: String,
        endpointUrl: String,
        authHeader: String?,
        conversationHistory: List<com.ekam.baton.core.network.dto.McpMessageDto>,
        newUserMessage: String,
        attachments: List<AttachmentDto> = emptyList(),
        relayUrl: String? = null,
        relayToken: String? = null
    ): Flow<String> = flow {
        val sessionResult = connectionManager.getOrCreateSession(agentId, endpointUrl, authHeader)
        if (sessionResult.isFailure) {
            throw java.io.IOException("Error connecting to MCP Agent: ${sessionResult.exceptionOrNull()?.message}")
        }

        val session = sessionResult.getOrNull() ?: throw java.io.IOException("Error: Session is null")

        val targetToolName = listOf("chat", "send_message", "complete", "generate").firstOrNull { desired ->
            session.availableTools.any { it.name == desired }
        }

        if (targetToolName == null) {
            throw IllegalStateException("Agent does not support chat/send_message tools. Available tools: ${session.availableTools.map { it.name }}")
        }

        val transport = connectionManager.getTransportForUrl(endpointUrl)

        val uploadedAttachments = mutableListOf<AttachmentDto>()
        for (att in attachments) {
            if (att.uri != null && att.fileId == null) {
                if (context == null) {
                    uploadedAttachments.add(att)
                    continue
                }
                val uploadResult = transport.uploadFile(endpointUrl, authHeader, att.uri, context, agentId)
                if (uploadResult.isSuccess) {
                    uploadedAttachments.add(att.copy(fileId = uploadResult.getOrNull()))
                } else {
                    throw java.io.IOException("Error uploading attachment: ${uploadResult.exceptionOrNull()?.message}")
                }
            } else {
                uploadedAttachments.add(att)
            }
        }

        // Build Arguments JSON
        val argumentsJson = buildJsonObject {
            put("message", newUserMessage)
            put("messages", buildJsonArray {
                conversationHistory.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    }
                }
            })
            if (uploadedAttachments.isNotEmpty()) {
                put("attachments", buildJsonArray {
                    uploadedAttachments.forEach { att ->
                        addJsonObject {
                            put("mimeType", att.mimeType)
                            att.dataBase64?.let { put("data", it) }
                            att.fileId?.let { put("fileId", it) }
                        }
                    }
                })
            }
        }

        raceEndpoints(endpointUrl, authHeader, relayUrl, relayToken, targetToolName, argumentsJson, agentId)
            .map { jsonString ->
                try {
                    val mcpResponse = jsonParser.decodeFromString<McpResponse>(jsonString)
                    if (mcpResponse.error != null) {
                        throw java.io.IOException("MCP Error: ${mcpResponse.error.message}")
                    } else {
                        val contentArray = mcpResponse.result?.get("content") as? kotlinx.serialization.json.JsonArray
                        val text = contentArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                        text?.get("text")?.jsonPrimitive?.content ?: "Success, but no text returned."
                    }
                } catch (e: Exception) {
                    if (e is java.io.IOException) throw e
                    if (jsonString.startsWith("Error:") || jsonString.startsWith("error:")) {
                        throw java.io.IOException(jsonString)
                    }
                    if (!jsonString.trim().startsWith("{")) {
                        jsonString
                    } else {
                        throw java.io.IOException("Parse error: ${e.message} in response: $jsonString")
                    }
                }
            }.collect { emit(it) }
    }

    open suspend fun pairWithAgent(endpointUrl: String, identityKeyHex: String, x25519KeyHex: String): Result<PairResult> {
        return try {
            val jsonBody = jsonParser.encodeToString(PairRequest.serializer(), PairRequest(identityKeyHex, x25519KeyHex))
            val requestBody = jsonBody.toByteArray().toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val pairUrl = if (endpointUrl.endsWith("/")) "${endpointUrl}pair" else "$endpointUrl/pair"
            val request = okhttp3.Request.Builder()
                .url(pairUrl)
                .post(requestBody)
                .build()
                
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val pairResponse = jsonParser.decodeFromString(PairResponse.serializer(), body)
                    Result.success(PairResult(
                        relayUrl = pairResponse.relay_url,
                        relayToken = pairResponse.relay_token,
                        ownerId = pairResponse.agent_owner_id,
                        ownerName = pairResponse.agent_owner_name,
                        x25519PublicKey = pairResponse.x25519_public_key,
                        ed25519PublicKey = pairResponse.ed25519_public_key
                    ))
                } else {
                    Result.failure(Exception("Pairing failed with status: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun executeTool(
        agentId: String,
        endpointUrl: String,
        authHeader: String?,
        toolName: String,
        arguments: JsonObject,
        relayUrl: String? = null,
        relayToken: String? = null
    ): Flow<String> = flow {
        val sessionResult = connectionManager.getOrCreateSession(agentId, endpointUrl, authHeader)
        if (sessionResult.isFailure) {
            throw java.io.IOException("Error connecting to MCP Agent: ${sessionResult.exceptionOrNull()?.message}")
        }

        val isApproved = authorizationManager.requestAuthorization(toolName, arguments)
        if (!isApproved) {
            throw SecurityException("Tool execution was denied by the user.")
        }

        raceEndpoints(endpointUrl, authHeader, relayUrl, relayToken, toolName, arguments, agentId)
            .map { jsonString ->
                try {
                    val mcpResponse = jsonParser.decodeFromString<McpResponse>(jsonString)
                    if (mcpResponse.error != null) {
                        throw java.io.IOException("MCP Error: ${mcpResponse.error.message}")
                    } else {
                        val contentArray = mcpResponse.result?.get("content") as? kotlinx.serialization.json.JsonArray
                        val text = contentArray?.firstOrNull() as? JsonObject
                        text?.get("text")?.jsonPrimitive?.content ?: "Success, but no text returned."
                    }
                } catch (e: Exception) {
                    if (e is java.io.IOException) throw e
                    if (jsonString.startsWith("Error:") || jsonString.startsWith("error:")) {
                        throw java.io.IOException(jsonString)
                    }
                    if (!jsonString.trim().startsWith("{")) {
                        jsonString
                    } else {
                        throw java.io.IOException("Parse error: ${e.message} in response: $jsonString")
                    }
                }
            }.collect { emit(it) }
    }

    private fun raceEndpoints(
        localUrl: String,
        localAuth: String?,
        relayUrl: String?,
        relayToken: String?,
        toolName: String,
        arguments: JsonObject,
        agentId: String? = null
    ): Flow<String> = flow {
        val relayAuthHeader = relayToken?.let { "Bearer $it" }
        
        if (relayUrl == null) {
            val transport = connectionManager.getTransportForUrl(localUrl)
            transport.callTool(localUrl, localAuth, toolName, arguments, agentId).collect { emit(it) }
            return@flow
        }

        kotlinx.coroutines.coroutineScope {
            val outputChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
            val won = java.util.concurrent.atomic.AtomicBoolean(false)
            val localFailed = kotlinx.coroutines.CompletableDeferred<Unit>()

            var relayJob: kotlinx.coroutines.Job? = null

            val localJob = launch {
                try {
                    val transport = connectionManager.getTransportForUrl(localUrl)
                    val localFlow = transport.callTool(localUrl, localAuth, toolName, arguments, agentId)
                    var firstEmitted = false
                    localFlow.collect { token ->
                        if (!firstEmitted) {
                            if (won.compareAndSet(false, true)) {
                                firstEmitted = true
                                relayJob?.cancel()
                            } else {
                                return@collect
                            }
                        }
                        outputChannel.send(token)
                    }
                    if (won.compareAndSet(false, true)) {
                        relayJob?.cancel()
                    }
                    outputChannel.close()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    localFailed.complete(Unit)
                    if (won.get() || (relayJob?.isCompleted == true && !won.get())) {
                        outputChannel.close(e)
                    }
                }
            }

            relayJob = launch {
                kotlinx.coroutines.withTimeoutOrNull(800) {
                    localFailed.await()
                }
                if (won.get()) return@launch

                try {
                    val transport = connectionManager.getTransportForUrl(relayUrl)
                    val relayFlow = transport.callTool(relayUrl, relayAuthHeader, toolName, arguments, agentId)
                    var firstEmitted = false
                    relayFlow.collect { token ->
                        if (!firstEmitted) {
                            if (won.compareAndSet(false, true)) {
                                firstEmitted = true
                                localJob.cancel()
                            } else {
                                return@collect
                            }
                        }
                        outputChannel.send(token)
                    }
                    if (won.compareAndSet(false, true)) {
                        localJob.cancel()
                    }
                    outputChannel.close()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!won.get()) {
                        if (localFailed.isCompleted) {
                            outputChannel.close(java.io.IOException("Both local and relay endpoints failed: ${e.message}", e))
                        }
                    } else {
                        outputChannel.close(e)
                    }
                }
            }

            try {
                for (token in outputChannel) {
                    emit(token)
                }
            } finally {
                localJob.cancel()
                relayJob.cancel()
            }
        }
    }

    open suspend fun getAvailableTools(agentId: String, endpointUrl: String, authHeader: String?): List<McpTool> {
        val sessionResult = connectionManager.getOrCreateSession(agentId, endpointUrl, authHeader)
        return sessionResult.getOrNull()?.availableTools ?: emptyList()
    }
}

