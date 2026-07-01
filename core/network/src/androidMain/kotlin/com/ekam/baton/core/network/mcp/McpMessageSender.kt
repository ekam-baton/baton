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
    val public_key_hex: String
)

@Serializable
data class PairResponse(
    val status: String,
    val message: String,
    val relay_url: String? = null,
    val relay_token: String? = null
)

class McpMessageSender constructor(
    private val connectionManager: McpConnectionManager,
    private val authorizationManager: ToolAuthorizationManager,
    private val context: android.content.Context
) {
    /**
     * @param agentId The ID of the agent to route to.
     * @param endpointUrl The base URL of the MCP server.
     * @param authHeader Optional authorization header.
     * @param conversationHistory Previous messages in the conversation mapped to McpMessageDto.
     * @param newUserMessage The new user prompt to append.
     */
    suspend fun sendUserMessage(
        agentId: String,
        endpointUrl: String,
        authHeader: String?,
        conversationHistory: List<com.ekam.baton.core.network.dto.McpMessageDto>,
        newUserMessage: String,
        attachments: List<AttachmentDto> = emptyList(),
        relayUrl: String? = null,
        relayToken: String? = null
    ): Flow<String> {
        val sessionResult = connectionManager.getOrCreateSession(agentId, endpointUrl, authHeader)
        if (sessionResult.isFailure) {
            return flow { emit("Error connecting to MCP Agent: ${sessionResult.exceptionOrNull()?.message}") }
        }

        val session = sessionResult.getOrNull() ?: return flow { emit("Error: Session is null") }

        val targetToolName = listOf("chat", "send_message", "complete", "generate").firstOrNull { desired ->
            session.availableTools.any { it.name == desired }
        }

        if (targetToolName == null) {
            return flow { emit("Error: Agent does not support chat/send_message tools. Available tools: ${session.availableTools.map { it.name }}") }
        }

        val transport = connectionManager.getTransportForUrl(endpointUrl)

        val uploadedAttachments = attachments.map { att ->
            if (att.uri != null && att.fileId == null) {
                val uploadResult = transport.uploadFile(endpointUrl, authHeader, att.uri, context)
                if (uploadResult.isSuccess) {
                    att.copy(fileId = uploadResult.getOrNull())
                } else {
                    // FIX: Return a special error attachment so the caller knows the upload failed
                    return flow { emit("[Upload Error] Failed to upload '${att.fileName ?: att.uri}': ${uploadResult.exceptionOrNull()?.message}") }
                }
            } else {
                att
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

        return raceEndpoints(endpointUrl, authHeader, relayUrl, relayToken, targetToolName, argumentsJson)
            .map { jsonString ->
                try {
                    // FIX: Reuse the injected Json instance — no per-event object creation
                    val mcpResponse = kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true; isLenient = true
                    }.decodeFromString<McpResponse>(jsonString)
                    if (mcpResponse.error != null) {
                        "Error: ${mcpResponse.error.message}"
                    } else {
                        val contentArray = mcpResponse.result?.get("content") as? kotlinx.serialization.json.JsonArray
                        val text = contentArray?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                        text?.get("text")?.jsonPrimitive?.content ?: "Success, but no text returned."
                    }
                } catch (e: Exception) {
                    "Parse Error: ${e.message ?: e.toString()}\nRaw: $jsonString"
                }
            }
    }

    suspend fun pairWithAgent(endpointUrl: String, publicKeyHex: String): Result<Pair<String?, String?>> {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val jsonBody = json.encodeToString(PairRequest.serializer(), PairRequest(publicKeyHex))
            val requestBody = jsonBody.toByteArray().toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val pairUrl = if (endpointUrl.endsWith("/")) "${endpointUrl}pair" else "$endpointUrl/pair"
            val request = okhttp3.Request.Builder()
                .url(pairUrl)
                .post(requestBody)
                .build()
                
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute()
            }
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val pairResponse = json.decodeFromString(PairResponse.serializer(), body)
                Result.success(Pair(pairResponse.relay_url, pairResponse.relay_token))
            } else {
                Result.failure(Exception("Pairing failed with status: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeTool(
        agentId: String,
        endpointUrl: String,
        authHeader: String?,
        toolName: String,
        arguments: JsonObject,
        relayUrl: String? = null,
        relayToken: String? = null
    ): Flow<String> {
        val sessionResult = connectionManager.getOrCreateSession(agentId, endpointUrl, authHeader)
        if (sessionResult.isFailure) {
            return flow { emit("Error connecting to MCP Agent: ${sessionResult.exceptionOrNull()?.message}") }
        }

        val isApproved = authorizationManager.requestAuthorization(toolName, arguments)
        if (!isApproved) {
            return flow { emit("Error: Tool execution was denied by the user.") }
        }

        return raceEndpoints(endpointUrl, authHeader, relayUrl, relayToken, toolName, arguments)
            .map { jsonString ->
                try {
                    val parser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
                    val mcpResponse = parser.decodeFromString<McpResponse>(jsonString)
                    if (mcpResponse.error != null) {
                        "Error: ${mcpResponse.error.message}"
                    } else {
                        val contentArray = mcpResponse.result?.get("content") as? kotlinx.serialization.json.JsonArray
                        val text = contentArray?.firstOrNull() as? JsonObject
                        text?.get("text")?.jsonPrimitive?.content ?: "Success, but no text returned."
                    }
                } catch (e: Exception) {
                    "Parse Error: ${e.message ?: e.toString()}\nRaw: $jsonString"
                }
            }
    }

    private fun raceEndpoints(
        localUrl: String,
        localAuth: String?,
        relayUrl: String?,
        relayToken: String?,
        toolName: String,
        arguments: JsonObject
    ): Flow<String> = flow {
        val relayAuthHeader = relayToken?.let { "Bearer $it" }
        
        if (relayUrl == null) {
            val transport = connectionManager.getTransportForUrl(localUrl)
            transport.callTool(localUrl, localAuth, toolName, arguments).collect { emit(it) }
            return@flow
        }

        kotlinx.coroutines.coroutineScope {
            val deferredResult = kotlinx.coroutines.CompletableDeferred<Flow<String>>()
            
            val localJob = launch {
                try {
                    val transport = connectionManager.getTransportForUrl(localUrl)
                    val f = transport.callTool(localUrl, localAuth, toolName, arguments)
                    
                    val channel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                    f.collect { channel.send(it) }
                    channel.close()
                    
                    deferredResult.complete(flow {
                        for (item in channel) emit(item)
                    })
                } catch (e: Exception) {
                    if (!deferredResult.isCompleted) {
                        try {
                            val transport = connectionManager.getTransportForUrl(relayUrl)
                            val f = transport.callTool(relayUrl, relayAuthHeader, toolName, arguments)
                            deferredResult.complete(f)
                        } catch (re: Exception) {
                            deferredResult.completeExceptionally(e)
                        }
                    }
                }
            }

            val relayJob = launch {
                delay(800)
                if (!deferredResult.isCompleted) {
                    try {
                        val transport = connectionManager.getTransportForUrl(relayUrl)
                        val f = transport.callTool(relayUrl, relayAuthHeader, toolName, arguments)
                        
                        val channel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                        f.collect { channel.send(it) }
                        channel.close()
                        
                        deferredResult.complete(flow {
                            for (item in channel) emit(item)
                        })
                    } catch (e: Exception) {
                        // ignore or handle
                    }
                }
            }

            val winner = try {
                deferredResult.await()
            } catch (e: Exception) {
                throw e
            } finally {
                localJob.cancel()
                relayJob.cancel()
            }
            winner.collect { emit(it) }
        }
    }

    suspend fun getAvailableTools(agentId: String, endpointUrl: String, authHeader: String?): List<McpTool> {
        val sessionResult = connectionManager.getOrCreateSession(agentId, endpointUrl, authHeader)
        return sessionResult.getOrNull()?.availableTools ?: emptyList()
    }
}
