package com.ekam.baton.core.network.mcp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

data class AttachmentDto(
    val mimeType: String,
    val dataBase64: String
)

@Singleton
class McpMessageSender @Inject constructor(
    private val connectionManager: McpConnectionManager,
    private val transport: McpTransport
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
        attachments: List<AttachmentDto> = emptyList()
    ): Flow<String> {
        val sessionResult = connectionManager.getOrCreateSession(agentId, endpointUrl, authHeader)
        if (sessionResult.isFailure) {
            return flow { emit("Error connecting to MCP Agent: ${sessionResult.exceptionOrNull()?.message}") }
        }

        val session = sessionResult.getOrNull() ?: return flow { emit("Error: Session is null") }

        // Determine tool
        val targetToolName = listOf("chat", "send_message", "complete", "generate").firstOrNull { desired ->
            session.availableTools.any { it.name == desired }
        }

        if (targetToolName == null) {
            return flow { emit("Error: Agent does not support chat/send_message tools. Available tools: ${session.availableTools.map { it.name }}") }
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
            if (attachments.isNotEmpty()) {
                put("attachments", buildJsonArray {
                    attachments.forEach { att ->
                        addJsonObject {
                            put("mimeType", att.mimeType)
                            put("data", att.dataBase64)
                        }
                    }
                })
            }
        }

        return transport.callTool(endpointUrl, authHeader, targetToolName, argumentsJson)
    }
}
