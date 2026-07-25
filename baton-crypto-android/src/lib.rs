use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jbyteArray, jlong, jstring};
use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use base64::Engine;
use base64::engine::general_purpose::STANDARD as BASE64;
use hmac::{Hmac, Mac};
use sha2::{Digest, Sha256};
use x25519_dalek::{PublicKey, StaticSecret};
use hkdf::Hkdf;
use rand::RngCore;

pub mod ratchet;



// Helper to convert JNI byte array to Rust Vec
fn to_vec(env: &mut JNIEnv, array: jbyteArray) -> Result<Vec<u8>, jni::errors::Error> {
    let array_wrapper = unsafe { JByteArray::from_raw(array) };
    env.convert_byte_array(&array_wrapper)
}

// Helper to convert Rust slice to JNI byte array
fn to_jarray(env: &mut JNIEnv, bytes: &[u8]) -> Result<jbyteArray, jni::errors::Error> {
    let array = env.byte_array_from_slice(bytes)?;
    Ok(array.into_raw())
}

// Helper to convert jstring to String
fn to_string(env: &mut JNIEnv, jstr: jstring) -> Result<String, jni::errors::Error> {
    let jstr_wrapper = unsafe { JString::from_raw(jstr) };
    let s: String = env.get_string(&jstr_wrapper)?.into();
    Ok(s)
}

// Helper to derive the symmetric key using HKDF-Extract-and-Expand
fn derive_hkdf_key(shared_key: &[u8], timestamp: jlong, nonce: &str) -> [u8; 32] {
    let info = format!("{timestamp}:{nonce}");
    let hk = Hkdf::<Sha256>::new(Some(b"baton-gateway-v1-hkdf-salt-2024"), shared_key);
    let mut okm = [0u8; 32];
    hk.expand(info.as_bytes(), &mut okm).expect("HKDF expand must succeed for 32 bytes");
    okm
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_generatePrivateKeyRust(
    mut env: JNIEnv,
    _class: JClass,
) -> jbyteArray {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let mut bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut bytes);
        
        // Clamp private key according to Curve25519 spec
        bytes[0] &= 248;
        bytes[31] &= 127;
        bytes[31] |= 64;

        match to_jarray(&mut env, &bytes) {
            Ok(arr) => arr,
            Err(_) => std::ptr::null_mut(),
        }
    })).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_getPublicKeyRust(
    mut env: JNIEnv,
    _class: JClass,
    private_key: jbyteArray,
) -> jbyteArray {
    let priv_bytes = match to_vec(&mut env, private_key) {
        Ok(b) if b.len() == 32 => b,
        _ => return std::ptr::null_mut(),
    };

    let mut priv_array = [0u8; 32];
    priv_array.copy_from_slice(&priv_bytes);
    
    let secret = StaticSecret::from(priv_array);
    let public = PublicKey::from(&secret);

    match to_jarray(&mut env, public.as_bytes()) {
        Ok(arr) => arr,
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_deriveSharedSecretRust(
    mut env: JNIEnv,
    _class: JClass,
    private_key: jbyteArray,
    peer_public_key: jbyteArray,
) -> jbyteArray {
    let priv_bytes = match to_vec(&mut env, private_key) {
        Ok(b) if b.len() == 32 => b,
        _ => return std::ptr::null_mut(),
    };
    let peer_bytes = match to_vec(&mut env, peer_public_key) {
        Ok(b) if b.len() == 32 => b,
        _ => return std::ptr::null_mut(),
    };

    let mut priv_array = [0u8; 32];
    priv_array.copy_from_slice(&priv_bytes);
    let secret = StaticSecret::from(priv_array);

    let mut peer_array = [0u8; 32];
    peer_array.copy_from_slice(&peer_bytes);
    let peer_pub = PublicKey::from(peer_array);

    let shared_point = secret.diffie_hellman(&peer_pub);
    
    // Hash shared point with SHA-256 to generate the final shared secret key
    let mut hasher = Sha256::new();
    hasher.update(shared_point.as_bytes());
    let shared_secret = hasher.finalize();

    match to_jarray(&mut env, shared_secret.as_slice()) {
        Ok(arr) => arr,
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_encryptPayloadRust(
    mut env: JNIEnv,
    _class: JClass,
    plaintext: jbyteArray,
    shared_key: jbyteArray,
    nonce: jstring,
    timestamp: jlong,
) -> jstring {
    let plain_bytes = match to_vec(&mut env, plaintext) {
        Ok(b) => b,
        Err(_) => return std::ptr::null_mut(),
    };
    let shared_bytes = match to_vec(&mut env, shared_key) {
        Ok(b) if b.len() == 32 => b,
        _ => return std::ptr::null_mut(),
    };
    let nonce_str = match to_string(&mut env, nonce) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    // 1. Derive per-request key
    let derived_key = derive_hkdf_key(&shared_bytes, timestamp, &nonce_str);

    // 2. Generate random 12-byte IV
    let mut iv = [0u8; 12];
    rand::thread_rng().fill_bytes(&mut iv);

    // 3. Encrypt payload via AES-256-GCM
    let cipher = match Aes256Gcm::new_from_slice(&derived_key) {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };
    let nonce_gcm = Nonce::from_slice(&iv);

    let ciphertext = match cipher.encrypt(nonce_gcm, plain_bytes.as_slice()) {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };

    // 4. Return as JSON String: {"ciphertext":"...","iv":"..."}
    let ciphertext_b64 = BASE64.encode(&ciphertext);
    let iv_b64 = BASE64.encode(&iv);

    let json_resp = format!(r#"{{"ciphertext":"{}","iv":"{}"}}"#, ciphertext_b64, iv_b64);
    
    match env.new_string(json_resp) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_decryptPayloadRust(
    mut env: JNIEnv,
    _class: JClass,
    ciphertext_b64: jstring,
    iv_b64: jstring,
    shared_key: jbyteArray,
    nonce: jstring,
    timestamp: jlong,
) -> jbyteArray {
    let ct_str = match to_string(&mut env, ciphertext_b64) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    let iv_str = match to_string(&mut env, iv_b64) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    let shared_bytes = match to_vec(&mut env, shared_key) {
        Ok(b) if b.len() == 32 => b,
        _ => return std::ptr::null_mut(),
    };
    let nonce_str = match to_string(&mut env, nonce) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    let ciphertext = match BASE64.decode(ct_str.trim()) {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };
    let iv = match BASE64.decode(iv_str.trim()) {
        Ok(i) if i.len() == 12 => i,
        _ => return std::ptr::null_mut(),
    };

    // 1. Derive per-request key
    let derived_key = derive_hkdf_key(&shared_bytes, timestamp, &nonce_str);

    // 2. Decrypt via AES-256-GCM
    let cipher = match Aes256Gcm::new_from_slice(&derived_key) {
        Ok(c) => c,
        Err(_) => return std::ptr::null_mut(),
    };
    let nonce_gcm = Nonce::from_slice(&iv);

    let decrypted = match cipher.decrypt(nonce_gcm, ciphertext.as_slice()) {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };

    match to_jarray(&mut env, &decrypted) {
        Ok(arr) => arr,
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_computeSignatureRust(
    mut env: JNIEnv,
    _class: JClass,
    timestamp: jlong,
    nonce: jstring,
    ciphertext_b64: jstring,
    shared_key: jbyteArray,
) -> jstring {
    let nonce_str = match to_string(&mut env, nonce) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    let ct_str = match to_string(&mut env, ciphertext_b64) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    let shared_bytes = match to_vec(&mut env, shared_key) {
        Ok(b) if b.len() == 32 => b,
        _ => return std::ptr::null_mut(),
    };

    // 1. Derive signing key: SHA-256(shared_key || "signing-key-derivation-label")
    let mut hasher = Sha256::new();
    hasher.update(&shared_bytes);
    hasher.update("signing-key-derivation-label".as_bytes());
    let signing_key = hasher.finalize();

    // 2. Compute signature using length-prefixed inputs to prevent canonicalization bypass
    let signature_input = format!("ts={}:n_len={}:n={}:ct_len={}:ct={}", timestamp, nonce_str.len(), nonce_str, ct_str.len(), ct_str);
    
    let mut mac = <Hmac<Sha256> as hmac::Mac>::new_from_slice(&signing_key)
        .expect("HMAC key length derived from SHA-256 is always 32 bytes (valid)");
    mac.update(signature_input.as_bytes());
    
    let signature_bytes = mac.finalize().into_bytes();
    let signature_hex = hex::encode(signature_bytes);

    match env.new_string(signature_hex) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_x25519_key_exchange() {
        let mut priv1 = [0u8; 32];
        let mut priv2 = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut priv1);
        rand::thread_rng().fill_bytes(&mut priv2);
        
        priv1[0] &= 248; priv1[31] &= 127; priv1[31] |= 64;
        priv2[0] &= 248; priv2[31] &= 127; priv2[31] |= 64;

        let sec1 = StaticSecret::from(priv1);
        let pub1 = PublicKey::from(&sec1);

        let sec2 = StaticSecret::from(priv2);
        let pub2 = PublicKey::from(&sec2);

        let shared1 = sec1.diffie_hellman(&pub2);
        let shared2 = sec2.diffie_hellman(&pub1);

        assert_eq!(shared1.as_bytes(), shared2.as_bytes());
    }

    #[test]
    fn test_hkdf_derivation() {
        let shared = [1u8; 32];
        let key1 = derive_hkdf_key(&shared, 12345, "nonce123");
        let key2 = derive_hkdf_key(&shared, 12345, "nonce123");
        let key3 = derive_hkdf_key(&shared, 12346, "nonce123");

        assert_eq!(key1, key2);
        assert_ne!(key1, key3);
    }
}

// ----- RATCHET JNI BINDINGS -----

use crate::ratchet::RatchetState;

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_ratchetInitAliceRust(
    mut env: JNIEnv,
    _class: JClass,
    shared_secret: jbyteArray,
    peer_public: jbyteArray,
) -> jstring {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let ss_bytes = match to_vec(&mut env, shared_secret) {
            Ok(b) if b.len() == 32 => b,
            _ => return std::ptr::null_mut(),
        };
        let peer_pub_bytes = match to_vec(&mut env, peer_public) {
            Ok(b) if b.len() == 32 => b,
            _ => return std::ptr::null_mut(),
        };

        let mut ss = [0u8; 32];
        ss.copy_from_slice(&ss_bytes);
        let mut pp = [0u8; 32];
        pp.copy_from_slice(&peer_pub_bytes);
        let peer_pub_key = PublicKey::from(pp);

        let state = RatchetState::init_alice(ss, peer_pub_key);
        let state_json = serde_json::to_string(&state).unwrap_or_default();
        
        match env.new_string(state_json) {
            Ok(js) => js.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_ratchetInitBobRust(
    mut env: JNIEnv,
    _class: JClass,
    shared_secret: jbyteArray,
    bob_keypair: jbyteArray,
) -> jstring {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let ss_bytes = match to_vec(&mut env, shared_secret) {
            Ok(b) if b.len() == 32 => b,
            _ => return std::ptr::null_mut(),
        };
        let my_priv_bytes = match to_vec(&mut env, bob_keypair) {
            Ok(b) if b.len() == 32 => b,
            _ => return std::ptr::null_mut(),
        };

        let mut ss = [0u8; 32];
        ss.copy_from_slice(&ss_bytes);
        let mut mp = [0u8; 32];
        mp.copy_from_slice(&my_priv_bytes);

        let state = RatchetState::init_bob(ss, mp);
        let state_json = serde_json::to_string(&state).unwrap_or_default();
        
        match env.new_string(state_json) {
            Ok(js) => js.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_ratchetEncryptRust(
    mut env: JNIEnv,
    _class: JClass,
    state_json: jstring,
    plaintext: jbyteArray,
) -> jstring {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let state_str = match to_string(&mut env, state_json) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        let pt_bytes = match to_vec(&mut env, plaintext) {
            Ok(b) => b,
            Err(_) => return std::ptr::null_mut(),
        };

        let mut state: RatchetState = match serde_json::from_str(&state_str) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };

        let (header_pub, header_n, header_pn, ciphertext_with_iv) = state.ratchet_encrypt(&pt_bytes);
        let new_state_json = serde_json::to_string(&state).unwrap_or_default();
        let header_pub_b64 = BASE64.encode(header_pub);
        let ct_b64 = BASE64.encode(&ciphertext_with_iv);

        let result = format!(
            r#"{{"state":{},"header_pub":"{}","header_n":{},"header_pn":{},"ciphertext":"{}"}}"#,
            new_state_json, header_pub_b64, header_n, header_pn, ct_b64
        );

        match env.new_string(result) {
            Ok(js) => js.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_ratchetDecryptRust(
    mut env: JNIEnv,
    _class: JClass,
    state_json: jstring,
    header_pub_b64: jstring,
    header_n: i32,
    header_pn: i32,
    ciphertext_b64: jstring,
) -> jstring {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let state_str = match to_string(&mut env, state_json) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        let hpub_str = match to_string(&mut env, header_pub_b64) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
        let ct_str = match to_string(&mut env, ciphertext_b64) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };

        let mut state: RatchetState = match serde_json::from_str(&state_str) {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };

        let hpub_bytes = match BASE64.decode(hpub_str) {
            Ok(b) if b.len() == 32 => b,
            _ => return std::ptr::null_mut(),
        };
        let mut hpub_array = [0u8; 32];
        hpub_array.copy_from_slice(&hpub_bytes);

        let ct_bytes = match BASE64.decode(ct_str) {
            Ok(b) => b,
            Err(_) => return std::ptr::null_mut(),
        };

        match state.ratchet_decrypt(hpub_array, header_n as u32, header_pn as u32, &ct_bytes) {
            Ok(pt) => {
                let new_state_json = serde_json::to_string(&state).unwrap_or_default();
                let pt_b64 = BASE64.encode(&pt);
                let result = format!(r#"{{"state":{},"plaintext":"{}"}}"#, new_state_json, pt_b64);
                match env.new_string(result) {
                    Ok(js) => js.into_raw(),
                    Err(_) => std::ptr::null_mut(),
                }
            },
            Err(_) => std::ptr::null_mut()
        }
    })).unwrap_or(std::ptr::null_mut())
}
