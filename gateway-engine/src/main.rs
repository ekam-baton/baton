use std::collections::HashSet;
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
use zeroize::Zeroize;

type HmacSha256 = Hmac<Sha256>;

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

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    // 1. Generate local gateway identity (X25519)
    let private_key = StaticSecret::random_from_rng(rand::thread_rng());
    let public_key = PublicKey::from(&private_key);

    println!("==========================================");
    println!("BATON RUST GATEWAY ENGINE");
    println!("Gateway Public Key (X25519 Hex): {}", to_hex(public_key.as_bytes()));
    println!("==========================================");

    // Mock client public key for demo/key-exchange validation
    // In production, load this from config or database
    let mock_client_private = StaticSecret::random_from_rng(rand::thread_rng());
    let client_public_key = PublicKey::from(&mock_client_private);
    println!("Generated Demo Client Public Key: {}", to_hex(client_public_key.as_bytes()));

    // Derive shared secret
    let shared_point = private_key.diffie_hellman(&client_public_key);
    let mut hasher = sha2::Sha256::new();
    use sha2::Digest;
    hasher.update(shared_point.as_bytes());
    let shared_secret = hasher.finalize();

    println!("Derived Shared Symmetric Key: {}", to_hex(&shared_secret));

    // Shared nonce store for replay protection
    let nonce_store = Arc::new(Mutex::new(NonceStore::new()));

    // 2. Start TCP HTTP Server listening on port 8080
    let listener = TcpListener::bind("127.0.0.1:8080").await?;
    println!("Gateway listening on http://127.0.0.1:8080");

    loop {
        let (mut socket, addr) = listener.accept().await?;
        let nonce_store = Arc::clone(&nonce_store);
        let shared_secret = shared_secret.clone();

        tokio::spawn(async move {
            println!("Connection accepted from {}", addr);
            let mut buf = [0; 8192];
            let n = match socket.read(&mut buf).await {
                Ok(n) if n > 0 => n,
                _ => return,
            };

            let request_str = String::from_utf8_lossy(&buf[..n]);
            
            // Extract headers & body
            let parts: Vec<&str> = request_str.split("\r\n\r\n").collect();
            if parts.len() < 2 {
                return;
            }
            
            let header_part = parts[0];
            let body_part = parts[1];

            let mut timestamp: Option<u64> = None;
            let mut nonce: Option<&str> = None;
            let mut signature: Option<&str> = None;

            for line in header_part.lines() {
                if line.starts_with("X-Baton-Timestamp:") {
                    timestamp = line.split(':').nth(1).map(|s| s.trim().parse().ok()).flatten();
                } else if line.starts_with("X-Baton-Nonce:") {
                    nonce = line.split(':').nth(1).map(|s| s.trim());
                } else if line.starts_with("X-Baton-Signature:") {
                    signature = line.split(':').nth(1).map(|s| s.trim());
                }
            }

            // Parse encrypted body
            let json_body: serde_json::Value = match serde_json::from_str(body_part) {
                Ok(v) => v,
                Err(_) => {
                    send_error(&mut socket, "Invalid JSON body").await;
                    return;
                }
            };

            let ciphertext_b64 = match json_body["ciphertext"].as_str() {
                Some(c) => c,
                None => {
                    send_error(&mut socket, "Missing ciphertext").await;
                    return;
                }
            };

            let iv_b64 = match json_body["iv"].as_str() {
                Some(iv) => iv,
                None => {
                    send_error(&mut socket, "Missing IV").await;
                    return;
                }
            };

            // Require Timestamp and Nonce for HKDF derivation
            if timestamp.is_none() || nonce.is_none() {
                send_error(&mut socket, "Missing X-Baton-Timestamp or X-Baton-Nonce headers").await;
                return;
            }
            
            let ts = timestamp.unwrap();
            let n = nonce.unwrap();

            // 3. Verify Signature & Replay Protection (Signed/Sovereign mode validation)
            if let Some(sig) = signature {
                // Verify timestamp skew (5 minutes)
                let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64;
                let skew = if now > ts { now - ts } else { ts - now };
                if skew > 300_000 {
                    send_error(&mut socket, "Signature expired").await;
                    return;
                }

                // Verify replay prevention
                let is_valid_nonce = {
                    let mut store = nonce_store.lock().unwrap();
                    store.check_and_add(n)
                };
                if !is_valid_nonce {
                    send_error(&mut socket, "Replay attack detected (nonce reused)").await;
                    return;
                }

                // Verify signature match
                let mut mac_hasher = sha2::Sha256::new();
                mac_hasher.update(shared_secret.as_slice());
                let signing_key = mac_hasher.finalize();

                let signature_input = format!("{}:{}:{}", ts, n, ciphertext_b64);
                // Use constant-time comparison for signature
                let mut mac_verify = <HmacSha256 as hmac::Mac>::new_from_slice(&signing_key).unwrap();
                mac_verify.update(signature_input.as_bytes());
                let sig_bytes = match hex::decode(sig) {
                    Ok(b) => b,
                    Err(_) => {
                        send_error(&mut socket, "Invalid signature format").await;
                        return;
                    }
                };
                if mac_verify.verify_slice(&sig_bytes).is_err() {
                    send_error(&mut socket, "Invalid signature").await;
                    return;
                }
                println!("Signature successfully verified!");
            }

            // 4. Derive per-request AES key using HKDF and decrypt payload
            let info = format!("{}:{}", ts, n);
            let salt = [0u8; 32];
            let hk = Hkdf::<Sha256>::new(Some(&salt), shared_secret.as_slice());
            let mut derived_key = [0u8; 32];
            hk.expand(info.as_bytes(), &mut derived_key).unwrap();

            let key = aes_gcm::Key::<Aes256Gcm>::from_slice(&derived_key);
            let cipher = Aes256Gcm::new(key);
            
            let ciphertext = base64::engine::general_purpose::STANDARD.decode(ciphertext_b64).unwrap();
            let iv = base64::engine::general_purpose::STANDARD.decode(iv_b64).unwrap();
            let nonce_gcm = Nonce::from_slice(&iv);

            let decrypted_bytes = match cipher.decrypt(nonce_gcm, ciphertext.as_ref()) {
                Ok(d) => d,
                Err(_) => {
                    send_error(&mut socket, "Decryption failed").await;
                    derived_key.zeroize();
                    return;
                }
            };
            
            // Decryption successful, zeroize derived key
            derived_key.zeroize();

            let decrypted_plaintext = String::from_utf8(decrypted_bytes).unwrap();
            println!("Decrypted Request Payload: {}", decrypted_plaintext);

            // 5. Mock MCP Server Response (In production, forward to real local agent)
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

            // 6. Encrypt response payload
            let response_iv_bytes = rand::random::<[u8; 12]>();
            let response_nonce = Nonce::from_slice(&response_iv_bytes);
            
            let encrypted_response = cipher.encrypt(response_nonce, response_plaintext.as_bytes()).unwrap();
            
            let enc_resp_b64 = base64::engine::general_purpose::STANDARD.encode(encrypted_response);
            let iv_resp_b64 = base64::engine::general_purpose::STANDARD.encode(response_iv_bytes);

            let json_response = serde_json::json!({
                "ciphertext": enc_resp_b64,
                "iv": iv_resp_b64
            });

            let http_response_body = serde_json::to_string(&json_response).unwrap();
            let http_response = format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                http_response_body.len(),
                http_response_body
            );

            let _ = socket.write_all(http_response.as_bytes()).await;
            let _ = socket.flush().await;
        });
    }
}

async fn send_error(socket: &mut tokio::net::TcpStream, msg: &str) {
    let err_json = serde_json::json!({ "error": msg });
    let body = serde_json::to_string(&err_json).unwrap();
    let resp = format!(
        "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        body.len(),
        body
    );
    let _ = socket.write_all(resp.as_bytes()).await;
    let _ = socket.flush().await;
}

fn to_hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect()
}

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
        assert!(!store.check_and_add("nonce1")); // Already exists
        assert!(store.check_and_add("nonce2"));
    }
}
