# Conventions

## Commands

- Every user-facing action is a command, registered in `ui/Commands`. Ids are dotted and grouped by
  area: `app.settings`, `view.toggleStatusBar`, `help.reportProblem`.
- A command that cannot run says why: register it with an `unavailable` supplier returning
  `tr("palette.disabled.<reason>")` ("Unavailable — what is missing. What to do first."). It stays
  listed, greyed, and its key binding echoes the reason.
- Key bindings live in `src/main/resources/com/example/app/keymaps/<id>.json`. `Shortcut` is Cmd on
  macOS and Ctrl elsewhere; Emacs notation (`C-x C-c`, `M-x`) works in any keymap.

## Strings

- `Messages.tr("key", args…)` is the only way text reaches a control. Six catalogs, all with every
  key (`MessagesTest`). Translate for real.
- A value with a placeholder (`{0}`) is a `MessageFormat` pattern: double its apostrophes (`n''est`).
  A value without one writes them single. Counts and ports use `{0,number,#}`.
- Status keys name their severity: ending in `Failed` or `Error` is an error, containing `cannot` or
  `invalid` a warning.
- Not translated: `--help`, `--version`, technical identifiers.

## Adding a setting

Five places, in one pull request:

1. **Model**: a public field with its default in `config/Settings`, `SCHEMA_VERSION` bumped, and a
   step in `config/Migrations` (the identity step for a purely additive field).
2. **Control**: built once in `SettingsWindow`, writing the field through `store.update`.
3. **Row** on its page, with search keywords (`row(card, page, "settings.x", control, "keywords")`).
4. **Load line** in the page's loader, setting the control from the model; safe to run any time.
5. **Palette command** in `ui/Commands` that flips or picks the same field, with strings in every
   catalog. Machine-local values (window placement, paths) never sync.

## Null safety

Every package has a `package-info.java` with `@NullMarked`. Mark what may be null with
`org.jspecify.annotations.Nullable` and handle it; NullAway (Error Prone) fails the build otherwise.
Do not suppress a report.

## UI

- Every scene and dialog goes through `Themes.style(scene)` / `Dialogs.styled(dialog, owner)`: a
  dialog is its own scene and inherits neither the stylesheet nor the Inter font.
- Overlays go in the window's `OverlayHost`, never a `javafx.stage.Popup`.
- CSS uses AtlantaFX tokens (`-color-fg-muted`, `-color-danger-fg`…), never hex. Readable text uses
  `-color-fg-muted` or stronger.
- On/off rows use `SettingSwitch` (keyboard-operable), not a bare `ToggleSwitch`.
- Platform differences live behind one check (`AppInfo.isMac()`, `Host`), never scattered.

## Logging and failures

`java.util.logging` (`Logger.getLogger(X.class.getName())`): the Debug Log captures INFO and above,
in memory and in the session file. User-facing failures also go to the status bar. Nothing fails
silently; nothing shows a stack trace dialog.
