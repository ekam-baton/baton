# BATON

BATON is a Zero-Trust, Local-First AI Orchestration Client for Android. It bridges the gap between on-device intelligence and external Model Context Protocol (MCP) servers, completely sidestepping centralized cloud LLM providers.

## Core Principles

1.  **Zero-Trust Architecture**: No API keys are stored on the device. All authentication is deferred to your secure, self-hosted MCP endpoints.
2.  **Local-First Orchestration**: The UI and message routing logic live natively on your Android device. You own your workflow.
3.  **Bring-Your-Own-Server (BYOS) Security**: Seamlessly connect to remote home servers using secure Cloudflare tunnels and stateless JWT token exchanges based on a custom `JWT_SECRET`.
4.  **Global Evidentiary Governance**: Every system action, chat message, and screenshot is rigorously audited and cryptographically secured for legal admissibility.

## Global Governance & Forensic Auditability

BATON is designed to be fully compliant with the world's most stringent digital evidence standards:

*   **ISO/IEC 27037 (Global)**: Evidence is exported into open, standardized `.zip` archives with detached cryptographic `.sig` files, ensuring any independent forensic lab can ingest and verify the chain of custody.
*   **eIDAS Regulation (EU/UK)**: We provide an `EnterpriseCertificateManager` that allows Mobile Device Management (MDM) platforms to inject a Qualified Trust Service Provider (QTSP) certificate. When present, BATON logs are signed using a **Qualified Electronic Signature (QES)**, making them legally equivalent to a handwritten signature.
*   **CISA & DISA STIG (US)**: Absolute data integrity is guaranteed via a **Merkle Tree Hash Chain**. Every log entry hashes the previous entry, meaning any tampering instantly breaks the chain.
*   **IT Act Sec 65B (India)**: By default, logs are signed using a hardware-backed ECDSA key generated in the Android Keystore, providing strong non-repudiation (Advanced Electronic Signature).
*   **Trusted NTP Time**: Time-spoofing is mitigated by fetching the true network time from `time.google.com` (SNTP), anchored against the device's monotonic uptime.

## Enterprise AI Environments
BATON acts as a zero-trust mobile endpoint for corporate VPCs. Since all data routing happens purely over HTTP/SSE on the device itself, BATON natively integrates with enterprise-managed AI instances without routing data through third-party servers:
- **Claude for Enterprise**: Connect to Anthropic managed endpoints via your AWS API Gateways.
- **Azure OpenAI / Codex**: Securely proxy to dedicated `.openai.azure.com` instances using internal corporate VPNs (e.g., GlobalProtect, Tailscale).
- **Gemini Enterprise (Vertex AI)**: Interface directly with Google Cloud Run endpoints authenticated via Google Workspace Service Accounts.

## Legal Disclaimer

*The cryptographic features provided by BATON are tools designed to secure data integrity. The developers of BATON provide no legal advice. You are solely responsible for ensuring your evidence exports meet the jurisdictional requirements of your local courts.*
