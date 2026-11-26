use base64::Engine;
use chacha20poly1305::aead::{Aead, KeyInit};
use chacha20poly1305::{XChaCha20Poly1305, XNonce};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};
use thiserror::Error;

const SERVICE_NAME: &str = "dev.nightclient.launcher";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SecretBackend {
    OsKeyring,
    EncryptedVault,
}

#[derive(Debug, Error)]
pub enum SecretError {
    #[error("keyring error: {0}")]
    Keyring(String),
    #[error("vault io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("vault encryption error: {0}")]
    Crypto(String),
}

#[derive(Debug, Serialize, Deserialize, Default)]
struct VaultFile {
    version: u32,
    salt_b64: String,
    nonce_b64: String,
    ciphertext_b64: String,
}

#[derive(Debug, Clone)]
pub struct SecretStore {
    vault_path: PathBuf,
    prefer_keyring: bool,
}

impl SecretStore {
    pub fn new(vault_path: PathBuf) -> Self {
        Self {
            vault_path,
            prefer_keyring: true,
        }
    }

    pub fn vault_only(vault_path: PathBuf) -> Self {
        Self {
            vault_path,
            prefer_keyring: false,
        }
    }

    pub fn detect_backend(&self) -> SecretBackend {
        if self.prefer_keyring && probe_keyring() {
            SecretBackend::OsKeyring
        } else {
            SecretBackend::EncryptedVault
        }
    }

    pub fn set(&self, key: &str, secret: &str) -> Result<SecretBackend, SecretError> {
        if self.prefer_keyring
            && let Ok(entry) = keyring::Entry::new(SERVICE_NAME, key)
            && entry.set_password(secret).is_ok()
        {
            return Ok(SecretBackend::OsKeyring);
        }
        let mut map = self.load_vault_map().unwrap_or_default();
        map.insert(key.to_string(), secret.to_string());
        self.save_vault_map(&map)?;
        Ok(SecretBackend::EncryptedVault)
    }

    pub fn get(&self, key: &str) -> Result<Option<String>, SecretError> {
        if self.prefer_keyring
            && let Ok(entry) = keyring::Entry::new(SERVICE_NAME, key)
        {
            match entry.get_password() {
                Ok(val) => return Ok(Some(val)),
                Err(keyring::Error::NoEntry) => {}
                Err(_) => {}
            }
        }
        let map = self.load_vault_map().unwrap_or_default();
        Ok(map.get(key).cloned())
    }

    pub fn delete(&self, key: &str) -> Result<(), SecretError> {
        if self.prefer_keyring
            && let Ok(entry) = keyring::Entry::new(SERVICE_NAME, key)
        {
            let _ = entry.delete_credential();
        }
        if self.vault_path.exists() {
            let mut map = self.load_vault_map().unwrap_or_default();
            if map.remove(key).is_some() {
                self.save_vault_map(&map)?;
            }
        }
        Ok(())
    }

    fn load_vault_map(&self) -> Result<BTreeMap<String, String>, SecretError> {
        if !self.vault_path.exists() {
            return Ok(BTreeMap::new());
        }
        let raw = fs::read(&self.vault_path)?;
        let vault: VaultFile =
            serde_json::from_slice(&raw).map_err(|e| SecretError::Crypto(e.to_string()))?;
        let engine = base64::engine::general_purpose::STANDARD;
        let salt = engine
            .decode(&vault.salt_b64)
            .map_err(|e| SecretError::Crypto(e.to_string()))?;
        let nonce_bytes = engine
            .decode(&vault.nonce_b64)
            .map_err(|e| SecretError::Crypto(e.to_string()))?;
        let ciphertext = engine
            .decode(&vault.ciphertext_b64)
            .map_err(|e| SecretError::Crypto(e.to_string()))?;
        if nonce_bytes.len() != 24 {
            return Err(SecretError::Crypto("invalid vault nonce length".into()));
        }
        let key = derive_machine_key(&salt, &self.vault_path);
        let cipher = XChaCha20Poly1305::new((&key).into());
        let nonce = XNonce::from_slice(&nonce_bytes);
        let plaintext = cipher
            .decrypt(nonce, ciphertext.as_ref())
            .map_err(|_| SecretError::Crypto("vault decryption failed".into()))?;
        serde_json::from_slice(&plaintext).map_err(|e| SecretError::Crypto(e.to_string()))
    }

    fn save_vault_map(&self, map: &BTreeMap<String, String>) -> Result<(), SecretError> {
        let mut salt = [0u8; 32];
        let mut nonce_bytes = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut salt);
        rand::thread_rng().fill_bytes(&mut nonce_bytes);

        let key = derive_machine_key(&salt, &self.vault_path);
        let cipher = XChaCha20Poly1305::new((&key).into());
        let plaintext =
            serde_json::to_vec(map).map_err(|e| SecretError::Crypto(e.to_string()))?;
        let ciphertext = cipher
            .encrypt(XNonce::from_slice(&nonce_bytes), plaintext.as_ref())
            .map_err(|_| SecretError::Crypto("vault encryption failed".into()))?;

        let engine = base64::engine::general_purpose::STANDARD;
        let vault = VaultFile {
            version: 1,
            salt_b64: engine.encode(salt),
            nonce_b64: engine.encode(nonce_bytes),
            ciphertext_b64: engine.encode(ciphertext),
        };
        let bytes =
            serde_json::to_vec_pretty(&vault).map_err(|e| SecretError::Crypto(e.to_string()))?;
        crate::paths::atomic_write(&self.vault_path, &bytes)?;
        restrict_permissions(&self.vault_path);
        Ok(())
    }
}

fn probe_keyring() -> bool {
    let Ok(entry) = keyring::Entry::new(SERVICE_NAME, "__probe_access__") else {
        return false;
    };
    match entry.get_password() {
        Ok(_) | Err(keyring::Error::NoEntry) => true,
        Err(_) => false,
    }
}

fn derive_machine_key(salt: &[u8], vault_path: &Path) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(b"nightclient-launcher-vault-v1");
    hasher.update(salt);
    if let Ok(mid) = fs::read_to_string("/etc/machine-id") {
        hasher.update(mid.trim().as_bytes());
    }
    if let Ok(user) = std::env::var("USER").or_else(|_| std::env::var("USERNAME")) {
        hasher.update(user.as_bytes());
    }
    hasher.update(vault_path.to_string_lossy().as_bytes());
    hasher.finalize().into()
}

fn restrict_permissions(path: &Path) {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o600));
    }
    #[cfg(not(unix))]
    let _ = path;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encrypted_vault_roundtrip() {
        let dir = tempfile::tempdir().unwrap();
        let store = SecretStore::vault_only(dir.path().join("vault.enc"));
        let backend = store.set("ms_refresh", "super-secret-token").unwrap();
        assert_eq!(backend, SecretBackend::EncryptedVault);
        assert_eq!(
            store.get("ms_refresh").unwrap().as_deref(),
            Some("super-secret-token")
        );
        let raw = fs::read_to_string(dir.path().join("vault.enc")).unwrap();
        assert!(!raw.contains("super-secret-token"));
        store.delete("ms_refresh").unwrap();
        assert_eq!(store.get("ms_refresh").unwrap(), None);
    }
}
