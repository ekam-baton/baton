package com.ekam.baton.core.network.dto

import com.ekam.baton.core.network.mcp.McpError
import com.ekam.baton.core.network.mcp.McpRequest
import com.ekam.baton.core.network.mcp.McpTool
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoSerializationTest {

    private val json = Json { encodeDefaults = true }

    // ── McpMessageDto ───────────────────────────────────────────────────

    @Test
    fun `McpMessageDto serializes and deserializes correctly`() {
        val original = McpMessageDto(role = "user", content = "Hello")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<McpMessageDto>(encoded)

        assertEquals(original, decoded)
        assertTrue(encoded.contains("\"role\":\"user\""))
        assertTrue(encoded.contains("\"content\":\"Hello\""))
    }

    // ── McpRequest (MCP protocol) ───────────────────────────────────────

    @Test
    fun `McpRequest serializes with jsonrpc field`() {
        val request = McpRequest(
            id = "req-1",
            method = "tools/list",
            params = null
        )
        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(encoded.contains("\"id\":\"req-1\""))
        assertTrue(encoded.contains("\"method\":\"tools/list\""))
    }

    @Test
    fun `McpRequest round-trips with params`() {
        val params = buildJsonObject { put("cursor", "abc") }
        val original = McpRequest(id = "42", method = "tools/call", params = params)
        val decoded = json.decodeFromString<McpRequest>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertEquals("2.0", decoded.jsonrpc)
        assertEquals("42", decoded.id)
    }

    // ── McpError ────────────────────────────────────────────────────────

    @Test
    fun `McpError holds code and message`() {
        val error = McpError(code = -32600, message = "Invalid Request")

        assertEquals(-32600, error.code)
        assertEquals("Invalid Request", error.message)
        assertNull(error.data)
    }

    @Test
    fun `McpError round-trips correctly`() {
        val data = buildJsonObject { put("detail", "extra info") }
        val original = McpError(code = -32601, message = "Method not found", data = data)
        val decoded = json.decodeFromString<McpError>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertEquals(-32601, decoded.code)
    }

    // ── McpTool ─────────────────────────────────────────────────────────

    @Test
    fun `McpTool holds name, description, and inputSchema`() {
        val schema = buildJsonObject {
            put("type", "object")
        }
        val tool = McpTool(
            name = "search",
            description = "Search the web",
            inputSchema = schema
        )

        assertEquals("search", tool.name)
        assertEquals("Search the web", tool.description)
        assertEquals(schema, tool.inputSchema)
    }

    @Test
    fun `McpTool round-trips correctly`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("required", "query")
        }
        val original = McpTool(
            name = "lookup",
            description = null,
            inputSchema = schema
        )
        val decoded = json.decodeFromString<McpTool>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertNull(decoded.description)
    }
}
