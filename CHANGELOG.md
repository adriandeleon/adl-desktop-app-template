# Changelog

All notable changes to ExampleApp are documented in this file.

The format is based on [Keep a Changelog 1.1](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each entry opens with a bold
one-line summary written for users.

## [Unreleased]

### Added

- **A Command Palette that finds and runs every command.** `Ctrl/Cmd+Shift+P`, or `M-x` in the Emacs
  keymap; unavailable commands are listed greyed with the reason.
- **CUA and Emacs keymaps, with your own bindings on top.** Edit any binding in Settings → Keymap.
- **Settings that apply as you change them.** A searchable Settings window with a palette command for
  every setting; the config folder can be exported and imported.
- **Light and dark themes that follow your system.**
- **Settings Sync through a Git repository you own.** Your key bindings, the same on every computer.
- **A Message Log and a Debug Log.** Click the status bar for this session's messages; Help → Debug
  Log for everything logged, also written to `exampleapp-session.log`.
- **Report a Problem.** A redacted report, opened as a prefilled GitHub issue; `exampleapp --report`
  does the same when the app cannot start.
- **A daily update check you can turn off,** shown in the status bar and in About.
- **Six languages:** English, Spanish, Portuguese, French, Italian and German.
- **Menus in the title bar.** Where the system allows it, the window draws its own title bar with the
  menus and the title in it (Settings → Appearance turns it off; macOS keeps the screen menu bar).
- **Menus show each command's key binding,** in the active keymap, Emacs sequences included.
- **About names the Pauta edition the app is built to.** A *Built to Pauta 2.0.0* link opens that
  edition's rules, and *Copy details* and `--version` carry a `Pauta:` line.
- **Import and Reset ask before they overwrite.** Importing a configuration and resetting a key binding
  you set now name what is replaced, and Escape cancels.
- **Help → Documentation and What's New.** Documentation opens this version's user guide on the
  website, and What's New this build's release notes; both are on the start page too.
- **Every button is a command.** Copy and Clear in the Message Log, the Debug Log's buttons and About's
  Copy details run from the Command Palette and can be bound to keys. The palette lists every command,
  itself included, and Help follows the family's order.
- **Right-click menus.** Message Log rows and the Debug Log have menus of their commands; the Menu key
  and Shift+F10 open the selected row's, and a click anywhere else closes it.
- **Faster start-up from an AOT cache.** Installed builds carry a JDK 25 ahead-of-time cache; the first
  window appears about a third sooner.
- **Settings Sync can create its repository.** Settings → Sync → Create on GitHub… makes a private
  repository with your GitHub CLI (`gh`) and connects to it.

### Changed

- **Links to Pauta point to its new home, [pauta-project.dev](https://pauta-project.dev/).**
- **New default themes: Caret & Ink, the Pauta family palette, in light and dark.** They replace
  AtlantaFX Primer; System still follows your OS. Colored text and badges read at WCAG AA in both,
  and the keyboard focus ring at 3:1 on every surface.
- **Tooltips, menus and popups use Inter too,** not only windows and dialogs.
- **The Settings sidebar has line icons,** the same glyph each command will wear everywhere.

### Fixed

- **A theme name this version does not know no longer resets all your settings.** The theme falls
  back to System and every other setting is kept.
