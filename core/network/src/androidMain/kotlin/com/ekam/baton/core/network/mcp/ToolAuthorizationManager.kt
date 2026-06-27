package com.ekam.baton.core.network.mcp

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.resume

data class ToolAuthorizationRequest(
    val id: String,
    val toolName: String,
    val arguments: JsonObject,
    val onResult: (Boolean) -> Unit
)

class ToolAuthorizationManager constructor() {
    private val _authorizationRequests = MutableSharedFlow<ToolAuthorizationRequest>(extraBufferCapacity = 10)
    val authorizationRequests: SharedFlow<ToolAuthorizationRequest> = _authorizationRequests.asSharedFlow()

    private val destructiveKeywords = listOf(
        "write", "execute", "delete", "update", "remove", "run", "shell", "rm", "mkdir", "bash", "cmd"
    )

    fun isDestructive(toolName: String): Boolean {
        val lowerName = toolName.lowercase()
        return destructiveKeywords.any { lowerName.contains(it) }
    }

    suspend fun requestAuthorization(toolName: String, arguments: JsonObject): Boolean {
        if (!isDestructive(toolName)) {
            return true
        }

        return suspendCancellableCoroutine { continuation ->
            val request = ToolAuthorizationRequest(
                id = java.util.UUID.randomUUID().toString(),
                toolName = toolName,
                arguments = arguments,
                onResult = { isApproved ->
                    if (continuation.isActive) {
                        continuation.resume(isApproved)
                    }
                }
            )
            
            val submitted = _authorizationRequests.tryEmit(request)
            if (!submitted) {
                // If we can't emit to the UI (e.g. no observers or buffer full), deny by default for safety
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
    }
}
