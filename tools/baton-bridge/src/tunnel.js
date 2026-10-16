/**
 * Cloudflare Quick Tunnel Manager.
 * Establishes a zero-configuration, secure, temporary *.trycloudflare.com tunnel
 * without requiring account registration, port forwarding, or firewall changes.
 */

const { spawn, execSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const https = require('node:https');

// Default binary cache directory
const BATON_HOME = path.join(os.homedir(), '.baton');
const BIN_DIR = path.join(BATON_HOME, 'bin');

function getLocalIpAddress() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}

function findExistingCloudflared() {
  try {
    const isWindows = process.platform === 'win32';
    const checkCmd = isWindows ? 'where.exe cloudflared' : 'which cloudflared';
    const stdout = execSync(checkCmd, { stdio: ['pipe', 'pipe', 'ignore'] }).toString().trim();
    if (stdout) {
      return stdout.split('\n')[0].trim();
    }
  } catch (_) {
    // Not on PATH
  }

  const binaryName = process.platform === 'win32' ? 'cloudflared.exe' : 'cloudflared';
  const cachedPath = path.join(BIN_DIR, binaryName);
  if (fs.existsSync(cachedPath)) {
    return cachedPath;
  }

  return null;
}

function getDownloadUrl() {
  const platform = process.platform;
  const arch = process.arch;

  if (platform === 'win32') {
    return 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe';
  } else if (platform === 'darwin') {
    return arch === 'arm64'
      ? 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-arm64.tgz'
      : 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-amd64.tgz';
  } else {
    // Linux
    return arch === 'arm64'
      ? 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64'
      : 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64';
  }
}

function downloadBinary(url, destPath, onProgress) {
  return new Promise((resolve, reject) => {
    fs.mkdirSync(path.dirname(destPath), { recursive: true });

    function fetch(currentUrl) {
      https.get(currentUrl, (response) => {
        // Handle HTTP 302 redirects (GitHub releases redirect to AWS S3)
        if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
          fetch(response.headers.location);
          return;
        }

        if (response.statusCode !== 200) {
          reject(new Error(`Failed to download binary: HTTP ${response.statusCode}`));
          return;
        }

        const file = fs.createWriteStream(destPath);
        const totalBytes = parseInt(response.headers['content-length'] || '0', 10);
        let downloadedBytes = 0;

        response.on('data', (chunk) => {
          downloadedBytes += chunk.length;
          if (totalBytes > 0 && onProgress) {
            const pct = Math.round((downloadedBytes / totalBytes) * 100);
            onProgress(pct, downloadedBytes, totalBytes);
          }
        });

        response.pipe(file);

        file.on('finish', () => {
          file.close(() => {
            // Make executable on Unix
            if (process.platform !== 'win32') {
              try { fs.chmodSync(destPath, 0o755); } catch (_) {}
            }
            resolve(destPath);
          });
        });

        file.on('error', (err) => {
          try { file.destroy(); } catch (_) {}
          fs.unlink(destPath, () => {});
          reject(err);
        });
      }).on('error', (err) => {
        fs.unlink(destPath, () => {});
        reject(err);
      });
    }

    fetch(url);
  });
}

async function ensureCloudflared(onProgress) {
  const existing = findExistingCloudflared();
  if (existing) return existing;

  const binaryName = process.platform === 'win32' ? 'cloudflared.exe' : 'cloudflared';
  const destPath = path.join(BIN_DIR, binaryName);
  const downloadUrl = getDownloadUrl();

  await downloadBinary(downloadUrl, destPath, onProgress);
  return destPath;
}

/**
 * Spawns a Cloudflare Quick Tunnel pointing to http://127.0.0.1:<port>
 * Equipped with process supervision and exponential backoff auto-reconnect.
 */
async function startTunnel(port, options = {}) {
  const { onStatus, onProgress, onReconnect } = options;

  if (options.noTunnel) {
    const localIp = getLocalIpAddress();
    const localUrl = `http://${localIp}:${port}`;
    return {
      tunnelUrl: localUrl,
      isLocal: true,
      close: () => {}
    };
  }

  onStatus?.('Checking Cloudflare Quick Tunnel binary...');
  const binaryPath = await ensureCloudflared(onProgress);

  onStatus?.('Starting secure tunnel to http://127.0.0.1:' + port + '...');

  let activeProc = null;
  let isClosed = false;
  let reconnectAttempt = 0;
  let reconnectTimer = null;

  function launchProcess() {
    return new Promise((resolve, reject) => {
      const tunnelArgs = ['tunnel', '--url', `http://127.0.0.1:${port}`];
      const proc = spawn(binaryPath, tunnelArgs, {
        stdio: ['ignore', 'pipe', 'pipe'],
        windowsHide: true
      });
      activeProc = proc;

      let tunnelUrl = null;
      let resolved = false;
      let stderrOutput = '';

      const timeout = setTimeout(() => {
        if (!resolved) {
          resolved = true;
          try { proc.kill(); } catch (_) {}
          reject(new Error('Tunnel handshake timed out after 30 seconds.\nOutput: ' + stderrOutput));
        }
      }, 30000);

      function checkChunk(chunk) {
        const text = chunk.toString();
        if (stderrOutput.length < 8192) {
          stderrOutput += text;
        }

        // Match *.trycloudflare.com
        const match = text.match(/https:\/\/[a-zA-Z0-9-]+\.trycloudflare\.com/);
        if (match && !resolved) {
          tunnelUrl = match[0];
          resolved = true;
          clearTimeout(timeout);
          reconnectAttempt = 0; // Reset backoff upon successful handshake
          resolve({ tunnelUrl, proc });
        }
      }

      proc.stdout.on('data', checkChunk);
      proc.stderr.on('data', checkChunk);

      proc.on('error', (err) => {
        if (!resolved) {
          resolved = true;
          clearTimeout(timeout);
          reject(err);
        }
      });

      proc.on('exit', (code) => {
        if (!resolved) {
          resolved = true;
          clearTimeout(timeout);
          reject(new Error(`cloudflared exited with code ${code}.\n${stderrOutput}`));
        } else if (!isClosed) {
          // Process exited after initial success — schedule supervisor reconnect
          function scheduleReconnect() {
            if (isClosed) return;
            reconnectAttempt++;
            const delayMs = Math.min(1000 * Math.pow(2, reconnectAttempt - 1), 30000);
            onStatus?.(`Tunnel process disconnected (code ${code}). Reconnecting in ${delayMs / 1000}s (attempt ${reconnectAttempt})...`);
            reconnectTimer = setTimeout(async () => {
              if (isClosed) return;
              try {
                const result = await launchProcess();
                currentTunnelUrl = result.tunnelUrl;
                onStatus?.(`Tunnel reconnected: ${result.tunnelUrl}`);
                onReconnect?.(result.tunnelUrl);
              } catch (reconnectErr) {
                onStatus?.(`Tunnel reconnect failed: ${reconnectErr.message}. Retrying...`);
                scheduleReconnect();
              }
            }, delayMs);
          }
          scheduleReconnect();
        }
      });
    });
  }

  // Initial connection
  const initial = await launchProcess();
  let currentTunnelUrl = initial.tunnelUrl;

  const handle = {
    get tunnelUrl() { return currentTunnelUrl; },
    isLocal: false,
    get process() { return activeProc; },
    close: () => {
      isClosed = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (activeProc) {
        try { activeProc.kill('SIGTERM'); } catch (_) {}
      }
    }
  };

  process.on('exit', () => {
    handle.close();
  });

  return handle;
}

module.exports = {
  startTunnel,
  getLocalIpAddress,
  ensureCloudflared,
  findExistingCloudflared
};
