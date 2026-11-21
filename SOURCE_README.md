# Odyssemu — complete corresponding source (GPL-3.0)

Source of Odyssemu (app.letostudio.gameemulator) at commit 78b3ef5.

Build:
1. Android Studio (JBR 17+), Android SDK 36, NDK 28.2.13676358, CMake 3.31.6.
2. `tools/build_core.sh` — builds the PPSSPP libretro core (tag pinned in the script) into
   `app/src/main/jniLibs`. Prebuilt copies of exactly these binaries are included.
3. Add your own `app/google-services.json` (Firebase) — see `app/google-services.json.example`.
4. `./gradlew assembleDebug`.

License: GPL-3.0 (`LICENSE`); third-party components: `THIRD_PARTY_NOTICES.md`.
