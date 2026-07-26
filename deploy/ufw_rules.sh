#!/bin/bash
# ==============================================================================
# BATON Layer 1 Network Firewall Hardening Script (UFW)
# ==============================================================================
# WARNING: Run as root on your production Linux VPS server.
# This locks down all ports except SSH (22) and HTTPS/WSS (443).
# ==============================================================================

set -euo pipefail

echo "[FIREWALL] Hardening VPS Network Boundary..."

# 1. Default Policies: Deny incoming, Allow outgoing
ufw default deny incoming
ufw default allow outgoing

# 2. Allow SSH (Port 22) - Key-only authentication recommended
ufw allow 22/tcp comment "SSH Remote Access"

# 3. Allow Public HTTPS / WSS (Port 443) - Reverse Proxy Entrypoint
ufw allow 443/tcp comment "Public WSS/HTTPS Gateway"

# 4. Explicitly DENY public access to internal ports (Defense-in-Depth)
ufw deny 8080/tcp comment "Block Raw Gateway Engine TCP"
ufw deny 8081/tcp comment "Block Internal Tool Server API"
ufw deny 8000/tcp comment "Block Internal Swarm Webhook"

# 5. Enable Firewall
ufw --force enable

echo "[FIREWALL] ✅ UFW Firewall Hardened Successfully!"
ufw status verbose
