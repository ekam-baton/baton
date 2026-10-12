#!/usr/bin/env bash
# ==============================================================================
# Baton Desktop Bridge - macOS & Linux Launcher
# ==============================================================================

set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

echo "Starting Baton Desktop Bridge..."
echo ""

if ! command -v node >/dev/null 2>&1; then
    echo "[ERROR] Node.js is required to run Baton Bridge."
    echo "Please install Node.js from https://nodejs.org or via your package manager."
    echo ""
    exit 1
fi

exec node "$DIR/tools/baton-bridge/bin/baton-bridge.js" "$@"
