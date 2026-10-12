#!/usr/bin/env node

/**
 * Baton Desktop Bridge CLI
 * 1-Click zero-config companion for pairing Antigravity & PC AI environments with Baton.
 */

const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const { McpBridgeServer } = require('../src/server.js');
const { startTunnel, getLocalIpAddress } = require('../src/tunnel.js');
const { generateQrCode } = require('../src/qr.js');

// Parse simple CLI flags
const args = process.argv.slice(2);
let port = 9420;
let workspace = process.cwd();
let noTunnel = false;
let customToken = null;

for (let i = 0; i < args.length; i++) {
  if (args[i] === '--port' && args[i + 1]) {
    port = parseInt(args[++i], 10);
  } else if (args[i] === '--workspace' && args[i + 1]) {
    workspace = path.resolve(args[++i]);
  } else if (args[i] === '--no-tunnel' || args[i] === '--local') {
    noTunnel = true;
  } else if (args[i] === '--token' && args[i + 1]) {
    customToken = args[++i];
  } else if (args[i] === '--help' || args[i] === '-h') {
    console.log(`
Baton Desktop Bridge - Telegram & WhatsApp-level AI Companion

Usage:
  npx baton-bridge [options]

Options:
  --port <num>        Port for the local MCP server (default: 9420)
  --workspace <path>  Workspace directory for tools to access (default: .)
  --no-tunnel, --local Run on local Wi-Fi only (no Cloudflare tunnel)
  --token <secret>    Set a custom authentication token
  --help, -h          Show this help message
`);
    process.exit(0);
  }
}

async function probeOllama() {
  return new Promise((resolve) => {
    const req = http.get('http://127.0.0.1:11434/api/tags', { timeout: 1500 }, (res) => {
      resolve(res.statusCode === 200);
    });
    req.on('error', () => resolve(false));
    req.on('timeout', () => { req.destroy(); resolve(false); });
  });
}

async function main() {
  console.clear?.();
  console.log('\x1b[36m%s\x1b[0m', `
  ╔═══════════════════════════════════════════════════════════════════╗
  ║                 ⚡ BATON DESKTOP BRIDGE v1.0.0                     ║
  ║     Sovereign Remote Link for Antigravity & Local AI Workstations  ║
  ╚═══════════════════════════════════════════════════════════════════╝
  `);

  console.log('\x1b[90m%s\x1b[0m', `[•] Host: ${os.hostname()} (${os.platform()} ${os.arch()})`);
  console.log('\x1b[90m%s\x1b[0m', `[•] Workspace: ${workspace}`);

  // Probe local engines
  const hasOllama = await probeOllama();
  if (hasOllama) {
    console.log('\x1b[32m%s\x1b[0m', '  [✓] Detected Local Ollama Engine (127.0.0.1:11434)');
  }

  // 1. Start Local Server
  const server = new McpBridgeServer({
    port,
    workspace,
    token: customToken,
    onActivity: (action, details) => {
      const time = new Date().toLocaleTimeString();
      console.log('\x1b[35m%s\x1b[0m', `  [${time}] ⚡ Mobile Action: ${action}`);
    }
  });

  const serverInfo = await server.start();
  console.log('\x1b[32m%s\x1b[0m', `  [✓] Local MCP Server active on http://127.0.0.1:${serverInfo.port}`);

  // 2. Start Tunnel or Local Endpoint
  let tunnelResult;
  try {
    tunnelResult = await startTunnel(serverInfo.port, {
      noTunnel,
      onStatus: (msg) => console.log('\x1b[33m%s\x1b[0m', `  [•] ${msg}`),
      onProgress: (pct) => {
        process.stdout.write(`\r  [•] Downloading Cloudflare Quick Tunnel binary... ${pct}%`);
        if (pct === 100) process.stdout.write('\n');
      }
    });
  } catch (err) {
    console.error('\x1b[31m%s\x1b[0m', `\n  [✗] Tunnel failed to start: ${err.message}`);
    console.log('\x1b[33m%s\x1b[0m', '  Falling back to Local Wi-Fi endpoint...');
    const localIp = getLocalIpAddress();
    tunnelResult = {
      tunnelUrl: `http://${localIp}:${serverInfo.port}`,
      isLocal: true,
      close: () => {}
    };
  }

  const endpointUrl = tunnelResult.tunnelUrl;
  server.tunnelUrl = endpointUrl;

  if (!tunnelResult.isLocal) {
    console.log('\x1b[33m%s\x1b[0m', '  [•] Warming up Cloudflare Edge routing...');
    await new Promise(r => setTimeout(r, 1500));
  }

  // 3. Prepare Baton QR Pairing Payload
  const qrPayload = JSON.stringify({
    endpoint: endpointUrl,
    token: server.authToken,
    name: `Antigravity (${os.hostname()})`,
    model: 'antigravity-bridge'
  });

  const qr = generateQrCode(qrPayload);
  server.qrMatrix = qr.matrix;

  console.log('\n\x1b[32m%s\x1b[0m', '  ═══════════════════════════════════════════════════════════════════');
  console.log('\x1b[1m\x1b[36m%s\x1b[0m', `  ★ TUNNEL READY: ${endpointUrl}`);
  console.log('\x1b[90m%s\x1b[0m', `  Token: ${server.authToken}`);
  console.log('\x1b[32m%s\x1b[0m', '  ═══════════════════════════════════════════════════════════════════\n');

  console.log('\x1b[1m\x1b[37m%s\x1b[0m', '  POINT BATON APP CAMERA AT THIS QR CODE TO PAIR:');
  console.log('\x1b[90m%s\x1b[0m', '  (Open Baton -> Bot Creator -> Cloudflare/Ngrok -> "Scan Tunnel QR")\n');

  // Print Terminal QR
  const qrLines = qr.terminalText.split('\n');
  for (const line of qrLines) {
    console.log(`    ${line}`);
  }

  // Direct clickable link
  const directUri = `baton://connect?url=${encodeURIComponent(endpointUrl)}&token=${encodeURIComponent(server.authToken)}&name=${encodeURIComponent('Antigravity Desktop')}&model=antigravity-bridge`;

  console.log('\n\x1b[90m%s\x1b[0m', '  ───────────────────────────────────────────────────────────────────');
  console.log('\x1b[33m%s\x1b[0m', '  Or click / paste link in Baton:');
  console.log('\x1b[36m%s\x1b[0m', `  ${directUri}`);
  console.log('\x1b[90m%s\x1b[0m', '  ───────────────────────────────────────────────────────────────────');
  console.log('\x1b[32m%s\x1b[0m', `  Browser QR Viewer: http://127.0.0.1:${serverInfo.port}/qr`);
  console.log('\x1b[90m%s\x1b[0m', '  Press Ctrl+C to terminate session.\n');

  // Graceful shutdown
  const cleanup = () => {
    console.log('\n\x1b[33m%s\x1b[0m', '  Stopping Baton Desktop Bridge...');
    tunnelResult?.close();
    server.stop().then(() => {
      console.log('\x1b[32m%s\x1b[0m', '  [✓] Bridge session closed safely.');
      process.exit(0);
    });
  };

  process.on('SIGINT', cleanup);
  process.on('SIGTERM', cleanup);
}

main().catch((err) => {
  console.error('\x1b[31m%s\x1b[0m', `Fatal error: ${err.message}`);
  process.exit(1);
});
