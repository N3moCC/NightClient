# NightClient Launcher

Cross-platform Minecraft instance launcher by **NemoCC**, built with **Rust** and **Iced 0.14**, designed around NightClient's universal JAR (`1.20` – `26.3`) and `.nc` theme ecosystem. Join the community on [Discord](https://discord.gg/GenCYdp7NX).

## Architecture

The workspace keeps all domain logic decoupled from the Iced UI:

- `crates/nl-platform`: OS data paths, atomic writes with `.bak` recovery, single-instance lock, OS keyring with `XChaCha20-Poly1305` encrypted vault fallback, GPU offload (OpenGL & Vulkan), GameMode/MangoHud detection, Linux `.desktop` installer, clipboard, and `nightclient://` deep links.
- `crates/nl-net`: HTTP/2 client, bounded parallel downloads, HTTP `Range` resume via `.part` files, stall timeouts, SHA-1/SHA-256 verification, ETag cache with offline fallback, proxy support, disk-space pre-checks, and cancellation tokens.
- `crates/nl-java`: Local JDK scanner, disk-cached GC capability probing, Eclipse Temurin automatic installer (Java 17 / 21 / 25), and calibrated JVM flag profiles (`Smooth`, `Zero-Pause`, `Peak FPS`, `Stock`).
- `crates/nl-night`: GitHub Releases (`N3moCC/NightClient`) updater for both NightClient universal JAR and launcher self-updates with `SHA256SUMS.txt` verification and Markdown changelog support, version/loader compatibility evaluator, zero-pollution `-Dfabric.addMods` JAR injection, duplicate `nightclient` mod detector, `.nc` theme token parser, and shared vs per-instance profile sync.
- `crates/nl-auth`: Microsoft OAuth2 (browser PKCE loopback + Device Code fallback -> Xbox Live -> XSTS -> Minecraft Services), offline accounts, and pixel-art skin head avatar cache.
- `crates/nl-content`: Modrinth v2 catalog (mods, resource packs, shaders, modpacks), recursive dependency tracking, SHA-1 batch updates and cross-version migration, `.mrpack` import/export, SQLite JAR metadata + icon cache, Drag & Drop auto-classifier, curated FPS optimization pack, and screenshot thumbnail cache.
- `crates/nl-core`: Schema-versioned `instance.json`, Mojang `version_manifest_v2` + rules/natives/classpath, all five loaders (**Vanilla, Fabric, Quilt, NeoForge, Forge**) with explicit loader version selection, supervised process launch with `Shutdown.halt0` deadlock watchdog, secret-redacted console logs, smart crash analyzer with 1-click fixes (`mclo.gs` upload), `servers.dat` NBT reader (`--quickPlayMultiplayer`), and read-only cross-platform migration from Prism, Modrinth, TLauncher, LegacyLauncher, Lunar, Official Minecraft, or any custom folder (never reading accounts, tokens, or session databases).
- `crates/nl-app`: Frosted-Graphite Iced 0.14 interface with native Wayland / Windows DWM translucency, first-run Welcome & Migration Wizard, `Play ▾` Quick Play server split-button, Geist + Geist Mono typography, bespoke SVG icons for all loaders and NightClient, and JSON locale catalog (`assets/locales/*.json` + `<launcher_home>/locales/*.json`).

## Building & Running

```bash
cd launcher
cargo run --bin nightclient-launcher
```

Run lint and tests across the full workspace:

```bash
cd launcher
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
```

### Configuration Tips

- **NightClient Universal JAR:** Automatically downloaded and verified from [GitHub Releases](https://github.com/N3moCC/NightClient/releases) according to the selected channel (`Stable` / `Beta`). For local development, the launcher also auto-discovers `build/distributions/*nightclient*.jar` / `build/universal/*nightclient*.jar` or honors `NIGHTLAUNCHER_LOCAL_JAR`.
- **Custom Translations:** Bundled locales live in `assets/locales/` (`en.json`, `ru.json`). Drop any additional `<code>.json` file into `<launcher_home>/locales/` with `"meta": { "code": "...", "name": "...", "native_name": "..." }` and `"strings": { ... }` and it appears automatically in **Settings -> Language**.
- **Microsoft Login:** Set your approved Azure Application Client ID via the `NIGHTLAUNCHER_MS_CLIENT_ID` environment variable before launching.
