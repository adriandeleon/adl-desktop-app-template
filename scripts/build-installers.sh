#!/usr/bin/env bash
# Wraps a finished application image into this platform's installers:
#   macOS   .dmg (Developer ID signed when EXAMPLEAPP_MAC_SIGN_IDENTITY is set)
#   Windows .msi
#   Linux   .deb, .rpm, .tar.gz and .AppImage
#
# Wraps rather than builds: jpackage's --app-image mode packages exactly the image it is handed, with
# the notices and signature (and an AOT cache, if you add a training step) already in it. jpackage is
# host-specific, so each platform builds its own; there is no cross-building.
#
# Usage: build-installers.sh <app-image> <app-version> <dest-dir> <icon>
set -euo pipefail

image="${1:?app image required}"
version="${2:?app version required}"
dest="${3:?destination required}"
icon="${4:?icon required}"

[ -d "$image" ] || { echo "no app image at $image" >&2; exit 1; }
[ -f "$icon" ] || { echo "no icon at $icon" >&2; exit 1; }
mkdir -p "$dest"

root="$(cd "$(dirname "$0")/.." && pwd)"
linux="$root/packaging/linux"
license="$root/LICENSE"
app_id=io.github.adriandeleon.ExampleApp
metainfo="$app_id.metainfo.xml"
description="A keyboard-first JavaFX desktop app."

common=(--app-image "$image" --name ExampleApp --app-version "$version" --dest "$dest"
        --vendor "Adrián Arturo De León Saldivar"
        --description "$description"
        # The deb's Homepage, the rpm's URL and the MSI's About link.
        --about-url "https://github.com/adriandeleon/ExampleApp"
        --copyright "Copyright $(date +%Y) Adrián Arturo De León Saldivar"
        --icon "$icon")

build() {
    local type="$1"; shift
    echo "[installers] building $type"
    if jpackage --type "$type" "${common[@]}" "$@"; then
        echo "[installers] $type OK"
    else
        # Named, not swallowed: a release that quietly ships three installers instead of four is a
        # bug report from whichever platform was missing.
        echo "::error::[installers] $type failed" >&2
        return 1
    fi
}

# jpackage's deb installs /opt/exampleapp and nothing else. GNOME Software and KDE Discover tie a
# listing to a package by asking which package owns the metainfo file, so the finished deb is unpacked
# and rebuilt with the metainfo, the theme icon and Debian's copyright file as files it owns.
own_in_deb() {
    local deb="$1" work size
    work="$(mktemp -d)"
    dpkg-deb -R "$deb" "$work"
    install -D -m 644 "$linux/$metainfo" "$work/usr/share/metainfo/$metainfo"
    install -D -m 644 "$icon" "$work/usr/share/icons/hicolor/512x512/apps/$app_id.png"
    install -D -m 644 "$resources/copyright" "$work/usr/share/doc/exampleapp/copyright"
    # The control file's sums and size describe the contents, so they are rewritten to match.
    (cd "$work" && find . -path ./DEBIAN -prune -o -type f -printf '%P\0' | LC_ALL=C sort -z |
        xargs -0 md5sum) > "$work/DEBIAN/md5sums"
    size=$(du -sk --exclude=DEBIAN "$work" | cut -f1)
    sed -i "s/^Installed-Size: .*/Installed-Size: $size/" "$work/DEBIAN/control"
    dpkg-deb --root-owner-group --build "$work" "$deb"
    rm -rf "$work"
}

# The image as it is, for a system without dpkg or rpm or a user without root. The top directory
# carries the version so two can be unpacked side by side; bin/ExampleApp starts it.
build_tarball() {
    echo "[installers] building tar.gz"
    tar --create --gzip --file "$dest/exampleapp-$version-linux.tar.gz" --sort=name \
        --owner=0 --group=0 --numeric-owner --mode=go-w \
        --transform "flags=rh;s,^$(basename "$image"),exampleapp-$version," \
        -C "$(dirname "$image")" "$(basename "$image")"
    echo "[installers] tar.gz OK"
}

# appimagetool and the runtime it prepends, pinned by version and SHA-256: one runs on the build
# machine, the other ships inside every AppImage. Cached, and checked on every use.
appimage_tool() {
    local name="$1" url="$2" sum="$3" cache file
    cache="${XDG_CACHE_HOME:-$HOME/.cache}/exampleapp-build"
    file="$cache/$sum-$name"
    mkdir -p "$cache"
    # The helper's stdout is its answer (the path), so the check must print nothing there: with a
    # missing file, `sha256sum --check --status` still writes "FAILED open or read" to stdout.
    if ! { [ -f "$file" ] && echo "$sum  $file" | sha256sum --check --status >/dev/null 2>&1; }; then
        curl --fail --silent --show-error --location --retry 3 --output "$file.part" "$url"
        echo "$sum  $file.part" | sha256sum --check --quiet >&2 ||
            { echo "::error::[installers] $name from $url does not match its pinned hash" >&2; return 1; }
        mv "$file.part" "$file"
    fi
    chmod +x "$file"
    echo "$file"
}

# One file that runs anywhere, carrying the same image as the packages: AppRun, a desktop entry and
# its icon at the top, the image under usr/lib. The entry is named after the app id, because
# appimagetool finds the metainfo (only as .appdata.xml) by the entry's name.
build_appimage() {
    local arch tool runtime appdir entry
    echo "[installers] building AppImage"
    arch="$(uname -m)"
    case "$arch" in
        x86_64)
            tool=$(appimage_tool appimagetool-1.9.1-x86_64.AppImage \
                https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage \
                ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0)
            runtime=$(appimage_tool runtime-20251108-x86_64 \
                https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64 \
                2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d) ;;
        aarch64)
            tool=$(appimage_tool appimagetool-1.9.1-aarch64.AppImage \
                https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-aarch64.AppImage \
                f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158)
            runtime=$(appimage_tool runtime-20251108-aarch64 \
                https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-aarch64 \
                00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444) ;;
        *)
            # Not a failure: the release-asset check decides what a release must contain.
            echo "::warning::[installers] no pinned AppImage tools for $arch, skipping the AppImage" >&2
            return 0 ;;
    esac

    appdir="$(mktemp -d)/ExampleApp.AppDir"
    mkdir -p "$appdir/usr/lib" "$appdir/usr/share/applications" "$appdir/usr/share/metainfo"
    cp -a "$image" "$appdir/usr/lib/exampleapp"
    install -m 755 "$linux/AppRun" "$appdir/AppRun"
    sed "s|>exampleapp-ExampleApp.desktop<|>$app_id.desktop<|" "$linux/$metainfo" \
        > "$appdir/usr/share/metainfo/$app_id.appdata.xml"
    install -D -m 644 "$icon" "$appdir/usr/share/icons/hicolor/512x512/apps/$app_id.png"
    cp "$icon" "$appdir/$app_id.png"
    ln -s "$app_id.png" "$appdir/.DirIcon"
    # jpackage's tokens filled in by hand, the icon by its theme name. No TryExec: an AppImage is not
    # on PATH, and an entry whose TryExec is missing is hidden.
    entry="$appdir/usr/share/applications/$app_id.desktop"
    sed -e 's/APPLICATION_NAME/ExampleApp/' -e "s/APPLICATION_DESCRIPTION/$description/" \
        -e "s/^Icon=APPLICATION_ICON$/Icon=$app_id/" \
        -e '/^TryExec=/d' -e 's/^Exec=APPLICATION_LAUNCHER$/Exec=exampleapp/' \
        "$linux/ExampleApp.desktop" > "$entry"
    cp "$entry" "$appdir/"

    # appimagetool's own AppStream check fetches every URL in the metainfo, so a repository that is
    # not public yet (or GitHub having a bad minute) fails the build. Validated offline instead.
    if command -v appstreamcli >/dev/null; then
        appstreamcli validate --no-net "$appdir/usr/share/metainfo/$app_id.appdata.xml"
    fi

    # Extracted rather than mounted: a container or a CI runner may have no FUSE.
    if APPIMAGE_EXTRACT_AND_RUN=1 ARCH="$arch" "$tool" --no-appstream --runtime-file "$runtime" \
        "$appdir" "$dest/ExampleApp-$version.AppImage"; then
        echo "[installers] AppImage OK"
    else
        echo "::error::[installers] AppImage failed" >&2
        return 1
    fi
    rm -rf "$(dirname "$appdir")"
}

case "$(uname -s)" in
    Darwin)
        # No --license-file on the dmg or msi: there it becomes a click-through agreement, which a
        # permissive licence does not ask for. The notices travel inside the image (add-notices.sh).
        build dmg --mac-package-name ExampleApp
        # The disk image gets its own signature so Gatekeeper can check it before mounting;
        # release.yml then notarizes and staples it.
        if [ -n "${EXAMPLEAPP_MAC_SIGN_IDENTITY:-}" ]; then
            sign=(codesign --force --timestamp)
            [ -n "${EXAMPLEAPP_MAC_KEYCHAIN:-}" ] && sign+=(--keychain "$EXAMPLEAPP_MAC_KEYCHAIN")
            for dmg in "$dest"/*.dmg; do
                "${sign[@]}" --sign "$EXAMPLEAPP_MAC_SIGN_IDENTITY" "$dmg"
                codesign --verify --verbose=2 "$dmg"
            done
        fi
        ;;
    Linux)
        # The resource directory overrides two things jpackage would otherwise generate: the desktop
        # entry (it must be named exactly <launcher>.desktop or jpackage ignores it silently), which
        # carries StartupWMClass so a running window matches its launcher; and the icon, since the deb
        # and rpm bundlers ignore --icon and ship a stock 32 px Java icon unless <name>.png is here.
        # The entry's Icon= is that file's path under /opt (APPLICATION_ICON), which works in both
        # packages; only the deb also installs the icon into the hicolor theme (own_in_deb).
        resources="$(mktemp -d)"
        trap 'rm -rf "$resources"' EXIT
        cp "$linux/ExampleApp.desktop" "$resources/"
        cp "$icon" "$resources/ExampleApp.png"

        # What JavaFX links. Its natives are extracted from the module jars at run time, so the
        # bundlers' library scan never sees them and the package would install on a minimal system
        # and die at start. From `readelf -d` of libglassgtk3, libprism_es2 and the font libraries;
        # recheck after a JavaFX upgrade. Debian names with the t64 rename as an alternative, so one
        # deb installs on Debian 12 and 13 and Ubuntu 22.04 and 24.04; the rpm by library capability.
        deb_deps="libgtk-3-0t64 | libgtk-3-0, libgl1, libx11-6, libxxf86vm1, libxtst6"
        deb_deps+=", libpango-1.0-0, libpangoft2-1.0-0, libfontconfig1, libfreetype6"
        rpm_deps="libgtk-3.so.0()(64bit), libGL.so.1()(64bit), libX11.so.6()(64bit)"
        rpm_deps+=", libXxf86vm.so.1()(64bit), libXtst.so.6()(64bit), libpangoft2-1.0.so.0()(64bit)"
        rpm_deps+=", libfontconfig.so.1()(64bit), libfreetype.so.6()(64bit)"

        # Debian's machine-readable copyright format, generated from LICENSE. --license-file alone
        # pastes the file raw under "License:", which reads as "Unknown" to the package tools.
        [ -f "$license" ] || { echo "no LICENSE at $license" >&2; exit 1; }
        {
            echo "Format: https://www.debian.org/doc/packaging-manuals/copyright-format/1.0/"
            echo "Upstream-Name: ExampleApp"
            echo "Source: https://github.com/adriandeleon/ExampleApp"
            echo
            echo "Files: *"
            echo "Copyright: $(sed -n 's/^Copyright (c) //p' "$license" | head -1)"
            echo "License: MIT"
            echo "Comment: Third-party components and their licences are listed in"
            echo " /opt/exampleapp/lib/app/NOTICE; the Java runtime's notices are in"
            echo " /opt/exampleapp/lib/runtime/legal."
            echo
            echo "License: MIT"
            sed -e '1{/^MIT License$/d;}' "$license" | sed -e '1{/^$/d;}' -e 's/^$/./' -e 's/^/ /'
        } > "$resources/copyright"

        linux_common=(--linux-package-name exampleapp --linux-shortcut
                      --license-file "$license" --resource-dir "$resources")
        build deb "${linux_common[@]}" \
            --linux-deb-maintainer "adrian.deleon@gmail.com" \
            --linux-package-deps "$deb_deps"
        own_in_deb "$(ls "$dest"/exampleapp_*.deb)"
        build rpm "${linux_common[@]}" \
            --linux-rpm-license-type MIT \
            --linux-package-deps "$rpm_deps"
        build_tarball
        build_appimage
        ;;
    *)
        # The upgrade UUID makes the next MSI replace this one instead of installing beside it.
        # GENERATE YOUR OWN ONCE (uuidgen) when you create the app, then never change it: changing it
        # strands every existing install.
        build msi \
            --win-menu \
            --win-menu-group ExampleApp \
            --win-shortcut \
            --win-dir-chooser \
            --win-per-user-install \
            --win-upgrade-uuid "c0ffee00-0000-4000-8000-000000000001"
        ;;
esac

echo "[installers] produced:"
ls -la "$dest"
