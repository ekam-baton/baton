package com.ekam.baton.core.data.telemetry

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * High-value operational security & app management telemetry collector.
 * Automatically transforms real-world Day 1 application events into
 * Alpaca-style instruction-tuning training samples for future autonomous
 * AI model fine-tuning (LoRA).
 *
 * Privacy Guarantees:
 * - ZERO personal message content or user identities are ever captured.
 * - Only operational system metrics, threat classifications, tool calls, and defense decisions.
 * - Fully local-first; stored in app files and exportable on demand.
 */
class SecurityTelemetryManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val storageFile: File by lazy {
        File(context.filesDir, "baton_ml_telemetry.jsonl")
    }

    private val _sampleCount = MutableStateFlow(0)
    val sampleCount: StateFlow<Int> = _sampleCount.asStateFlow()

    init {
        scope.launch {
            _sampleCount.value = countExistingSamples()
        }
    }

    private suspend fun countExistingSamples(): Int = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) return@withContext 0
        try {
            storageFile.useLines { lines -> lines.count() }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Log a security decision (e.g., JIT authorization, SSRF block, path traversal)
     * and compile it into an LLM training instruction pair.
     */
    fun logSecurityEvent(
        eventType: String,
        severity: String, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
        contextDetails: Map<String, Any>,
        decision: String, // e.g. "BLOCKED", "ALLOWED", "QUARANTINED"
        rationale: String
    ) {
        val inputContext = JSONObject().apply {
            put("event_type", eventType)
            put("severity", severity)
            put("platform", "android")
            put("timestamp", System.currentTimeMillis())
            contextDetails.forEach { (k, v) -> put(k, v) }
        }.toString()

        val outputDecision = JSONObject().apply {
            put("decision", decision)
            put("rationale", rationale)
            put("recommended_action", when (severity) {
                "CRITICAL" -> "REVOKE_PERMISSIONS_AND_ALERT"
                "HIGH" -> "BLOCK_AND_RATE_LIMIT"
                "MEDIUM" -> "PROMPT_USER_CONFIRMATION"
                else -> "AUDIT_AND_CONTINUE"
            })
        }.toString()

        recordSample(
            instruction = "You are the Baton Sovereign Security & Operations Kernel. Analyze this security event and recommend the autonomous defense mitigation.",
            input = inputContext,
            output = outputDecision
        )
    }

    /**
     * Log app performance & management metrics (cold start, query latency, memory pressure).
     */
    fun logManagementEvent(
        metricName: String,
        currentValue: Double,
        threshold: Double,
        actionTaken: String,
        analysis: String
    ) {
        val inputContext = JSONObject().apply {
            put("metric", metricName)
            put("value", currentValue)
            put("threshold", threshold)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        val outputDecision = JSONObject().apply {
            put("action", actionTaken)
            put("analysis", analysis)
            put("optimization_suggestion", if (currentValue > threshold) "REDUCE_BATCH_SIZE_OR_COMPACT_DB" else "HEALTHY")
        }.toString()

        recordSample(
            instruction = "You are the Baton System Diagnostics Kernel. Analyze this performance metric and recommend application health optimizations.",
            input = inputContext,
            output = outputDecision
        )
    }

    /**
     * Log tool execution telemetry from companion agents.
     */
    fun logToolExecution(
        toolName: String,
        executionDurationMs: Long,
        wasSuccessful: Boolean,
        riskScore: Double,
        policyDecision: String
    ) {
        val inputContext = JSONObject().apply {
            put("tool_name", toolName)
            put("duration_ms", executionDurationMs)
            put("success", wasSuccessful)
            put("risk_score", riskScore)
        }.toString()

        val outputDecision = JSONObject().apply {
            put("policy_decision", policyDecision)
            put("audit_status", if (wasSuccessful) "VERIFIED" else "FAILED_INVESTIGATE")
        }.toString()

        recordSample(
            instruction = "You are the Baton Agent Tool Execution Monitor. Validate tool invocation telemetry and determine runtime safety status.",
            input = inputContext,
            output = outputDecision
        )
    }

    private fun recordSample(instruction: String, input: String, output: String) {
        scope.launch {
            try {
                val record = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                    put("instruction", instruction)
                    put("input", input)
                    put("output", output)
                }

                withContext(Dispatchers.IO) {
                    FileWriter(storageFile, true).use { writer ->
                        writer.write(record.toString() + "\n")
                    }
                }

                _sampleCount.value += 1
            } catch (e: Exception) {
                android.util.Log.e("BatonTelemetry", "Failed to write ML telemetry sample", e)
            }
        }
    }

    /**
     * Export the collected Day-1 dataset to a target file (e.g. for syncing to PC).
     */
    suspend fun exportDataset(destination: File): Boolean = withContext(Dispatchers.IO) {
        if (!storageFile.exists()) return@withContext false
        try {
            storageFile.copyTo(destination, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getTelemetryFile(): File = storageFile
}
