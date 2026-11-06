#!/usr/bin/env bash
# Puts the true version into the macOS bundle's Info.plist and re-seals the bundle ad hoc.
#
# jpackage refuses an --app-version whose first number is zero, but only on macOS, so a 0.x release is
# built under a placeholder (0.3.1 -> 1.3.1, see the pom). This writes the real one back, or Finder's
# Get Info would disagree with the app's own About.
#
# The re-seal is unconditional: jpackage ad-hoc-signs the image, and every file added after it (the
# notices, an AOT cache) breaks that seal, which macOS reports as "damaged". sign-mac-app.sh replaces
# this signature with a Developer ID one when a release has the certificate.
#
# Usage: fix-mac-bundle-version.sh <app-bundle> <true-version>
set -euo pipefail

app="${1:?app bundle path required}"
version="${2:?version required}"

[ "$(uname -s)" = "Darwin" ] || exit 0
[ -d "$app" ] || { echo "no app bundle at $app" >&2; exit 1; }

plist="$app/Contents/Info.plist"
current=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$plist" 2>/dev/null || echo "")
if [ "$current" != "$version" ]; then
    /usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $version" "$plist"
    /usr/libexec/PlistBuddy -c "Set :CFBundleVersion $version" "$plist"
    echo "bundle version: $current -> $version"
fi

codesign --force --deep --sign - "$app"
codesign --verify --deep --verbose=2 "$app"
echo "bundle re-sealed ad hoc and verified"
