pub mod github;
pub mod inject;
pub mod manifest;
pub mod profile_sync;
pub mod theme;

pub use github::{
    GITHUB_RELEASES_API, GITHUB_RELEASES_PAGE, GITHUB_REPO, GitHubAsset, GitHubRelease,
    GitHubReleaseBundle, LauncherUpdateError, LauncherUpdateInfo, build_launcher_update_info,
    build_manifest_from_releases, cleanup_stale_old_binary, current_launcher_asset_name,
    download_and_apply_launcher_update, fetch_github_releases_bundle, parse_sha256sums,
    version_gt,
};
pub use inject::{
    InjectionPlan, cleanup_managed_mod, detect_duplicate_nightclient_jars, plan_injection,
};
pub use manifest::{
    JarResolveRequest, NightClientChannel, NightClientManifest, NightClientRelease,
    VisualsCompatibility, default_embedded_manifest, evaluate_compatibility,
    fetch_or_cached_manifest, resolve_or_download_jar, verify_ed25519_signature, version_gte,
};
pub use profile_sync::{sync_after_exit, sync_before_launch};
pub use theme::{
    InstalledNcTheme, NcThemeTokens, install_nc_theme_file, list_installed_themes,
    parse_nc_tokens,
};
