use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use hkdf::Hkdf;
use rand::rngs::OsRng;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use x25519_dalek::{PublicKey, StaticSecret};
use zeroize::{Zeroize, ZeroizeOnDrop};

const PROTOCOL_NAME: &[u8] = b"Noise_XX_25519_AESGCM_SHA256\0\0\0";

/// Symmetric CipherState for continuous encrypted bi-directional session communication.
#[derive(Debug, Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub struct NoiseCipherChannel {
    pub key: [u8; 32],
    #[zeroize(skip)]
    pub nonce: u64,
}

impl NoiseCipherChannel {
    pub fn new(key: [u8; 32]) -> Self {
        Self { key, nonce: 0 }
    }

    /// Encrypts an outgoing payload with AES-256-GCM and increments the 64-bit nonce.
    pub fn encrypt(&mut self, plaintext: &[u8]) -> Result<Vec<u8>, String> {
        let cipher = Aes256Gcm::new_from_slice(&self.key)
            .map_err(|e| format!("Cipher init failed: {e}"))?;

        let mut nonce_bytes = [0u8; 12];
        nonce_bytes[4..12].copy_from_slice(&self.nonce.to_be_bytes());
        let nonce_gcm = Nonce::from_slice(&nonce_bytes);

        let ciphertext = cipher
            .encrypt(nonce_gcm, plaintext)
            .map_err(|e| format!("Encryption error: {e}"))?;

        self.nonce += 1;
        Ok(ciphertext)
    }

    /// Decrypts an incoming payload with AES-256-GCM and increments the 64-bit nonce.
    pub fn decrypt(&mut self, ciphertext: &[u8]) -> Result<Vec<u8>, String> {
        let cipher = Aes256Gcm::new_from_slice(&self.key)
            .map_err(|e| format!("Cipher init failed: {e}"))?;

        let mut nonce_bytes = [0u8; 12];
        nonce_bytes[4..12].copy_from_slice(&self.nonce.to_be_bytes());
        let nonce_gcm = Nonce::from_slice(&nonce_bytes);

        let plaintext = cipher
            .decrypt(nonce_gcm, ciphertext)
            .map_err(|e| format!("Decryption error: {e}"))?;

        self.nonce += 1;
        Ok(plaintext)
    }
}

/// Symmetric state maintaining chaining key `ck` and handshake hash `h`.
#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct SymmetricState {
    pub ck: [u8; 32],
    pub h: [u8; 32],
    pub k: Option<[u8; 32]>,
    #[zeroize(skip)]
    pub nonce: u64,
}

impl SymmetricState {
    pub fn new() -> Self {
        let mut h = [0u8; 32];
        let mut hasher = Sha256::new();
        hasher.update(PROTOCOL_NAME);
        h.copy_from_slice(&hasher.finalize());

        let mut ck = [0u8; 32];
        ck.copy_from_slice(&h);

        Self {
            ck,
            h,
            k: None,
            nonce: 0,
        }
    }

    pub fn mix_hash(&mut self, data: &[u8]) {
        let mut hasher = Sha256::new();
        hasher.update(&self.h);
        hasher.update(data);
        self.h.copy_from_slice(&hasher.finalize());
    }

    pub fn mix_key(&mut self, input_key_material: &[u8]) {
        let hk = Hkdf::<Sha256>::new(Some(&self.ck), input_key_material);
        let mut okm = [0u8; 64];
        hk.expand(b"Noise-MixKey", &mut okm).expect("HKDF expand failed");

        self.ck.copy_from_slice(&okm[0..32]);
        let mut k = [0u8; 32];
        k.copy_from_slice(&okm[32..64]);
        self.k = Some(k);
        self.nonce = 0;
    }

    pub fn mix_key_and_hash(&mut self, input_key_material: &[u8]) {
        let hk = Hkdf::<Sha256>::new(Some(&self.ck), input_key_material);
        let mut okm = [0u8; 64];
        hk.expand(b"Noise-MixKeyAndHash", &mut okm).expect("HKDF expand failed");

        self.ck.copy_from_slice(&okm[0..32]);
        let mut temp_h = [0u8; 32];
        temp_h.copy_from_slice(&okm[32..64]);
        self.mix_hash(&temp_h);

        let mut k = [0u8; 32];
        k.copy_from_slice(&temp_h);
        self.k = Some(k);
        self.nonce = 0;
    }

    pub fn encrypt_and_hash(&mut self, plaintext: &[u8]) -> Result<Vec<u8>, String> {
        let ciphertext = match self.k {
            Some(ref k) => {
                let cipher = Aes256Gcm::new_from_slice(k)
                    .map_err(|e| format!("Cipher init failed: {e}"))?;
                let mut nonce_bytes = [0u8; 12];
                nonce_bytes[4..12].copy_from_slice(&self.nonce.to_be_bytes());
                let nonce_gcm = Nonce::from_slice(&nonce_bytes);
                let ct = cipher.encrypt(nonce_gcm, plaintext)
                    .map_err(|e| format!("AEAD encrypt error: {e}"))?;
                self.nonce += 1;
                ct
            }
            None => plaintext.to_vec(),
        };

        self.mix_hash(&ciphertext);
        Ok(ciphertext)
    }

    pub fn decrypt_and_hash(&mut self, ciphertext: &[u8]) -> Result<Vec<u8>, String> {
        let plaintext = match self.k {
            Some(ref k) => {
                let cipher = Aes256Gcm::new_from_slice(k)
                    .map_err(|e| format!("Cipher init failed: {e}"))?;
                let mut nonce_bytes = [0u8; 12];
                nonce_bytes[4..12].copy_from_slice(&self.nonce.to_be_bytes());
                let nonce_gcm = Nonce::from_slice(&nonce_bytes);
                let pt = cipher.decrypt(nonce_gcm, ciphertext)
                    .map_err(|e| format!("AEAD decrypt error: {e}"))?;
                self.nonce += 1;
                pt
            }
            None => ciphertext.to_vec(),
        };

        self.mix_hash(ciphertext);
        Ok(plaintext)
    }

    pub fn split(&self) -> (NoiseCipherChannel, NoiseCipherChannel) {
        let hk = Hkdf::<Sha256>::new(Some(&self.ck), b"");
        let mut okm = [0u8; 64];
        hk.expand(b"Noise-Split", &mut okm).expect("HKDF expand failed");

        let mut k1 = [0u8; 32];
        let mut k2 = [0u8; 32];
        k1.copy_from_slice(&okm[0..32]);
        k2.copy_from_slice(&okm[32..64]);

        (NoiseCipherChannel::new(k1), NoiseCipherChannel::new(k2))
    }
}

/// Initiator (Alice) Handshake State Machine
pub struct NoiseInitiator {
    pub s: StaticSecret,
    pub s_pub: PublicKey,
    pub e: Option<StaticSecret>,
    pub e_pub: Option<PublicKey>,
    pub re_pub: Option<PublicKey>,
    pub rs_pub: Option<PublicKey>,
    pub state: SymmetricState,
}

impl NoiseInitiator {
    pub fn new(static_priv_bytes: &[u8; 32]) -> Self {
        let s = StaticSecret::from(*static_priv_bytes);
        let s_pub = PublicKey::from(&s);
        let state = SymmetricState::new();

        Self {
            s,
            s_pub,
            e: None,
            e_pub: None,
            re_pub: None,
            rs_pub: None,
            state,
        }
    }

    /// Step 1: Initiator sends ephemeral key `e` ->
    pub fn write_msg1(&mut self) -> Result<Vec<u8>, String> {
        let e = StaticSecret::random_from_rng(OsRng);
        let e_pub = PublicKey::from(&e);

        self.state.mix_hash(e_pub.as_bytes());
        let payload = self.state.encrypt_and_hash(&[])?;

        let mut msg1 = Vec::with_capacity(32 + payload.len());
        msg1.extend_from_slice(e_pub.as_bytes());
        msg1.extend_from_slice(&payload);

        self.e = Some(e);
        self.e_pub = Some(e_pub);

        Ok(msg1)
    }

    /// Step 2: Initiator processes message 2 from Responder <-
    /// Parses `re`, performs DH `ee`, decrypts Bob's `rs_pub`, performs DH `se`.
    pub fn read_msg2(&mut self, msg2: &[u8]) -> Result<(), String> {
        if msg2.len() < 32 {
            return Err("Msg2 too short for ephemeral public key".to_string());
        }

        let mut re_bytes = [0u8; 32];
        re_bytes.copy_from_slice(&msg2[0..32]);
        let re_pub = PublicKey::from(re_bytes);
        self.state.mix_hash(re_pub.as_bytes());

        // DH(e, re)
        let e = self.e.as_ref().ok_or("Missing initiator ephemeral key")?;
        let ee = e.diffie_hellman(&re_pub);
        self.state.mix_key(ee.as_bytes());

        // Decrypt Bob's static public key (32 bytes + 16 bytes tag = 48 bytes)
        if msg2.len() < 32 + 48 {
            return Err("Msg2 too short for encrypted static key".to_string());
        }
        let encrypted_rs = &msg2[32..32 + 48];
        let rs_bytes = self.state.decrypt_and_hash(encrypted_rs)?;
        if rs_bytes.len() != 32 {
            return Err("Invalid static key length after decryption".to_string());
        }

        let mut rs_arr = [0u8; 32];
        rs_arr.copy_from_slice(&rs_bytes);
        let rs_pub = PublicKey::from(rs_arr);

        // DH(e, rs)
        let es = e.diffie_hellman(&rs_pub);
        self.state.mix_key(es.as_bytes());

        // Decrypt optional payload
        let payload_enc = &msg2[32 + 48..];
        let _ = self.state.decrypt_and_hash(payload_enc)?;

        self.re_pub = Some(re_pub);
        self.rs_pub = Some(rs_pub);

        Ok(())
    }

    /// Step 3: Initiator writes message 3 ->
    /// Encrypts Alice's static key `s_pub`, performs DH `se = s * re`.
    /// Returns Message 3 bytes and final (tx, rx) cipher channels.
    pub fn write_msg3(&mut self) -> Result<(Vec<u8>, NoiseCipherChannel, NoiseCipherChannel), String> {
        let re_pub = self.re_pub.as_ref().ok_or("Missing responder ephemeral key")?;

        let enc_s = self.state.encrypt_and_hash(self.s_pub.as_bytes())?;

        // DH(s, re)
        let se = self.s.diffie_hellman(re_pub);
        self.state.mix_key(se.as_bytes());

        let payload = self.state.encrypt_and_hash(b"BATON_AGENT_INIT")?;

        let mut msg3 = Vec::with_capacity(enc_s.len() + payload.len());
        msg3.extend_from_slice(&enc_s);
        msg3.extend_from_slice(&payload);

        let (tx, rx) = self.state.split();
        Ok((msg3, tx, rx))
    }
}

/// Responder (Bob) Handshake State Machine
pub struct NoiseResponder {
    pub s: StaticSecret,
    pub s_pub: PublicKey,
    pub e: Option<StaticSecret>,
    pub e_pub: Option<PublicKey>,
    pub re_pub: Option<PublicKey>,
    pub rs_pub: Option<PublicKey>,
    pub state: SymmetricState,
}

impl NoiseResponder {
    pub fn new(static_priv_bytes: &[u8; 32]) -> Self {
        let s = StaticSecret::from(*static_priv_bytes);
        let s_pub = PublicKey::from(&s);
        let state = SymmetricState::new();

        Self {
            s,
            s_pub,
            e: None,
            e_pub: None,
            re_pub: None,
            rs_pub: None,
            state,
        }
    }

    /// Step 1: Responder reads message 1 from Initiator
    pub fn read_msg1(&mut self, msg1: &[u8]) -> Result<(), String> {
        if msg1.len() < 32 {
            return Err("Msg1 too short".to_string());
        }

        let mut re_bytes = [0u8; 32];
        re_bytes.copy_from_slice(&msg1[0..32]);
        let re_pub = PublicKey::from(re_bytes);

        self.state.mix_hash(re_pub.as_bytes());
        let _ = self.state.decrypt_and_hash(&msg1[32..])?;

        self.re_pub = Some(re_pub);
        Ok(())
    }

    /// Step 2: Responder generates ephemeral key `e`, performs DH `ee`, encrypts `s_pub`, performs DH `es`.
    /// Returns message 2 bytes.
    pub fn write_msg2(&mut self) -> Result<Vec<u8>, String> {
        let re_pub = self.re_pub.as_ref().ok_or("Missing initiator ephemeral key")?;

        let e = StaticSecret::random_from_rng(OsRng);
        let e_pub = PublicKey::from(&e);

        self.state.mix_hash(e_pub.as_bytes());

        // DH(e, re)
        let ee = e.diffie_hellman(re_pub);
        self.state.mix_key(ee.as_bytes());

        // Encrypt Bob's static key
        let enc_s = self.state.encrypt_and_hash(self.s_pub.as_bytes())?;

        // DH(s, re)
        let es = self.s.diffie_hellman(re_pub);
        self.state.mix_key(es.as_bytes());

        let payload = self.state.encrypt_and_hash(b"BATON_AGENT_ACK")?;

        let mut msg2 = Vec::with_capacity(32 + enc_s.len() + payload.len());
        msg2.extend_from_slice(e_pub.as_bytes());
        msg2.extend_from_slice(&enc_s);
        msg2.extend_from_slice(&payload);

        self.e = Some(e);
        self.e_pub = Some(e_pub);

        Ok(msg2)
    }

    /// Step 3: Responder reads message 3, decrypts Alice's static key, performs DH `se`.
    /// Returns final (rx, tx) cipher channels.
    pub fn read_msg3(&mut self, msg3: &[u8]) -> Result<(NoiseCipherChannel, NoiseCipherChannel), String> {
        let e = self.e.as_ref().ok_or("Missing responder ephemeral key")?;

        if msg3.len() < 48 {
            return Err("Msg3 too short for encrypted static key".to_string());
        }

        let enc_rs = &msg3[0..48];
        let rs_bytes = self.state.decrypt_and_hash(enc_rs)?;
        if rs_bytes.len() != 32 {
            return Err("Invalid static key length after decryption".to_string());
        }

        let mut rs_arr = [0u8; 32];
        rs_arr.copy_from_slice(&rs_bytes);
        let rs_pub = PublicKey::from(rs_arr);

        // DH(e, rs)
        let se = e.diffie_hellman(&rs_pub);
        self.state.mix_key(se.as_bytes());

        let payload_enc = &msg3[48..];
        let _ = self.state.decrypt_and_hash(payload_enc)?;

        self.rs_pub = Some(rs_pub);

        let (rx, tx) = self.state.split();
        Ok((rx, tx))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_noise_handshake_and_bidirectional_traffic() {
        let alice_static = [0x41u8; 32]; // 'A'
        let bob_static = [0x42u8; 32];   // 'B'

        let mut alice = NoiseInitiator::new(&alice_static);
        let mut bob = NoiseResponder::new(&bob_static);

        // 1. Alice -> Bob: Msg 1
        let msg1 = alice.write_msg1().expect("Alice Msg1 failed");
        bob.read_msg1(&msg1).expect("Bob read Msg1 failed");

        // 2. Bob -> Alice: Msg 2
        let msg2 = bob.write_msg2().expect("Bob Msg2 failed");
        alice.read_msg2(&msg2).expect("Alice read Msg2 failed");

        // 3. Alice -> Bob: Msg 3 & Split
        let (msg3, mut alice_tx, mut alice_rx) = alice.write_msg3().expect("Alice Msg3 failed");
        let (mut bob_rx, mut bob_tx) = bob.read_msg3(&msg3).expect("Bob read Msg3 failed");

        // 4. Verify Alice can send encrypted message to Bob
        let secret_msg = b"Hello Bob, this is sovereign encrypted communication!";
        let ciphertext = alice_tx.encrypt(secret_msg).expect("Alice encrypt failed");
        let decrypted = bob_rx.decrypt(&ciphertext).expect("Bob decrypt failed");
        assert_eq!(secret_msg, decrypted.as_slice());

        // 5. Verify Bob can reply to Alice
        let reply_msg = b"Acknowledged Alice. Zero server dependency verified.";
        let reply_ct = bob_tx.encrypt(reply_msg).expect("Bob encrypt failed");
        let reply_pt = alice_rx.decrypt(&reply_ct).expect("Alice decrypt failed");
        assert_eq!(reply_msg, reply_pt.as_slice());
    }

    #[test]
    fn test_noise_tampering_detection() {
        let alice_static = [0x11u8; 32];
        let bob_static = [0x22u8; 32];

        let mut alice = NoiseInitiator::new(&alice_static);
        let mut bob = NoiseResponder::new(&bob_static);

        let msg1 = alice.write_msg1().expect("Msg1 failed");
        bob.read_msg1(&msg1).expect("Bob read Msg1 failed");

        let mut msg2 = bob.write_msg2().expect("Msg2 failed");
        // Tamper with encrypted static key in Msg2
        msg2[40] ^= 0xFF;

        let result = alice.read_msg2(&msg2);
        // Tampered ciphertext fails GCM authentication tag check
        assert!(result.is_err());
    }
}
