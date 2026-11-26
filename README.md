<div align="center">

<img src="assets/nightclient-logo.png" alt="NightClient Logo" width="108" height="108" />

<h1>NightClient</h1>

<p>
  <strong>Glassmorphic Fabric client, 14 draggable HUD widgets, cinematic atmosphere shaders, Rust JNI performance engine &amp; cross-platform launcher.</strong><br />
  <sub>One universal JAR for 24 stable Minecraft releases (<code>1.20 – 26.3</code>) · OpenGL &amp; Native Vulkan · Zero Fabric API required</sub>
</p>

<p>
  <strong>🇺🇸 English</strong> &nbsp;|&nbsp; <a href="README.ru.md">🇷🇺 Русский</a>
</p>

<p>
  <a href="https://github.com/N3moCC/NightClient/releases"><img src="https://img.shields.io/github/v/release/N3moCC/NightClient?style=flat-square&color=6366f1&label=Release" alt="Latest Release" /></a>
  <a href="https://github.com/N3moCC/NightClient/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/N3moCC/NightClient/ci.yml?style=flat-square&label=CI" alt="CI Status" /></a>
  <a href="https://discord.gg/GenCYdp7NX"><img src="https://img.shields.io/badge/Discord-Join%20Community-5865F2?style=flat-square&logo=discord&logoColor=white" alt="Discord Community" /></a>
  <img src="https://img.shields.io/badge/Minecraft-1.20%20%E2%80%93%2026.3-2ea043?style=flat-square" alt="Minecraft 1.20 - 26.3" />
  <img src="https://img.shields.io/badge/Fabric-No%20Fabric%20API-a371f7?style=flat-square" alt="Fabric without Fabric API" />
  <img src="https://img.shields.io/badge/Render-OpenGL%20%26%20Vulkan-f78166?style=flat-square" alt="OpenGL and Vulkan" />
  <a href="LICENSE"><img src="https://img.shields.io/github/license/N3moCC/NightClient?style=flat-square&color=8b949e&label=License" alt="MIT License" /></a>
</p>

<p>
  <a href="#highlights">Highlights</a> ·
  <a href="#features">Features</a> ·
  <a href="#installation">Installation</a> ·
  <a href="#nightclient-launcher">Launcher</a> ·
  <a href="#optimization-engine">Optimization</a> ·
  <a href="#server-feature-control-liteapi">LiteAPI</a> ·
  <a href="#building-from-source">Build</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="https://discord.gg/GenCYdp7NX">Discord</a>
</p>

</div>

---

## Highlights

| Capability | Details |
| :--- | :--- |
| **1 Universal JAR → 24 Versions** | Single JAR supports every stable release from `1.20` through `26.3` (patches included). A Java 17 bootstrap dynamically loads the exact version adapter at startup. |
| **OpenGL & Native Vulkan** | First-class support across classic OpenGL releases and Minecraft `26.2` / `26.3` native Vulkan rendering with bounded Dual-Kawase glass backdrop blur. |
| **Zero Dependencies** | Only **Fabric Loader** (`>= 0.19.5`) is required — **Fabric API is not needed**. |
| **Optional Rust JNI Engine** | `nightclient-native` accelerates entity/hologram batching, async PBO texture uploads, and packet compression. Works **with or without the launcher**, and degrades cleanly to pure Java. |
| **Cross-Platform Rust Launcher** | Native Iced 0.14 launcher (Linux, Windows, macOS) with Modrinth browser, 5 mod loaders, auto-updated universal JAR, and built-in self-updater with Markdown changelog. |
| **Server Feature Control (`LiteAPI`)** | Native zero-Fabric-API `liteapi:feature-control` support across all 24 versions (`76` granular feature IDs). Restricted features are disabled and completely hidden in-game without mutating configs. |
| **Bilingual UI, Profiles & `.nc` Themes** | Instant **English / Русский** toggle, Dark / Light / Custom palettes, live drag-and-drop HUD editor, instant search, config profiles, and declarative `.nc` themes. |

---

## Features

NightClient organizes its client-side visuals, camera tools, atmosphere shaders, utilities, and **14 draggable HUD widgets** into clean, searchable categories inside the glassmorphic ClickGUI (`Right Shift`).

<details open>
<summary><strong>Visuals &amp; Rendering (10 modules + 3D Cosmetics)</strong></summary>

| Module | Description |
| :--- | :--- |
| **Saturation** | Adjust overall scene color intensity. |
| **Hit Color** | Customize entity damage flash tint. |
| **No Render** | Hide overlays, hurt camera shake, totem animation, pumpkin, freeze, portal, nausea, vignette, boss bar, water/lava overlays & fog, and weather. |
| **Low Fire** | Lower or completely hide the first-person fire overlay. |
| **Enchant Glint** | Customize enchantment glint color, preset/chroma mode, intensity (`0` to hide), and scroll speed. |
| **Wavy Capes** | Realistic 2D cloth physics, wind gusts, underwater buoyancy, and sculpted folds for player capes. |
| **Motion Blur** | Directional camera-motion blur with adjustable strength and sample quality (`8` / `12` / `16` taps). |
| **Tab Animation** | Smooth scale-from-top or slide-down open/close animation for the player list. |
| **Tooltips** | Shulker & Bundle container preview (compact or `9×3` grid, Ctrl-lock) plus custom HolyWorld tooltips (talismans, spheres, raid items, `/ah` unit prices, golden spawners, special compass, coin exchange). |
| **Optimization** | Terrain/cave/leaf/occlusion culling, static entity & block-entity batching, hologram/HUD/scoreboard caches, particle caps, and optional `nightclient-native` acceleration. |
| **Cosmetics & 3D Models** | Built-in local wardrobe for custom 4K skins, capes, and high-poly `.glb` (glTF 2.0) / `.obj` weapon models with live 3D preview. |

</details>

<details>
<summary><strong>Camera &amp; Combat Feel (6 modules)</strong></summary>

| Module | Description |
| :--- | :--- |
| **Crosshair** | Pixel crosshair editor with mirror axes, scale, opacity, adaptive colors, and on-target highlight/shape morphing. |
| **Zoom** | Smooth camera magnification hotkey. |
| **Freelook** | Independent third-person camera orbit without rotating your character. |
| **Aspect Ratio** | Widescreen, standard, ultrawide, or custom scene proportion presets. |
| **Swing Animation** | Custom first-person attack/swing motion curves. |
| **Viewmodel** | Per-hand position, rotation, scale transforms, and viewmodel outline styling. |

</details>

<details>
<summary><strong>Atmosphere &amp; World (11 modules)</strong></summary>

| Module | Description |
| :--- | :--- |
| **Atmosphere** | Cinematic shaders & weather moods: Dawn, Dust, Snow, Sakura, Autumn, Fireflies, and Mystic night. |
| **Custom Sky** | Procedural vector skybox, planets, stars, clouds/nebula, and client-side time changer. |
| **World Map** | Fullscreen map with disk region cache, relief shading, zoom, radar layers, and waypoint management. |
| **Waypoints** | 3D spatial markers with glass badges, death points, `/way` commands, and clickable chat coordinates. |
| **Player Health** | In-world health bars above players with golden absorption hearts and enchanted apple indicator. |
| **Totem Counter** | Enemy totem pop counter displayed under nametags. |
| **Player Ping** | Player latency badges above nametags and in the Tab list. |
| **Damage Indicator** | Floating combat damage & critical hit numbers. |
| **Explosion Ring** | Danger radius rings for TNT, End Crystals, and Respawn Anchors with fuse countdown & armor-aware lethal zone. |
| **Hitboxes & Block Overlay** | Clean entity hitbox outlines and smooth hovered-block highlight (outline, fill, corner brackets). |
| **Item Physics** | Realistic client-side physics for dropped items on the ground. |

</details>

<details>
<summary><strong>Player &amp; Utilities (9 modules)</strong></summary>

| Module | Description |
| :--- | :--- |
| **Account Switcher** | Switch offline nicknames and restore your original launcher session (Modern 3D & Classic layouts, quick reconnect). |
| **Protect** | Mask your nickname, saved accounts, and typed passwords for streams and screenshots. |
| **HolyWorld Helper** | Auction rate tracker (`aucprice`), `AnSwap` (`/an <number>`), `BetterSell` (`/ah sells <price>` unit pricing), live event tracker (`api.holyworld.me`), trap/stun timers, 3D zone previews, and treasure compass auto-waypoints. |
| **Safe CT Leave** | Protects against accidentally disconnecting, closing the window, or running leave commands while Combat Tagged. |
| **Item Scroller** | Fast mouse-scroll and drag inventory item transfers. |
| **TapeMouse** | Continuous hold or tick-timed clicker with Alt+Tab background mode and `/tapemouse` commands. |
| **Sound Control** | Per-block sound filter, custom TNT `.mp3`/`.wav` audio, PvP volume mixer, and chat mention alerts. |
| **Effect Alert** | Audio and HUD alerts before selected potion effects expire. |
| **Tweaks** | `MainLeftFix`, `CommandFix`, `CursorFix`, adjustable `Full Bright`, own nametag in F5, and invisible-armor nametags/hitboxes. |

</details>

<details>
<summary><strong>HUD Suite (14 draggable widgets)</strong></summary>

- **Target HUD** — Sleek target card or compact `ZakoHP` indicator with absorption and distance.
- **Cooldowns** — Active item recovery timers with progress bars.
- **TNT Timer** — Countdown bars for nearby primed TNT.
- **Minimap** — Live terrain radar with relief, biomes, chunk grid, entity filters, and enlarge hotkey.
- **Better Chat** — Glassmorphic chat with smooth animations, anti-spam stacking `[x2]`, timestamps, history retention, and click-to-copy.
- **Inventory & Armor** — Live inventory preview and armor durability strip.
- **Potion Effects** — Active effects list in list, compact, pill, or vanilla layouts.
- **Food** — Visual saturation overlay above the vanilla hunger bar.
- **Scoreboard, Keystrokes (WASD + LMB/RMB CPS), Coordinates (XYZ + Nether + biome + speed), Network (ping & stability), and FPS.**

</details>

---

## Installation

### Option A — With NightClient Launcher *(Recommended)*

1. Download the binary for your OS from [GitHub Releases](https://github.com/N3moCC/NightClient/releases):
   - **Windows (x64):** `nightclient-launcher-windows-x64.exe`
   - **Linux (x64):** `nightclient-launcher-linux-x64`
   - **macOS (Apple Silicon):** `nightclient-launcher-macos-arm64`
   - **macOS (Intel):** `nightclient-launcher-macos-x64`
2. On first launch, either migrate an existing modpack/profile from **Prism, Modrinth, TLauncher, LegacyLauncher, Lunar, Official Minecraft**, or any custom folder (read-only — never touches accounts or tokens), or create a clean Fabric instance (`1.20` – `26.3`) with **NightClient Visuals** toggled on.
3. The launcher automatically downloads and SHA-256-verifies the latest universal NightClient JAR from GitHub Releases, injects it cleanly via `-Dfabric.addMods` (zero `mods/` clutter), and notifies you unobtrusively when launcher updates with Markdown changelogs are ready.

### Option B — Standalone Fabric Mod *(No Launcher Required)*

1. Install **Fabric Loader** (`>= 0.19.5`) for any supported release (`1.20` – `26.3`). **Fabric API is not required.**
2. Download `NightClient-<version>.jar` (or `NightClient-<version>-pure-java.jar` for a pure-Java build) from [GitHub Releases](https://github.com/N3moCC/NightClient/releases) and place it in `.minecraft/mods/`.
3. Launch Minecraft with the matching Java runtime:

| Minecraft Versions | Required Java | Graphics Backend |
| :--- | :--- | :--- |
| `1.20` – `1.20.4` | Java 17 | OpenGL |
| `1.20.5` – `1.21.11` | Java 21 | OpenGL |
| `26.1` – `26.1.2` | Java 25 | OpenGL |
| `26.2` – `26.3` | Java 25 | Native Vulkan |

4. Press **`Right Shift`** in-game to open ClickGUI. Place optional custom `.nc` themes in `.minecraft/config/nightclient/`.

---

## NightClient Launcher

The optional cross-platform launcher in [`launcher/`](launcher/) is written in **Rust** with **Iced 0.14** and native OS translucency (Wayland blur / Windows DWM):

- **First-Run Migration Wizard:** Auto-detects profiles from Prism, Modrinth, TLauncher, LegacyLauncher, Lunar, Official Minecraft, or any custom folder — strictly copies selected game files (mods, configs, resourcepacks, shaders, servers, worlds) and never touches accounts, tokens, or session databases.
- **Quick Play Split-Button (`Play ▾`):** Launch directly into any saved `servers.dat` multiplayer server right from the Play button dropdown.
- **All 5 Loaders:** Vanilla, Fabric, Quilt, NeoForge, and Forge with explicit version selection.
- **Modrinth Catalog & `.mrpack`:** Search, install with recursive dependencies, batch SHA-1 updates, cross-version mod migration, Drag & Drop auto-import, and curated FPS pack installer.
- **Managed Java & GC Tuning:** Local JDK scanner, one-click Eclipse Temurin installer (Java 17/21/25), and calibrated JVM profiles (`Smooth`, `Zero-Pause`, `Peak FPS`, `Stock`).
- **Opt-In GitHub Releases Updater:** Tracks `Stable` or `Beta` releases from `N3moCC/NightClient`, shows an unobtrusive badge with rich Markdown release notes inside the app, verifies `SHA256SUMS.txt`, and only updates when you explicitly confirm.
- **Crash Analyzer & Diagnostics:** Secret-redacted logs, `mclo.gs` upload, and 1-click recovery fixes (RAM, Java, Fabric Loader, duplicate JARs).

See [`launcher/README.md`](launcher/README.md) for launcher workspace details.

---

## Optimization Engine

NightClient includes a modular client-side optimization engine (`Lossless`, `Performance`, `Max FPS`, `Custom`) designed to coexist peacefully with popular performance mods:

- **Auto-Compatibility:** Automatically detects and yields overlapping hooks to **Sodium**, **EntityCulling**, **MoreCulling**, **ImmediatelyFast**, **EnhancedBlockEntities**, **Krypton**, **Exordium**, **FerriteCore**, **Lithium**, and others.
- **Optional `nightclient-native` (Rust JNI):** Compiles openly via GitHub Actions for `linux-x64`, `windows-x64`, `macos-arm64`, and `macos-x64`. Loaded automatically from the launcher (`-Dnightclient.native.path`), from bundled JAR resources, or from `config/nightclient/natives/`. When absent or disabled, every optimization falls back cleanly to pure Java.

---

## Server Feature Control (`LiteAPI`)

NightClient implements the [HolyWorld LiteAPI](https://wiki.holyworld.me/lite/api/protocol) protocol across **all 24 supported versions (`1.20` – `26.3`)** with zero Fabric API dependency:

- **Client ID:** `nightclient` · **Plugin Channel:** `liteapi:feature-control`
- Sends `checkFeatures` on server join (strictly honoring the `1 request / 10s` rate limit) and processes live `blocklist` updates.
- Blocked features and granular sub-features (e.g., `minimap.invisibles`, `worldMap.players`, `hwhelper.zones`, `tapeMouse`) are **completely disabled in logic/render/keybinds/commands AND completely hidden from ClickGUI, search, HUD editor, and map screens**, without altering saved config files on disk.
- All restrictions automatically clear when disconnecting or entering singleplayer.

---

## Building from Source

### Prerequisites
- **JDK 25** (targets Java 17 bytecode for legacy MC versions automatically)
- **Python 3.10+**
- **Rust stable** *(optional — only needed for `nightclient-native` and `launcher`)*

### Mod (Java / Gradle)

```bash
# Run all common unit tests (api, core, renderer, ui, runtime, universal)
./gradlew -PcommonOnly=true test

# Build a single Minecraft target (e.g. 26.3 or 1.20.1)
./gradlew -PmcVersion=26.3 :mc26_3:build
./gradlew -PmcVersion=1.20.1 :mc1_20_1:build

# Build the full 24-version matrix and assemble the universal JAR
python3 tools/build.py build-universal

# Build a pure-Java universal JAR without bundled native libraries
python3 tools/build.py build-universal --no-native

# Run generator & compatibility audit checks
python3 tools/checks/test_compatibility_audit.py
python3 tools/checks/test_optimization_features.py
```

### Native Library (`nightclient-native`)

```bash
cd native/nightclient-native
cargo test
cargo build --release
```

### Launcher (`launcher`)

```bash
cd launcher
cargo test --workspace
cargo run --release --bin nightclient-launcher
```

---

## Architecture

```text
NightClient/
├── .github/workflows/        # Transparent CI & cross-platform GitHub Release pipelines
├── assets/                   # Brand icons and SVGs
├── launcher/                 # Cross-platform Rust + Iced 0.14 launcher workspace
│   └── crates/               # nl-platform, nl-net, nl-java, nl-night, nl-auth, nl-content, nl-core, nl-app
├── native/
│   └── nightclient-native/   # Optional Rust JNI acceleration library (zlib-rs, SIMD, PBO/MDI)
├── modules/
│   ├── api/                  # Zero-dependency contracts & view models (Java 17)
│   ├── core/                 # Settings, FeaturePolicy, stores, i18n, cosmetics, .nc parser
│   ├── renderer/             # Icon compiler, OpenGL/Vulkan shaders, 3D model loader, font atlas
│   ├── ui/                   # Declarative .nc layout/skin engine, ClickGUI & 14 HUD widgets
│   ├── runtime/              # Client runtime state, LiteAPI manager, audio alerts, gameplay logic
│   ├── platform/             # 24-version templates, bridges, mixins & compatibility specs
│   └── universal/            # Java 17 universal bootstrap & runtime adapter selector
├── themes/default/           # Bundled default declarative .nc theme
└── tools/
    ├── build.py              # Unified shader/icon/platform generator & universal JAR assembler
    ├── generators/           # Feature, world, camera, backdrop & 3D cosmetic generators
    ├── matrix/               # 24-version matrix builder, ASM relocator & bytecode auditor
    └── checks/               # Standalone architecture & generator contract tests
```

---

## Community, Transparency & License

- **Discord Community:** Questions, bug reports, custom `.nc` themes, and release updates — **[discord.gg/GenCYdp7NX](https://discord.gg/GenCYdp7NX)**.
- **100% Open Source & Unobfuscated:** All mod modules, native Rust JNI sources, launcher crates, and CI/Release workflows are published openly in this repository with `SHA256SUMS.txt` checksums attached to every GitHub Release.
- Released under the **[MIT License](LICENSE)**. Third-party notices are preserved under `META-INF/licenses/`.
