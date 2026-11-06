#!/usr/bin/env bash
# Copies the licence notices into a finished application image, beside the application's jars, so
# every installed copy carries them (the MIT licence and the bundled libraries' licences ask for it).
#
# Into the app directory rather than the image root: on macOS anything outside Contents/'s standard
# folders is "unsealed contents" that codesign refuses. Adding files breaks the bundle's ad-hoc seal;
# fix-mac-bundle-version.sh, which runs next, re-seals it.
#
# Usage: add-notices.sh <app-image-dir> <file>...
set -euo pipefail

image="${1:?app image directory required}"
shift

case "$(uname -s)" in
    Darwin) appdir="$image/Contents/app" ;;
    Linux)  appdir="$image/lib/app" ;;
    *)      appdir="$image/app" ;;
esac

[ -d "$appdir" ] || { echo "[notices] no app directory at $appdir" >&2; exit 1; }
for file in "$@"; do
    [ -f "$file" ] || { echo "[notices] missing $file" >&2; exit 1; }
    cp "$file" "$appdir/"
    echo "[notices] $(basename "$file") -> $appdir"
done
