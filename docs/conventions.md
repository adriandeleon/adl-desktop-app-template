# Conventions

## Commands

- Every user-facing action is a command, registered in `ui/Commands`. Ids are dotted and grouped by
  area: `app.settings`, `view.toggleStatusBar`, `help.reportProblem`.
- A command that cannot run says why: register it with an `unavailable` supplier returning
  `tr("palette.disabled.<reason>")` ("Unavailable — what is missing. What to do first."). It stays
  listed, grayed, and its key binding echoes the reason.
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

## Docs in the same PR

A change that affects behavior updates, in the same pull request, never as a follow-up:

- `CHANGELOG.md`, under `## [Unreleased]`, an entry opening with a bold one-line summary for users;
- `TODO.md`, when it adds, finishes or drops planned work;
- the contributor guides in `docs/` it touches;
- the website's user docs (the `exampleapp-website` repository) for what a user sees.

A reviewer treats a feature pull request with no docs change as incomplete. When docs and code disagree,
the code wins and the docs are fixed in the same change.

## Adding a setting

Five places, in one pull request:

1. **Model**: a public field with its default in `config/Settings`, `SCHEMA_VERSION` bumped, and a
   step in `config/Migrations` (the identity step for a purely additive field).
2. **Control**: built once in `SettingsWindow`, writing the field through `store.update`.
3. **Row** on its page, with search keywords (`row(card, page, "settings.x", control, "keywords")`).
4. **Load line** in the page's loader, setting the control from the model; safe to run any time.
5. **Palette command** in `ui/Commands` that flips, picks or prompts for the same field (SET-06): a
   text value goes through `Prompts.text` with the same validator the Settings field uses, so an invalid
   value is never committed. Strings in every catalog. Machine-local values (window placement, paths) never sync.

## Null safety

Every package has a `package-info.java` with `@NullMarked`. Mark what may be null with
`org.jspecify.annotations.Nullable` and handle it; NullAway (Error Prone) fails the build otherwise.
Do not suppress a report.

## UI

- Every scene and dialog goes through `Themes.style(scene)` / `Dialogs.styled(dialog, owner)`: a
  dialog is its own scene and does not inherit the stylesheet. The Inter font needs nothing:
  `Themes.installUiFont` attaches `ui-font.css` to every window's scene, popups and tooltips included.
- Overlays go in the window's `OverlayHost`, never a `javafx.stage.Popup`.
- The themes are Caret & Ink, `styles/editora-light.css` and `editora-dark.css`, copied unchanged from
  Editora: update them only by copying Editora's again.
- CSS uses the theme's `-color-*` roles, never hex. A state (amber not durable yet, red broken, green
  verified, accent an offer) is a `-state-*` token from the `.root` block at the top of `app.css`,
  never a role directly. Readable text uses `-color-fg-muted` or stronger; ink on an `-emphasis` fill
  is `-color-fg-emphasis`, never white. `StyleContrastTest` holds all of this in both themes.
- Glyphs come in two families (`Icons`): `Icons.line` (stroked, `icon-line`) and `Icons.of` (filled,
  `toolbar-icon`), one class per node. A command's glyph comes from `Icons.forCommand(id)` /
  `Icons.commandGlyph(id)`, so one action wears one glyph everywhere.
- An icon-only button is built by `Icons.button`, or `Icons.toolbarButton` on a toolbar (out of the Tab
  order), and renamed with `Icons.name`: its tooltip and accessible text are one string (TBAR-01). A
  command's button is named `Icons.withChord(title, chord)` and named again on a keymap change (TBAR-03).
- A tab operation or a tool window action is a command like any other, registered in `Commands` with a
  Window menu row and a glyph. A tool window's command is `tool.<id>`, and its stripe button runs it; its
  content is built on its first open, never at start (TABS-14).
- On/off rows use `SettingSwitch` (keyboard-operable), not a bare `ToggleSwitch`.
- Platform differences live behind one check (`AppInfo.isMac()`, `Host`), never scattered.

## Logging and failures

`java.util.logging` (`Logger.getLogger(X.class.getName())`): the Debug Log captures INFO and above,
in memory and in the session file. User-facing failures also go to the status bar. Nothing fails
silently; nothing shows a stack trace dialog.
