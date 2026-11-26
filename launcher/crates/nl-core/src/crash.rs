use crate::redact::redact_log_text;
use nl_net::{NetClient, NetError};
use regex::Regex;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use std::time::SystemTime;
use tokio_util::sync::CancellationToken;

const MCLOGS_API_URL: &str = "https://api.mclo.gs/1/log";
const MAX_READ_BYTES: usize = 256 * 1024;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CrashKind {
    DuplicateNightClient,
    UnsupportedJava { required_major: u32 },
    OutOfMemory,
    MissingDependency { mod_id: Option<String> },
    MixinConflict { culprit: Option<String> },
    GraphicsDriver,
    Unknown { exit_code: Option<i32> },
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum CrashFixAction {
    RemoveDuplicateNightClient,
    InstallMod { mod_id: String },
    InstallAndUseJava { major: u32 },
    IncreaseMemory { target_mb: u32 },
    OpenModsFolder,
}

#[derive(Debug, Clone)]
pub struct CrashDiagnosis {
    pub kind: CrashKind,
    pub excerpt: Vec<String>,
    pub log_path: Option<PathBuf>,
    pub redacted_log: String,
    pub fix_action: Option<CrashFixAction>,
    pub mclogs_url: Option<String>,
}

pub fn has_fresh_crash_report(instance_dir: &Path, launched_at: SystemTime) -> bool {
    find_newest_file(&instance_dir.join("crash-reports"), "crash-", launched_at).is_some()
        || find_newest_file(instance_dir, "hs_err_pid", launched_at).is_some()
}

pub fn analyze_crash(
    instance_dir: &Path,
    launched_at: SystemTime,
    process_log_tail: &str,
    exit_code: Option<i32>,
) -> CrashDiagnosis {
    let newest_crash = find_newest_file(&instance_dir.join("crash-reports"), "crash-", launched_at);
    let newest_hs_err = find_newest_file(instance_dir, "hs_err_pid", launched_at);
    let latest_log = instance_dir.join("logs/latest.log");

    let (chosen_path, raw_text) = if let Some(p) = newest_crash
        && let Some(txt) = read_bounded(&p)
    {
        (Some(p), txt)
    } else if let Some(p) = newest_hs_err
        && let Some(txt) = read_bounded(&p)
    {
        (Some(p), txt)
    } else if latest_log.is_file()
        && fs::metadata(&latest_log)
            .and_then(|m| m.modified())
            .is_ok_and(|mtime| mtime >= launched_at)
        && let Some(txt) = read_bounded(&latest_log)
    {
        (Some(latest_log), txt)
    } else {
        (None, process_log_tail.to_string())
    };

    let combined = if process_log_tail.is_empty() {
        raw_text.clone()
    } else {
        format!("{raw_text}\n{process_log_tail}")
    };

    let mut diag = diagnose_log_text(&combined, exit_code);
    diag.log_path = chosen_path;
    diag.redacted_log = redact_log_text(&raw_text);
    diag
}

pub fn diagnose_log_text(text: &str, exit_code: Option<i32>) -> CrashDiagnosis {
    let lower = text.to_ascii_lowercase();

    if (lower.contains("duplicate") || lower.contains("same id")) && lower.contains("nightclient") {
        return CrashDiagnosis {
            kind: CrashKind::DuplicateNightClient,
            excerpt: extract_matching_lines(text, &["nightclient", "duplicate"], 8),
            log_path: None,
            redacted_log: redact_log_text(text),
            fix_action: Some(CrashFixAction::RemoveDuplicateNightClient),
            mclogs_url: None,
        };
    }

    if text.contains("UnsupportedClassVersionError") || lower.contains("class file version") {
        let required_major = parse_required_java_major(text).unwrap_or(21);
        return CrashDiagnosis {
            kind: CrashKind::UnsupportedJava { required_major },
            excerpt: extract_matching_lines(
                text,
                &["UnsupportedClassVersionError", "class file version"],
                8,
            ),
            log_path: None,
            redacted_log: redact_log_text(text),
            fix_action: Some(CrashFixAction::InstallAndUseJava {
                major: required_major,
            }),
            mclogs_url: None,
        };
    }

    if text.contains("java.lang.OutOfMemoryError") || lower.contains("out of memory") {
        return CrashDiagnosis {
            kind: CrashKind::OutOfMemory,
            excerpt: extract_matching_lines(text, &["OutOfMemoryError", "Java heap space"], 8),
            log_path: None,
            redacted_log: redact_log_text(text),
            fix_action: Some(CrashFixAction::IncreaseMemory { target_mb: 6144 }),
            mclogs_url: None,
        };
    }

    if lower.contains("incompatible mods found")
        || lower.contains("mod resolution failed")
        || lower.contains("requires version")
    {
        let mod_id = extract_missing_mod_id(text);
        let fix = mod_id
            .clone()
            .map(|id| CrashFixAction::InstallMod { mod_id: id })
            .or(Some(CrashFixAction::OpenModsFolder));
        return CrashDiagnosis {
            kind: CrashKind::MissingDependency { mod_id },
            excerpt: extract_matching_lines(
                text,
                &["Install", "requires", "Replace", "Incompatible"],
                10,
            ),
            log_path: None,
            redacted_log: redact_log_text(text),
            fix_action: fix,
            mclogs_url: None,
        };
    }

    if text.contains("MixinApplyError") || text.contains("MixinTransformerError") {
        let culprit = extract_mixin_culprit(text);
        return CrashDiagnosis {
            kind: CrashKind::MixinConflict { culprit },
            excerpt: extract_matching_lines(
                text,
                &["MixinApplyError", "MixinTransformerError", "from mod"],
                10,
            ),
            log_path: None,
            redacted_log: redact_log_text(text),
            fix_action: Some(CrashFixAction::OpenModsFolder),
            mclogs_url: None,
        };
    }

    if text.contains("EXCEPTION_ACCESS_VIOLATION")
        || text.contains("SIGSEGV")
        || lower.contains("glfw error")
        || lower.contains("vkresult")
        || (lower.contains("vulkan") && lower.contains("failed"))
    {
        return CrashDiagnosis {
            kind: CrashKind::GraphicsDriver,
            excerpt: extract_matching_lines(
                text,
                &["SIGSEGV", "EXCEPTION_ACCESS_VIOLATION", "GLFW", "Vulkan", "Problematic frame"],
                10,
            ),
            log_path: None,
            redacted_log: redact_log_text(text),
            fix_action: None,
            mclogs_url: None,
        };
    }

    CrashDiagnosis {
        kind: CrashKind::Unknown { exit_code },
        excerpt: extract_matching_lines(text, &["ERROR", "Exception", "Caused by", "FATAL"], 10),
        log_path: None,
        redacted_log: redact_log_text(text),
        fix_action: None,
        mclogs_url: None,
    }
}

pub async fn upload_to_mclogs(
    net: &NetClient,
    raw_log: &str,
    cancel: &CancellationToken,
) -> Result<String, NetError> {
    let clean = redact_log_text(raw_log);
    let truncated = if clean.len() > MAX_READ_BYTES {
        let mut start = clean.len() - MAX_READ_BYTES;
        while start < clean.len() && !clean.is_char_boundary(start) {
            start += 1;
        }
        &clean[start..]
    } else {
        &clean
    };

    let req = net
        .inner()
        .post(MCLOGS_API_URL)
        .form(&[("content", truncated)]);
    let resp = tokio::select! {
        _ = cancel.cancelled() => return Err(NetError::Cancelled),
        r = req.send() => r?,
    };
    let json: serde_json::Value = resp.json().await?;
    json["url"]
        .as_str()
        .map(str::to_string)
        .ok_or_else(|| NetError::NoUrls("mclo.gs upload returned no url".into()))
}

fn parse_required_java_major(text: &str) -> Option<u32> {
    static RE: OnceLock<Regex> = OnceLock::new();
    let re = RE.get_or_init(|| Regex::new(r"class file version (\d+)\.").unwrap());
    let caps = re.captures(text)?;
    let class_ver: u32 = caps.get(1)?.as_str().parse().ok()?;
    if class_ver >= 52 {
        Some(class_ver - 44)
    } else {
        None
    }
}

fn extract_missing_mod_id(text: &str) -> Option<String> {
    static RE: OnceLock<Regex> = OnceLock::new();
    let re = RE.get_or_init(|| {
        Regex::new(r"(?i)install\s+([a-z0-9_\-]+),|mod\s+'([a-z0-9_\-]+)'\s+is\s+missing").unwrap()
    });
    let caps = re.captures(text)?;
    caps.get(1)
        .or_else(|| caps.get(2))
        .map(|m| m.as_str().to_string())
}

fn extract_mixin_culprit(text: &str) -> Option<String> {
    static RE: OnceLock<Regex> = OnceLock::new();
    let re = RE.get_or_init(|| Regex::new(r"from mod ([a-zA-Z0-9_\-]+)").unwrap());
    re.captures(text)
        .and_then(|c| c.get(1))
        .map(|m| m.as_str().to_string())
}

fn extract_matching_lines(text: &str, needles: &[&str], limit: usize) -> Vec<String> {
    let mut out = Vec::new();
    for line in text.lines() {
        if needles.iter().any(|n| line.contains(n)) {
            out.push(line.trim().to_string());
            if out.len() >= limit {
                break;
            }
        }
    }
    if out.is_empty() {
        out = text
            .lines()
            .rev()
            .take(limit)
            .map(|l| l.trim().to_string())
            .collect();
        out.reverse();
    }
    out
}

fn find_newest_file(dir: &Path, prefix: &str, after: SystemTime) -> Option<PathBuf> {
    let entries = fs::read_dir(dir).ok()?;
    entries
        .flatten()
        .filter_map(|e| {
            let p = e.path();
            let name = p.file_name()?.to_str()?;
            if !p.is_file() || !name.starts_with(prefix) {
                return None;
            }
            let modified = fs::metadata(&p).ok()?.modified().ok()?;
            if modified >= after {
                Some((modified, p))
            } else {
                None
            }
        })
        .max_by_key(|(m, _)| *m)
        .map(|(_, p)| p)
}

fn read_bounded(path: &Path) -> Option<String> {
    let bytes = fs::read(path).ok()?;
    let slice = if bytes.len() > MAX_READ_BYTES {
        &bytes[bytes.len() - MAX_READ_BYTES..]
    } else {
        &bytes
    };
    Some(String::from_utf8_lossy(slice).into_owned())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn diagnoses_oom_java_mismatch_and_duplicate_nightclient() {
        let d1 = diagnose_log_text(
            "java.lang.UnsupportedClassVersionError: class file version 69.0",
            Some(1),
        );
        assert_eq!(d1.kind, CrashKind::UnsupportedJava { required_major: 25 });
        assert_eq!(d1.fix_action, Some(CrashFixAction::InstallAndUseJava { major: 25 }));

        let d2 = diagnose_log_text("java.lang.OutOfMemoryError: Java heap space", Some(1));
        assert_eq!(d2.kind, CrashKind::OutOfMemory);
        assert_eq!(d2.fix_action, Some(CrashFixAction::IncreaseMemory { target_mb: 6144 }));

        let d3 = diagnose_log_text(
            "Mod resolution failed: duplicate mod with same id 'nightclient'",
            Some(1),
        );
        assert_eq!(d3.kind, CrashKind::DuplicateNightClient);
        assert_eq!(d3.fix_action, Some(CrashFixAction::RemoveDuplicateNightClient));

        let d4 = diagnose_log_text(
            "Incompatible mods found!\n - Install fabric-api, any version.",
            Some(1),
        );
        assert_eq!(
            d4.fix_action,
            Some(CrashFixAction::InstallMod {
                mod_id: "fabric-api".into()
            })
        );
    }
}
