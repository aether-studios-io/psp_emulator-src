#!/usr/bin/env bash
# Prepares the public GPL source mirror (spec CLAUDE.md §5d) for the current commit.
#
#   tools/prepare_source_mirror.sh [out_dir]     default: ../psp_emulator-src
#
# Exports HEAD with `git archive` (only tracked files, so local.properties, keystores and
# admob.properties never leave the machine), then strips what must not be public:
#   - app/google-services.json  -> replaced by the template's .example
#   - docs/, store_assets         -> internal ops notes (project/AdMob IDs, console steps) and ASO material, not source
#   - CLAUDE.md, TEMPLATE.md       -> internal agent/team process docs
# It does NOT create or push the public repo: that needs the owner's OK (hard stop §8.3).
# After approval:
#   gh repo create psp_emulator-src --public --source=<out_dir> --remote=origin --push \
#     --description "RetroNova source code (GPL-3.0)"
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:-$REPO_ROOT/../psp_emulator-src}"
SHA="$(git -C "$REPO_ROOT" rev-parse --short HEAD)"

rm -rf "$OUT"
mkdir -p "$OUT"
git -C "$REPO_ROOT" archive --format=tar HEAD | tar -x -C "$OUT"

rm -f "$OUT/app/google-services.json"
rm -rf "$OUT/docs" "$OUT/store_assets" "$OUT/.github"
rm -f "$OUT/CLAUDE.md" "$OUT/TEMPLATE.md"
cat > "$OUT/SOURCE_README.md" <<EOF
# RetroNova — complete corresponding source (GPL-3.0)

Source of RetroNova (app.letostudio.gameemulator) at commit $SHA.

Build:
1. Android Studio (JBR 17+), Android SDK 36, NDK 28.2.13676358, CMake 3.31.6.
2. \`tools/build_core.sh\` — builds the PPSSPP libretro core (tag pinned in the script) into
   \`app/src/main/jniLibs\`. Prebuilt copies of exactly these binaries are included.
3. Add your own \`app/google-services.json\` (Firebase) — see \`app/google-services.json.example\`.
4. \`./gradlew assembleDebug\`.

License: GPL-3.0 (\`LICENSE\`); third-party components: \`THIRD_PARTY_NOTICES.md\`.
EOF
echo "mirror prepared at $OUT (commit $SHA) — not published"
