#!/usr/bin/env bash
# Builds the PPSSPP libretro core (GPL-2.0-or-later) from source for RetroNova.
#
#   tools/build_core.sh [abi ...]        default: arm64-v8a armeabi-v7a x86_64
#
# Output
#   app/src/main/jniLibs/<abi>/libppsspp_libretro_android.so    stripped, release ABIs
#   app/src/debug/jniLibs/x86_64/libppsspp_libretro_android.so  stripped, emulator only
#   tools/core-out/unstripped/<abi>/libppsspp_libretro_android.so  for Crashlytics NDK symbols
#   app/src/main/assets/ppsspp/                                  flash0 fonts, compat.ini, shaders
#
# Pinned: PPSSPP tag below (docs/DECISIONS.md D-003). NDK r28+ links with 16 KB pages.
# Never download native code at runtime (Play policy) — the .so is bundled in the AAB.
#
# Env overrides: PPSSPP_SRC (checkout dir), ANDROID_NDK_HOME, CMAKE_BIN_DIR, JOBS.
set -euo pipefail

PPSSPP_TAG="v1.20.4"
API_LEVEL=24

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}}"
NDK="${ANDROID_NDK_HOME:-$SDK/ndk/28.2.13676358}"
CMAKE_BIN_DIR="${CMAKE_BIN_DIR:-$SDK/cmake/3.31.6/bin}"
PPSSPP_SRC="${PPSSPP_SRC:-$REPO_ROOT/../_ppsspp_src}"
JOBS="${JOBS:-$(nproc 2>/dev/null || echo 4)}"
ASSETS_ONLY=0
if [ "${1:-}" = "--assets-only" ]; then ASSETS_ONLY=1; shift; fi
ABIS=("$@")
[ ${#ABIS[@]} -eq 0 ] && ABIS=(arm64-v8a armeabi-v7a x86_64)
[ "$ASSETS_ONLY" = 1 ] && ABIS=()

export PATH="$CMAKE_BIN_DIR:$PATH"
STRIP="$(ls "$NDK"/toolchains/llvm/prebuilt/*/bin/llvm-strip* | head -1)"

[ -d "$NDK" ] || { echo "NDK not found: $NDK" >&2; exit 1; }
command -v cmake >/dev/null || { echo "cmake not found (CMAKE_BIN_DIR=$CMAKE_BIN_DIR)" >&2; exit 1; }
command -v ninja >/dev/null || { echo "ninja not found" >&2; exit 1; }

if [ ! -d "$PPSSPP_SRC/.git" ]; then
  git clone --depth 1 --branch "$PPSSPP_TAG" --recurse-submodules --shallow-submodules \
    https://github.com/hrydgard/ppsspp.git "$PPSSPP_SRC"
fi
actual_tag="$(git -C "$PPSSPP_SRC" describe --tags --exact-match 2>/dev/null || echo none)"
[ "$actual_tag" = "$PPSSPP_TAG" ] || { echo "PPSSPP checkout is $actual_tag, expected $PPSSPP_TAG" >&2; exit 1; }

for abi in "${ABIS[@]+"${ABIS[@]}"}"; do
  build="$PPSSPP_SRC/build-libretro-$abi"
  echo "=== $abi -> $build"
  cmake -S "$PPSSPP_SRC" -B "$build" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DANDROID_STL=c++_static \
    -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
    -DCMAKE_BUILD_TYPE=Release \
    -DLIBRETRO=ON
  cmake --build "$build" --target ppsspp_libretro -j "$JOBS"

  so="$(find "$build" -maxdepth 3 -name 'ppsspp_libretro*.so' | head -1)"
  [ -n "$so" ] || { echo "core .so not found in $build" >&2; exit 1; }

  unstripped="$REPO_ROOT/tools/core-out/unstripped/$abi"
  mkdir -p "$unstripped"
  cp "$so" "$unstripped/libppsspp_libretro_android.so"

  if [ "$abi" = "x86_64" ] || [ "$abi" = "x86" ]; then
    out="$REPO_ROOT/app/src/debug/jniLibs/$abi"
  else
    out="$REPO_ROOT/app/src/main/jniLibs/$abi"
  fi
  mkdir -p "$out"
  "$STRIP" --strip-unneeded -o "$out/libppsspp_libretro_android.so" "$so"
  ls -l "$out/libppsspp_libretro_android.so"
done

# Core assets (fonts, compat database, shaders) — copied to filesDir/system/PPSSPP on first
# run by CoreAssets. Licence files travel with them.
assets="$REPO_ROOT/app/src/main/assets/ppsspp"
rm -rf "$assets"
mkdir -p "$assets"
for d in flash0 vfpu shaders; do cp -r "$PPSSPP_SRC/assets/$d" "$assets/"; done
for f in compat.ini compatvr.ini knownfuncs.ini langregion.ini ppge_atlas.zim ppge_atlas.meta; do
  cp "$PPSSPP_SRC/assets/$f" "$assets/" 2>/dev/null || true
done
mkdir -p "$assets/lang" && cp "$PPSSPP_SRC/assets/lang/en_US.ini" "$assets/lang/"
cp "$PPSSPP_SRC/LICENSE.TXT" "$assets/LICENSE.TXT"
echo "$PPSSPP_TAG $(git -C "$PPSSPP_SRC" rev-parse HEAD)" > "$assets/CORE_VERSION"
echo "done"
