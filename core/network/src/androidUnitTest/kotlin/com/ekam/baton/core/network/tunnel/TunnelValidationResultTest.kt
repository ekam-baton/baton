package com.ekam.baton.core.network.tunnel

// Status and TunnelValidationResult are in the same package (com.ekam.baton.core.network.tunnel)
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [TunnelValidationResult] and [Status].
 */
class TunnelValidationResultTest {

    // -------------------------------------------------------------------------
    // Status enum
    // -------------------------------------------------------------------------

    @Test
    fun `Status enum contains VALID`() {
        assertEquals("VALID", Status.VALID.name)
    }

    @Test
    fun `Status enum contains REACHABLE_NO_MCP`() {
        assertEquals("REACHABLE_NO_MCP", Status.REACHABLE_NO_MCP.name)
    }

    @Test
    fun `Status enum contains UNREACHABLE`() {
        assertEquals("UNREACHABLE", Status.UNREACHABLE.name)
    }

    @Test
    fun `Status enum contains INVALID_URL`() {
        assertEquals("INVALID_URL", Status.INVALID_URL.name)
    }

    @Test
    fun `Status enum has exactly four values`() {
        val values = Status.values()
        assertEquals(4, values.size)
        assertEquals(
            setOf(Status.VALID, Status.REACHABLE_NO_MCP, Status.UNREACHABLE, Status.INVALID_URL),
            values.toSet(),
        )
    }

    // -------------------------------------------------------------------------
    // TunnelValidationResult data class – field holding
    // -------------------------------------------------------------------------

    @Test
    fun `TunnelValidationResult holds all fields`() {
        val result = TunnelValidationResult(
            status = Status.VALID,
            serverName = "my-server",
            availableTools = listOf("tool-a", "tool-b"),
            error = null,
        )

        assertEquals(Status.VALID, result.status)
        assertEquals("my-server", result.serverName)
        assertEquals(listOf("tool-a", "tool-b"), result.availableTools)
        assertNull(result.error)
    }

    @Test
    fun `TunnelValidationResult holds error state`() {
        val result = TunnelValidationResult(
            status = Status.UNREACHABLE,
            serverName = null,
            availableTools = null,
            error = "Connection timed out",
        )

        assertEquals(Status.UNREACHABLE, result.status)
        assertNull(result.serverName)
        assertNull(result.availableTools)
        assertEquals("Connection timed out", result.error)
    }

    // -------------------------------------------------------------------------
    // copy()
    // -------------------------------------------------------------------------

    @Test
    fun `copy preserves unchanged fields`() {
        val original = TunnelValidationResult(
            status = Status.VALID,
            serverName = "server",
            availableTools = listOf("a"),
            error = null,
        )

        val copied = original.copy(status = Status.UNREACHABLE)

        assertEquals(Status.UNREACHABLE, copied.status)
        // All other fields remain untouched
        assertEquals(original.serverName, copied.serverName)
        assertEquals(original.availableTools, copied.availableTools)
        assertEquals(original.error, copied.error)
    }

    @Test
    fun `copy creates a distinct instance`() {
        val original = TunnelValidationResult(
            status = Status.VALID,
            serverName = "server",
            availableTools = listOf("a"),
            error = null,
        )

        val copied = original.copy(error = "new error")

        assertNotEquals(original, copied)
    }

    @Test
    fun `copy with no changes produces equal instance`() {
        val original = TunnelValidationResult(
            status = Status.REACHABLE_NO_MCP,
            serverName = null,
            availableTools = emptyList(),
            error = "some error",
        )

        val copied = original.copy()

        assertEquals(original, copied)
    }
}
