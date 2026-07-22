package com.ekam.baton.core.network.repository

// McpNetworkResult is in the same package (com.ekam.baton.core.network.repository)
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the [McpNetworkResult] sealed class hierarchy.
 */
class McpNetworkResultTest {

    // -------------------------------------------------------------------------
    // Success
    // -------------------------------------------------------------------------

    @Test
    fun `Success holds data correctly`() {
        val data = "hello"
        val result: McpNetworkResult<String> = McpNetworkResult.Success(data)

        assertTrue(result is McpNetworkResult.Success)
        assertEquals("hello", (result as McpNetworkResult.Success).data)
    }

    @Test
    fun `Success holds complex data correctly`() {
        val list = listOf(1, 2, 3)
        val result: McpNetworkResult<List<Int>> = McpNetworkResult.Success(list)

        val success = result as McpNetworkResult.Success
        assertEquals(listOf(1, 2, 3), success.data)
        assertSame(list, success.data)
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    @Test
    fun `Error holds code and message`() {
        val result: McpNetworkResult<String> = McpNetworkResult.Error(404, "Not Found")

        assertTrue(result is McpNetworkResult.Error)
        val error = result as McpNetworkResult.Error
        assertEquals(404, error.code)
        assertEquals("Not Found", error.message)
        assertNull(error.exception)
    }

    @Test
    fun `Error holds code, message, and exception`() {
        val cause = RuntimeException("boom")
        val result: McpNetworkResult<String> = McpNetworkResult.Error(500, "Server Error", cause)

        val error = result as McpNetworkResult.Error
        assertEquals(500, error.code)
        assertEquals("Server Error", error.message)
        assertSame(cause, error.exception)
    }

    @Test
    fun `Error exception defaults to null`() {
        val error = McpNetworkResult.Error(400, "Bad Request")
        assertNull(error.exception)
    }

    // -------------------------------------------------------------------------
    // Pattern matching
    // -------------------------------------------------------------------------

    @Test
    fun `when expression matches Success branch`() {
        val result: McpNetworkResult<Int> = McpNetworkResult.Success(42)

        val output = when (result) {
            is McpNetworkResult.Success -> "data=${result.data}"
            is McpNetworkResult.Error -> "error=${result.code}"
        }

        assertEquals("data=42", output)
    }

    @Test
    fun `when expression matches Error branch`() {
        val result: McpNetworkResult<Int> = McpNetworkResult.Error(503, "Unavailable")

        val output = when (result) {
            is McpNetworkResult.Success -> "data=${result.data}"
            is McpNetworkResult.Error -> "error=${result.code}"
        }

        assertEquals("error=503", output)
    }
}
