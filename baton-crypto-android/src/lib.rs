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
    let salt = [0u8; 32];
    let hk = Hkdf::<Sha256>::new(Some(&salt), shared_key);
    let mut okm = [0u8; 32];
    hk.expand(info.as_bytes(), &mut okm).expect("HKDF expand must succeed for 32 bytes");
    okm
}

#[no_mangle]
pub extern "system" fn Java_com_ekam_baton_core_network_security_ConnectionSecurityManager_generatePrivateKeyRust(
    mut env: JNIEnv,
    _class: JClass,
) -> jbyteArray {
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

    // 2. Compute signature over "$timestamp:$nonce:$ciphertextBase64"
    let signature_input = format!("{timestamp}:{nonce_str}:{ct_str}");
    
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
