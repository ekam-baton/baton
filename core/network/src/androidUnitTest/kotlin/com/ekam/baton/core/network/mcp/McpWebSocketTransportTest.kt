package com.ekam.baton.core.network.mcp

import com.ekam.baton.core.network.security.AgentSecurityConfigProvider
import com.ekam.baton.core.network.security.AgentSecurityDetails
import com.ekam.baton.core.network.security.ConnectionSecurityManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.serialization.json.Json

class McpWebSocketTransportTest {

    private lateinit var server: MockWebServer
    private lateinit var transport: McpWebSocketTransport
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private val fakeConfigProvider = object : AgentSecurityConfigProvider {
        override suspend fun getSecurityConfig(agentId: String): AgentSecurityDetails? {
            return AgentSecurityDetails(
                securityMode = "standard",
                clientPrivateKeyBase64 = null,
                clientPrivateKeyIvBase64 = null,
                peerPublicKeyHex = null,
                certificatePins = emptyList(),
                ratchetStateBase64 = null
            )
        }

        override suspend fun saveRatchetState(agentId: String, stateBase64: String) {}
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val securityManager = ConnectionSecurityManager()
        transport = McpWebSocketTransport(client, json, securityManager, fakeConfigProvider)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `initialize returns success with empty JsonObject`() = runBlocking {
        val result = transport.initialize("ws://localhost:${server.port}", null, null)
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `listTools returns success with empty list`() = runBlocking {
        val result = transport.listTools("ws://localhost:${server.port}", null, null)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun `ping returns false when server does not accept websocket`() = runBlocking {
        // Enqueue regular HTTP 404 response instead of WS upgrade
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(404))
        val isAlive = transport.ping(server.url("/").toString().replace("http", "ws"))
        assertFalse(isAlive)
    }
}
