/**
 * Automated test suite for @baton/bridge
 * Verifies QR generation, MCP protocol compliance, token auth, tool execution, and security.
 */

const assert = require('node:assert');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { generateQrCode } = require('../src/qr.js');
const { McpBridgeServer } = require('../src/server.js');

const TEST_PORT = 9488;
const TEST_TOKEN = 'btn_sec_test_secret_token_12345';
const TEST_WORKSPACE = path.join(os.tmpdir(), 'baton_bridge_test_' + Date.now());

function makeRequest(method, pathname, headers = {}, body = null) {
  return new Promise((resolve, reject) => {
    const payload = body ? JSON.stringify(body) : null;
    const req = http.request({
      hostname: '127.0.0.1',
      port: TEST_PORT,
      path: pathname,
      method: method,
      headers: {
        'Content-Type': 'application/json',
        ...(payload ? { 'Content-Length': Buffer.byteLength(payload) } : {}),
        ...headers
      }
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        let json = null;
        try { json = JSON.parse(data); } catch (_) {}
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          data,
          json
        });
      });
    });
    req.on('error', reject);
    if (payload) req.write(payload);
    req.end();
  });
}

async function runTests() {
  console.log('Testing @baton/bridge components...\n');
  fs.mkdirSync(TEST_WORKSPACE, { recursive: true });

  let passed = 0;
  let total = 0;

  async function test(name, fn) {
    total++;
    try {
      await fn();
      console.log(`  ✓ ${name}`);
      passed++;
    } catch (err) {
      console.error(`  ✗ ${name}: ${err.message}`);
      console.error(err.stack);
    }
  }

  // 1. QR Generator
  await test('QR Generator produces non-empty terminal and SVG strings', () => {
    const qr = generateQrCode('baton://connect?url=https://test.trycloudflare.com&token=secret');
    assert(qr.matrix.length >= 21, 'QR matrix size should be at least 21');
    assert(qr.terminalText.includes('█') || qr.terminalText.includes('▀'), 'Terminal QR should contain block characters');
    assert(qr.svgText.includes('<svg'), 'SVG QR should be valid XML/SVG');
  });

  // 2. Start Test Server
  const server = new McpBridgeServer({
    port: TEST_PORT,
    workspace: TEST_WORKSPACE,
    token: TEST_TOKEN
  });
  await server.start();

  try {
    // 3. Health check
    await test('GET /health returns 200 OK without authentication', async () => {
      const res = await makeRequest('GET', '/health');
      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.json.status, 'ok');
      assert.strictEqual(res.json.name, 'baton-desktop-bridge');
    });

    // 4. Browser QR page & Web App
    await test('GET /qr returns 200 HTML pairing page', async () => {
      const res = await makeRequest('GET', '/qr');
      assert.strictEqual(res.statusCode, 200);
      assert(res.data.includes('Pair with Baton App') || res.data.includes('Baton PC Edition'), 'Page should include title');
    });

    // 5. Auth rejection
    await test('POST /tools/list rejects requests missing Bearer token with 401', async () => {
      const res = await makeRequest('POST', '/tools/list', {}, {});
      assert.strictEqual(res.statusCode, 401);
    });

    await test('POST /tools/list rejects requests with incorrect token', async () => {
      const res = await makeRequest('POST', '/tools/list', {
        'Authorization': 'Bearer wrong_token_value_0000000000'
      }, {});
      assert.strictEqual(res.statusCode, 401);
    });

    // 6. MCP Initialize
    await test('POST /initialize with valid token returns protocolVersion and serverInfo', async () => {
      const res = await makeRequest('POST', '/initialize', {
        'Authorization': `Bearer ${TEST_TOKEN}`
      }, {
        id: 'init-1',
        method: 'initialize',
        params: { clientInfo: { name: 'baton-test', version: '1.0' } }
      });
      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.json.result.protocolVersion, '2024-11-05');
      assert.strictEqual(res.json.result.serverInfo.name, 'Baton Desktop Bridge');
    });

    // 7. MCP Tools List
    await test('POST /tools/list with valid token returns all core tools', async () => {
      const res = await makeRequest('POST', '/tools/list', {
        'Authorization': `Bearer ${TEST_TOKEN}`
      }, {});
      assert.strictEqual(res.statusCode, 200);
      const tools = res.json.result.tools;
      assert(Array.isArray(tools), 'tools should be an array');
      const toolNames = tools.map(t => t.name);
      assert(toolNames.includes('read_file'), 'should include read_file');
      assert(toolNames.includes('write_file'), 'should include write_file');
      assert(toolNames.includes('list_directory'), 'should include list_directory');
      assert(toolNames.includes('execute_command'), 'should include execute_command');
      assert(toolNames.includes('system_info'), 'should include system_info');
    });

    // 8. Tool Execution: system_info
    await test('POST /tools/call executes system_info successfully', async () => {
      const res = await makeRequest('POST', '/tools/call', {
        'Authorization': `Bearer ${TEST_TOKEN}`
      }, {
        id: 'call-1',
        params: { name: 'system_info', arguments: {} }
      });
      assert.strictEqual(res.statusCode, 200);
      const resultObj = JSON.parse(res.json.result.content[0].text);
      assert(resultObj.platform, 'should return platform');
      assert(resultObj.cpus > 0, 'should report CPU count');
    });

    // 9. Tool Execution: write_file and read_file
    await test('POST /tools/call executes write_file and read_file', async () => {
      // Write
      const writeRes = await makeRequest('POST', '/tools/call', {
        'Authorization': `Bearer ${TEST_TOKEN}`
      }, {
        id: 'call-w',
        params: {
          name: 'write_file',
          arguments: { path: 'hello.txt', content: 'Sovereign Baton Agent Link' }
        }
      });
      assert.strictEqual(writeRes.statusCode, 200);

      // Read
      const readRes = await makeRequest('POST', '/tools/call', {
        'Authorization': `Bearer ${TEST_TOKEN}`
      }, {
        id: 'call-r',
        params: {
          name: 'read_file',
          arguments: { path: 'hello.txt' }
        }
      });
      assert.strictEqual(readRes.statusCode, 200);
      const readResult = JSON.parse(readRes.json.result.content[0].text);
      assert.strictEqual(readResult.content, 'Sovereign Baton Agent Link');
    });

    // 10. Tool Execution: SSE streaming
    await test('POST /tools/call supports Accept: text/event-stream with [DONE]', async () => {
      const res = await makeRequest('POST', '/tools/call', {
        'Authorization': `Bearer ${TEST_TOKEN}`,
        'Accept': 'text/event-stream'
      }, {
        id: 'call-sse',
        params: {
          name: 'system_info',
          arguments: {}
        }
      });
      assert.strictEqual(res.statusCode, 200);
      assert(res.data.includes('data:'), 'SSE should include data frames');
      assert(res.data.includes('[DONE]'), 'SSE should terminate with [DONE]');
    });

    // 11. Security boundary check
    await test('Security: path traversal outside workspace throws error', async () => {
      const res = await makeRequest('POST', '/tools/call', {
        'Authorization': `Bearer ${TEST_TOKEN}`
      }, {
        id: 'call-traversal',
        params: {
          name: 'read_file',
          arguments: { path: '../../../../../../windows/system32/calc.exe' }
        }
      });
      assert.strictEqual(res.statusCode, 500);
      assert(res.json.error.message.includes('outside workspace boundary'), 'Should block traversal');
    });

    // 12. Asynchronous Task Execution & Tracking (WorkManager / Background Alerts)
    await test('POST /tasks/execute and GET /tasks/:taskId track background task status', async () => {
      const execRes = await makeRequest('POST', '/tasks/execute', {
        'Authorization': `Bearer ${TEST_TOKEN}`
      }, {
        command: 'echo "BATON_ASYNC_TASK_SUCCESS"',
        title: 'Echo Test Task'
      });
      assert.strictEqual(execRes.statusCode, 200);
      assert(execRes.json.taskId, 'Should return a taskId');
      assert.strictEqual(execRes.json.status, 'running');

      const taskId = execRes.json.taskId;

      // Poll until task finishes
      let pollRes = null;
      for (let i = 0; i < 20; i++) {
        await new Promise(r => setTimeout(r, 100));
        pollRes = await makeRequest('GET', `/tasks/${taskId}`, {
          'Authorization': `Bearer ${TEST_TOKEN}`
        });
        if (pollRes.json && pollRes.json.status === 'completed') break;
      }

      assert.strictEqual(pollRes.statusCode, 200);
      assert.strictEqual(pollRes.json.status, 'completed');
      assert.strictEqual(pollRes.json.exitCode, 0);
      assert(pollRes.json.stdout.includes('BATON_ASYNC_TASK_SUCCESS'), 'stdout should contain task output');
    });

    // 13. GET /tasks list endpoint
    await test('GET /tasks returns list of recent tasks', async () => {
      const res = await makeRequest('GET', '/tasks', {
        'Authorization': `Bearer ${TEST_TOKEN}`
      });
      assert.strictEqual(res.statusCode, 200);
      assert(Array.isArray(res.json.tasks), 'tasks should be an array');
      assert(res.json.tasks.length >= 1, 'should contain at least one task');
    });

    // 14. GET / serves Baton PC Edition web dashboard
    await test('GET / serves Baton PC Edition web dashboard with 200 OK', async () => {
      const res = await makeRequest('GET', '/');
      assert.strictEqual(res.statusCode, 200);
      assert(res.data.includes('Baton PC Edition') || res.data.includes('BATON'), 'Should contain Baton brand');
      assert(res.data.includes('auth-screen') || res.data.includes('dashboard-screen'), 'Should contain sovereign UI views');
    });

    // 15. GET /status metadata endpoint
    await test('GET /status returns valid system metadata and pairing info', async () => {
      const res = await makeRequest('GET', '/status');
      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.json.status, 'ok');
      assert(res.json.workspace, 'Workspace should be non-empty');
      assert(res.json.pairingUrl.startsWith('baton://connect'), 'Pairing URL should have baton:// scheme');
    });

    // 16. POST /auth/verify token authentication
    await test('POST /auth/verify returns 200 authorized:true with valid Bearer token', async () => {
      const res = await makeRequest('POST', '/auth/verify', {
        'Authorization': `Bearer ${TEST_TOKEN}`
      });
      assert.strictEqual(res.statusCode, 200);
      assert.strictEqual(res.json.authorized, true);
    });

    // 17. POST /auth/verify rejects invalid token with 401
    await test('POST /auth/verify returns 401 authorized:false with invalid token', async () => {
      const res = await makeRequest('POST', '/auth/verify', {
        'Authorization': 'Bearer invalid_secret_token_99999'
      });
      assert.strictEqual(res.statusCode, 401);
      assert.strictEqual(res.json.authorized, false);
    });

    // 18. Port conflict retry synchronizes tunnelUrl
    await test('Server increments port and synchronizes tunnelUrl on EADDRINUSE', async () => {
      const secondServer = new McpBridgeServer({
        port: server.port,
        workspace: TEST_WORKSPACE
      });
      const info = await secondServer.start();
      assert.strictEqual(info.port, server.port + 1);
      assert.strictEqual(info.tunnelUrl, `http://127.0.0.1:${server.port + 1}`);
      assert.strictEqual(secondServer.tunnelUrl, `http://127.0.0.1:${server.port + 1}`);
      await secondServer.stop();
    });

  } finally {
    await server.stop();
    try { fs.rmSync(TEST_WORKSPACE, { recursive: true, force: true }); } catch (_) {}
  }

  console.log(`\nResults: ${passed}/${total} tests passed.`);
  if (passed !== total) {
    process.exit(1);
  }
}

runTests().catch(err => {
  console.error('Test runner failure:', err);
  process.exit(1);
});
