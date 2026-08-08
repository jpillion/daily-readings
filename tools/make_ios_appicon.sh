#!/usr/bin/env bash
# Render an App Store app icon (1024x1024, opaque, square) from the same vector source the
# Android *launcher* icon uses.
#
# NOTE — this is not the only candidate, and the two Android icons are NOT the same artwork:
#   * the LAUNCHER icon (this script's source) is the flat white book glyph on solid green;
#   * the PLAY STORE LISTING icon (docs/play-listing/play-icon-512.png) is a richer, separately
#     authored asset — gradient background, and three dots under the book for the three streams.
# On iOS a single 1024 asset serves as both the App Store icon and the home-screen icon, so
# which of the two to match is a design decision for the owner. See docs/RELEASING-IOS.md Part 2.
#
# Source of truth for this variant:
#   glyph      app/src/main/res/drawable/ic_launcher_foreground.xml  (VectorDrawable pathData)
#   background app/src/main/res/values/colors.xml  -> ic_launcher_background
#
# Apple's requirements this satisfies:
#   * exactly 1024x1024
#   * NO alpha channel (an icon with alpha is rejected at upload)
#   * square, flat, no pre-applied rounded corners (iOS applies its own mask)
#
# Why the glyph is scaled up: Android's adaptive-icon canvas is 108x108 with only the inner
# ~72x72 reliably visible after the launcher mask, so the source glyph is drawn small on
# purpose. iOS shows almost the whole square, so a 1:1 port would look undersized. SCALE below
# makes the glyph occupy ~60% of the square, which visually matches the Android launcher.
#
# Deps: rsvg-convert (brew install librsvg). Deterministic: same input -> same output bytes.
#
# Usage:  tools/make_ios_appicon.sh [output.png]

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${1:-$REPO_ROOT/docs/app-store-listing/appicon-1024.png}"

VECTOR="$REPO_ROOT/app/src/main/res/drawable/ic_launcher_foreground.xml"
COLORS="$REPO_ROOT/app/src/main/res/values/colors.xml"
SIZE=1024
SCALE=1.30

command -v rsvg-convert >/dev/null || { echo "rsvg-convert missing: brew install librsvg" >&2; exit 1; }
[ -f "$VECTOR" ] || { echo "Vector source not found: $VECTOR" >&2; exit 1; }

# Extract the background colour and the glyph path from the Android sources rather than
# duplicating either here. If the Android icon changes, re-running this picks the change up.
BG="$(sed -n 's/.*name="ic_launcher_background">\(#[0-9A-Fa-f]\{6,8\}\)<.*/\1/p' "$COLORS" | head -1)"
PATH_DATA="$(sed -n 's/.*android:pathData="\([^"]*\)".*/\1/p' "$VECTOR" | head -1)"

[ -n "$BG" ] || { echo "Could not read ic_launcher_background from $COLORS" >&2; exit 1; }
[ -n "$PATH_DATA" ] || { echo "Could not read pathData from $VECTOR" >&2; exit 1; }

mkdir -p "$(dirname "$OUT")"
TMP_SVG="$(mktemp -t appicon).svg"
TMP_PNG="$(mktemp -t appicon).png"
trap 'rm -f "$TMP_SVG" "$TMP_PNG"' EXIT

# VectorDrawable pathData is SVG path syntax, and both default to the nonzero fill rule, so the
# glyph renders identically. The viewport is the Android 108x108 canvas.
cat > "$TMP_SVG" <<SVG
<svg xmlns="http://www.w3.org/2000/svg" width="$SIZE" height="$SIZE" viewBox="0 0 108 108">
  <rect x="0" y="0" width="108" height="108" fill="$BG"/>
  <g transform="translate(54,54) scale($SCALE) translate(-54,-54)">
    <path fill="#FFFFFF" d="$PATH_DATA"/>
  </g>
</svg>
SVG

rsvg-convert -w "$SIZE" -h "$SIZE" -o "$TMP_PNG" "$TMP_SVG"

# Flatten away the alpha channel. rsvg always emits RGBA; Apple rejects that, so round-trip
# through a format that cannot carry alpha. sips ships with macOS, so this needs no extra dep.
TMP_BMP="${TMP_PNG%.png}.bmp"
sips -s format bmp "$TMP_PNG" --out "$TMP_BMP" >/dev/null
sips -s format png "$TMP_BMP" --out "$OUT" >/dev/null
rm -f "$TMP_BMP"

W=$(sips -g pixelWidth  "$OUT" | awk '/pixelWidth/{print $2}')
H=$(sips -g pixelHeight "$OUT" | awk '/pixelHeight/{print $2}')
A=$(sips -g hasAlpha    "$OUT" | awk '/hasAlpha/{print $2}')

echo "wrote $OUT  (${W}x${H}, hasAlpha=${A}, bg=${BG})"

# Fail loudly rather than handing Apple an icon it will reject at upload.
[ "$W" = "$SIZE" ] && [ "$H" = "$SIZE" ] || { echo "FAIL: expected ${SIZE}x${SIZE}" >&2; exit 1; }
[ "$A" = "no" ] || { echo "FAIL: icon still has an alpha channel" >&2; exit 1; }
