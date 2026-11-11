# Architecture

One JPMS module, `com.example.app`, in ten packages. The rule of thumb: **a decision lives in a pure
class with a unit test; the toolkit code around it stays thin.**

| Package | What | Key classes |
|---|---|---|
| `com.example.app` | Entry point and identity | `Main` (CLI, hand-off, then `Application.launch`), `App`, `AppInfo`, `Os` (the one class that reads `os.name`, with an override for the tests), `PautaConformance` (the exceptions, the `template` flag and the `checked` list in `pauta.json`, and the conformance computed from them) |
| `cli` | The command line, pure | `CommandLine` |
| `config` | Versioned `settings.json`, export/import | `Settings`, `ConfigStore`, `Migrations`, `ConfigDir`, `ConfigExporter`, `ConfigImport`, `ConfigFiles` (every config file written whole: a temporary file, then a move) |
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
2. Just before `launch`, `main` calls `Startup.readAhead`, which runs `Startup.prepare` on a thread of its
   own while the toolkit starts: config folder, session log file, settings (migrated, and every value
   clamped to its range), language and the single-instance listener. None of that file work is on the
   FX thread; `prepare` refuses to run there.
3. `App.init`, on the launcher thread, takes what was read (`Startup.takePrepared`).
4. `App.start` calls `Startup.start`: fonts (and the UI-font listener on every window), theme,
   `AppServices`, `MainWindow`. After the first frame has painted: the deferred fonts, the keymaps, the
   update check (once per session, at most daily, 5 s later) and Settings Sync's automatic runs.
5. At exit, the quit method (`MainWindow.quit`) asks before unsaved typing is lost, then waits for the
   last settings save and the Debug Log's last lines off the FX thread; `Startup.Running.close`, which
   `App.stop` calls, makes the last save again on the FX thread, the one wait Pauta's PERF-01 allows there,
   and closes the single-instance socket in the background. Every thread the app starts is a daemon.

The tests start the app through the same `Startup.prepare` and `Startup.start`, with a `FakeHost`.

## The UI package

- `AppServices`: the long-lived parts shared by every window: `ConfigStore`, `CommandRegistry`,
  effective `Keybindings`, `MessageLog`, `UpdateCoordinator`, `SyncController`, and the `Host` (browser,
  file choosers, exit), the one seam the tests replace.
- `AppMenus`: the menu bar, every row a command with its key binding drawn beside it (Emacs
  sequences too). No accelerators: see the class comment for why. Two tables, the full one and Simple
  mode's shorter one (`menus(simple)`); `setSimple` rebuilds the bar only when the mode changes (MENU-04).
- `AppToolbar`: the main toolbar, every button a command that a menu also lists. Two containers in
  one row: the icon cluster (a `ToolBar`, in groups, overflowing into its chevron) and the pinned tail
  (the Simple mode toggle, then Settings). Buttons are built by `Icons.toolbarButton`, named by
  `Icons.withChord` of the title and the chord, and look pressed (`:open`) while their overlay is up
  (`OverlayToggles`) or, for the Simple mode toggle, while the mode is on. Simple mode hides the groups
  `SIMPLE_HIDES` names, the gaps they leave go with them, and the row keeps its height. The class comment
  maps each Toolbars and tooltips rule to the code that meets it. **Put your app's commands in
  `CLUSTER`, and the groups a newcomer can do without in `SIMPLE_HIDES`.**
- `MainWindow`: the top band (the title bar, a JavaFX `HeaderBar` holding the menus and the title, where
  the platform supports extended windows and the menus are in the window; a plain menu row otherwise,
  and the screen menu bar on macOS; then the toolbar), the tool windows round the document tabs,
  `StatusBar`, and an `OverlayHost` (the root `StackPane`) that carries the in-scene overlays. The
  Welcome tab's page is `placeholder()`. **Put your app's documents in tabs, and its panels in tool
  windows.**
- The tabs (Pauta's Tabs and tool panels, after Editora): `DocumentTabs` owns the `TabPane`, the pinned
  group, the closed list and the window title; `TabStrip` is the pure arithmetic (bulk closes skip pinned
  tabs, the pin boundary, where a dragged tab lands); `TabHeaders` draws each header (the header is the
  title, carrying the unsaved, pinned and read-only states, and is the drag handle); `ClosedTabs` is
  Reopen Closed Tab's list; `TabContextMenu` the per-tab commands; `Switcher` the tab switcher;
  `TabGitStatus` the Git colors. The documents are `SampleFiles` in a `DocumentView`: placeholders that
  exist only in memory. **Replace them with your files.**
- The tool windows: `ToolWindowManager` lays out a stripe on each of three sides and the open windows
  (at most two a side) between the stripes and the tabs, keeps the arrangement in `settings.json`
  (`window.toolWindows`), and lines the side stripes' first icon up with the documents' first line of
  text. A `ToolWindow` builds its content on first open; `ToolWindowVisibility` and `ToolWindowDock` are
  its pure decisions. The three windows' content is `SamplePanels` (Documents, Outline, Output).
  The class comments map each rule to the code that meets it.
- `Commands`: every command, in one place. Menus, buttons, the palette and keys all run them by id. The
  text commands (`edit.cut`, `edit.copy`, `edit.paste`, `edit.selectAll`) act on the text that has the
  keyboard; every editor and console has a `CommandMenu` of them in place of JavaFX's stock one.
- `FindBar`: one find bar over a text area, in the window: each document's (`find.show`, `C-s` /
  `Ctrl/Cmd+F`), the Output's (`output.find`) and the Debug Log's (`debugLog.find`). Each searches off the
  FX thread and steps through its own commands.
- `Dialogs.styled`: every dialog goes through it: the stylesheets, one button order with Cancel first on
  every OS, and JavaFX's stock buttons worded from the catalog. `Confirm` asks before anything is lost,
  with Cancel as the default; `MainWindow.quit` is the one quit method and asks before typing is lost.
- `UiZoom` and `Themes.zoom`: the UI zoom. The stylesheets are written at 100 % in pixels; at another zoom
  every window gets a copy with each pixel size scaled (made off the FX thread), and the few sizes the code
  fixes are bound to `UiZoom.factor`. **Size new controls in the stylesheet, or bind them with
  `UiZoom.bind`, never in plain pixels.**
- `PickerKeys`: the keys of every list overlay, the palette, the pickers and the tab switcher: the keymap's
  `nav.*` chords and the fixed arrows, page keys and Ctrl/Cmd+Home/End, and the palette's help chord.
  `TextInputKeys`: the keymap's caret and editing chords a text field keeps.
- `DoctorWindow` over `sync/Doctor`: the tools and the folder the app depends on, each checked.
  `sync/ShellPath`: the login shell's `PATH`, which git and gh run with. `sync/AccountPin`: the GitHub account
  a sync clone uses.
- `FxSampler` (`-Dexampleapp.fxSampler=<ms>`): logs the FX thread's stack when it stalls. `StartupMarks`: the
  start's phase marks, in the Debug Log and for `scripts/measure-startup.sh`. `StyleCompiler`: the release
  build's `.bss` stylesheets, which `Themes` prefers at 100 % zoom.
- `MacAppMenu`: About and Settings in macOS's application menu, through Glass (only macOS's JavaFX has
  it; the launchers export `com.sun.glass.ui.mac` to the app on macOS alone).
- `Picker`: the one list chooser; the Command Palette is a `Picker` over commands, and the theme,
  keymap and language pickers are `Picker`s too.
- `KeyDispatcher`: key presses to commands through the active bindings, with `C-x`-style prefixes
  and a which-key hint in the status bar.
- `SettingsWindow` + `SettingsSearch`: pages, search, live apply.
- `AboutDialog`, `PautaWindow`, `ReportProblemWindow`, `DebugLogWindow`, `MessageLogView`: the family templates.

## How things flow

- **A command**: `CommandRegistry.run(id)` → the action. Its title and description come from
  `command.<id>` / `command.<id>.desc`. An unavailable command explains itself in the palette.
- **A setting**: a control or a command calls `store.update(s -> …)`, which saves `settings.json`
  atomically and notifies every listener: `MainWindow` re-applies theme, toolbar, tab bar, status bar and Simple mode, the Settings
  window reloads its controls, `AppServices` rebuilds the key bindings.
- **A message**: `services.echo("status.key", args)` → `MessageLog` → the status bar's echo line.
  Severity comes from the key's name. Logging goes to `DebugLog` (memory + session file).

## Config folder

`~/.exampleapp` (`--config-dir`, then `--dev` (`~/.exampleapp-dev`), then `EXAMPLEAPP_CONFIG_DIR`, then the
default): `settings.json` (preferences only), `session.json` (the window's place, the tool windows and the
open documents: Pauta SET-08, WIN-08), `exampleapp-session.log` (+ `.prev.log`), `instance.sock`, `sync/`
(the clone, `base.json`, and `account.txt`, the GitHub account pinned to the clone: SET-18), `backups/`
(`settings-<time>.json`, kept before a sync replaces your settings: SET-20) and the backups of files that
could not be read (`settings.json.v<n>.bak`, `settings.json.broken.bak`, the same for `session.json`). Every JSON file there has a `schemaVersion` and a
migration step per version: `settings.json` (`Settings`, `Migrations`) and Settings Sync's
`keybindings.json` and `base.json` (`SyncData`). A sync file from a newer build is refused, never
overwritten.
