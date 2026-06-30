package com.ekam.baton.core.network.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.source
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.UUID

class HttpSseMcpTransport constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : McpTransport {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun initialize(endpointUrl: String, authHeader: String?): Result<JsonObject> = withContext(Dispatchers.IO) {
        try {
            val initParams = McpInitializeParams(
                capabilities = buildJsonObject { },
                // FIX: Provide required clientInfo per MCP spec
                clientInfo = buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("baton-android"))
                    put("version", kotlinx.serialization.json.JsonPrimitive("1.0.0"))
                }
            )
            val requestPayload = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "initialize",
                params = json.encodeToJsonElement(McpInitializeParams.serializer(), initParams) as JsonObject
            )

            val body = json.encodeToString(requestPayload).toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder().url("$endpointUrl/initialize").post(body)
            authHeader?.let { requestBuilder.addHeader("Authorization", it) }

            // FIX: Use response.use{} to ensure the connection is closed in ALL branches
            val mcpResponse = okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("Initialize failed: ${response.code}"))
                val responseBody = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                json.decodeFromString<McpResponse>(responseBody)
            }

            if (mcpResponse.error != null) {
                return@withContext Result.failure(IOException("MCP Error: ${mcpResponse.error.message}"))
            }

            // Send notifications/initialized — FIX: close() the response even though we don't need its body
            val notifPayload = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "notifications/initialized",
                params = null
            )
            val notifBody = json.encodeToString(notifPayload).toRequestBody(jsonMediaType)
            val notifBuilder = Request.Builder().url("$endpointUrl/notifications/initialized").post(notifBody)
            authHeader?.let { notifBuilder.addHeader("Authorization", it) }
            okHttpClient.newCall(notifBuilder.build()).execute().use { /* close the response */ }

            Result.success(mcpResponse.result ?: buildJsonObject { })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listTools(endpointUrl: String, authHeader: String?): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            val requestPayload = McpRequest(
                id = UUID.randomUUID().toString(),
                method = "tools/list",
                params = null
            )
            val body = json.encodeToString(requestPayload).toRequestBody(jsonMediaType)
            val requestBuilder = Request.Builder().url("$endpointUrl/tools/list").post(body)
            authHeader?.let { requestBuilder.addHeader("Authorization", it) }

            // FIX: response.use{} closes connection in all branches
            val mcpResponse = okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(IOException("List tools failed: ${response.code}"))
                val responseBody = response.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
                json.decodeFromString<McpResponse>(responseBody)
            }

            if (mcpResponse.error != null) {
                return@withContext Result.failure(IOException("MCP Error: ${mcpResponse.error.message}"))
            }

            val toolsArray = mcpResponse.result?.get("tools")
            if (toolsArray != null) {
                Result.success(json.decodeFromJsonElement<List<McpTool>>(toolsArray))
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

            override fun onClosed(eventSource: EventSource) { close() }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IOException("SSE Failure with code ${response?.code}"))
            }
        }

        eventSource = factory.newEventSource(request, listener)

        // FIX: Cancel the EventSource and close the flow if no response within 120s
        // We can just use `launch` since callbackFlow provides a CoroutineScope
        val timeoutJob = launch {
            kotlinx.coroutines.delay(120_000L)
            eventSource?.cancel()
            close(IOException("SSE response timed out after 120 seconds"))
        }

        awaitClose {
            timeoutJob.cancel()
            eventSource?.cancel()
        }
    }

    // FIX: Ping /health endpoint (GET) instead of HEAD on root — more broadly compatible
    override suspend fun ping(endpointUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = okHttpClient.newCall(
                Request.Builder().url("$endpointUrl/health").get().build()
            ).execute()
            response.use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun uploadFile(endpointUrl: String, authHeader: String?, uri: String, context: android.content.Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentUri = android.net.Uri.parse(uri)
            val mimeType = context.contentResolver.getType(contentUri) ?: "application/octet-stream"

            var filename = "upload.tmp"
            context.contentResolver.query(contentUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    filename = cursor.getString(nameIndex) ?: filename
                }
            }

            val requestBody = object : okhttp3.RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()
                override fun writeTo(sink: okio.BufferedSink) {
                    // FIX: Check for null InputStream and propagate error rather than silently uploading 0 bytes
                    val inputStream = context.contentResolver.openInputStream(contentUri)
                        ?: throw java.io.IOException("Cannot open content URI: $uri — permission may have been revoked")
                    inputStream.use { stream ->
                        stream.source().use { source ->
                            sink.writeAll(source)
                        }
                    }
                }
            }

            val multipartBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", filename, requestBody)
                .build()

            val requestBuilder = Request.Builder()
                .url("$endpointUrl/upload")
                .post(multipartBody)
            authHeader?.let { requestBuilder.addHeader("Authorization", it) }

            // FIX: response.use{} to close connection in all branches
            val fileId = okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Upload failed with code ${response.code}"))
                }
                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty upload response"))
                try {
                    val jsonRes = json.decodeFromString<JsonObject>(responseBody)
                    jsonRes["file_id"]?.let {
                        if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
                    } ?: responseBody
                } catch (e: Exception) {
                    responseBody
                }
            }
            Result.success(fileId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
