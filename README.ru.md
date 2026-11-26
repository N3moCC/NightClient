<div align="center">

<img src="assets/nightclient-logo.png" alt="NightClient Logo" width="108" height="108" />

<h1>NightClient</h1>

<p>
  <strong>Стеклянный Fabric-клиент, 14 настраиваемых HUD-виджетов, атмосферные шейдеры, нативный Rust JNI движок оптимизации и кроссплатформенный лаунчер.</strong><br />
  <sub>Один универсальный JAR на 24 стабильных релиза Minecraft (<code>1.20 – 26.3</code>) · OpenGL и нативный Vulkan · Без Fabric API</sub>
</p>

<p>
  <a href="README.md">🇺🇸 English</a> &nbsp;|&nbsp; <strong>🇷🇺 Русский</strong>
</p>

<p>
  <a href="https://github.com/N3moCC/NightClient/releases"><img src="https://img.shields.io/github/v/release/N3moCC/NightClient?style=flat-square&color=6366f1&label=Release" alt="Последний релиз" /></a>
  <a href="https://github.com/N3moCC/NightClient/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/N3moCC/NightClient/ci.yml?style=flat-square&label=CI" alt="Статус CI" /></a>
  <a href="https://discord.gg/GenCYdp7NX"><img src="https://img.shields.io/badge/Discord-%D0%A1%D0%BE%D0%BE%D0%B1%D1%89%D0%B5%D1%81%D1%82%D0%B2%D0%BE-5865F2?style=flat-square&logo=discord&logoColor=white" alt="Discord-сервер" /></a>
  <img src="https://img.shields.io/badge/Minecraft-1.20%20%E2%80%93%2026.3-2ea043?style=flat-square" alt="Minecraft 1.20 - 26.3" />
  <img src="https://img.shields.io/badge/Fabric-Без%20Fabric%20API-a371f7?style=flat-square" alt="Fabric без Fabric API" />
  <img src="https://img.shields.io/badge/Рендер-OpenGL%20%26%20Vulkan-f78166?style=flat-square" alt="OpenGL и Vulkan" />
  <a href="LICENSE"><img src="https://img.shields.io/github/license/N3moCC/NightClient?style=flat-square&color=8b949e&label=License" alt="Лицензия MIT" /></a>
</p>

<p>
  <a href="#главные-особенности">Особенности</a> ·
  <a href="#функции-и-модули">Функции</a> ·
  <a href="#установка">Установка</a> ·
  <a href="#лаунчер-nightclient">Лаунчер</a> ·
  <a href="#движок-оптимизации">Оптимизация</a> ·
  <a href="#контроль-функций-сервером-liteapi">LiteAPI</a> ·
  <a href="#сборка-из-исходников">Сборка</a> ·
  <a href="#структура-проекта">Структура</a> ·
  <a href="https://discord.gg/GenCYdp7NX">Discord</a>
</p>

</div>

---

## Главные особенности

| Возможность | Подробности |
| :--- | :--- |
| **1 универсальный JAR → 24 версии** | Один файл работает на всех стабильных релизах от `1.20` до `26.3` включительно. Java 17 загрузчик автоматически подключает адаптер под текущую версию игры. |
| **OpenGL и нативный Vulkan** | Полная поддержка классического OpenGL и нативного Vulkan-рендера Minecraft `26.2` / `26.3` с Dual-Kawase размытием стеклянных подложек. |
| **Без Fabric API** | Нужен только **Fabric Loader** (`>= 0.19.5`) — сторонние зависимости не требуются. |
| **Опциональный Rust JNI движок** | `nightclient-native` ускоряет батчинг сущностей/голограмм, асинхронную загрузку текстур и сжатие пакетов. Работает **и с лаунчером, и без лаунчера**, а без библиотеки спокойно работает на чистой Java. |
| **Кроссплатформенный Rust-лаунчер** | Нативный интерфейс на Iced 0.14 (Linux, Windows, macOS), каталог Modrinth, 5 загрузчиков модов, автозагрузка NightClient JAR и встроенное самообновление с Markdown-changelog из GitHub Releases. |
| **Поддержка HolyWorld `LiteAPI`** | Нативная реализация `liteapi:feature-control` на все 24 версии (`76` гранулярных ID) без Fabric API. Ограниченные сервером функции отключаются и полностью скрываются из меню и HUD без изменения конфига. |
| **Двуязычный UI, профили и `.nc`-темы** | Мгновенное переключение **Русский / English**, тёмная/светлая/кастомная палитры, живой drag-and-drop редактор HUD, поиск по функциям, профили и декларативные `.nc`-темы. |

---

## Функции и модули

Все визуальные функции, инструменты камеры, атмосферные шейдеры, утилиты и **14 перетаскиваемых HUD-виджетов** собраны в стеклянном ClickGUI (открывается на **`Right Shift`**).

<details open>
<summary><strong>Визуалы и рендер (10 модулей + 3D-косметика)</strong></summary>

| Модуль | Описание |
| :--- | :--- |
| **Saturation** | Настройка насыщенности цветов изображения. |
| **Hit Color** | Кастомный цвет подсветки сущностей при получении урона. |
| **No Render** | Отключение лишних оверлеев, тряски камеры, анимации тотема, тыквы, заморозки, портала, тошноты, виньетки, боссбара, тумана в воде/лаве и погоды. |
| **Low Fire** | Смещение вниз или полное скрытие огня от первого лица. |
| **Enchant Glint** | Цвет, пресеты/перелив, яркость (`0` — скрыть) и скорость перелива зачарований. |
| **Wavy Capes** | Реалистичная 2D-физика ткани, ветер, плавучесть в воде и плавные складки плащей. |
| **Motion Blur** | Направленное размытие при повороте камеры с настройкой силы и качества сэмплов (`8` / `12` / `16`). |
| **Tab Animation** | Плавная анимация раскрытия или выезда списка игроков (Tab). |
| **Tooltips** | Просмотр содержимого шалкеров и мешков (компактно или сетка `9×3`, фиксация по Ctrl) + кастомные HolyWorld-тултипы (талисманы, сферы, рейд-предметы, цена за 1 шт. на `/ah`, золотые спавнеры, особый компас, биржа коинов). |
| **Optimization** | Отсечение пещер, листвы и скрытых за стенами сущностей, батчинг статичных сущностей/блоков, кэш HUD/скорборда/голограмм, лимиты частиц и опциональное ускорение `nightclient-native`. |
| **Косметика и 3D-модели** | Локальный гардероб с 4K-скинами, плащами и 3D-моделями оружия `.glb` (glTF 2.0) / `.obj` с живым 3D-превью. |

</details>

<details>
<summary><strong>Камера и ощущение боя (6 модулей)</strong></summary>

| Модуль | Описание |
| :--- | :--- |
| **Crosshair** | Пиксельный редактор прицела с симметрией, масштабом, прозрачностью, адаптивным цветом и сменой формы/цвета при наведении на цель. |
| **Zoom** | Плавное приближение камеры по клавише. |
| **Freelook** | Свободный обзор от третьего лица без поворота персонажа. |
| **Aspect Ratio** | Пресеты и ручная настройка пропорций кадра. |
| **Swing Animation** | Кастомная анимация взмаха руки и предмета от первого лица. |
| **Viewmodel** | Положение, поворот, масштаб каждой руки и контурная обводка viewmodel. |

</details>

<details>
<summary><strong>Атмосфера и мир (11 модулей)</strong></summary>

| Модуль | Описание |
| :--- | :--- |
| **Atmosphere** | Кинематографичные шейдеры и настроения: Рассвет, Пыль, Снег, Сакура, Осень, Светлячки и Мистика. |
| **Custom Sky** | Процедурное векторное небо, планеты, звёзды, облака/туманности и смена времени суток. |
| **World Map** | Полноэкранная карта мира с дисковым кэшем регионов, рельефом, зумом, радаром и метками. |
| **Waypoints** | 3D-метки в мире со стеклянной плашкой, точкой смерти, командами `/way` и кликабельными координатами в чате. |
| **Player Health** | Полоски HP над головами игроков с золотыми сердцами поглощения и индикатором чарки. |
| **Totem Counter** | Счётчик сбитых тотемов противника под ником. |
| **Player Ping** | Пинг игроков над головой и в Tab. |
| **Damage Indicator** | Всплывающие цифры обычного и критического урона. |
| **Explosion Ring** | Кольца зоны поражения TNT, кристаллов Энда и якорей возрождения с таймером и расчётом летальной зоны по броне. |
| **Hitboxes и Block Overlay** | Аккуратные хитбоксы сущностей и плавная подсветка выделенного блока (контур, заливка, уголки). |
| **Item Physics** | Реалистичная клиентская физика лежащих на земле предметов. |

</details>

<details>
<summary><strong>Игрок и утилиты (9 модулей)</strong></summary>

| Модуль | Описание |
| :--- | :--- |
| **Account Switcher** | Смена оффлайн-ников и возврат исходной сессии лаунчера (3D и классический режимы, быстрый реконнект). |
| **Protect** | Скрытие ника, сохранённых аккаунтов и паролей на стримах и скриншотах. |
| **HolyWorld Helper** | Курс аукциона (`aucprice`), `AnSwap` (`/an <номер>`), `BetterSell` (цена за 1 шт. через `/ah sells <цена>`), трекер ивентов (`api.holyworld.me`), таймеры трапок/станов, 3D-превью зон и авто-метка по компасу сокровищ. |
| **Safe CT Leave** | Защита от случайного выхода с сервера, закрытия окна и команд выхода во время режима боя (КТ). |
| **Item Scroller** | Быстрый перенос предметов в инвентаре колёсиком мыши и зажатием. |
| **TapeMouse** | Авто-зажатие или кликер по тикам с работой в фоне Alt+Tab и командами `/tapemouse`. |
| **Sound Control** | Фильтр звуков выбранных блоков, кастомный звук TNT (`.mp3`/`.wav`), микшер PvP-громкости и оповещение при упоминании в чате. |
| **Effect Alert** | Звуковое и HUD-предупреждение перед окончанием выбранных эффектов зелий. |
| **Tweaks** | `MainLeftFix`, `CommandFix`, `CursorFix`, настраиваемый `Full Bright`, свой ник в F5 и ники/хитбоксы инвизок в броне. |

</details>

<details>
<summary><strong>Набор HUD-виджетов (14 перетаскиваемых элементов)</strong></summary>

- **Target HUD** — карточка цели или компактный индикатор `ZakoHP` с поглощением и дистанцией.
- **Cooldowns** — таймеры перезарядки предметов с полосами прогресса.
- **TNT Timer** — обратный отсчёт до взрыва ближайшего TNT.
- **Minimap** — мини-карта с рельефом, биомами, сеткой чанков, фильтрами сущностей и увеличением по клавише.
- **Better Chat** — стеклянный чат с плавными анимациями, схлопыванием дублей `[x2]`, временем, сохранением истории и копированием кликом.
- **Inventory и Armor** — превью инвентаря и полоска прочности брони.
- **Potion Effects** — активные эффекты в виде списка, пилюль, компактного или ванильного блока.
- **Food** — индикатор насыщения поверх ванильной шкалы голода.
- **Scoreboard, Keystrokes (WASD + ЛКМ/ПКМ CPS), Coordinates (XYZ + Незер + биом + скорость), Network (пинг и стабильность) и FPS.**

</details>

---

## Установка

### Вариант A — Через лаунчер NightClient *(рекомендуется)*

1. Скачай бинарник под свою ОС со страницы [GitHub Releases](https://github.com/N3moCC/NightClient/releases):
   - **Windows (x64):** `nightclient-launcher-windows-x64.exe`
   - **Linux (x64):** `nightclient-launcher-linux-x64`
   - **macOS (Apple Silicon):** `nightclient-launcher-macos-arm64`
   - **macOS (Intel):** `nightclient-launcher-macos-x64`
2. При первом запуске можно перенести готовую сборку из **Prism, Modrinth, TLauncher, LegacyLauncher, Lunar, Official Minecraft** или любой папки (только чтение игровых файлов — аккаунты и токены не затрагиваются), либо создать чистый инстанс на Fabric (`1.20` – `26.3`) с тумблером **Визуалы NightClient**.
3. Лаунчер сам скачает и проверит по SHA-256 свежий универсальный JAR с GitHub Releases, аккуратно подключит его через `-Dfabric.addMods` (без захламления `mods/`) и ненавязчиво уведомит об обновлениях лаунчера со списком изменений.

### Вариант B — Как обычный Fabric-мод *(без лаунчера)*

1. Установи **Fabric Loader** (`>= 0.19.5`) для нужной версии Minecraft (`1.20` – `26.3`). **Fabric API не требуется.**
2. Скачай `NightClient-<version>.jar` (или pure-Java версию `NightClient-<version>-pure-java.jar`) из [GitHub Releases](https://github.com/N3moCC/NightClient/releases) и положи в `.minecraft/mods/`.
3. Запусти игру с подходящей версией Java:

| Версии Minecraft | Требуемая Java | Графический движок |
| :--- | :--- | :--- |
| `1.20` – `1.20.4` | Java 17 | OpenGL |
| `1.20.5` – `1.21.11` | Java 21 | OpenGL |
| `26.1` – `26.1.2` | Java 25 | OpenGL |
| `26.2` – `26.3` | Java 25 | Нативный Vulkan |

4. Нажми **`Right Shift`** в игре, чтобы открыть меню. Кастомные `.nc`-темы можно класть в `.minecraft/config/nightclient/`.

---

## Лаунчер NightClient

Опциональный кроссплатформенный лаунчер в [`launcher/`](launcher/) написан на **Rust** с **Iced 0.14** и нативной полупрозрачностью (Wayland blur / Windows DWM):

- **Мастер миграции при первом запуске:** автоматически находит сборки из Prism, Modrinth, TLauncher, LegacyLauncher, Lunar, Official Minecraft или любой указанной папки — переносит только выбранные игровые файлы (моды, конфиги, ресурспаки, шейдеры, серверы, миры) и никогда не касается аккаунтов, токенов и баз сессий.
- **Быстрый вход на сервер (`Играть ▾`):** выпадающий список сохранённых серверов из `servers.dat` прямо на кнопке запуска.
- **Все 5 загрузчиков:** Vanilla, Fabric, Quilt, NeoForge и Forge с выбором конкретной версии.
- **Каталог Modrinth и `.mrpack`:** поиск, автоустановка зависимостей, массовое обновление по SHA-1, миграция модов между версиями, Drag & Drop и установка FPS-пака в один клик.
- **Управление Java и GC:** поиск локальных JDK, автоустановка Eclipse Temurin (Java 17/21/25) и профили флагов JVM (`Smooth`, `Zero-Pause`, `Peak FPS`, `Stock`).
- **Обновления по согласию с GitHub Releases:** поддержка каналов `Stable` и `Beta` для `N3moCC/NightClient`, ненавязчивый индикатор новой версии, просмотр Markdown-changelog, проверка `SHA256SUMS.txt` и установка только после подтверждения.
- **Анализ крашей:** автоскрытие токенов в логах, выгрузка на `mclo.gs` и быстрые фиксы (ОЗУ, Java, Fabric Loader, дубликаты JAR).

Подробнее — в [`launcher/README.md`](launcher/README.md).

---

## Движок оптимизации

Модульный движок оптимизации (`Без потерь`, `Оптимальный`, `Максимум FPS`, `Свой`) спроектирован для совместной работы с популярными модами:

- **Автосовместимость:** автоматически обнаруживает и уступает пересекающиеся хуки модам **Sodium**, **EntityCulling**, **MoreCulling**, **ImmediatelyFast**, **EnhancedBlockEntities**, **Krypton**, **Exordium**, **FerriteCore**, **Lithium** и др.
- **Опциональный `nightclient-native` (Rust JNI):** открыто собирается в GitHub Actions под `linux-x64`, `windows-x64`, `macos-arm64` и `macos-x64`. Подхватывается из лаунчера (`-Dnightclient.native.path`), из ресурсов универсального JAR или из `config/nightclient/natives/`. При отсутствии нативки клиент стабильно работает на чистой Java.

---

## Контроль функций сервером (`LiteAPI`)

NightClient поддерживает протокол [HolyWorld LiteAPI](https://wiki.holyworld.me/lite/api/protocol) на **всех 24 версиях (`1.20` – `26.3`)** без зависимости от Fabric API:

- **Client ID:** `nightclient` · **Канал:** `liteapi:feature-control`
- При входе на сервер отправляет `checkFeatures` (строго соблюдая лимит `1 запрос / 10 сек`) и применяет `blocklist` в реальном времени.
- Заблокированные функции и отдельные подфункции (например, `minimap.invisibles`, `worldMap.players`, `hwhelper.zones`, `tapeMouse`) **полностью отключаются в логике/рендере/биндах/командах и полностью скрываются из ClickGUI, поиска, редактора HUD и экранов карты** без изменения сохранённого конфига.
- При выходе с сервера или входе в одиночный мир все ограничения автоматически сбрасываются.

---

## Сборка из исходников

### Требования
- **JDK 25** (автоматически компилирует таргеты в байткод Java 17/21/25)
- **Python 3.10+**
- **Rust stable** *(опционально — только для `nightclient-native` и `launcher`)*

### Мод (Java / Gradle)

```bash
# Запустить все общие юнит-тесты (api, core, renderer, ui, runtime, universal)
./gradlew -PcommonOnly=true test

# Собрать конкретную версию Minecraft (например, 26.3 или 1.20.1)
./gradlew -PmcVersion=26.3 :mc26_3:build
./gradlew -PmcVersion=1.20.1 :mc1_20_1:build

# Собрать полную матрицу из 24 версий и один универсальный JAR
python3 tools/build.py build-universal

# Собрать чистый Java universal JAR без встроенных нативных библиотек
python3 tools/build.py build-universal --no-native

# Запустить проверки генераторов и байткод-аудита
python3 tools/checks/test_compatibility_audit.py
python3 tools/checks/test_optimization_features.py
```

### Нативная библиотека (`nightclient-native`)

```bash
cd native/nightclient-native
cargo test
cargo build --release
```

### Лаунчер (`launcher`)

```bash
cd launcher
cargo test --workspace
cargo run --release --bin nightclient-launcher
```

---

## Структура проекта

```text
NightClient/
├── .github/workflows/        # Прозрачные CI и кроссплатформенные пайплайны релизов GitHub
├── assets/                   # Иконки и логотипы
├── launcher/                 # Кроссплатформенный лаунчер на Rust + Iced 0.14
│   └── crates/               # nl-platform, nl-net, nl-java, nl-night, nl-auth, nl-content, nl-core, nl-app
├── native/
│   └── nightclient-native/   # Опциональная Rust JNI библиотека ускорения (zlib-rs, SIMD, PBO/MDI)
├── modules/
│   ├── api/                  # Контракты и модели без зависимостей (Java 17)
│   ├── core/                 # Настройки, FeaturePolicy, хранилища, i18n, косметика, парсер .nc
│   ├── renderer/             # Компилятор иконок, OpenGL/Vulkan шейдеры, 3D-модели, атлас шрифтов
│   ├── ui/                   # Декларативный движок .nc, ClickGUI и 14 HUD-виджетов
│   ├── runtime/              # Рантайм клиента, LiteAPI-менеджер, аудио-оповещения, игровая логика
│   ├── platform/             # Шаблоны, мосты, миксины и спецификации совместимости для 24 версий
│   └── universal/            # Java 17 загрузчик и рантайм-селектор адаптеров
├── themes/default/           # Встроенная стандартная тема .nc
└── tools/
    ├── build.py              # Генератор шейдеров/иконок/платформ и сборщик универсального JAR
    ├── generators/           # Генераторы фич, мира, камеры, размытия и 3D-косметики
    ├── matrix/               # Сборщик 24 версий, ASM-релокатор и аудитор совместимости
    └── checks/               # Автономные тесты контрактов и генераторов
```

---

## Сообщество, прозрачность и лицензия

- **Discord-сервер:** вопросы, баг-репорты, кастомные `.nc`-темы и анонсы релизов — **[discord.gg/GenCYdp7NX](https://discord.gg/GenCYdp7NX)**.
- **100% открытый исходный код без обфускации:** все модули мода, исходники Rust JNI, лаунчер и GitHub Actions workflows открыты; к каждому релизу прилагается `SHA256SUMS.txt`.
- Распространяется под лицензией **[MIT](LICENSE)**. Лицензии сторонних библиотек сохранены в `META-INF/licenses/`.
