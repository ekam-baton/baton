package com.ekam.baton.core.network.mcp

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpSseMcpTransport @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : McpTransport {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun initialize(endpointUrl: String, authHeader: String?): Result<JsonObject> {
        return try {
            val initParams = McpInitializeParams(
                capabilities = buildJsonObject { },
                clientInfo = buildJsonObject { }
            )
            val requestPayload = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "initialize",
                params = json.encodeToJsonElement(McpInitializeParams.serializer(), initParams) as JsonObject
            )

            val body = json.encodeToString(requestPayload).toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder().url("$endpointUrl/initialize").post(body)
            authHeader?.let { requestBuilder.addHeader("Authorization", it) }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return Result.failure(IOException("Initialize failed: ${response.code}"))

            val responseBody = response.body?.string() ?: return Result.failure(IOException("Empty body"))
            val mcpResponse = json.decodeFromString<McpResponse>(responseBody)

            if (mcpResponse.error != null) {
                return Result.failure(IOException("MCP Error: ${mcpResponse.error.message}"))
            }

            // Send notifications/initialized
            val notifPayload = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "notifications/initialized",
                params = null
            )
            val notifBody = json.encodeToString(notifPayload).toRequestBody(jsonMediaType)
            val notifBuilder = Request.Builder().url("$endpointUrl/notifications/initialized").post(notifBody)
            authHeader?.let { notifBuilder.addHeader("Authorization", it) }
            okHttpClient.newCall(notifBuilder.build()).execute() // Ignore response

            Result.success(mcpResponse.result ?: buildJsonObject { })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listTools(endpointUrl: String, authHeader: String?): Result<List<McpTool>> {
        return try {
            val requestPayload = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/list",
                params = null
            )
            val body = json.encodeToString(requestPayload).toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder().url("$endpointUrl/tools/list").post(body)
            authHeader?.let { requestBuilder.addHeader("Authorization", it) }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) return Result.failure(IOException("List tools failed: ${response.code}"))

            val responseBody = response.body?.string() ?: return Result.failure(IOException("Empty body"))
            val mcpResponse = json.decodeFromString<McpResponse>(responseBody)
            
            if (mcpResponse.error != null) {
                return Result.failure(IOException("MCP Error: ${mcpResponse.error.message}"))
            }

            val toolsArray = mcpResponse.result?.get("tools")
            if (toolsArray != null) {
                val tools = json.decodeFromJsonElement<List<McpTool>>(toolsArray)
                Result.success(tools)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun callTool(
        endpointUrl: String,
        authHeader: String?,
        toolName: String,
        arguments: JsonObject
    ): Flow<String> = callbackFlow {
        val toolCallParams = McpToolCallParams(name = toolName, arguments = arguments)
        val requestPayload = McpRequest(
            id = UUID.randomUUID().toString(),
            method = "tools/call",
            params = json.encodeToJsonElement(McpToolCallParams.serializer(), toolCallParams) as JsonObject
        )

        val body = json.encodeToString(requestPayload).toRequestBody(jsonMediaType)
        val requestBuilder = Request.Builder().url("$endpointUrl/tools/call").post(body)
        authHeader?.let { requestBuilder.addHeader("Authorization", it) }
        requestBuilder.addHeader("Accept", "text/event-stream")

        val request = requestBuilder.build()
        val factory = EventSources.createFactory(okHttpClient)
        
        var eventSource: EventSource? = null

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    eventSource.cancel()
                    close()
                } else {
                    trySend(data)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IOException("SSE Failure with code ${response?.code}"))
            }
        }

        eventSource = factory.newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    override suspend fun ping(endpointUrl: String): Boolean {
        // Implement simple ping, maybe a GET or OPTIONS request depending on MCP server impl.
        // For standard MCP over HTTP, usually you can just send an empty JSON-RPC ping
        return try {
            val requestBuilder = Request.Builder().url(endpointUrl).head()
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
