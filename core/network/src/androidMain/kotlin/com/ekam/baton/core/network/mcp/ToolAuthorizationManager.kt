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
    private val _authorizationRequests = MutableSharedFlow<ToolAuthorizationRequest>(replay = 0, extraBufferCapacity = 16)
    val authorizationRequests: SharedFlow<ToolAuthorizationRequest> = _authorizationRequests.asSharedFlow()

    private val destructiveKeywords = listOf(
        "write", "execute", "delete", "update", "remove", "run", "shell", "rm", "mkdir", "bash", "cmd"
    )

    fun isDestructive(toolName: String): Boolean {
        val lowerName = toolName.lowercase()
        return destructiveKeywords.any { lowerName.contains(it) }
    }

    suspend fun requestAuthorization(toolName: String, arguments: JsonObject): Boolean {
        // Enforce sovereign sandbox policy (blocked directives, payload caps)
        val defaultPolicyJson = """{"allowed_tools":["*"],"max_payload_bytes":65536,"allowed_hosts":[],"blocked_keywords":["rm -rf","su -","/system/bin","/data/data","chmod 777",":(){ :|:& };:","mkfs"]}"""
        val validationResult = com.ekam.baton.core.network.security.NativeCoreEngine.validateToolCall(
            toolName = toolName,
            argumentsJson = arguments.toString(),
            policyJson = defaultPolicyJson
        )
        if (validationResult.isFailure) {
            return false
        }

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
