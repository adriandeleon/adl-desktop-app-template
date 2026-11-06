# Architecture

One JPMS module, `com.example.app`, in ten packages. The rule of thumb: **a decision lives in a pure
class with a unit test; the toolkit code around it stays thin.**

| Package | What | Key classes |
|---|---|---|
| `com.example.app` | Entry point and identity | `Main` (CLI, hand-off, then `Application.launch`), `App`, `AppInfo` |
| `cli` | The command line, pure | `CommandLine` |
| `config` | Versioned `settings.json`, export/import | `Settings`, `ConfigStore`, `Migrations`, `ConfigDir`, `ConfigExporter` |
| `command` | Commands and key bindings, pure | `CommandRegistry`, `Command`, `KeyStroke`, `Keymap`, `Keybindings`, `FuzzyMatch` |
| `i18n` | The string catalogs | `Messages` (`tr`) |
| `instance` | One app per config folder | `SingleInstance` (Unix-domain socket) |
| `report` | Report a Problem and `--report` | `ProblemReport`, `Redactor`, `ReportCommand`, `Browser` |
| `sync` | Settings Sync through Git | `SyncEngine`, `SyncMerge`, `Git` |
| `update` | The release check, pure + HTTP | `UpdateCheck`, `UpdateService`, `ReleaseInfo` |
| `ui` | Everything JavaFX | see below |

## Start-up

1. `Main.main`: `java.awt.headless=true` first, then `DebugLog.install()` (logging + the uncaught
   exception handler), then `Main.run`: `--help`, `--version` and `--report` answer and exit; a second
   launch hands its arguments to the running app (`SingleInstance.handOff`) and exits.
2. `App.start` calls `Startup.start`: fonts, config folder, session log file, settings (migrated),
   language, theme, `AppServices`, `MainWindow`, the single-instance listener, the update check
   (once per session, at most daily, 5 s after the first frame) and Settings Sync.

The tests start the app through the same `Startup.start`, with a `FakeHost`.

## The UI package

- `AppServices`: the long-lived parts shared by every window: `ConfigStore`, `CommandRegistry`,
  effective `Keybindings`, `MessageLog`, `UpdateCoordinator`, `SyncController`, and the `Host` (browser,
  file choosers, exit), the one seam the tests replace.
- `MainWindow`: menus, the content placeholder, `StatusBar`, and an `OverlayHost` (the root
  `StackPane`) that carries the in-scene overlays. **Put your app's content in `placeholder()`.**
- `Commands`: every command, in one place. Menus, buttons, the palette and keys all run them by id.
- `Picker`: the one list chooser; the Command Palette is a `Picker` over commands, and the theme,
  keymap and language pickers are `Picker`s too.
- `KeyDispatcher`: key presses to commands through the active bindings, with `C-x`-style prefixes
  and a which-key hint in the status bar.
- `SettingsWindow` + `SettingsSearch`: pages, search, live apply.
- `AboutDialog`, `ReportProblemWindow`, `DebugLogWindow`, `MessageLogView`: the family templates.

## How things flow

- **A command**: `CommandRegistry.run(id)` → the action. Its title and description come from
  `command.<id>` / `command.<id>.desc`. An unavailable command explains itself in the palette.
- **A setting**: a control or a command calls `store.update(s -> …)`, which saves `settings.json`
  atomically and notifies every listener: `MainWindow` re-applies theme and status bar, the Settings
  window reloads its controls, `AppServices` rebuilds the key bindings.
- **A message**: `services.echo("status.key", args)` → `MessageLog` → the status bar's echo line.
  Severity comes from the key's name. Logging goes to `DebugLog` (memory + session file).

## Config folder

`~/.exampleapp` (`--config-dir`, `--dev`, `EXAMPLEAPP_CONFIG_DIR`): `settings.json`,
`exampleapp-session.log` (+ `.prev.log`), `instance.sock`, `sync/`, and backups
(`settings.json.v<n>.bak`, `settings.json.broken.bak`).
