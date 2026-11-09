# Building and packaging

Everything is built with the Maven wrapper on JDK 25. jpackage and the JavaFX natives are
host-specific, so each OS builds its own packages; there is no cross-building.

## Local commands

| Command | Produces |
|---|---|
| `./mvnw verify` | Compiles, tests, checks formatting and coverage |
| `./mvnw clean -Pdist package` | The app image: `target/dist/ExampleApp` (`ExampleApp.app` on macOS) |
| `./mvnw clean -Pdist -Dskip.installers=false package` | The image, plus this OS's installers in `target/installers` |
| `./mvnw -Pfatjar package` | `target/exampleapp-<version>-fat.jar`, runnable with `java -jar` on this OS |

Run the image's launcher to try it: `target/dist/ExampleApp/bin/ExampleApp` (Linux),
`target/dist/ExampleApp.app/Contents/MacOS/ExampleApp` (macOS), `target\dist\ExampleApp\ExampleApp.exe`
(Windows). `scripts/smoke-test.sh <launcher> <work-dir>` does what the release does: it starts the app
with a fresh config directory and fails unless the first window comes up within 90 seconds.

Installer prerequisites: `fakeroot` and `rpm` on Linux (and `dpkg-deb`, which Debian and Ubuntu have),
WiX 3.14 on Windows, Xcode command-line tools on macOS.

## What `-Pdist` runs

After jpackage builds the app image, the pom runs these scripts, in this order:

| Script | What it does |
|---|---|
| `scripts/add-notices.sh` | Copies `LICENSE` and `NOTICE` into the image's app directory |
| `scripts/aot-train.sh` | Records the AOT cache into the image (see below) |
| `scripts/fix-mac-bundle-version.sh` | macOS: writes the real version into `Info.plist` (jpackage refuses a 0.x version there) and re-seals the bundle ad hoc |
| `scripts/sign-mac-app.sh` | macOS: Developer ID signing with `packaging/macos/ExampleApp.entitlements`. A no-op unless `EXAMPLEAPP_MAC_SIGN_IDENTITY` is set |
| `scripts/build-installers.sh` | Only with `-Dskip.installers=false`: wraps the finished image into installers |

**AOT cache (JDK 25).** `scripts/aot-train.sh` runs the finished image once, in a fresh config folder,
with `-XX:AOTCacheOutput`: the app opens its window, waits 2.5 s (`-Dexampleapp.aotTrain=1`) and exits,
and the classes it loaded are archived as `exampleapp.aot` in the image's app folder. The launcher
starts with `-XX:AOTCache=$APPDIR/exampleapp.aot`; measured on Linux, the first window appears in about
770 ms instead of 1100 ms. The training run needs a display (Linux CI uses `xvfb-run`). Without one,
or when training fails, the build still succeeds with a warning and the app starts uncached; the release
sets `EXAMPLEAPP_REQUIRE_AOT=1` so a release cannot ship without its cache. The step runs after the
notices and before the macOS version fix and signing, so the signature covers the cache.

## Installers

| OS | Files | Notes |
|---|---|---|
| macOS | `.dmg` | Signed with the app's Developer ID identity when one is set, then notarized by the release |
| Windows | `.msi` | Per-user install, Start menu entry, desktop shortcut. **Generate your own `--win-upgrade-uuid` once** in `build-installers.sh`; never change it after the first release |
| Linux | `.deb`, `.rpm`, `.tar.gz`, `.AppImage` | Same image in each. The deb also owns the AppStream metainfo, the theme icon and a Debian `copyright` file; the rpm installs only under `/opt` (no software-centre listing). The AppImage tools are pinned by version and SHA-256 and cached in `~/.cache/exampleapp-build` |

The Linux packages install to `/opt/exampleapp`. The menu entry comes from `packaging/linux/ExampleApp.desktop`;
its name must match the launcher (`ExampleApp`) exactly, or jpackage silently ships its own without
`StartupWMClass`. `StartupWMClass` is `com.example.app.App`, the JavaFX application class, which is
how a desktop matches a running window to its launcher.

The deb and rpm declare JavaFX's system libraries (GTK 3, GL, X11, Pango, fontconfig, FreeType)
themselves, because the bundler cannot see natives that JavaFX unpacks at run time. Recheck the list
in `build-installers.sh` after a JavaFX upgrade.

`packaging/linux/io.github.adriandeleon.ExampleApp.metainfo.xml` is what GNOME Software and KDE
Discover show. Add a `<release>` to it for every version.

## Icons

`branding/exampleapp.svg` is the single source. After changing it, run `scripts/make-icons.sh`
(rsvg-convert, ImageMagick 7 and a JDK) and commit what it writes:

- `packaging/icons/exampleapp.png` (512 px, Linux), `.ico` (16 to 256 px, Windows), `.icns` (128 to
  1024 px, macOS, written by `scripts/Icns.java`)
- `src/main/resources/com/example/app/icons/app-{16,32,64,128,256}.png`, the window icons

rsvg-convert (librsvg) draws the SVG; ImageMagick only scales the result and writes the `.ico`.
ImageMagick's own SVG renderer, which `magick` uses unless it was built with the rsvg delegate, draws
no strokes and no gradient fills, so a family icon would come out as a bare tile. The script stops if
the middle of the rendered icon is one flat color. Install rsvg-convert with `librsvg2-bin` (Debian,
Ubuntu), `librsvg2-tools` (Fedora) or `brew install librsvg` (macOS).

## Release builds

`.github/workflows/release.yml` runs the same `-Pdist -Dskip.installers=false verify` on five runners
and renames every file to `ExampleApp-<version>-<target>.<ext>`:

| Target | Runner | Downloads |
|---|---|---|
| `linux-x64` | `ubuntu-latest` | `.deb`, `.rpm`, `.AppImage`, `.tar.gz`, `.jar`, each with a `.asc` |
| `linux-arm64` | `ubuntu-24.04-arm` | `.deb`, `.rpm`, `.AppImage`, `.tar.gz`, `.jar`, each with a `.asc` |
| `macos-arm64` | `macos-15` | `.dmg` (signed, notarized, stapled) |
| `macos-x64` | `macos-15-intel` | `.dmg` (signed, notarized, stapled) |
| `windows-x64` | `windows-latest` | `.msi`, `.jar` |

Windows on ARM has no build: OpenJFX publishes no `win-aarch64` natives yet, so those users run the
x64 MSI under emulation. See [release.md](release.md) for signing and publishing.
