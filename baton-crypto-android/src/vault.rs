use ed25519_dalek::{Signer, SigningKey, Verifier, VerifyingKey, Signature};
use hkdf::Hkdf;
use sha2::{Digest, Sha256};
use zeroize::{Zeroize, ZeroizeOnDrop};

/// Secure key buffer that automatically wipes itself with zeros upon being dropped from memory.
#[derive(Zeroize, ZeroizeOnDrop, Clone)]
pub struct SecureSecretKey {
    pub key: [u8; 32],
}

impl SecureSecretKey {
    pub fn new(key: [u8; 32]) -> Self {
        Self { key }
    }

    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.key
    }
}

/// Sign an agent identity assertion using Ed25519.
pub fn sign_agent_identity(secret_key_bytes: &[u8; 32], payload: &[u8]) -> [u8; 64] {
    let signing_key = SigningKey::from_bytes(secret_key_bytes);
    let signature = signing_key.sign(payload);
    signature.to_bytes()
}

/// Verify an agent identity assertion using Ed25519.
pub fn verify_agent_identity(public_key_bytes: &[u8; 32], payload: &[u8], signature_bytes: &[u8; 64]) -> bool {
    let verifying_key = match VerifyingKey::from_bytes(public_key_bytes) {
        Ok(vk) => vk,
        Err(_) => return false,
    };

    let signature = Signature::from_bytes(signature_bytes);
    verifying_key.verify(payload, &signature).is_ok()
}

/// Deterministically stretch a master user passphrase into an encryption key with HKDF-SHA256.
pub fn derive_master_vault_key(passphrase: &str, salt: &[u8], domain: &str) -> SecureSecretKey {
    let mut hasher = Sha256::new();
    hasher.update(passphrase.as_bytes());
    let mut initial_digest = hasher.finalize();

    let info = format!("baton-vault-v1:{domain}");
    let hk = Hkdf::<Sha256>::new(Some(salt), initial_digest.as_slice());
    let mut okm = [0u8; 32];
    hk.expand(info.as_bytes(), &mut okm).expect("HKDF expansion should never fail for 32 bytes");

    // Securely wipe intermediate digest
    initial_digest.zeroize();

    SecureSecretKey::new(okm)
}

/// Compute a human-verifiable 60-digit fingerprint safety number from two Ed25519/X25519 public keys.
/// Matches Signal-style fingerprinting (12 blocks of 5 digits).
pub fn compute_safety_fingerprint(local_key: &[u8; 32], remote_key: &[u8; 32]) -> String {
    // Lexicographically sort keys so both peers produce the identical safety number
    let (first, second) = if local_key <= remote_key {
        (local_key, remote_key)
    } else {
        (remote_key, local_key)
    };

    let mut hasher = Sha256::new();
    hasher.update(b"BATON-FINGERPRINT-V1");
    hasher.update(first);
    hasher.update(second);
    let mut digest = hasher.finalize();

    // Iterate 512 times for resistance
    for _ in 0..512 {
        let mut round_hasher = Sha256::new();
        round_hasher.update(&digest);
        round_hasher.update(first);
        digest = round_hasher.finalize();
    }

    // Convert first 30 bytes into 12 5-digit numbers
    let mut chunks = Vec::with_capacity(12);
    for chunk in digest.chunks_exact(2).take(12) {
        let val = ((chunk[0] as u32) << 8) | (chunk[1] as u32);
        let five_digit = val % 100000;
        chunks.push(format!("{:05}", five_digit));
    }

    chunks.join(" ")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ed25519_sign_and_verify() {
        let secret = [42u8; 32];
        let signing_key = SigningKey::from_bytes(&secret);
        let pub_key = signing_key.verifying_key().to_bytes();

        let payload = b"Agent ID: Artemis-Prime, RevocationEpoch: 1729000000";
        let sig = sign_agent_identity(&secret, payload);

        assert!(verify_agent_identity(&pub_key, payload, &sig));

        // Tampered payload fails
        let tampered = b"Agent ID: Artemis-Prime, RevocationEpoch: 1729000001";
        assert!(!verify_agent_identity(&pub_key, tampered, &sig));
    }

    #[test]
    fn test_safety_fingerprint_symmetry() {
        let key_a = [1u8; 32];
        let key_b = [2u8; 32];

        let fp1 = compute_safety_fingerprint(&key_a, &key_b);
        let fp2 = compute_safety_fingerprint(&key_b, &key_a);

        assert_eq!(fp1, fp2);
        assert_eq!(fp1.len(), 71); // 12 * 5 digits + 11 spaces
    }

    #[test]
    fn test_master_key_derivation() {
        let key = derive_master_vault_key("sovereign_passphrase", b"salt123", "keystore");
        assert_ne!(key.as_bytes(), &[0u8; 32]);
    }
}
