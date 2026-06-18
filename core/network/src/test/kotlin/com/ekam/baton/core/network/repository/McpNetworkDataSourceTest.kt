package com.ekam.baton.core.network.repository

import com.ekam.baton.core.network.api.McpApi
import com.ekam.baton.core.network.dto.McpMessageDto
import com.ekam.baton.core.network.dto.McpRequestDto
import com.ekam.baton.core.network.dto.McpResponseDto
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Unit tests for [McpNetworkDataSource].
 *
 * Uses a hand-written [FakeMcpApi] instead of MockWebServer so that the tests
 * stay fast, deterministic, and free of any Retrofit/OkHttp wiring.
 */
class McpNetworkDataSourceTest {

    // -------------------------------------------------------------------------
    // Fake
    // -------------------------------------------------------------------------

    private class FakeMcpApi : McpApi {

        /** The next [Response] that [sendMessage] will return. */
        var nextResponse: Response<McpResponseDto>? = null

        /** If non-null, [sendMessage] will throw this instead of returning. */
        var nextException: Exception? = null

        override suspend fun sendMessage(
            url: String,
            authorization: String?,
            request: McpRequestDto,
        ): Response<McpResponseDto> {
            nextException?.let { throw it }
            return nextResponse ?: error("FakeMcpApi: neither nextResponse nor nextException was set")
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private val fakeApi = FakeMcpApi()
    private val dataSource = McpNetworkDataSource(fakeApi)

    private val sampleRequest = McpRequestDto(
        systemPrompt = "You are a helpful assistant.",
        messages = listOf(McpMessageDto(role = "user", content = "Hello")),
    )

    private val sampleResponseDto = McpResponseDto(
        message = McpMessageDto(role = "assistant", content = "Hi there!"),
    )

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `sendMessage returns Success on 200 with valid body`() = runTest {
        fakeApi.nextResponse = Response.success(200, sampleResponseDto)

        val result = dataSource.sendMessage(
            url = "https://example.com/mcp",
            authorization = "Bearer token",
            request = sampleRequest,
        )

        assertTrue("Expected Success but got $result", result is McpNetworkResult.Success)
        val success = result as McpNetworkResult.Success
        assertEquals(sampleResponseDto, success.data)
    }

    @Test
    fun `sendMessage returns Error on 500 server error`() = runTest {
        fakeApi.nextResponse = Response.error(
            500,
            "Internal Server Error".toResponseBody(null),
        )

        val result = dataSource.sendMessage(
            url = "https://example.com/mcp",
            authorization = null,
            request = sampleRequest,
        )

        assertTrue("Expected Error but got $result", result is McpNetworkResult.Error)
        val error = result as McpNetworkResult.Error
        assertEquals(500, error.code)
    }

    @Test
    fun `sendMessage returns Error when response body is null`() = runTest {
        // Response.success(null) produces a 200 response whose body() returns null.
        fakeApi.nextResponse = Response.success<McpResponseDto>(null)

        val result = dataSource.sendMessage(
            url = "https://example.com/mcp",
            authorization = "Bearer token",
            request = sampleRequest,
        )

        assertTrue("Expected Error but got $result", result is McpNetworkResult.Error)
        val error = result as McpNetworkResult.Error
        assertEquals(200, error.code)
        assertEquals("Response body is null", error.message)
        assertNull(error.exception)
    }

    @Test
    fun `sendMessage returns Error on network exception`() = runTest {
        val ioException = IOException("Connection reset")
        fakeApi.nextException = ioException

        val result = dataSource.sendMessage(
            url = "https://example.com/mcp",
            authorization = null,
            request = sampleRequest,
        )

        assertTrue("Expected Error but got $result", result is McpNetworkResult.Error)
        val error = result as McpNetworkResult.Error
        assertEquals(-1, error.code)
        assertEquals("Connection reset", error.message)
        assertEquals(ioException, error.exception)
    }
}
