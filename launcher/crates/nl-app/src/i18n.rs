use nl_core::{CrashFixAction, CrashKind, LaunchStage};
use nl_java::MemoryWarning;
use serde::Deserialize;
use std::collections::BTreeMap;
use std::fmt;
use std::fs;
use std::path::Path;

const BUNDLED_LOCALES: &[&str] = &[
    include_str!("../../../assets/locales/en.json"),
    include_str!("../../../assets/locales/ru.json"),
];

#[derive(Debug, Clone, PartialEq, Eq, Deserialize)]
pub struct LocaleMeta {
    pub code: String,
    pub name: String,
    pub native_name: String,
}

impl fmt::Display for LocaleMeta {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        if self.native_name.eq_ignore_ascii_case(&self.name) {
            write!(f, "{}", self.native_name)
        } else {
            write!(f, "{} ({})", self.native_name, self.name)
        }
    }
}

#[derive(Debug, Clone, Deserialize)]
struct LocaleFile {
    meta: LocaleMeta,
    strings: BTreeMap<String, String>,
}

#[derive(Debug, Clone)]
pub struct I18nCatalog {
    bundles: BTreeMap<String, LocaleFile>,
    order: Vec<LocaleMeta>,
    active_code: String,
}

impl I18nCatalog {
    pub fn load(external_dir: Option<&Path>, preferred_code: &str) -> Self {
        let mut bundles: BTreeMap<String, LocaleFile> = BTreeMap::new();

        for raw in BUNDLED_LOCALES {
            if let Ok(file) = serde_json::from_str::<LocaleFile>(raw) {
                bundles.insert(file.meta.code.to_ascii_lowercase(), file);
            }
        }

        if let Some(dir) = external_dir
            && let Ok(entries) = fs::read_dir(dir)
        {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.extension().and_then(|e| e.to_str()) == Some("json")
                    && let Ok(raw) = fs::read_to_string(&path)
                    && let Ok(mut file) = serde_json::from_str::<LocaleFile>(&raw)
                {
                    let code = file.meta.code.to_ascii_lowercase();
                    file.meta.code = code.clone();
                    if let Some(existing) = bundles.get_mut(&code) {
                        existing.meta = file.meta;
                        existing.strings.extend(file.strings);
                    } else {
                        bundles.insert(code, file);
                    }
                }
            }
        }

        let mut order: Vec<LocaleMeta> = bundles.values().map(|b| b.meta.clone()).collect();
        order.sort_by_key(|a| a.name.to_lowercase());

        let mut catalog = Self {
            bundles,
            order,
            active_code: "en".to_string(),
        };
        catalog.set_locale(preferred_code);
        catalog
    }

    pub fn set_locale(&mut self, code: &str) -> String {
        let normalized = code
            .trim()
            .split(['_', '-'])
            .next()
            .unwrap_or("en")
            .to_ascii_lowercase();
        if self.bundles.contains_key(&normalized) {
            self.active_code = normalized;
        } else if self.bundles.contains_key("en") {
            self.active_code = "en".to_string();
        } else if let Some(first) = self.order.first() {
            self.active_code = first.code.clone();
        }
        self.active_code.clone()
    }

    pub fn active_meta(&self) -> Option<LocaleMeta> {
        self.bundles.get(&self.active_code).map(|b| b.meta.clone())
    }

    pub fn available_locales(&self) -> &[LocaleMeta] {
        &self.order
    }

    pub fn tr<'a>(&'a self, key: &'a str) -> &'a str {
        if let Some(bundle) = self.bundles.get(&self.active_code)
            && let Some(val) = bundle.strings.get(key)
        {
            return val.as_str();
        }
        if let Some(en) = self.bundles.get("en")
            && let Some(val) = en.strings.get(key)
        {
            return val.as_str();
        }
        key
    }

    pub fn tr_fmt(&self, key: &str, args: &[(&str, &str)]) -> String {
        let mut out = self.tr(key).to_string();
        for (k, v) in args {
            let needle = format!("{{{k}}}");
            out = out.replace(&needle, v);
        }
        out
    }

    pub fn stage_label(&self, stage: &LaunchStage) -> String {
        match stage {
            LaunchStage::Authenticating { username } => {
                self.tr_fmt("stage.resolving_loader", &[("loader", username)])
            }
            LaunchStage::CheckingJava => self.tr("stage.verifying_client").to_string(),
            LaunchStage::ResolvingProfile { mc_version, loader } => self.tr_fmt(
                "stage.resolving_loader",
                &[("loader", &format!("{} {}", loader.display_name(), mc_version))],
            ),
            LaunchStage::InstallingJava { major, .. } => {
                self.tr_fmt("stage.preparing_java", &[("major", &major.to_string())])
            }
            LaunchStage::VerifyingFiles => self.tr("stage.verifying_client").to_string(),
            LaunchStage::DownloadingFiles {
                completed, total, ..
            } => self.tr_fmt(
                "stage.downloading_assets",
                &[
                    ("done", &completed.to_string()),
                    ("total", &total.to_string()),
                ],
            ),
            LaunchStage::PreparingNightClient => self.tr("stage.preparing_nightclient").to_string(),
            LaunchStage::StartingJvm { .. } => self.tr("stage.launching").to_string(),
        }
    }

    pub fn memory_warning(&self, warn: &MemoryWarning) -> String {
        match warn {
            MemoryWarning::HighSystemRatio {
                requested_mb,
                system_mb,
            } => self.tr_fmt(
                "mem_warn.exceeds_total",
                &[
                    ("alloc", &requested_mb.to_string()),
                    ("total", &system_mb.to_string()),
                ],
            ),
            MemoryWarning::ExcessiveHeap { .. } => self.tr("mem_warn.very_high").to_string(),
            MemoryWarning::LowHeapForZeroPause { recommended_mb, .. } => {
                let key = "mem_warn.zero_pause_low_ram";
                let gb = (recommended_mb / 1024).to_string();
                if self.tr(key) != key {
                    self.tr_fmt(key, &[("gb", &gb), ("mb", &recommended_mb.to_string())])
                } else if self.active_code == "ru" {
                    format!(
                        "Zero-Pause (ZGC) требует запас памяти; рекомендуется от {gb} ГБ ОЗУ (для <=4 ГБ лучше Smooth)"
                    )
                } else {
                    format!(
                        "Zero-Pause (ZGC) needs extra headroom; {gb} GB+ RAM is recommended (use Smooth for <=4 GB)"
                    )
                }
            }
        }
    }

    pub fn crash_copy(&self, kind: &CrashKind) -> (String, String) {
        match kind {
            CrashKind::OutOfMemory => (
                self.tr("crash.oom.title").to_string(),
                self.tr("crash.oom.summary").to_string(),
            ),
            CrashKind::UnsupportedJava { .. } => (
                self.tr("crash.java.title").to_string(),
                self.tr("crash.java.summary").to_string(),
            ),
            CrashKind::DuplicateNightClient => (
                self.tr("crash.dup_nc.title").to_string(),
                self.tr("crash.dup_nc.summary").to_string(),
            ),
            CrashKind::MissingDependency { .. } | CrashKind::MixinConflict { .. } => (
                self.tr("crash.mod_conflict.title").to_string(),
                self.tr("crash.mod_conflict.summary").to_string(),
            ),
            CrashKind::GraphicsDriver => (
                self.tr("crash.opengl.title").to_string(),
                self.tr("crash.opengl.summary").to_string(),
            ),
            CrashKind::Unknown { exit_code } => (
                self.tr_fmt(
                    "crash.unknown.title",
                    &[("code", &exit_code.unwrap_or(-1).to_string())],
                ),
                self.tr("crash.unknown.summary").to_string(),
            ),
        }
    }

    pub fn crash_fix_label(&self, fix: &CrashFixAction) -> String {
        match fix {
            CrashFixAction::IncreaseMemory { target_mb } => {
                self.tr_fmt("crash.fix.increase_ram", &[("mb", &target_mb.to_string())])
            }
            CrashFixAction::InstallAndUseJava { major } => {
                self.tr_fmt("crash.fix.install_java", &[("major", &major.to_string())])
            }
            CrashFixAction::RemoveDuplicateNightClient => {
                self.tr("crash.fix.remove_dup_nc").to_string()
            }
            CrashFixAction::InstallMod { mod_id } => format!("+ {mod_id}"),
            CrashFixAction::OpenModsFolder => self.tr("crash.fix.open_mods").to_string(),
        }
    }

    pub fn format_downloads(n: u64) -> String {
        if n >= 1_000_000 {
            format!("{:.1}M", n as f64 / 1_000_000.0)
        } else if n >= 1_000 {
            format!("{:.1}K", n as f64 / 1_000.0)
        } else {
            n.to_string()
        }
    }

    pub fn format_elapsed(&self, secs: u64) -> String {
        let su = self.tr("unit.sec");
        let mu = self.tr("unit.min");
        let hu = self.tr("unit.hr");
        if secs < 60 {
            format!("{secs}{su}")
        } else if secs < 3600 {
            let mins = secs / 60;
            let rem = secs % 60;
            format!("{mins}{mu} {rem:02}{su}")
        } else {
            let hrs = secs / 3600;
            let mins = (secs % 3600) / 60;
            format!("{hrs}{hu} {mins:02}{mu}")
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn loads_all_bundled_locales_and_falls_back_cleanly() {
        let mut cat = I18nCatalog::load(None, "ru");
        assert!(cat.available_locales().len() >= 2);
        assert_eq!(cat.tr("header.play"), "Играть");
        assert_eq!(cat.format_elapsed(42), "42с");
        assert_eq!(cat.format_elapsed(125), "2м 05с");
        assert_eq!(cat.format_elapsed(3720), "1ч 02м");

        cat.set_locale("unknown_lang");
        assert_eq!(cat.tr("header.play"), "Play");
        assert_eq!(cat.format_elapsed(42), "42s");
        assert_eq!(cat.format_elapsed(125), "2m 05s");
        assert_eq!(cat.format_elapsed(3720), "1h 02m");
        assert_eq!(
            cat.tr_fmt("header.mods_count", &[("count", "12")]),
            "12 mods"
        );
    }

    #[test]
    fn bundled_locales_have_complete_key_parity_with_en() {
        let cat = I18nCatalog::load(None, "en");
        let en_keys: Vec<&String> = cat.bundles["en"].strings.keys().collect();
        for (code, bundle) in &cat.bundles {
            for key in &en_keys {
                assert!(
                    bundle.strings.contains_key(*key),
                    "Locale `{code}` is missing key `{key}`"
                );
            }
        }
    }
}
