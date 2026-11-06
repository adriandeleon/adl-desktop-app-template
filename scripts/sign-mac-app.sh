#!/usr/bin/env bash
# Signs the finished ExampleApp.app with a Developer ID Application identity, the hardened runtime
# and a secure timestamp: what Apple's notarization asks for.
#
# Runs only when EXAMPLEAPP_MAC_SIGN_IDENTITY names the identity (release.yml imports the certificate
# into a throw-away keychain, EXAMPLEAPP_MAC_KEYCHAIN). Anywhere else it is a no-op and the bundle keeps
# its ad-hoc signature.
#
# It runs after every edit to the bundle and before build-installers.sh wraps it, and signs inside
# out as codesign requires: loose Mach-O files, then the nested runtime, then the app. Not --deep:
# that would give every nested binary the app's entitlements.
#
# Usage: sign-mac-app.sh <app-bundle>
set -euo pipefail

app="${1:?app bundle path required}"
identity="${EXAMPLEAPP_MAC_SIGN_IDENTITY:-}"

[ "$(uname -s)" = "Darwin" ] || exit 0
[ -n "$identity" ] || { echo "sign: EXAMPLEAPP_MAC_SIGN_IDENTITY not set, bundle stays ad-hoc signed"; exit 0; }
[ -d "$app" ] || { echo "sign: no app bundle at $app" >&2; exit 1; }

entitlements="$(cd "$(dirname "$0")/.." && pwd)/packaging/macos/ExampleApp.entitlements"
[ -f "$entitlements" ] || { echo "sign: no $entitlements" >&2; exit 1; }

sign=(codesign --force --timestamp --options runtime)
[ -n "${EXAMPLEAPP_MAC_KEYCHAIN:-}" ] && sign+=(--keychain "$EXAMPLEAPP_MAC_KEYCHAIN")
sign+=(--sign "$identity")
echo "sign: Developer ID signing $app as \"$identity\""

# jpackage's dmg step re-signs ad hoc (throwing our signature away) any image its state file calls
# unsigned. The file is sealed with the bundle, so it is marked signed before codesign runs.
state="$app/Contents/app/.jpackage.xml"
[ -f "$state" ] || { echo "sign: no $state; jpackage would re-sign the app ad hoc in the dmg" >&2; exit 1; }
if grep -q '<signed>' "$state"; then
    perl -pi -e 's#<signed>\s*false\s*</signed>#<signed>true</signed>#' "$state"
else
    perl -pi -e 's#</jpackage-state>#  <signed>true</signed>\n</jpackage-state>#' "$state"
fi
grep -q '<signed>true</signed>' "$state" || { echo "sign: could not mark $state as signed" >&2; exit 1; }

# Finder metadata or resource forks anywhere in the bundle make codesign refuse to seal it.
xattr -cr "$app"

main="$app/Contents/MacOS/ExampleApp"
libraries=()
executables=()
while IFS= read -r -d '' file; do
    [ "$file" = "$main" ] && continue
    file -b "$file" | grep -q '^Mach-O' || continue
    case "$file" in
        *.dylib) libraries+=("$file") ;;
        *) if [ -x "$file" ]; then executables+=("$file"); else libraries+=("$file"); fi ;;
    esac
done < <(find "$app" -type f -print0)
echo "sign: ${#libraries[@]} libraries, ${#executables[@]} executables besides the main launcher"

if [ "${#libraries[@]}" -gt 0 ]; then "${sign[@]}" "${libraries[@]}"; fi
if [ "${#executables[@]}" -gt 0 ]; then "${sign[@]}" --entitlements "$entitlements" "${executables[@]}"; fi
if [ -d "$app/Contents/runtime" ]; then "${sign[@]}" "$app/Contents/runtime"; fi
"${sign[@]}" --entitlements "$entitlements" "$app"

codesign --verify --deep --strict --verbose=2 "$app"
echo "sign: app signed and verified"
