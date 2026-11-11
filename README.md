<p align="center">
  <img src="branding/exampleapp.svg" alt="" width="96" height="96">
</p>

<h1 align="center">ExampleApp</h1>

<p align="center">

[![CI](https://github.com/adriandeleon/ExampleApp/actions/workflows/ci.yml/badge.svg)](https://github.com/adriandeleon/ExampleApp/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/adriandeleon/ExampleApp)](https://github.com/adriandeleon/ExampleApp/releases/latest)
[![License: MIT](https://img.shields.io/github/license/adriandeleon/ExampleApp)](LICENSE)
![Java](https://img.shields.io/badge/Java-25%2B-orange?logo=openjdk&logoColor=white)
![JavaFX](https://img.shields.io/badge/JavaFX-27%2B-1e90ff)
![Platforms](https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey)
[![Pauta](https://img.shields.io/badge/Pauta-6.0.1%20%C2%B7%20Complete-2ea043)](https://exampleapp-project.dev/docs/pauta/)
[![Stars](https://img.shields.io/github/stars/adriandeleon/ExampleApp?style=flat)](https://github.com/adriandeleon/ExampleApp/stargazers)

</p>

<!-- template:start -->
> [!IMPORTANT]
> **This is the template for [Pauta (the adl desktop apps guidelines)](https://pauta-project.dev/), edition [6.0.1](https://pauta-project.dev/6.0.1/).** Click **Use this template**, clone your new repository, then run:
>
> ```bash
> scripts/rename.sh MyApp com.myapp
> ./mvnw verify
> ./mvnw javafx:run
> ```
>
> `rename.sh` turns every `ExampleApp` / `exampleapp` / `EXAMPLEAPP` / `com.example.app` into your
> names, moves the package folders, gives the Windows installer its own upgrade code, and deletes
> this box, the rename check in CI and itself. Then replace the icon (`branding/`, then
> `scripts/make-icons.sh`) and the screenshots, and write your app in tabs and tool windows in place of
> the samples (`SampleFiles`, `SamplePanels`).
> Every MUST and SHOULD of Pauta 6.0.1 that a template can meet is here and tested, so the template is
> Complete; see [docs/architecture.md](docs/architecture.md) for where each piece lives. What only your app
> can have is listed, rule by rule, in [`pauta.json`](src/main/resources/com/example/app/pauta.json) and in
> the app's Pauta window (Help → Pauta Conformance) as `app-only`: your website, your screenshots and your
> repository settings. After `rename.sh`, `./mvnw verify` lists them, and the release build (`-Pdist`)
> fails until each is met or pending on an issue of your own ([TODO.md](TODO.md)).
<!-- template:end -->

ExampleApp is a keyboard-first JavaFX desktop app for Windows, macOS and Linux. Every action is a
command you can run from the Command Palette, and nothing leaves your computer unless you ask.

Built to [Pauta (the adl desktop apps guidelines)](https://pauta-project.dev/), edition [6.0.1](https://pauta-project.dev/6.0.1/), [Pauta conformance: Complete](https://exampleapp-project.dev/docs/pauta/).

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/screenshot-dark.svg">
    <img src="docs/images/screenshot-light.svg" alt="ExampleApp's main window" width="800">
  </picture>
</p>

## Highlights

- **The same app on Windows, macOS and Linux.** One codebase gives you the same window, commands,
  settings and keymaps on every system, with `Ctrl` or `Cmd` where each expects it and a native
  installer for each.
- **Keyboard first.** A Command Palette (`Ctrl/Cmd+Shift+P`, or `M-x` in the Emacs keymap) finds and
  runs every command. CUA and Emacs keymaps, with your own bindings on top. A toolbar puts the everyday
  ones a click away, each button naming its key, and Simple mode trims the menus and the toolbar to the
  essentials.
- **Tabs and tool windows.** Documents in tabs you can pin, reorder, switch between and reopen, and tool
  windows docked on three sides that you can move, float, maximize and resize, every action a command.
- **Settings that apply as you change them.** A searchable Settings window, a palette command for
  every setting, and a versioned config folder that older versions never overwrite.
- **Light and dark**, following your system: Caret & Ink, the Pauta family palette, with the Inter
  typeface on every OS.
- **Settings Sync** through a private Git repository you own.
- **Nothing fails silently.** A Message Log of every status message, a Debug Log of everything
  logged, and Help → Report a Problem to open a redacted, prefilled GitHub issue.
- **Six languages:** English, Spanish, Portuguese, French, Italian and German.
- **Local first.** No telemetry. The only automatic network call is a once-a-day release check you
  can turn off.

## Documentation

The user guide lives on the website, [exampleapp-project.dev/docs](https://exampleapp-project.dev/docs/),
and Help → Documentation opens the page for the version you run:

- [Getting started](https://exampleapp-project.dev/docs/getting-started/): install, first launch, the first thing to do
- [Keyboard reference](https://exampleapp-project.dev/docs/keymaps/): every command and its keys, CUA and Emacs
- [Settings](https://exampleapp-project.dev/docs/settings/): every setting, the config folder, Settings Sync
- [FAQ](https://exampleapp-project.dev/docs/faq/) and [What's new](https://exampleapp-project.dev/docs/whats-new/)

This repository's [docs/](docs/README.md) is for contributors.

## Install

Download the installer for your system from the
[latest release](https://github.com/adriandeleon/ExampleApp/releases/latest):

| System | Download |
|---|---|
| macOS (Apple Silicon / Intel) | `ExampleApp-<version>-macos-arm64.dmg` / `-macos-x64.dmg`, signed and notarized |
| Windows x64 | `ExampleApp-<version>-windows-x64.msi` |
| Linux x64 / arm64 | `.deb`, `.rpm`, `.AppImage` or `.tar.gz`, each with an `.asc` signature |

Every download has a build provenance attestation:
`gh attestation verify <file> --repo adriandeleon/ExampleApp`.

## Build

You need JDK 25 or newer. The Maven Wrapper fetches Maven.

```bash
./mvnw javafx:run                 # run the app
./mvnw test                       # unit + headless FX tests, writes the JaCoCo report
./mvnw spotless:apply             # format
./mvnw verify                     # full gate: tests, coverage check, NullAway, formatting
./mvnw -Pfatjar package           # runnable fat jar for this OS
./mvnw clean -Pdist package       # native app image for this OS (clean is mandatory)
```

[CONTRIBUTING.md](CONTRIBUTING.md) has the rest, and [docs/](docs/) explains how the app is built. To check
the app against its Pauta edition, ask a coding agent to "audit against Pauta": the
[`pauta-audit` skill](.claude/skills/pauta-audit/SKILL.md) writes `docs/pauta-audit.json` and
`docs/pauta-audit.md`.

## Command line

```text
exampleapp [options] [files...]

  -h, --help            Show the help and exit
  -v, --version         Show the version and exit
      --config-dir DIR  Use DIR as the config folder (default ~/.exampleapp)
      --dev             Use ~/.exampleapp-dev, so a development build keeps its own config
      --new-instance    Start a second process instead of handing off to the running one
      --no-session      Do not restore the last window size and position
      --report          Write a redacted problem report and open a GitHub issue
```

A second launch on the same config folder hands its arguments to the running app and exits.

## Config folder

Settings live in `~/.exampleapp` (`~/.exampleapp-dev` with `--dev`); `--config-dir` or `EXAMPLEAPP_CONFIG_DIR`
names another folder, in that order:

| File | What |
|---|---|
| `settings.json` | Preferences, with a `schemaVersion`; migrated forward on upgrade, backed up when a newer version wrote it |
| `exampleapp-session.log` | This session's Debug Log; the previous one is `exampleapp-session.prev.log` |
| `sync/` | The Settings Sync clone, when sync is set up |

Settings → Advanced exports the folder as a `.zip` and imports one back, replacing only the parts you tick.

## License

ExampleApp is released under the [MIT License](LICENSE). Third-party components and their licenses
are listed in [NOTICE](NOTICE). ExampleApp is built with the help of AI coding tools.
