package com.ekam.baton.core.network.mcp

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HttpSseMcpTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: HttpSseMcpTransport
    private val client = OkHttpClient()
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        transport = HttpSseMcpTransport(client, json)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `ping returns true when server returns 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        
        val result = transport.ping(server.url("/").toString())
        assertTrue(result)
    }

    @Test
    fun `callTool processes SSE event data correctly`() = runTest {
        // Prepare SSE response body
        // Event data format: "data: <json>\n\n"
        val sseBody = "data: {\"result\": \"Hello\"}\n\ndata: {\"result\": \" world!\"}\n\ndata: [DONE]\n\n"

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody)
        )

        val endpoint = server.url("/mcp").toString()
        val flow = transport.callTool(
            endpointUrl = endpoint,
            authHeader = null,
            toolName = "chat",
            arguments = buildJsonObject { put("message", "hi") }
        )

        val results = flow.toList()
        
        assertEquals(2, results.size)
        assertEquals("{\"result\": \"Hello\"}", results[0])
        assertEquals("{\"result\": \" world!\"}", results[1])
    }
}
