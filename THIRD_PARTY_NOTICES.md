# Third-party notices — RetroNova

RetroNova (app.letostudio.gameemulator) is distributed under the **GNU General Public License
v3.0** (see `LICENSE`) because it links GPL software. The complete corresponding source code
of every released build is published at https://github.com/aether-studios-io/psp_emulator-src
(see "Source code" in Settings › Open-source licenses).

RetroNova does not include, download or link to any games, BIOS or firmware files.

## Emulation

| Component | Version | License | Source |
| --- | --- | --- | --- |
| PPSSPP libretro core (`libppsspp_libretro_android.so`) | v1.20.4 (built from source by `tools/build_core.sh`) | GPL-2.0-or-later | https://github.com/hrydgard/ppsspp |
| — FFmpeg (statically linked into the core) | PPSSPP's bundled build | LGPL-2.1-or-later | https://ffmpeg.org |
| — libretro-common | bundled with PPSSPP | MIT | https://github.com/libretro/libretro-common |
| — PPSSPP assets (flash0 fonts, shaders, compat.ini, vfpu tables) | v1.20.4 | see `assets/ppsspp/LICENSE.TXT` | https://github.com/hrydgard/ppsspp |
| LibretroDroid | 0.14.0 | GPL-3.0 | https://github.com/Swordfish90/LibretroDroid |
| Oboe (via LibretroDroid) | — | Apache-2.0 | https://github.com/google/oboe |

Emulation by the PPSSPP project. RetroNova is an independent app and is not affiliated with,
endorsed by or sponsored by any console maker or by the PPSSPP project.

## Android libraries

| Component | License |
| --- | --- |
| AndroidX, Jetpack Compose, Material 3, Navigation, Room, DataStore, WorkManager, Splashscreen | Apache-2.0 |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| Dagger / Hilt | Apache-2.0 |
| OkHttp, Retrofit | Apache-2.0 |
| Coil | Apache-2.0 |
| Google Play Billing Library | Android SDK License |
| Google Mobile Ads SDK, User Messaging Platform | Google APIs Terms of Service |
| Firebase Analytics, Crashlytics (+NDK), Remote Config | Apache-2.0 / Google APIs Terms |
| Google Play In-App Review | Play Core Software Development Kit Terms |

## Fonts and icons

| Asset | License |
| --- | --- |
| Be Vietnam Pro (bundled in `res/font`) | SIL Open Font License 1.1 — https://github.com/bettergui/BeVietnamPro |
| Material Symbols Rounded (converted to vector drawables) | Apache-2.0 — https://github.com/google/material-design-icons |

## Test data

`tools/make_test_pbp.py` wraps open test programs from PPSSPP's `pspautotests` (used for
development only; nothing from it ships in the app).
