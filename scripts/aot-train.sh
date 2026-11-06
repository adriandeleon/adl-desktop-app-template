#!/usr/bin/env bash
# Records a JDK 25 AOT cache for the packaged app, into the image it was built from.
#
# The cache archives the classes a run loads, so the next start skips most loading and verifying:
# a quarter or more of a JavaFX app's start-up. The training run must render a window, because the
# JavaFX scene, control and CSS classes worth archiving are not touched until something paints; a
# headless run archives almost nothing while appearing to succeed.
#
# Failure-tolerant: the launcher runs under AOTMode=auto, which warns and starts normally when the
# cache is missing or stale. A machine with no display produces no cache and a working app. The
# release sets EXAMPLEAPP_REQUIRE_AOT=1 so a release without its cache fails instead.
#
# Usage: aot-train.sh <app-image-dir> [cache-file-name]
set -uo pipefail

image="${1:?app image directory required}"
cache_name="${2:-exampleapp.aot}"

# jpackage lays an image out differently per platform; Linux keeps runtime and app under lib/.
case "$(uname -s)" in
    Darwin) java="$image/Contents/runtime/Contents/Home/bin/java"; appdir="$image/Contents/app" ;;
    Linux)  java="$image/lib/runtime/bin/java"; appdir="$image/lib/app" ;;
    *)      java="$image/runtime/bin/java.exe"; appdir="$image/app" ;;
esac
if [ ! -x "$java" ]; then
    echo "[aot] no runtime java at $java, skipping" >&2
    exit 0
fi
cache="$appdir/$cache_name"

# es2,sw rather than the platform default: CI runners have virtual GPUs, and macOS's Metal pipeline can
# abort on one with an exception Prism's fallback cannot catch.
# shellcheck disable=SC2054
opts=(-XX:AOTCacheOutput="$cache"
      -Dprism.order=es2,sw
      -Dexampleapp.aotTrain=1
      -Dexampleapp.updateCheck=off
      --enable-native-access=javafx.graphics
      -m com.example.app/com.example.app.Main --new-instance)

# A fresh config folder, removed on the way out: the trainer is the real app, and what the cache
# records should be a first launch, never the developer's own settings.
config="$(mktemp -d "${TMPDIR:-/tmp}/exampleapp-aot.XXXXXX")"
trap 'rm -rf "$config"' EXIT
if command -v cygpath >/dev/null 2>&1; then
    EXAMPLEAPP_CONFIG_DIR="$(cygpath -w "$config")"
else
    EXAMPLEAPP_CONFIG_DIR="$config"
fi
export EXAMPLEAPP_CONFIG_DIR

# A hung trainer must not hang the build: GNU timeout where there is one (not on stock macOS).
bounded() {
    if command -v timeout >/dev/null 2>&1; then timeout 180 "$@"; else "$@"; fi
}

echo "[aot] training -> $cache"
if [ "$(uname -s)" = Linux ] && [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
    bounded xvfb-run -a "$java" "${opts[@]}" >/dev/null 2>&1
else
    bounded "$java" "${opts[@]}" >/dev/null 2>&1
fi
status=$?

if [ -s "$cache" ]; then
    echo "[aot] cache written: $(du -h "$cache" | cut -f1) (trainer exit $status)"
    exit 0
fi
echo "::warning::[aot] no cache produced (trainer exit $status); the app will start uncached" >&2
if [ "${EXAMPLEAPP_REQUIRE_AOT:-0}" = 1 ]; then
    echo "[aot] EXAMPLEAPP_REQUIRE_AOT=1: failing the build" >&2
    exit 1
fi
exit 0
