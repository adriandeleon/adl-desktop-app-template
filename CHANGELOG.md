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
