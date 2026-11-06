#!/usr/bin/env bash
# Turns the template into your app: renames every placeholder, moves the Java package folders,
# gives the Windows installer its own upgrade code, then removes the template-only parts (the
# "Start from this template" section of README.md, the rename check in ci.yml, and this script).
#
# Usage: scripts/rename.sh <AppName> <package>
#   scripts/rename.sh Lumen io.github.you.lumen
#
#   <AppName>  the display name, one word in UpperCamelCase (Lumen, PhotoDesk). It also gives the
#              lower-case id (lumen: launcher, ~/.lumen, file names) and the upper-case environment
#              prefix (LUMEN_CONFIG_DIR).
#   <package>  the Java package and JPMS module name (com.lumen, io.github.you.lumen).
#
# Run it once, from the repository root, on a clean working tree, then review `git diff`.
set -euo pipefail

name="${1:-}"
package="${2:-}"
if [[ ! "$name" =~ ^[A-Z][A-Za-z0-9]*$ ]] || [[ ! "$package" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
    echo "Usage: scripts/rename.sh <AppName> <package>   e.g. scripts/rename.sh Lumen com.lumen" >&2
    echo "  AppName: UpperCamelCase letters and digits; package: lower-case, dotted, at least two parts." >&2
    exit 2
fi
[ -f pom.xml ] && [ -d src/main/java/com/example/app ] || {
    echo "Run this from the root of an unrenamed template (src/main/java/com/example/app is missing)." >&2
    exit 1
}

lower="$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')"
upper="$(printf '%s' "$name" | tr '[:lower:]' '[:upper:]')"
path="${package//.//}"
uuid="$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen | tr '[:upper:]' '[:lower:]')"
echo "Renaming ExampleApp -> $name ($lower, ${upper}_*), com.example.app -> $package"

# 1. Move the package folders (main, test, resources) to the new package path.
for root in src/main/java src/test/java src/main/resources; do
    [ -d "$root/com/example/app" ] || continue
    mkdir -p "$root/$path.tmp-rename"
    cp -R "$root/com/example/app/." "$root/$path.tmp-rename/"
    rm -rf "$root/com/example/app"
    rmdir "$root/com/example" "$root/com" 2>/dev/null || true
    mkdir -p "$(dirname "$root/$path")"
    mv "$root/$path.tmp-rename" "$root/$path"
done

# 2. Rename files and folders that carry the name.
rename_path() {
    local from="$1" to="$2"
    [ -e "$from" ] && mv "$from" "$to"
    return 0
}
rename_path branding/exampleapp.svg "branding/$lower.svg"
for ext in png ico icns; do rename_path "packaging/icons/exampleapp.$ext" "packaging/icons/$lower.$ext"; done
rename_path packaging/linux/ExampleApp.desktop "packaging/linux/$name.desktop"
rename_path packaging/linux/io.github.adriandeleon.ExampleApp.metainfo.xml \
    "packaging/linux/io.github.adriandeleon.$name.metainfo.xml"
rename_path packaging/macos/ExampleApp.entitlements "packaging/macos/$name.entitlements"
rename_path src/jreleaser/distributions/exampleapp-windows "src/jreleaser/distributions/$lower-windows"
rename_path src/jreleaser/distributions/exampleapp "src/jreleaser/distributions/$lower"

# 3. Replace the placeholders in every text file, most specific first.
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    files() { git ls-files -co --exclude-standard -z; }
else
    files() { find . -type f -not -path './.git/*' -not -path './target/*' -print0; }
fi
files | while IFS= read -r -d '' file; do
    [ -f "$file" ] || continue
    [ "$file" = scripts/rename.sh ] || [ "$file" = ./scripts/rename.sh ] && continue
    grep -Iq . "$file" 2>/dev/null || continue   # skip binaries (fonts, icons)
    NAME="$name" LOWER="$lower" UPPER="$upper" PKG="$package" PKGPATH="$path" UUID="$uuid" perl -0pi -e '
        s/com\.example\.app/$ENV{PKG}/g;
        s{com/example/app}{$ENV{PKGPATH}}g;
        s/c0ffee00-0000-4000-8000-000000000001/$ENV{UUID}/g;
        s/ExampleApp/$ENV{NAME}/g;
        s/exampleapp/$ENV{LOWER}/g;
        s/EXAMPLEAPP/$ENV{UPPER}/g;
    ' "$file"
done

# 4. Reformat: a name of another length re-wraps some lines. Needs JDK 25+ (Palantir Java Format);
#    without one, the next steps say to run it.
formatted=false
if command -v java >/dev/null 2>&1; then
    major="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = \([0-9]*\).*/\1/p')"
    if [ "${major:-0}" -ge 25 ] && ./mvnw -B -q --no-transfer-progress spotless:apply >/dev/null 2>&1; then
        formatted=true
    fi
fi

# 5. Remove what only the template needs.
perl -0pi -e 's/<!-- template:start -->.*?<!-- template:end -->\n*//s' README.md
perl -0pi -e 's/\n *# template:start.*?# template:end\n/\n/s' .github/workflows/ci.yml
rm -f scripts/rename.sh

echo "Done. Next:"
echo "  - review: git status && git diff --stat"
$formatted || echo "  - ./mvnw spotless:apply on JDK 25 (the new names re-wrap some lines)"
echo "  - ./mvnw verify, then ./mvnw javafx:run"
echo "  - replace branding/$lower.svg, then scripts/make-icons.sh"
echo "  - the Windows upgrade code in scripts/build-installers.sh is now $uuid: never change it after a release"
