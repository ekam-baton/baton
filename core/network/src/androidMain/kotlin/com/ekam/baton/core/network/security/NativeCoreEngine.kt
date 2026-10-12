package com.ekam.baton.core.network.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NativeMemorySearchResult(
    val id: String,
    val layer: String,
    val title: String,
    val content: String,
    val score: Float
)

@Serializable
data class NativeVectorMemoryEntry(
    val id: String,
    val layer: String,
    val title: String,
    val content: String,
    val embedding: List<Float>,
    val timestamp: Long
)

@Serializable
data class NativeStreamingMarkdownResult(
    val thinking_trace: String? = null,
    val is_thinking_active: Boolean = false,
    val estimated_tokens: Int = 0,
    val has_unclosed_code_block: Boolean = false
)

@Serializable
data class NativeMeshFrame(
    val packet_type: String,
    val session_id: List<Int>,
    val sequence: Long,
    val nonce: List<Int>,
    val payload: List<Int>
)

object NativeCoreEngine {

    val isLoaded: Boolean = try {
        System.loadLibrary("baton_crypto")
        true
    } catch (e: Throwable) {
        false
    }

    private val json = Json { ignoreUnknownKeys = true }

    // --- Vector Memory Engine ---

    fun vectorCosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (!isLoaded || a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        return nativeVectorCosineSimilarity(a, b)
    }

    fun vectorBatchSearch(
        query: FloatArray,
        indexJson: String,
        topK: Int = 10,
        minScore: Float = 0.0f
    ): List<NativeMemorySearchResult> {
        if (!isLoaded || query.isEmpty() || indexJson.isBlank()) return emptyList()
        val resultJson = nativeVectorBatchSearch(query, indexJson, topK, minScore) ?: return emptyList()
        return try {
            json.decodeFromString<List<NativeMemorySearchResult>>(resultJson)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    // --- Sovereign Vault & Key Enclave ---

    fun signIdentity(privateKey: ByteArray, message: ByteArray): ByteArray? {
        if (!isLoaded || privateKey.size != 32) return null
        return nativeSignIdentity(privateKey, message)
    }

    fun verifyIdentity(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (!isLoaded || publicKey.size != 32 || signature.size != 64) return false
        return nativeVerifyIdentity(publicKey, message, signature)
    }

    fun computeSafetyFingerprint(localKey: ByteArray, remoteKey: ByteArray): String {
        if (!isLoaded || localKey.size != 32 || remoteKey.size != 32) return ""
        return nativeComputeSafetyFingerprint(localKey, remoteKey) ?: ""
    }

    // --- Sovereign A2A Mesh Framing ---

    fun encodeMeshFrame(
        packetType: Int,
        sessionId: ByteArray,
        sequence: Long,
        nonce: ByteArray,
        payload: ByteArray
    ): ByteArray? {
        if (!isLoaded || sessionId.size != 16 || nonce.size != 12) return null
        return nativeEncodeMeshFrame(packetType, sessionId, sequence, nonce, payload)
    }

    fun decodeMeshFrame(wireBytes: ByteArray): String? {
        if (!isLoaded || wireBytes.size < 46) return null
        return nativeDecodeMeshFrame(wireBytes)
    }

    fun splitAirGapQr(payload: ByteArray, chunkSize: Int = 300): List<String> {
        if (!isLoaded || payload.isEmpty()) return emptyList()
        val jsonArrayStr = nativeSplitAirGapQr(payload, chunkSize) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(jsonArrayStr)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun reassembleAirGapQr(chunks: List<String>): ByteArray? {
        if (!isLoaded || chunks.isEmpty()) return null
        val chunksJson = json.encodeToString(chunks)
        return nativeReassembleAirGapQr(chunksJson)
    }

    // --- Agent Tool Policy Sandbox ---

    fun validateToolCall(toolName: String, argumentsJson: String, policyJson: String = ""): Result<Unit> {
        if (!isLoaded) return Result.success(Unit) // Fallback if native library unavailable
        val violation = nativeValidateToolCall(toolName, argumentsJson, policyJson)
        return if (violation.isNullOrEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(SecurityException("Sovereign Policy Violation: $violation"))
        }
    }

    // --- Persistent Binary Vector Storage ---

    fun vectorStorageSave(path: String, dimension: Int, entries: List<NativeVectorMemoryEntry>): Boolean {
        if (!isLoaded || entries.isEmpty() || path.isBlank()) return false
        val entriesJson = json.encodeToString(entries)
        return nativeVectorStorageSave(path, dimension, entriesJson)
    }

    fun vectorStorageQuery(
        path: String,
        query: FloatArray,
        topK: Int = 10,
        minScore: Float = 0.0f,
        layerFilter: String = ""
    ): List<NativeMemorySearchResult> {
        if (!isLoaded || query.isEmpty() || path.isBlank()) return emptyList()
        val resultJson = nativeVectorStorageQuery(path, query, topK, minScore, layerFilter) ?: return emptyList()
        return try {
            json.decodeFromString<List<NativeMemorySearchResult>>(resultJson)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    // --- Streaming LLM Markdown & Token AST Parser ---

    fun parseStreamingMarkdown(rawText: String): NativeStreamingMarkdownResult {
        if (!isLoaded || rawText.isEmpty()) {
            return NativeStreamingMarkdownResult()
        }
        val resultJson = nativeParseStreamingMarkdown(rawText) ?: return NativeStreamingMarkdownResult()
        return try {
            json.decodeFromString<NativeStreamingMarkdownResult>(resultJson)
        } catch (e: Throwable) {
            NativeStreamingMarkdownResult()
        }
    }

    // --- Sovereign Noise Protocol Handshake & Cipher Channel ---

    fun noiseChannelEncrypt(key: ByteArray, nonce: Long, plaintext: ByteArray): ByteArray? {
        if (!isLoaded || key.size != 32 || plaintext.isEmpty()) return null
        return nativeNoiseChannelEncrypt(key, nonce, plaintext)
    }

    fun noiseChannelDecrypt(key: ByteArray, nonce: Long, ciphertext: ByteArray): ByteArray? {
        if (!isLoaded || key.size != 32 || ciphertext.isEmpty()) return null
        return nativeNoiseChannelDecrypt(key, nonce, ciphertext)
    }

    // --- External JNI Declarations ---

    @JvmStatic
    private external fun nativeVectorCosineSimilarity(a: FloatArray, b: FloatArray): Float

    @JvmStatic
    private external fun nativeVectorBatchSearch(
        query: FloatArray,
        indexJson: String,
        topK: Int,
        minScore: Float
    ): String?

    @JvmStatic
    private external fun nativeSignIdentity(privateKey: ByteArray, message: ByteArray): ByteArray?

    @JvmStatic
    private external fun nativeVerifyIdentity(
        publicKey: ByteArray,
        message: ByteArray,
        signature: ByteArray
    ): Boolean

    @JvmStatic
    private external fun nativeComputeSafetyFingerprint(localKey: ByteArray, remoteKey: ByteArray): String?

    @JvmStatic
    private external fun nativeEncodeMeshFrame(
        packetType: Int,
        sessionId: ByteArray,
        sequence: Long,
        nonce: ByteArray,
        payload: ByteArray
    ): ByteArray?

    @JvmStatic
    private external fun nativeDecodeMeshFrame(wireBytes: ByteArray): String?

    @JvmStatic
    private external fun nativeSplitAirGapQr(payload: ByteArray, chunkSize: Int): String?

    @JvmStatic
    private external fun nativeReassembleAirGapQr(chunksJson: String): ByteArray?

    @JvmStatic
    private external fun nativeValidateToolCall(
        toolName: String,
        argumentsJson: String,
        policyJson: String
    ): String?

    @JvmStatic
    private external fun nativeVectorStorageSave(path: String, dimension: Int, entriesJson: String): Boolean

    @JvmStatic
    private external fun nativeVectorStorageQuery(
        path: String,
        query: FloatArray,
        topK: Int,
        minScore: Float,
        layerFilter: String
    ): String?

    @JvmStatic
    private external fun nativeParseStreamingMarkdown(rawText: String): String?

    @JvmStatic
    private external fun nativeNoiseChannelEncrypt(key: ByteArray, nonce: Long, plaintext: ByteArray): ByteArray?

    @JvmStatic
    private external fun nativeNoiseChannelDecrypt(key: ByteArray, nonce: Long, ciphertext: ByteArray): ByteArray?
}
