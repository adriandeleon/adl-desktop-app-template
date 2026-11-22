#!/usr/bin/env bash
# Retakes the README's hero screenshot from the real window, headless, at 2x, in the light and the
# dark theme (Pauta DOCS-18):
#
#   docs/images/screenshot-light.png
#   docs/images/screenshot-dark.png
#
# ScreenshotsFxTest builds the window the way a launch does and writes each theme's PNG. Run it after a UI change and commit the
# results. Needs only the JDK: the Maven Wrapper fetches the rest, and no display is used.
#
# Usage: scripts/screenshots.sh [folder]   (default docs/images)
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(mkdir -p "${1:-$root/docs/images}" && cd "${1:-$root/docs/images}" && pwd)"
cd "$root"
./mvnw -B -q test -Dtest=ScreenshotsFxTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Djacoco.skip=true -Dexampleapp.shots="$out"
ls -l "$out"/screenshot-*.png
