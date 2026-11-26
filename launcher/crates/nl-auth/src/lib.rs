use base64::Engine;
use chrono::{DateTime, Duration as ChronoDuration, Utc};
use image::{GenericImageView, ImageBuffer, Rgba};
use md5::{Digest as Md5Digest, Md5};
use nl_net::{NetClient, NetError};
use nl_platform::{
    LauncherPaths, SecretBackend, SecretError, SecretStore, read_json_with_backup,
    write_json_atomic,
};
use rand::RngCore;
use serde::{Deserialize, Serialize};
use sha2::Sha256;
use std::fs;
use std::path::{Path, PathBuf};
use thiserror::Error;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AccountKind {
    Microsoft,
    Offline,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Account {
    pub id: String,
    pub username: String,
    pub kind: AccountKind,
    pub added_at: DateTime<Utc>,
    pub last_used_at: Option<DateTime<Utc>>,
    #[serde(default)]
    pub skin_url: Option<String>,
    #[serde(default)]
    pub token_expires_at: Option<DateTime<Utc>>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct AccountBook {
    pub schema_version: u32,
    pub active_account_id: Option<String>,
    pub accounts: Vec<Account>,
}

#[derive(Debug, Clone)]
pub struct LaunchCredentials {
    pub username: String,
    pub uuid: String,
    pub access_token: String,
    pub user_type: &'static str,
}

#[derive(Debug, Clone)]
pub struct DeviceCodePrompt {
    pub user_code: String,
    pub verification_uri: String,
    pub device_code: String,
    pub interval_secs: u64,
    pub expires_in_secs: u64,
}

#[derive(Debug, Clone)]
pub struct PkceLoginSession {
    pub auth_url: String,
    pub port: u16,
    pub code_verifier: String,
    pub state: String,
}

#[derive(Debug, Error)]
pub enum AuthError {
    #[error("network error: {0}")]
    Net(#[from] NetError),
    #[error("http error: {0}")]
    Http(#[from] reqwest::Error),
    #[error("secret storage error: {0}")]
    Secret(#[from] SecretError),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
    #[error("invalid username '{0}' (3-16 ASCII letters, digits, or underscore)")]
    InvalidUsername(String),
    #[error("Microsoft Client ID is not configured (set it in Settings or NIGHTLAUNCHER_MS_CLIENT_ID)")]
    MissingClientId,
    #[error("Microsoft login failed: {0}")]
    MicrosoftFlow(String),
    #[error("Xbox Live error: {0}")]
    XboxLive(String),
    #[error("this Microsoft account does not own Minecraft Java Edition")]
    NoMinecraftProfile,
    #[error("login cancelled")]
    Cancelled,
}

pub fn load_accounts(paths: &LauncherPaths) -> AccountBook {
    read_json_with_backup(&paths.accounts_file()).unwrap_or(AccountBook {
        schema_version: 1,
        active_account_id: None,
        accounts: Vec::new(),
    })
}

pub fn save_accounts(paths: &LauncherPaths, book: &AccountBook) -> std::io::Result<()> {
    write_json_atomic(&paths.accounts_file(), book)
}

pub fn create_offline_account(username: &str) -> Result<Account, AuthError> {
    let trimmed = username.trim();
    if !(3..=16).contains(&trimmed.len())
        || !trimmed
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '_')
    {
        return Err(AuthError::InvalidUsername(trimmed.to_string()));
    }
    let uuid = offline_uuid(trimmed);
    Ok(Account {
        id: uuid,
        username: trimmed.to_string(),
        kind: AccountKind::Offline,
        added_at: Utc::now(),
        last_used_at: None,
        skin_url: None,
        token_expires_at: None,
    })
}

pub fn offline_uuid(username: &str) -> String {
    let mut hash: [u8; 16] = Md5::digest(format!("OfflinePlayer:{username}").as_bytes()).into();
    hash[6] = (hash[6] & 0x0f) | 0x30;
    hash[8] = (hash[8] & 0x3f) | 0x80;
    Uuid::from_bytes(hash).simple().to_string()
}

pub fn resolve_client_id(configured: Option<&str>) -> Option<String> {
    if let Some(id) = configured.map(str::trim).filter(|s| !s.is_empty()) {
        return Some(id.to_string());
    }
    std::env::var("NIGHTLAUNCHER_MS_CLIENT_ID")
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

pub async fn start_device_code_login(
    net: &NetClient,
    client_id: &str,
    cancel: &CancellationToken,
) -> Result<DeviceCodePrompt, AuthError> {
    #[derive(Deserialize)]
    struct DcResp {
        device_code: String,
        user_code: String,
        verification_uri: String,
        expires_in: u64,
        interval: Option<u64>,
    }

    let req = net
        .inner()
        .post("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode")
        .form(&[
            ("client_id", client_id),
            ("scope", "XboxLive.signin offline_access"),
        ]);

    let resp = tokio::select! {
        _ = cancel.cancelled() => return Err(AuthError::Cancelled),
        r = req.send() => r?,
    };
    if !resp.status().is_success() {
        let text = resp.text().await.unwrap_or_default();
        return Err(AuthError::MicrosoftFlow(text));
    }
    let dc: DcResp = resp.json().await?;
    Ok(DeviceCodePrompt {
        user_code: dc.user_code,
        verification_uri: dc.verification_uri,
        device_code: dc.device_code,
        interval_secs: dc.interval.unwrap_or(5).max(2),
        expires_in_secs: dc.expires_in,
    })
}

pub async fn complete_device_code_login(
    net: &NetClient,
    secrets: &SecretStore,
    client_id: &str,
    prompt: &DeviceCodePrompt,
    cancel: &CancellationToken,
) -> Result<(Account, SecretBackend), AuthError> {
    #[derive(Deserialize)]
    struct TokenResp {
        access_token: Option<String>,
        refresh_token: Option<String>,
        error: Option<String>,
        error_description: Option<String>,
    }

    let deadline = tokio::time::Instant::now()
        + std::time::Duration::from_secs(prompt.expires_in_secs.min(900));
    let mut interval = std::time::Duration::from_secs(prompt.interval_secs);

    let (ms_access, ms_refresh) = loop {
        if cancel.is_cancelled() {
            return Err(AuthError::Cancelled);
        }
        if tokio::time::Instant::now() >= deadline {
            return Err(AuthError::MicrosoftFlow("login code expired".into()));
        }

        tokio::select! {
            _ = cancel.cancelled() => return Err(AuthError::Cancelled),
            _ = tokio::time::sleep(interval) => {}
        }

        let resp = net
            .inner()
            .post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")
            .form(&[
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", client_id),
                ("device_code", &prompt.device_code),
            ])
            .send()
            .await?;

        let body: TokenResp = resp.json().await?;
        if let Some(access) = body.access_token {
            break (access, body.refresh_token);
        }
        match body.error.as_deref() {
            Some("authorization_pending") => continue,
            Some("slow_down") => {
                interval += std::time::Duration::from_secs(5);
                continue;
            }
            Some(err) => {
                return Err(AuthError::MicrosoftFlow(
                    body.error_description.unwrap_or_else(|| err.to_string()),
                ));
            }
            None => return Err(AuthError::MicrosoftFlow("unexpected empty token response".into())),
        }
    };

    exchange_ms_token_for_minecraft(net, secrets, &ms_access, ms_refresh.as_deref(), cancel).await
}

pub async fn run_pkce_browser_login(
    net: &NetClient,
    secrets: &SecretStore,
    client_id: &str,
    on_url: impl FnOnce(String),
    cancel: &CancellationToken,
) -> Result<(Account, SecretBackend), AuthError> {
    let listener = TcpListener::bind("127.0.0.1:0").await?;
    let port = listener.local_addr()?.port();
    let redirect_uri = format!("http://127.0.0.1:{port}/callback");

    let mut verifier_bytes = [0u8; 32];
    let mut state_bytes = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut verifier_bytes);
    rand::thread_rng().fill_bytes(&mut state_bytes);
    let url_safe = base64::engine::general_purpose::URL_SAFE_NO_PAD;
    let code_verifier = url_safe.encode(verifier_bytes);
    let state = url_safe.encode(state_bytes);
    let challenge = url_safe.encode(Sha256::digest(code_verifier.as_bytes()));

    let mut auth_url = url::Url::parse(
        "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
    )
    .map_err(|e| AuthError::MicrosoftFlow(e.to_string()))?;
    auth_url
        .query_pairs_mut()
        .append_pair("client_id", client_id)
        .append_pair("response_type", "code")
        .append_pair("redirect_uri", &redirect_uri)
        .append_pair("scope", "XboxLive.signin offline_access")
        .append_pair("code_challenge", &challenge)
        .append_pair("code_challenge_method", "S256")
        .append_pair("state", &state)
        .append_pair("prompt", "select_account");

    on_url(auth_url.to_string());

    let code = tokio::select! {
        _ = cancel.cancelled() => return Err(AuthError::Cancelled),
        res = accept_oauth_callback(listener, &state) => res?,
    };

    #[derive(Deserialize)]
    struct TokenResp {
        access_token: String,
        refresh_token: Option<String>,
    }

    let resp = net
        .inner()
        .post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")
        .form(&[
            ("client_id", client_id),
            ("grant_type", "authorization_code"),
            ("code", &code),
            ("redirect_uri", &redirect_uri),
            ("code_verifier", &code_verifier),
        ])
        .send()
        .await?;

    if !resp.status().is_success() {
        let text = resp.text().await.unwrap_or_default();
        return Err(AuthError::MicrosoftFlow(text));
    }
    let body: TokenResp = resp.json().await?;
    exchange_ms_token_for_minecraft(
        net,
        secrets,
        &body.access_token,
        body.refresh_token.as_deref(),
        cancel,
    )
    .await
}

async fn accept_oauth_callback(
    listener: TcpListener,
    expected_state: &str,
) -> Result<String, AuthError> {
    let (mut stream, _) = listener.accept().await?;
    let mut buf = [0u8; 4096];
    let n = stream.read(&mut buf).await?;
    let req_str = String::from_utf8_lossy(&buf[..n]);
    let first_line = req_str.lines().next().unwrap_or("");
    let path = first_line.split_whitespace().nth(1).unwrap_or("/");
    let parsed = url::Url::parse(&format!("http://localhost{path}"))
        .map_err(|e| AuthError::MicrosoftFlow(e.to_string()))?;

    let mut code = None;
    let mut state = None;
    let mut err = None;
    for (k, v) in parsed.query_pairs() {
        match k.as_ref() {
            "code" => code = Some(v.into_owned()),
            "state" => state = Some(v.into_owned()),
            "error_description" | "error" if err.is_none() => err = Some(v.into_owned()),
            _ => {}
        }
    }

    let html = if code.is_some() && state.as_deref() == Some(expected_state) {
        "<html><body style='background:#101216;color:#f4f6fb;font-family:sans-serif;display:grid;place-items:center;height:100vh'><div><h2>NightClient Launcher</h2><p>Signed in. You can close this tab.</p></div></body></html>"
    } else {
        "<html><body style='background:#101216;color:#e5484d;font-family:sans-serif;display:grid;place-items:center;height:100vh'><div><h2>Sign-in failed</h2><p>Return to NightClient Launcher for details.</p></div></body></html>"
    };
    let response = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        html.len(),
        html
    );
    let _ = stream.write_all(response.as_bytes()).await;

    if let Some(e) = err {
        return Err(AuthError::MicrosoftFlow(e));
    }
    if state.as_deref() != Some(expected_state) {
        return Err(AuthError::MicrosoftFlow("OAuth state mismatch".into()));
    }
    code.ok_or_else(|| AuthError::MicrosoftFlow("missing authorization code".into()))
}

pub async fn resolve_launch_credentials(
    net: &NetClient,
    secrets: &SecretStore,
    account: &mut Account,
    client_id: Option<&str>,
    cancel: &CancellationToken,
) -> Result<LaunchCredentials, AuthError> {
    if account.kind == AccountKind::Offline {
        return Ok(LaunchCredentials {
            username: account.username.clone(),
            uuid: account.id.clone(),
            access_token: "0".to_string(),
            user_type: "legacy",
        });
    }

    let access_key = format!("account:{}:mc_access", account.id);
    let refresh_key = format!("account:{}:ms_refresh", account.id);

    let still_valid = account
        .token_expires_at
        .map(|exp| exp > Utc::now() + ChronoDuration::minutes(5))
        .unwrap_or(false);

    if still_valid
        && let Some(token) = secrets.get(&access_key)?
    {
        return Ok(LaunchCredentials {
            username: account.username.clone(),
            uuid: account.id.clone(),
            access_token: token,
            user_type: "msa",
        });
    }

    let cid = resolve_client_id(client_id).ok_or(AuthError::MissingClientId)?;
    let refresh_token = secrets
        .get(&refresh_key)?
        .ok_or_else(|| AuthError::MicrosoftFlow("session expired; sign in again".into()))?;

    #[derive(Deserialize)]
    struct RefreshResp {
        access_token: String,
        refresh_token: Option<String>,
    }

    let resp = net
        .inner()
        .post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")
        .form(&[
            ("client_id", cid.as_str()),
            ("grant_type", "refresh_token"),
            ("refresh_token", refresh_token.as_str()),
            ("scope", "XboxLive.signin offline_access"),
        ])
        .send()
        .await?;

    if !resp.status().is_success() {
        return Err(AuthError::MicrosoftFlow(
            "failed to refresh Microsoft session; sign in again".into(),
        ));
    }
    let body: RefreshResp = resp.json().await?;
    let (refreshed, _) = exchange_ms_token_for_minecraft(
        net,
        secrets,
        &body.access_token,
        body.refresh_token.as_deref().or(Some(&refresh_token)),
        cancel,
    )
    .await?;

    account.username = refreshed.username;
    account.skin_url = refreshed.skin_url;
    account.token_expires_at = refreshed.token_expires_at;

    let token = secrets
        .get(&access_key)?
        .ok_or_else(|| AuthError::MicrosoftFlow("refreshed token missing".into()))?;

    Ok(LaunchCredentials {
        username: account.username.clone(),
        uuid: account.id.clone(),
        access_token: token,
        user_type: "msa",
    })
}

pub fn remove_account_secrets(secrets: &SecretStore, account_id: &str) {
    let _ = secrets.delete(&format!("account:{account_id}:mc_access"));
    let _ = secrets.delete(&format!("account:{account_id}:ms_refresh"));
}

async fn exchange_ms_token_for_minecraft(
    net: &NetClient,
    secrets: &SecretStore,
    ms_access_token: &str,
    ms_refresh_token: Option<&str>,
    cancel: &CancellationToken,
) -> Result<(Account, SecretBackend), AuthError> {
    if cancel.is_cancelled() {
        return Err(AuthError::Cancelled);
    }

    let xbl_payload = serde_json::json!({
        "Properties": {
            "AuthMethod": "RPS",
            "SiteName": "user.auth.xboxlive.com",
            "RpsTicket": format!("d={ms_access_token}")
        },
        "RelyingParty": "http://auth.xboxlive.com",
        "TokenType": "JWT"
    });
    let xbl_resp = net
        .inner()
        .post("https://user.auth.xboxlive.com/user/authenticate")
        .json(&xbl_payload)
        .send()
        .await?;
    if !xbl_resp.status().is_success() {
        return Err(AuthError::XboxLive(xbl_resp.text().await.unwrap_or_default()));
    }
    let xbl_json: serde_json::Value = xbl_resp.json().await?;
    let xbl_token = xbl_json["Token"]
        .as_str()
        .ok_or_else(|| AuthError::XboxLive("missing XBL token".into()))?;
    let user_hash = xbl_json["DisplayClaims"]["xui"][0]["uhs"]
        .as_str()
        .ok_or_else(|| AuthError::XboxLive("missing user hash".into()))?;

    let xsts_payload = serde_json::json!({
        "Properties": {
            "SandboxId": "RETAIL",
            "UserTokens": [xbl_token]
        },
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT"
    });
    let xsts_resp = net
        .inner()
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .json(&xsts_payload)
        .send()
        .await?;
    if !xsts_resp.status().is_success() {
        let body = xsts_resp.text().await.unwrap_or_default();
        let hint = if body.contains("2148916233") {
            "This Microsoft account has no Xbox profile. Create one at xbox.com first."
        } else if body.contains("2148916238") {
            "Child account. Add it to an adult Microsoft Family group first."
        } else if body.contains("2148916235") {
            "Xbox Live is unavailable in this account's region"
        } else {
            &body
        };
        return Err(AuthError::XboxLive(hint.to_string()));
    }
    let xsts_json: serde_json::Value = xsts_resp.json().await?;
    let xsts_token = xsts_json["Token"]
        .as_str()
        .ok_or_else(|| AuthError::XboxLive("missing XSTS token".into()))?;

    let mc_payload = serde_json::json!({
        "identityToken": format!("XBL3.0 x={user_hash};{xsts_token}")
    });
    let mc_resp = net
        .inner()
        .post("https://api.minecraftservices.com/authentication/login_with_xbox")
        .json(&mc_payload)
        .send()
        .await?;
    if !mc_resp.status().is_success() {
        return Err(AuthError::MicrosoftFlow(
            mc_resp.text().await.unwrap_or_default(),
        ));
    }
    let mc_json: serde_json::Value = mc_resp.json().await?;
    let mc_access = mc_json["access_token"]
        .as_str()
        .ok_or_else(|| AuthError::MicrosoftFlow("missing Minecraft access token".into()))?;
    let expires_in = mc_json["expires_in"].as_i64().unwrap_or(86400);

    let profile_resp = net
        .inner()
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(mc_access)
        .send()
        .await?;
    if profile_resp.status() == reqwest::StatusCode::NOT_FOUND {
        return Err(AuthError::NoMinecraftProfile);
    }
    if !profile_resp.status().is_success() {
        return Err(AuthError::MicrosoftFlow(
            profile_resp.text().await.unwrap_or_default(),
        ));
    }
    let profile: serde_json::Value = profile_resp.json().await?;
    let id = profile["id"]
        .as_str()
        .ok_or(AuthError::NoMinecraftProfile)?
        .to_string();
    let username = profile["name"]
        .as_str()
        .ok_or(AuthError::NoMinecraftProfile)?
        .to_string();
    let skin_url = profile["skins"]
        .as_array()
        .and_then(|arr| {
            arr.iter()
                .find(|s| s["state"].as_str() == Some("ACTIVE"))
                .or_else(|| arr.first())
        })
        .and_then(|s| s["url"].as_str())
        .map(str::to_string);

    let backend = secrets.set(&format!("account:{id}:mc_access"), mc_access)?;
    if let Some(refresh) = ms_refresh_token {
        secrets.set(&format!("account:{id}:ms_refresh"), refresh)?;
    }

    Ok((
        Account {
            id,
            username,
            kind: AccountKind::Microsoft,
            added_at: Utc::now(),
            last_used_at: Some(Utc::now()),
            skin_url,
            token_expires_at: Some(Utc::now() + ChronoDuration::seconds(expires_in)),
        },
        backend,
    ))
}

pub async fn fetch_avatar_png(
    net: &NetClient,
    skins_cache_dir: &Path,
    account: &Account,
    cancel: &CancellationToken,
) -> Option<PathBuf> {
    let dest = skins_cache_dir.join(format!("{}_v2.png", account.id));
    if dest.is_file() {
        return Some(dest);
    }

    let mut head_png: Option<Vec<u8>> = None;

    if let Some(ref skin_url) = account.skin_url
        && !skin_url.trim().is_empty()
        && let Ok(bytes) = net.fetch_bytes(skin_url, cancel).await
    {
        head_png = extract_head_avatar_png(&bytes);
    }

    if head_png.is_none() {
        let encoded: String = url::form_urlencoded::byte_serialize(account.username.as_bytes())
            .collect();
        for url in [
            format!("https://minotar.net/helm/{encoded}/64"),
            format!("https://mc-heads.net/avatar/{encoded}/64"),
        ] {
            if let Ok(bytes) = net.fetch_bytes(&url, cancel).await
                && image::load_from_memory(&bytes).is_ok()
            {
                head_png = Some(bytes);
                break;
            }
        }
    }

    let final_bytes = head_png.unwrap_or_else(default_steve_head_png);
    let _ = fs::create_dir_all(skins_cache_dir);
    let _ = nl_platform::atomic_write(&dest, &final_bytes);
    Some(dest)
}

pub fn default_steve_head_png() -> Vec<u8> {
    const H: [u8; 4] = [47, 32, 19, 255];
    const S: [u8; 4] = [184, 134, 102, 255];
    const L: [u8; 4] = [196, 146, 114, 255];
    const W: [u8; 4] = [255, 255, 255, 255];
    const E: [u8; 4] = [74, 59, 130, 255];
    const N: [u8; 4] = [152, 104, 78, 255];
    const B: [u8; 4] = [106, 68, 45, 255];
    const M: [u8; 4] = [142, 86, 66, 255];

    const GRID: [[[u8; 4]; 8]; 8] = [
        [H, H, H, H, H, H, H, H],
        [H, H, H, H, H, H, H, H],
        [H, L, S, S, S, S, L, H],
        [S, L, L, S, S, L, L, S],
        [S, W, E, S, S, E, W, S],
        [S, S, S, N, N, S, S, S],
        [S, S, B, M, M, B, S, S],
        [S, S, B, B, B, B, S, S],
    ];

    let scale = 8u32;
    let mut out = ImageBuffer::<Rgba<u8>, Vec<u8>>::new(8 * scale, 8 * scale);
    for (y, row) in GRID.iter().enumerate() {
        for (x, px) in row.iter().enumerate() {
            let rgba = Rgba(*px);
            for dy in 0..scale {
                for dx in 0..scale {
                    out.put_pixel(x as u32 * scale + dx, y as u32 * scale + dy, rgba);
                }
            }
        }
    }
    let mut cursor = std::io::Cursor::new(Vec::new());
    let _ = out.write_to(&mut cursor, image::ImageFormat::Png);
    cursor.into_inner()
}

pub fn extract_head_avatar_png(skin_bytes: &[u8]) -> Option<Vec<u8>> {
    let img = image::load_from_memory(skin_bytes).ok()?;
    if img.width() < 64 || img.height() < 32 {
        return None;
    }

    let first_hat = img.get_pixel(40, 8);
    let uniform_opaque_hat = first_hat[3] == 255
        && (0..8u32).all(|y| (0..8u32).all(|x| img.get_pixel(40 + x, 8 + y) == first_hat));

    let scale = 8u32;
    let mut out = ImageBuffer::<Rgba<u8>, Vec<u8>>::new(8 * scale, 8 * scale);

    for y in 0..8u32 {
        for x in 0..8u32 {
            let base = img.get_pixel(8 + x, 8 + y);
            let pixel = if uniform_opaque_hat {
                Rgba([base[0], base[1], base[2], 255])
            } else {
                let hat = img.get_pixel(40 + x, 8 + y);
                alpha_blend(Rgba([base[0], base[1], base[2], 255]), hat)
            };
            for dy in 0..scale {
                for dx in 0..scale {
                    out.put_pixel(x * scale + dx, y * scale + dy, pixel);
                }
            }
        }
    }

    let mut cursor = std::io::Cursor::new(Vec::new());
    out.write_to(&mut cursor, image::ImageFormat::Png).ok()?;
    Some(cursor.into_inner())
}

fn alpha_blend(dst: Rgba<u8>, src: Rgba<u8>) -> Rgba<u8> {
    let a = src[3] as u32;
    if a == 0 {
        return dst;
    }
    if a == 255 {
        return src;
    }
    let inv = 255 - a;
    Rgba([
        ((src[0] as u32 * a + dst[0] as u32 * inv) / 255) as u8,
        ((src[1] as u32 * a + dst[1] as u32 * inv) / 255) as u8,
        ((src[2] as u32 * a + dst[2] as u32 * inv) / 255) as u8,
        255,
    ])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_offline_usernames_and_matches_minecraft_offline_uuid() {
        assert!(create_offline_account("ab").is_err());
        assert!(create_offline_account("bad name!").is_err());
        assert_eq!(offline_uuid("Steve"), "5627dd98e6be3c21b8a8e92344183641");
        let a = create_offline_account("NemoCC").unwrap();
        let b = create_offline_account("NemoCC").unwrap();
        assert_eq!(a.id, b.id);

        let steve = default_steve_head_png();
        let img = image::load_from_memory(&steve).unwrap();
        assert_eq!(img.width(), 64);
        assert_eq!(img.height(), 64);

        let mut legacy = ImageBuffer::<Rgba<u8>, Vec<u8>>::from_pixel(64, 32, Rgba([0, 0, 0, 255]));
        for y in 8..16u32 {
            for x in 8..16u32 {
                legacy.put_pixel(x, y, Rgba([200, 150, 120, 255]));
            }
        }
        let mut cur = std::io::Cursor::new(Vec::new());
        legacy.write_to(&mut cur, image::ImageFormat::Png).unwrap();
        let face = extract_head_avatar_png(&cur.into_inner()).unwrap();
        let decoded = image::load_from_memory(&face).unwrap();
        assert_eq!(decoded.get_pixel(10, 10), Rgba([200, 150, 120, 255]));
    }
}
