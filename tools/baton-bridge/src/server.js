/**
 * Lightweight, robust MCP HTTP & SSE Server for the Baton Desktop Bridge.
 * Implements the standard Model Context Protocol (MCP 2024-11-05).
 */

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { exec } = require('node:child_process');
const { renderSvgQr } = require('./qr.js');

class McpBridgeServer {
  constructor(options = {}) {
    this.port = options.port || 9420;
    this.workspaceDir = path.resolve(options.workspace || process.cwd());
    this.authToken = options.token || `btn_sec_${crypto.randomBytes(16).toString('hex')}`;
    this.server = null;
    this.activeClients = new Set();
    this.tunnelUrl = options.tunnelUrl || `http://127.0.0.1:${this.port}`;
    this.onActivity = options.onActivity || (() => {});
    this.qrMatrix = options.qrMatrix || null;
    this.tasks = new Map();
  }

  isAuthorized(req) {
    const authHeader = req.headers['authorization'] || '';
    if (!authHeader.startsWith('Bearer ')) return false;
    const clientToken = authHeader.substring(7).trim();
    if (clientToken.length !== this.authToken.length) return false;
    return crypto.timingSafeEqual(Buffer.from(clientToken), Buffer.from(this.authToken));
  }

  resolveSafePath(userPath) {
    const resolved = path.resolve(this.workspaceDir, userPath || '.');
    // Ensure path remains inside workspace directory
    if (!resolved.startsWith(this.workspaceDir)) {
      throw new Error(`Access denied: path ${userPath} is outside workspace boundary`);
    }
    return resolved;
  }

  sendJson(res, statusCode, data) {
    const payload = JSON.stringify(data);
    res.writeHead(statusCode, {
      'Content-Type': 'application/json; charset=utf-8',
      'Content-Length': Buffer.byteLength(payload),
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Authorization, Content-Type, X-Baton-Agent-Id, Accept'
    });
    res.end(payload);
  }

  async readBody(req) {
    return new Promise((resolve, reject) => {
      let body = '';
      req.on('data', (chunk) => {
        body += chunk;
        if (body.length > 10 * 1024 * 1024) { // 10MB safety limit
          reject(new Error('Payload too large'));
        }
      });
      req.on('end', () => {
        try {
          resolve(body ? JSON.parse(body) : {});
        } catch (e) {
          reject(new Error('Invalid JSON payload'));
        }
      });
      req.on('error', reject);
    });
  }

  getToolsList() {
    return [
      {
        name: 'execute_command',
        description: 'Run a shell command inside the desktop workspace',
        inputSchema: {
          type: 'object',
          properties: {
            command: { type: 'string', description: 'The terminal command to run' }
          },
          required: ['command']
        }
      },
      {
        name: 'execute_async_command',
        description: 'Run a long-running terminal command asynchronously in background and return a taskId for tracking',
        inputSchema: {
          type: 'object',
          properties: {
            command: { type: 'string', description: 'The terminal command to run' },
            title: { type: 'string', description: 'Human-readable title for the task' }
          },
          required: ['command']
        }
      },
      {
        name: 'read_file',
        description: 'Read the contents of a file in the workspace',
        inputSchema: {
          type: 'object',
          properties: {
            path: { type: 'string', description: 'Relative path to file' }
          },
          required: ['path']
        }
      },
      {
        name: 'write_file',
        description: 'Write or update a file in the workspace',
        inputSchema: {
          type: 'object',
          properties: {
            path: { type: 'string', description: 'Relative path to file' },
            content: { type: 'string', description: 'Content to write' }
          },
          required: ['path', 'content']
        }
      },
      {
        name: 'list_directory',
        description: 'List contents of a directory in the workspace',
        inputSchema: {
          type: 'object',
          properties: {
            path: { type: 'string', description: 'Relative directory path (optional)' }
          }
        }
      },
      {
        name: 'git_status',
        description: 'Get current git status and modified files in workspace',
        inputSchema: {
          type: 'object',
          properties: {}
        }
      },
      {
        name: 'system_info',
        description: 'Inspect desktop CPU, memory, OS and host stats',
        inputSchema: {
          type: 'object',
          properties: {}
        }
      }
    ];
  }

  async executeTool(toolName, args) {
    this.onActivity(`Tool call: ${toolName}`, args);

    switch (toolName) {
      case 'read_file': {
        const filePath = this.resolveSafePath(args.path);
        if (!fs.existsSync(filePath)) throw new Error(`File not found: ${args.path}`);
        const content = fs.readFileSync(filePath, 'utf-8');
        return { path: args.path, content, size: content.length };
      }

      case 'write_file': {
        const filePath = this.resolveSafePath(args.path);
        fs.mkdirSync(path.dirname(filePath), { recursive: true });
        fs.writeFileSync(filePath, args.content || '', 'utf-8');
        return { success: true, path: args.path, bytesWritten: (args.content || '').length };
      }

      case 'list_directory': {
        const targetDir = this.resolveSafePath(args.path || '.');
        if (!fs.existsSync(targetDir)) throw new Error(`Directory not found: ${args.path}`);
        const entries = fs.readdirSync(targetDir, { withFileTypes: true });
        return {
          path: args.path || '.',
          entries: entries.map(e => ({
            name: e.name,
            isDirectory: e.isDirectory(),
            isFile: e.isFile()
          }))
        };
      }

      case 'git_status': {
        return new Promise((resolve) => {
          exec('git status --short --branch', { cwd: this.workspaceDir }, (err, stdout, stderr) => {
            if (err) {
              resolve({ git: false, message: 'Not a git repository or git not installed' });
            } else {
              resolve({ git: true, status: stdout.trim(), branchInfo: stdout.split('\n')[0] });
            }
          });
        });
      }

      case 'execute_command': {
        const command = args.command;
        if (!command) throw new Error('Command argument is required');
        return new Promise((resolve, reject) => {
          exec(command, { cwd: this.workspaceDir, timeout: 60000 }, (err, stdout, stderr) => {
            resolve({
              exitCode: err ? (err.code || 1) : 0,
              stdout: stdout.toString(),
              stderr: stderr.toString()
            });
          });
        });
      }

      case 'execute_async_command': {
        const command = args.command;
        if (!command) throw new Error('Command argument is required');
        const taskId = `task_${Date.now()}_${crypto.randomBytes(4).toString('hex')}`;
        const title = args.title || command;
        const taskRecord = {
          id: taskId,
          command,
          title,
          status: 'running',
          startedAt: Date.now(),
          finishedAt: null,
          exitCode: null,
          stdout: '',
          stderr: '',
          durationMs: 0
        };
        this.tasks.set(taskId, taskRecord);
        this.onActivity(`Async task started: ${taskId} (${command})`);

        exec(command, { cwd: this.workspaceDir, timeout: 600000 }, (err, stdout, stderr) => {
          taskRecord.finishedAt = Date.now();
          taskRecord.durationMs = taskRecord.finishedAt - taskRecord.startedAt;
          taskRecord.exitCode = err ? (err.code || 1) : 0;
          taskRecord.stdout = stdout ? stdout.toString() : '';
          taskRecord.stderr = stderr ? stderr.toString() : '';
          taskRecord.status = err ? 'failed' : 'completed';
          this.onActivity(`Async task finished: ${taskId} (${taskRecord.status})`, { exitCode: taskRecord.exitCode });
        });

        return {
          taskId,
          command,
          title,
          status: 'running',
          startedAt: taskRecord.startedAt
        };
      }

      case 'system_info': {
        return {
          hostname: os.hostname(),
          platform: os.platform(),
          release: os.release(),
          cpus: os.cpus().length,
          totalMemMb: Math.round(os.totalmem() / 1024 / 1024),
          freeMemMb: Math.round(os.freemem() / 1024 / 1024),
          uptimeSeconds: Math.round(os.uptime())
        };
      }

      default:
        throw new Error(`Unknown tool: ${toolName}`);
    }
  }

  handleBrowserQrPage(res) {
    const webPath = path.join(__dirname, 'web', 'index.html');
    if (fs.existsSync(webPath)) {
      const html = fs.readFileSync(webPath, 'utf-8');
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(html);
      return;
    }

    const pairingUrl = `baton://connect?url=${encodeURIComponent(this.tunnelUrl)}&token=${encodeURIComponent(this.authToken)}&name=${encodeURIComponent('Antigravity Desktop')}&model=antigravity-bridge`;
    const svg = this.qrMatrix ? renderSvgQr(this.qrMatrix, 10) : '';

    const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Baton Desktop Bridge - Quick Pair</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <style>
    body {
      margin: 0;
      padding: 0;
      background: #0A0F1D;
      color: #FFFFFF;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
    }
    .card {
      background: #131D31;
      border: 1px solid rgba(0, 210, 255, 0.3);
      border-radius: 20px;
      padding: 36px;
      max-width: 440px;
      text-align: center;
      box-shadow: 0 10px 40px rgba(0, 0, 0, 0.6);
    }
    h1 {
      font-size: 22px;
      margin: 0 0 8px 0;
      color: #00D2FF;
      letter-spacing: 0.5px;
    }
    p {
      font-size: 13px;
      color: rgba(255, 255, 255, 0.7);
      margin: 0 0 24px 0;
    }
    .qr-container {
      background: #0A0F1D;
      padding: 16px;
      border-radius: 16px;
      display: inline-block;
      margin-bottom: 24px;
      border: 1px solid rgba(255, 255, 255, 0.08);
    }
    .badge {
      display: inline-block;
      padding: 6px 12px;
      background: rgba(16, 185, 129, 0.15);
      border: 1px solid #10B981;
      color: #10B981;
      border-radius: 8px;
      font-size: 11px;
      font-family: monospace;
      font-weight: bold;
      margin-bottom: 18px;
    }
    .btn {
      display: block;
      background: #00D2FF;
      color: #0A0F1D;
      text-decoration: none;
      padding: 12px 20px;
      border-radius: 10px;
      font-weight: bold;
      font-size: 13px;
      transition: opacity 0.2s;
    }
    .btn:hover { opacity: 0.9; }
    .mono {
      font-family: monospace;
      font-size: 11px;
      color: rgba(255, 255, 255, 0.5);
      word-break: break-all;
      margin-top: 14px;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">● DESKTOP BRIDGE ACTIVE</div>
    <h1>Pair with Baton App</h1>
    <p>Open Baton on your Android phone, tap <b>[ 📷 Scan Tunnel QR ]</b> in Bot Creator, and point your camera here.</p>
    <div class="qr-container">
      ${svg}
    </div>
    <a href="${pairingUrl}" class="btn">Open in Baton (If on same device)</a>
    <div class="mono">Tunnel: ${this.tunnelUrl}</div>
  </div>
</body>
</html>`;

    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
  }

  start() {
    return new Promise((resolve, reject) => {
      this.server = http.createServer(async (req, res) => {
        // CORS preflight
        if (req.method === 'OPTIONS') {
          res.writeHead(204, {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Authorization, Content-Type, X-Baton-Agent-Id, Accept'
          });
          res.end();
          return;
        }

        const url = new URL(req.url, `http://localhost:${this.port}`);
        const pathname = url.pathname;

        try {
          // 1. Health check (unauthenticated for instant reachability verification)
          if (pathname === '/health' && req.method === 'GET') {
            this.sendJson(res, 200, {
              status: 'ok',
              name: 'baton-desktop-bridge',
              version: '1.0.0',
              workspace: this.workspaceDir,
              timestamp: Date.now()
            });
            return;
          }

          // 1.5 System status and web companion metadata
          if (pathname === '/status' && req.method === 'GET') {
            const pairingUrl = `baton://connect?url=${encodeURIComponent(this.tunnelUrl)}&token=${encodeURIComponent(this.authToken)}&name=${encodeURIComponent('Antigravity Desktop')}&model=antigravity-bridge`;
            const svg = this.qrMatrix ? renderSvgQr(this.qrMatrix, 10) : '';
            this.sendJson(res, 200, {
              status: 'ok',
              workspace: this.workspaceDir,
              tunnelUrl: this.tunnelUrl,
              pairingUrl,
              token: this.authToken,
              svgQr: svg
            });
            return;
          }

          // 1.6 Token validation for PC web auth
          if (pathname === '/auth/verify' && req.method === 'POST') {
            const isAuth = this.isAuthorized(req);
            this.sendJson(res, isAuth ? 200 : 401, {
              authorized: isAuth,
              timestamp: Date.now()
            });
            return;
          }

          // 2. Visual Browser QR Pairing page
          if ((pathname === '/qr' || pathname === '/') && req.method === 'GET') {
            this.handleBrowserQrPage(res);
            return;
          }

          // 2.5 Task execution & status endpoints for WorkManager & Background alerts
          if (pathname === '/tasks/execute' && req.method === 'POST') {
            if (!this.isAuthorized(req)) {
              this.sendJson(res, 401, { error: 'Unauthorized' });
              return;
            }
            const body = await this.readBody(req);
            const command = body.command;
            if (!command) {
              this.sendJson(res, 400, { error: 'command is required' });
              return;
            }
            const result = await this.executeTool('execute_async_command', { command, title: body.title });
            this.sendJson(res, 200, result);
            return;
          }

          if (pathname.startsWith('/tasks/') && req.method === 'GET') {
            if (!this.isAuthorized(req)) {
              this.sendJson(res, 401, { error: 'Unauthorized' });
              return;
            }
            const taskId = pathname.substring(7);
            const task = this.tasks.get(taskId);
            if (!task) {
              this.sendJson(res, 404, { error: `Task not found: ${taskId}` });
              return;
            }
            this.sendJson(res, 200, task);
            return;
          }

          if (pathname === '/tasks' && req.method === 'GET') {
            if (!this.isAuthorized(req)) {
              this.sendJson(res, 401, { error: 'Unauthorized' });
              return;
            }
            const allTasks = Array.from(this.tasks.values()).slice(-20);
            this.sendJson(res, 200, { tasks: allTasks });
            return;
          }

          // 3. MCP Initialize
          if (pathname === '/initialize' && req.method === 'POST') {
            if (!this.isAuthorized(req)) {
              this.sendJson(res, 401, { error: 'Unauthorized: invalid or missing Bearer token' });
              return;
            }

            const body = await this.readBody(req);
            this.onActivity('Client Handshake (initialize)', body.params?.clientInfo || {});

            this.sendJson(res, 200, {
              id: body.id || '1',
              jsonrpc: '2.0',
              result: {
                protocolVersion: '2024-11-05',
                capabilities: {
                  tools: { listChanged: false }
                },
                serverInfo: {
                  name: 'Baton Desktop Bridge',
                  version: '1.0.0'
                }
              }
            });
            return;
          }

          // 4. MCP Notifications initialized
          if (pathname === '/notifications/initialized' && req.method === 'POST') {
            this.sendJson(res, 200, { status: 'acknowledged' });
            return;
          }

          // 5. MCP Tools list
          if (pathname === '/tools/list' && req.method === 'POST') {
            if (!this.isAuthorized(req)) {
              this.sendJson(res, 401, { error: 'Unauthorized' });
              return;
            }

            const body = await this.readBody(req);
            this.sendJson(res, 200, {
              id: body.id || '1',
              jsonrpc: '2.0',
              result: {
                tools: this.getToolsList()
              }
            });
            return;
          }

          // 6. MCP Tools call
          if (pathname === '/tools/call' && req.method === 'POST') {
            if (!this.isAuthorized(req)) {
              this.sendJson(res, 401, { error: 'Unauthorized' });
              return;
            }

            const body = await this.readBody(req);
            const toolName = body.params?.name || body.name;
            const toolArgs = body.params?.arguments || body.arguments || {};
            const requestId = body.id || '1';

            const acceptSse = (req.headers['accept'] || '').includes('text/event-stream');

            if (acceptSse) {
              // Server-Sent Events stream for HttpSseMcpTransport.kt
              res.writeHead(200, {
                'Content-Type': 'text/event-stream',
                'Cache-Control': 'no-cache',
                'Connection': 'keep-alive',
                'Access-Control-Allow-Origin': '*'
              });

              try {
                const result = await this.executeTool(toolName, toolArgs);
                const chunkPayload = JSON.stringify({
                  id: requestId,
                  jsonrpc: '2.0',
                  result: { content: [{ type: 'text', text: JSON.stringify(result) }] }
                });
                res.write(`data: ${chunkPayload}\n\n`);
                res.write('data: [DONE]\n\n');
              } catch (toolErr) {
                const errPayload = JSON.stringify({
                  id: requestId,
                  jsonrpc: '2.0',
                  error: { code: -32603, message: toolErr.message }
                });
                res.write(`data: ${errPayload}\n\n`);
                res.write('data: [DONE]\n\n');
              }
              res.end();
            } else {
              try {
                const result = await this.executeTool(toolName, toolArgs);
                this.sendJson(res, 200, {
                  id: requestId,
                  jsonrpc: '2.0',
                  result: { content: [{ type: 'text', text: JSON.stringify(result) }] }
                });
              } catch (toolErr) {
                this.sendJson(res, 500, {
                  id: requestId,
                  jsonrpc: '2.0',
                  error: { code: -32603, message: toolErr.message }
                });
              }
            }
            return;
          }

          // 404 Fallback
          this.sendJson(res, 404, { error: `Not found: ${pathname}` });
        } catch (err) {
          this.sendJson(res, 500, { error: err.message });
        }
      });

      this.server.on('error', (err) => {
        if (err.code === 'EADDRINUSE') {
          // Retry on incremented port
          this.port++;
          this.server.listen(this.port, '127.0.0.1');
        } else {
          reject(err);
        }
      });

      this.server.listen(this.port, '127.0.0.1', () => {
        resolve({
          port: this.port,
          workspace: this.workspaceDir,
          token: this.authToken
        });
      });
    });
  }

  stop() {
    return new Promise((resolve) => {
      if (this.server) {
        this.server.close(() => resolve());
      } else {
        resolve();
      }
    });
  }
}

module.exports = { McpBridgeServer };
