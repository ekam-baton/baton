use std::collections::HashSet;
use std::fs;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;

use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use base64::Engine;
use hmac::{Hmac, Mac};
use sha2::Sha256;
use x25519_dalek::{PublicKey, StaticSecret};
use hkdf::Hkdf;
use serde::Deserialize;
use zeroize::Zeroize;

type HmacSha256 = Hmac<Sha256>;

// ---------------------------------------------------------------------------
// Trusted client database
// ---------------------------------------------------------------------------

/// One entry in trusted_clients.json.
#[derive(Debug, Deserialize, Clone)]
struct TrustedClient {
    /// Human-readable identifier for logs / audit trails.
    user_email: String,
    /// Hex-encoded 32-byte X25519 public key issued to this user.
    client_public_key: String,
    /// Revocation flag — set to false to instantly block access.
    is_active: bool,
}

/// Load the full trusted-clients list from disk.
/// The file is read fresh on every incoming request so that revoking a key
/// takes effect without restarting the gateway.
fn load_trusted_clients() -> Result<Vec<TrustedClient>, String> {
    let data = fs::read_to_string("trusted_clients.json")
        .map_err(|e| format!("Could not read trusted_clients.json: {}", e))?;
    serde_json::from_str::<Vec<TrustedClient>>(&data)
        .map_err(|e| format!("Malformed trusted_clients.json: {}", e))
}

/// Look up a hex-encoded public key in the trusted-clients database.
/// Returns `Ok(client)` if the key is found and active, or an HTTP-ready
/// error string otherwise.
fn authorize_client_key(hex_key: &str) -> Result<TrustedClient, (u16, &'static str)> {
    let clients = load_trusted_clients().map_err(|_| (500u16, "Internal server error"))?;

    let normalised = hex_key.trim().to_lowercase();
    match clients.into_iter().find(|c| c.client_public_key.trim().to_lowercase() == normalised) {
        None => Err((403, "Unknown client key")),
        Some(c) if !c.is_active => Err((403, "Access revoked")),
        Some(c) => Ok(c),
    }
}

// ---------------------------------------------------------------------------
// Replay protection
// ---------------------------------------------------------------------------

struct NonceStore {
    nonces: HashSet<String>,
}

impl NonceStore {
    fn new() -> Self {
        Self {
            nonces: HashSet::new(),
        }
    }

    fn check_and_add(&mut self, nonce: &str) -> bool {
        if self.nonces.contains(nonce) {
            false
        } else {
            self.nonces.insert(nonce.to_string());
            true
        }
    }
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // 1. Generate local gateway identity (X25519)
    let private_key = Arc::new(StaticSecret::random_from_rng(rand::thread_rng()));
    let public_key = PublicKey::from(private_key.as_ref());

    println!("==========================================");
    println!("BATON RUST GATEWAY ENGINE");
    println!("Gateway Public Key (X25519 Hex): {}", to_hex(public_key.as_bytes()));
    println!("Share the public key above with each client so they can");
    println!("derive the same Diffie-Hellman shared secret.");
    println!("==========================================");

    // Validate that trusted_clients.json is present and parseable at startup.
    match load_trusted_clients() {
        Ok(clients) => println!("Loaded {} trusted client(s) from trusted_clients.json", clients.len()),
        Err(e) => eprintln!("WARNING: {}", e),
    }

    // Shared nonce store for replay protection (across all connections).
    let nonce_store = Arc::new(Mutex::new(NonceStore::new()));

    // 2. Start TCP HTTP server on port 8080.
    let listener = TcpListener::bind("127.0.0.1:8080").await?;
    println!("Gateway listening on http://127.0.0.1:8080");

    loop {
        let (mut socket, addr) = listener.accept().await?;
        let nonce_store = Arc::clone(&nonce_store);
        let private_key = Arc::clone(&private_key);

        tokio::spawn(async move {
            println!("Connection accepted from {}", addr);
            let mut buf = [0; 8192];
            let n = match socket.read(&mut buf).await {
                Ok(n) if n > 0 => n,
                _ => return,
            };

            let request_str = String::from_utf8_lossy(&buf[..n]);

            // Split headers and body.
            let parts: Vec<&str> = request_str.split("\r\n\r\n").collect();
            if parts.len() < 2 {
                return;
            }

            let header_part = parts[0];
            let body_part = parts[1];

            // ------------------------------------------------------------------
            // Parse security-relevant headers
            // ------------------------------------------------------------------
            let mut timestamp: Option<u64> = None;
            let mut nonce: Option<&str> = None;
            let mut signature: Option<&str> = None;
            let mut client_key_hex: Option<&str> = None;

            for line in header_part.lines() {
                let lower = line.to_lowercase();
                if lower.starts_with("x-baton-timestamp:") {
                    timestamp = line.splitn(2, ':').nth(1)
                        .and_then(|s| s.trim().parse().ok());
                } else if lower.starts_with("x-baton-nonce:") {
                    nonce = line.splitn(2, ':').nth(1).map(str::trim);
                } else if lower.starts_with("x-baton-signature:") {
                    signature = line.splitn(2, ':').nth(1).map(str::trim);
                } else if lower.starts_with("x-baton-client-key:") {
                    client_key_hex = line.splitn(2, ':').nth(1).map(str::trim);
                }
            }

            // ------------------------------------------------------------------
            // Step 1 — Require X-Baton-Client-Key
            // ------------------------------------------------------------------
            let key_hex = match client_key_hex {
                Some(k) => k,
                None => {
                    send_status_error(&mut socket, 401, "Missing X-Baton-Client-Key header").await;
                    return;
                }
            };

            // ------------------------------------------------------------------
            // Step 2 — Look up key in trusted_clients.json (revocation check)
            // ------------------------------------------------------------------
            let trusted_client = match authorize_client_key(key_hex) {
                Ok(c) => c,
                Err((status, msg)) => {
                    println!("Rejected request from {} — {} (key: {})", addr, msg, key_hex);
                    send_status_error(&mut socket, status, msg).await;
                    return;
                }
            };

            println!(
                "Authorized client: {} (key: {}...{})",
                trusted_client.user_email,
                &key_hex[..8.min(key_hex.len())],
                &key_hex[key_hex.len().saturating_sub(8)..]
            );

            // ------------------------------------------------------------------
            // Step 3 — Parse client public key bytes and derive shared secret
            // ------------------------------------------------------------------
            let key_bytes_vec = match hex::decode(key_hex) {
                Ok(b) if b.len() == 32 => b,
                _ => {
                    send_status_error(&mut socket, 400, "Invalid client public key format (expected 32-byte hex)").await;
                    return;
                }
            };

            let mut key_bytes = [0u8; 32];
            key_bytes.copy_from_slice(&key_bytes_vec);
            let client_public_key = PublicKey::from(key_bytes);

            // Perform X25519 Diffie-Hellman
            let shared_point = private_key.diffie_hellman(&client_public_key);
            let mut hasher = sha2::Sha256::new();
            use sha2::Digest;
            hasher.update(shared_point.as_bytes());
            let shared_secret = hasher.finalize();

            // ------------------------------------------------------------------
            // Step 4 — Parse encrypted JSON body
            // ------------------------------------------------------------------
            let json_body: serde_json::Value = match serde_json::from_str(body_part) {
                Ok(v) => v,
                Err(_) => {
                    send_status_error(&mut socket, 400, "Invalid JSON body").await;
                    return;
                }
            };

            let ciphertext_b64 = match json_body["ciphertext"].as_str() {
                Some(c) => c,
                None => {
                    send_status_error(&mut socket, 400, "Missing ciphertext").await;
                    return;
                }
            };

            let iv_b64 = match json_body["iv"].as_str() {
                Some(iv) => iv,
                None => {
                    send_status_error(&mut socket, 400, "Missing IV").await;
                    return;
                }
            };

            // Require Timestamp and Nonce for HKDF key derivation.
            if timestamp.is_none() || nonce.is_none() {
                send_status_error(&mut socket, 400, "Missing X-Baton-Timestamp or X-Baton-Nonce headers").await;
                return;
            }

            let ts = timestamp.unwrap();
            let n = nonce.unwrap();

            // ------------------------------------------------------------------
            // Step 5 — Verify HMAC Signature & Replay Protection
            // ------------------------------------------------------------------
            if let Some(sig) = signature {
                // Enforce 5-minute timestamp skew window.
                let now = SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap()
                    .as_millis() as u64;
                let skew = if now > ts { now - ts } else { ts - now };
                if skew > 300_000 {
                    send_status_error(&mut socket, 401, "Signature expired").await;
                    return;
                }

                // Check replay prevention.
                let is_valid_nonce = {
                    let mut store = nonce_store.lock().unwrap();
                    store.check_and_add(n)
                };
                if !is_valid_nonce {
                    send_status_error(&mut socket, 401, "Replay attack detected (nonce reused)").await;
                    return;
                }

                // Derive HMAC signing key from the shared secret.
                let mut mac_hasher = sha2::Sha256::new();
                mac_hasher.update(shared_secret.as_slice());
                let signing_key = mac_hasher.finalize();

                let signature_input = format!("{}:{}:{}", ts, n, ciphertext_b64);
                let mut mac_verify = <HmacSha256 as hmac::Mac>::new_from_slice(&signing_key)
                    .expect("HMAC key size is always valid");
                mac_verify.update(signature_input.as_bytes());

                let sig_bytes = match hex::decode(sig) {
                    Ok(b) => b,
                    Err(_) => {
                        send_status_error(&mut socket, 400, "Invalid signature format").await;
                        return;
                    }
                };

                if mac_verify.verify_slice(&sig_bytes).is_err() {
                    send_status_error(&mut socket, 401, "Invalid signature").await;
                    return;
                }

                println!("Signature successfully verified for {}!", trusted_client.user_email);
            }

            // ------------------------------------------------------------------
            // Step 6 — Derive per-request AES key via HKDF and decrypt payload
            // ------------------------------------------------------------------
            let info = format!("{}:{}", ts, n);
            let salt = [0u8; 32];
            let hk = Hkdf::<Sha256>::new(Some(&salt), shared_secret.as_slice());
            let mut derived_key = [0u8; 32];
            hk.expand(info.as_bytes(), &mut derived_key).unwrap();

            let key = aes_gcm::Key::<Aes256Gcm>::from_slice(&derived_key);
            let cipher = Aes256Gcm::new(key);

            let ciphertext = match base64::engine::general_purpose::STANDARD.decode(ciphertext_b64) {
                Ok(b) => b,
                Err(_) => {
                    send_status_error(&mut socket, 400, "Invalid base64 ciphertext").await;
                    derived_key.zeroize();
                    return;
                }
            };

            let iv = match base64::engine::general_purpose::STANDARD.decode(iv_b64) {
                Ok(b) => b,
                Err(_) => {
                    send_status_error(&mut socket, 400, "Invalid base64 IV").await;
                    derived_key.zeroize();
                    return;
                }
            };

            let nonce_gcm = Nonce::from_slice(&iv);
            let decrypted_bytes = match cipher.decrypt(nonce_gcm, ciphertext.as_ref()) {
                Ok(d) => d,
                Err(_) => {
                    send_status_error(&mut socket, 400, "Decryption failed").await;
                    derived_key.zeroize();
                    return;
                }
            };

            derived_key.zeroize();

            let decrypted_plaintext = String::from_utf8(decrypted_bytes).unwrap_or_default();
            println!(
                "Decrypted payload from {}: {}",
                trusted_client.user_email,
                decrypted_plaintext
            );

            // ------------------------------------------------------------------
            // Step 7 — Forward to real MCP agent (mock response for now)
            // ------------------------------------------------------------------
            let mock_mcp_response = serde_json::json!({
                "jsonrpc": "2.0",
                "id": "1",
                "result": {
                    "tools": [
                        {
                            "name": "read_file",
                            "description": "Read file contents",
                            "inputSchema": {}
                        }
                    ]
                }
            });

            let response_plaintext = serde_json::to_string(&mock_mcp_response).unwrap();

            // Encrypt the response with the same per-request cipher.
            let response_iv_bytes = rand::random::<[u8; 12]>();
            let response_nonce = Nonce::from_slice(&response_iv_bytes);

            // Derive a fresh cipher for the response (same shared secret, new IV).
            let info_resp = format!("{}:{}", ts, n);
            let hk_resp = Hkdf::<Sha256>::new(Some(&salt), shared_secret.as_slice());
            let mut derived_key_resp = [0u8; 32];
            hk_resp.expand(info_resp.as_bytes(), &mut derived_key_resp).unwrap();
            let key_resp = aes_gcm::Key::<Aes256Gcm>::from_slice(&derived_key_resp);
            let cipher_resp = Aes256Gcm::new(key_resp);

            let encrypted_response = cipher_resp
                .encrypt(response_nonce, response_plaintext.as_bytes())
                .unwrap();
            derived_key_resp.zeroize();

            let enc_resp_b64 = base64::engine::general_purpose::STANDARD.encode(encrypted_response);
            let iv_resp_b64 = base64::engine::general_purpose::STANDARD.encode(response_iv_bytes);

            let json_response = serde_json::json!({
                "ciphertext": enc_resp_b64,
                "iv": iv_resp_b64
            });

            let http_body = serde_json::to_string(&json_response).unwrap();
            let http_response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                http_body.len(),
                http_body
            );

            let _ = socket.write_all(http_response.as_bytes()).await;
            let _ = socket.flush().await;
        });
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Send an HTTP error response with an explicit status code.
async fn send_status_error(socket: &mut tokio::net::TcpStream, status: u16, msg: &str) {
    let reason = match status {
        400 => "Bad Request",
        401 => "Unauthorized",
        403 => "Forbidden",
        500 => "Internal Server Error",
        _ => "Error",
    };
    let err_json = serde_json::json!({ "error": msg });
    let body = serde_json::to_string(&err_json).unwrap();
    let resp = format!(
        "HTTP/1.1 {status} {reason}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        body.len(),
        body
    );
    let _ = socket.write_all(resp.as_bytes()).await;
    let _ = socket.flush().await;
}

fn to_hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

// ---------------------------------------------------------------------------
// Unit tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_key_exchange_and_dh() {
        let private1 = StaticSecret::random_from_rng(rand::thread_rng());
        let public1 = PublicKey::from(&private1);

        let private2 = StaticSecret::random_from_rng(rand::thread_rng());
        let public2 = PublicKey::from(&private2);

        let shared1 = private1.diffie_hellman(&public2);
        let shared2 = private2.diffie_hellman(&public1);

        assert_eq!(shared1.as_bytes(), shared2.as_bytes());
    }

    #[test]
    fn test_encrypt_decrypt_payload() {
        use aes_gcm::aead::{Aead, KeyInit};
        use aes_gcm::{Aes256Gcm, Nonce};

        let key_bytes = [0u8; 32];
        let key = aes_gcm::Key::<Aes256Gcm>::from_slice(&key_bytes);
        let cipher = Aes256Gcm::new(key);

        let iv = [1u8; 12];
        let nonce = Nonce::from_slice(&iv);

        let plaintext = b"hello world";
        let encrypted = cipher.encrypt(nonce, plaintext.as_ref()).unwrap();
        let decrypted = cipher.decrypt(nonce, encrypted.as_ref()).unwrap();

        assert_eq!(plaintext.as_ref(), decrypted.as_slice());
    }

    #[test]
    fn test_nonce_store() {
        let mut store = NonceStore::new();
        assert!(store.check_and_add("nonce1"));
        assert!(!store.check_and_add("nonce1")); // Replay — must be rejected.
        assert!(store.check_and_add("nonce2"));
    }

    #[test]
    fn test_authorize_client_key_unknown() {
        // A random hex key that is definitely not in the file on CI.
        let result = authorize_client_key("0000000000000000000000000000000000000000000000000000000000000000");
        // Either the file is missing (500) or the key is not found (403).
        assert!(result.is_err());
    }

    #[test]
    fn test_to_hex() {
        assert_eq!(to_hex(&[0x0a, 0xff, 0x10]), "0aff10");
    }

    #[test]
    fn test_trusted_client_deserialization() {
        let json = r#"[
            {"user_email":"a@b.com","client_public_key":"aabb","is_active":true},
            {"user_email":"c@d.com","client_public_key":"ccdd","is_active":false}
        ]"#;
        let clients: Vec<TrustedClient> = serde_json::from_str(json).unwrap();
        assert_eq!(clients.len(), 2);
        assert!(clients[0].is_active);
        assert!(!clients[1].is_active);
        assert_eq!(clients[0].user_email, "a@b.com");
    }

    #[test]
    fn test_authorize_revoked_key_from_inline_db() {
        // Simulate what authorize_client_key does with a revoked client.
        let clients: Vec<TrustedClient> = serde_json::from_str(r#"[
            {"user_email":"revoked@example.com","client_public_key":"deadbeef","is_active":false}
        ]"#).unwrap();
        let hex_key = "deadbeef";
        let normalised = hex_key.trim().to_lowercase();
        let found = clients.into_iter().find(|c| c.client_public_key.trim().to_lowercase() == normalised);
        assert!(found.is_some());
        assert!(!found.unwrap().is_active, "Revoked client should have is_active=false");
    }

    #[test]
    fn test_authorize_active_key_from_inline_db() {
        let clients: Vec<TrustedClient> = serde_json::from_str(r#"[
            {"user_email":"active@example.com","client_public_key":"cafebabe","is_active":true}
        ]"#).unwrap();
        let hex_key = "cafebabe";
        let normalised = hex_key.trim().to_lowercase();
        let found = clients.into_iter().find(|c| c.client_public_key.trim().to_lowercase() == normalised);
        assert!(found.is_some());
        assert!(found.unwrap().is_active, "Active client should have is_active=true");
    }
}
