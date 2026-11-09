#!/usr/bin/env bash
# Generates every icon the build and the app use from the one source, branding/exampleapp.svg:
#
#   packaging/icons/exampleapp.png   512x512, jpackage on Linux and the hicolor theme icon
#   packaging/icons/exampleapp.ico   16-256, jpackage on Windows
#   packaging/icons/exampleapp.icns  128-1024, jpackage on macOS (written by scripts/Icns.java)
#   src/main/resources/com/example/app/icons/app-{16,32,64,128,256}.png   the window icons
#
# Run it after changing the SVG and commit the results: the build does not regenerate them, so it
# needs neither ImageMagick nor a renderer. Needs rsvg-convert (librsvg), ImageMagick 7 (`magick`)
# and a JDK.
#
# rsvg-convert draws the SVG, not ImageMagick: unless ImageMagick was built with the rsvg delegate,
# its own renderer (MSVG) draws no strokes and no gradient fills, and a family icon (a stroked
# periwinkle glyph, a teal gradient) comes out as a bare tile without an error.
#
# Usage: scripts/make-icons.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
svg="$root/branding/exampleapp.svg"
icons="$root/packaging/icons"
window="$root/src/main/resources/com/example/app/icons"
[ -f "$svg" ] || { echo "no $svg" >&2; exit 1; }
command -v rsvg-convert >/dev/null || {
    echo "rsvg-convert is required: librsvg2-bin (Debian, Ubuntu), librsvg2-tools (Fedora)," >&2
    echo "brew install librsvg (macOS), pacman -S mingw-w64-ucrt-x86_64-librsvg (MSYS2)" >&2
    exit 1
}
command -v magick >/dev/null || { echo "ImageMagick 7 (magick) is required" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$icons" "$window"

# One 1024 px master, rendered at 2x the SVG's 512 px canvas, and every size scaled down from it.
# -strip and no date chunks: the same SVG gives byte-identical PNGs, so a re-run makes no diff.
png() { magick "$work/master.png" -resize "$1x$1" -strip -define png:exclude-chunks=date,time "PNG32:$2"; }
rsvg-convert --width 1024 --height 1024 --format png --output "$work/master.png" "$svg"

# A renderer that drops what it cannot draw still writes a PNG, so look at it: the middle half of
# an icon is its mark, and one flat color there means the mark is missing.
spread="$(magick "$work/master.png" -gravity center -crop 50%x50%+0+0 +repage \
    -format '%[fx:standard_deviation]' info:)"
if awk -v s="$spread" 'BEGIN { exit !(s < 0.01) }'; then
    echo "the rendered icon is one flat color in the middle: $svg drew no mark" >&2
    exit 1
fi

png 512 "$icons/exampleapp.png"
for size in 16 32 64 128 256; do
    png "$size" "$window/app-$size.png"
done

for size in 16 24 32 48 64 128 256; do
    png "$size" "$work/ico-$size.png"
done
magick "$work"/ico-{16,24,32,48,64,128,256}.png "$icons/exampleapp.ico"

for size in 128 256 512 1024; do
    png "$size" "$work/icns-$size.png"
done
java "$root/scripts/Icns.java" "$icons/exampleapp.icns" \
    "ic07=$work/icns-128.png" "ic08=$work/icns-256.png" \
    "ic09=$work/icns-512.png" "ic10=$work/icns-1024.png"

ls -l "$icons" "$window"
