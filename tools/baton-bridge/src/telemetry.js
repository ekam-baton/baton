const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

/**
 * Desktop Companion ML Telemetry Logger.
 * Records operational security decisions, tool executions, and system events
 * directly into baton-ml/baton_live_telemetry.jsonl as Alpaca/LoRA training samples.
 */
class BridgeTelemetryLogger {
  constructor(workspaceDir) {
    this.workspaceDir = workspaceDir;
    // Prefer repo baton-ml directory, fallback to workspace
    const repoMlDir = path.resolve(__dirname, '..', '..', '..', 'baton-ml');
    if (fs.existsSync(repoMlDir)) {
      this.telemetryFile = path.resolve(repoMlDir, 'baton_live_telemetry.jsonl');
    } else {
      this.telemetryFile = path.resolve(workspaceDir, 'baton-ml', 'baton_live_telemetry.jsonl');
    }
  }

  recordSample(instruction, inputObj, outputObj) {
    try {
      const sample = {
        id: crypto.randomUUID(),
        date: new Date().toISOString(),
        instruction,
        input: typeof inputObj === 'string' ? inputObj : JSON.stringify(inputObj),
        output: typeof outputObj === 'string' ? outputObj : JSON.stringify(outputObj)
      };

      const dir = path.dirname(this.telemetryFile);
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
      }

      fs.appendFileSync(this.telemetryFile, JSON.stringify(sample) + '\n', 'utf-8');
    } catch (err) {
      // Non-blocking error logging
      console.error('[TELEMETRY] Failed to write sample:', err.message);
    }
  }

  logSecurityIncident(type, details, decision, rationale) {
    this.recordSample(
      'You are the Baton Sovereign Security & Operations Kernel. Analyze this host security incident and enforce autonomous defense mitigation.',
      {
        event_type: type,
        platform: 'desktop_bridge',
        timestamp: Date.now(),
        ...details
      },
      {
        decision,
        rationale,
        action: decision === 'BLOCKED' ? 'ISOLATE_WORKSPACE_AND_REJECT' : 'LOG_AUDIT'
      }
    );
  }

  logToolCall(toolName, args, resultSummary, durationMs) {
    this.recordSample(
      'You are the Baton Autonomous Desktop Bridge Monitor. Analyze this tool invocation and evaluate runtime safety and success.',
      {
        tool: toolName,
        args_sanitized: typeof args === 'object' ? Object.keys(args) : args,
        duration_ms: durationMs,
        platform: 'desktop_bridge'
      },
      {
        status: 'SUCCESS',
        result_summary: resultSummary,
        confidence_score: 0.98
      }
    );
  }
}

module.exports = { BridgeTelemetryLogger };
