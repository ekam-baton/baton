# BATON

BATON is a Zero-Trust AI Orchestration Client for Android and Desktop. It serves as a highly secure, privacy-first router that bridges your devices directly to Model Context Protocol (MCP) servers and Enterprise AI APIs, without sacrificing convenience or security.

## Core Principles

1.  **Zero-Trust Architecture & E2EE**: While BATON provides seamless relay infrastructure, we can never read your data. Traffic routed through our network is secured using End-to-End Encryption (Sovereign X25519) and HMAC-SHA256 token verification. Your keys never leave your device.
2.  **Hybrid Networking (`raceEndpoints`)**: BATON automatically guarantees the fastest connection possible. When sending a message, the app simultaneously races a connection over your Local LAN and our public TURN/Relay servers. If you are at home, the local route wins instantly. If you step outside onto cellular data, the relay takes over seamlessly with zero manual configuration.
3.  **Local-First Orchestration**: The UI, cryptographic key generation, and message routing logic live natively on your device. You own your workflow.
4.  **Global Evidentiary Governance**: Every system action, chat message, and screenshot is rigorously audited and cryptographically secured for legal admissibility.

## Seamless Connectivity & Deployment

BATON supports three distinct topologies for connecting to your AI agents:

*   **Local Wi-Fi Network**: Run an MCP server on your personal laptop. BATON connects directly over your local router (e.g., `192.168.1.10`) with zero cloud dependencies.
*   **The BATON Relay (TURN)**: For users on the go, BATON provides a managed, robust TURN/Gateway Server. This allows your phone to traverse restrictive NATs and firewalls to reach your home server without exposing your network to the public internet.
*   **WebRTC Agent-to-Agent (A2A)**: Devices can establish direct, peer-to-peer WebRTC tunnels with other BATON clients. 

## Global Governance & Forensic Auditability

BATON is designed to be fully compliant with the world's most stringent digital evidence standards:

*   **ISO/IEC 27037 (Global)**: Evidence is exported into open, standardized `.zip` archives with detached cryptographic `.sig` files, ensuring any independent forensic lab can ingest and verify the chain of custody.
*   **eIDAS Regulation (EU/UK)**: We provide an `EnterpriseCertificateManager` that allows Mobile Device Management (MDM) platforms to inject a Qualified Trust Service Provider (QTSP) certificate. When present, BATON logs are signed using a **Qualified Electronic Signature (QES)**.
*   **CISA & DISA STIG (US)**: Absolute data integrity is guaranteed via a **Merkle Tree Hash Chain**. Every log entry hashes the previous entry, meaning any tampering instantly breaks the chain.
*   **IT Act Sec 65B (India)**: By default, logs are signed using a hardware-backed ECDSA key generated in the Android Keystore.

## Enterprise AI Environments

BATON acts as a zero-trust mobile endpoint for corporate VPCs. Since all data routing happens purely over HTTP/SSE on the device itself, BATON natively integrates with enterprise-managed AI instances without routing data through third-party servers:
- **Claude for Enterprise**: Connect to Anthropic managed endpoints via your AWS API Gateways.
- **Azure OpenAI / Codex**: Securely proxy to dedicated `.openai.azure.com` instances using internal corporate VPNs (e.g., GlobalProtect, Tailscale).
- **Gemini Enterprise (Vertex AI)**: Interface directly with Google Cloud Run endpoints authenticated via Google Workspace Service Accounts.

## Legal Disclaimer

*The cryptographic features provided by BATON are tools designed to secure data integrity. The developers of BATON provide no legal advice. You are solely responsible for ensuring your evidence exports meet the jurisdictional requirements of your local courts.*
