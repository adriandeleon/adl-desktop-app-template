# Changelog

All notable changes to ExampleApp are documented in this file.

The format is based on [Keep a Changelog 1.1](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each entry opens with a bold
one-line summary written for users.

## [Unreleased]

### Added

- **The documents you had open come back.** Quitting remembers the open documents and the selected one, and
  the next start opens them again; `--no-session` starts with the Welcome page and leaves them for next time
  (Pauta WIN-08, CLI-04). The window's place and what was open live in `session.json`, apart from your
  preferences in `settings.json` (SET-08).
- **Zoom the documents' text on its own:** Zoom Text In, Zoom Text Out and Actual Text Size, from the
  Command Palette or `Ctrl/Cmd+=`, `-` and `0` (`C-=`, `C--`, `C-0` in Emacs) or Settings → Appearance → Text
  size, kept across launches; the window's own zoom stays apart (Pauta WIN-09).
- **IntelliJ, VS Code and Sublime Text keymaps,** each with its macOS form, beside CUA and Emacs (Pauta
  KBD-10).
- **A High contrast theme,** chosen in Settings, and followed on its own while Windows' high-contrast mode
  is on (Pauta ACC-09).
- **Next Region and Previous Region** (`F6`, `Shift+F6`; `C-x o`, `C-x O` in Emacs) move the keyboard between
  the documents and each open tool window (Pauta ACC-06).
- **A Doctor** (Settings → Advanced, or the palette) checks git, gh, Java, JavaFX and the config folder, and
  says what to do about anything that fails (Pauta DIAG-07).
- **The palette opens a command's help page** with the first of `Ctrl+H`, `F1` and `Shift+F1` your keymap
  leaves free, and its key legend is your keymap's (Pauta PAL-08, PAL-09).
- **Zoom the whole window.** View → Zoom In, Zoom Out and Actual Size (`Ctrl+Alt+=`, `Ctrl+Alt+-` and
  `Ctrl+Alt+0`, `Cmd+Option` on macOS; `Ctrl/Cmd+=` stays free for zooming a document's text), or Settings →
  Appearance → Zoom, from 80 % to 200 %: menus, toolbar, tabs, status bar and dialogs grow and shrink together, at once, and the size is
  kept (Pauta WIN-12, MENU-16).
- **Find in a document and in the Output.** Edit → Find… (`Ctrl/Cmd+F` or `F3`, `Cmd+G` on macOS, `C-s` in
  Emacs; again for the next match) opens a find bar over the open document, and Output: Find… one over the Output
  console: they search as you type, count the matches, and Enter and Shift+Enter step (Pauta FIND-01,
  FIND-04).
- **An Edit menu, and right-click menus on the document, the Output and the Documents list,** with Cut,
  Copy, Paste and Select All as commands the palette reaches too, Output: Clear and Documents: Open
  (Pauta MENU-05).
- **Quit asks before your typing is lost.** Quitting with changes in a document asks first, by name;
  Cancel keeps the app open (Pauta WIN-13).
- **About and Settings in macOS's application menu,** with Settings' keys in its title (Pauta ABOUT-01,
  WIN-15).
- **Help → Keyboard Shortcuts** opens the searchable key binding list (Pauta DOCS-08).
- **A console launcher on Windows,** `exampleapp-cli`, so `--help`, `--version` and `--report` print in the
  console they were typed in (Pauta CLI-11). `-V` works as `--version` (CLI-13).
- **Every menu row has an icon,** drawn for the commands that had none (Pauta MENU-14), and a file's icon
  follows its type (ICON-23).
- **A Command Palette that finds and runs every command.** `Ctrl/Cmd+Shift+P`, or `M-x` in the Emacs
  keymap; unavailable commands are listed grayed with the reason.
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
- **About names the Pauta edition and how fully the app conforms to it.** The edition's link opens its
  rules (*Working toward Pauta x.y.z* while the conformance is Partial), a *Pauta conformance* link opens
  the Pauta window, and *Copy details* and `--version` carry `Pauta:` and `Pauta conformance:` lines.
- **A Pauta window lists every rule the app does not meet.** Help → Pauta Conformance, or the Command
  Palette: the edition, the conformance (Complete, Core or Partial), and each exception with its reason
  and its issue, pending first. *Copy as text* copies the list as Markdown.
- **Import and Reset ask before they overwrite.** Importing a configuration and resetting a key binding
  you set now name what is replaced, and Escape cancels.
- **Help → Documentation and What's New.** Documentation opens this version's user guide on the
  website, and What's New this build's release notes; both are on the start page too.
- **Every button is a command.** Copy and Clear in the Message Log, the Debug Log's buttons and About's
  Copy details run from the Command Palette and can be bound to keys. The palette lists every command,
  itself included, and Help follows the family's order.
- **Right-click menus.** Message Log rows and the Debug Log have menus of their commands; the Menu key
  and Shift+F10 open the selected row's, and a click anywhere else closes it.
- **Import replaces only what you tick.** Importing a configuration lists its parts (preferences, key
  bindings, sync setup, other files); the ones you untick stay as they are. A zip that is not an export,
  too large or from a newer version is refused before anything changes.
- **A palette command for every setting.** Change Key Binding…, and Settings Sync: Set Repository…, Set
  Branch… and Set Interval… prompt for the value and refuse an invalid one.
- **Find in the Debug Log.** `Ctrl/Cmd+F` there, or Debug Log: Find… from the palette, opens a find bar
  that searches as you type, counts the matches and steps with Enter and Shift+Enter.
- **Key-binding rows have a right-click menu,** Change and Reset, also in the palette for the selected
  row; a Settings page's row in the sidebar has one with that page's commands.
- **A macOS CUA keymap.** On a Mac the CUA keymap is `cua.mac.json`, with Cmd in place of Ctrl.
- **Faster start-up from an AOT cache.** Installed builds carry a JDK 25 ahead-of-time cache; the first
  window appears about a third sooner.
- **Settings Sync can create its repository.** Settings → Sync → Create on GitHub… makes a private
  repository with your GitHub CLI (`gh`) and connects to it.
- **A `pauta-audit` skill for coding agents.** Ask for "audit against Pauta" and it checks every rule of the
  app's Pauta edition, writing `docs/pauta-audit.json` and `docs/pauta-audit.md` with a status (pass, fail,
  manual, pending, deviation, not-applicable, app-only or limited) and evidence for each rule, failed MUSTs
  first. The skill is the copy Pauta 6.0.0 serves, word for word.
- **`AGENTS.md` points coding agents at the app's own Pauta edition.** A Pauta section gives the edition's
  `llms.txt` and `llms-full.txt`, says to cite rules as "KBD-07 (Pauta 6.0.0)" and to read the numbered
  edition, never `latest/`.
- **A toolbar under the menus.** The Command Palette, Theme, Keymap, Sync Now and the Message Log are one
  click away, with Settings at the right end. Each button's tooltip names its command and its key in your
  keymap; a button looks pressed while what it opened is showing, and Sync Now waits until sync is set up.
  View → Toggle Toolbar, its right-click menu or Settings → General hides it, and the choice is kept.
  Sync Now is in the File menu too.
- **Tabs and tool windows.** Sample documents open in tabs beside the Welcome page; pin a tab to keep it
  at the front and out of every bulk close, close others, unmodified or to either side, reopen the last
  closed file where its caret was, drag tabs to reorder them, and switch with Window → Switch Tab
  (`C-x C-b` in the Emacs keymap). A tab shows when it is unsaved, pinned or read-only, and its file's Git
  status, and the window title names the file. Around the tabs, three tool windows (Documents, Outline
  and Output) dock on the left, right and bottom: a stripe button or `Alt+1`…`Alt+3` (`M-1`… in Emacs)
  opens each; drag a button to another side, open two on one side, float, maximize or resize one from the
  keyboard, and they come back as you left them. Every action is in the Window menu and the Command
  Palette. View → Toggle Tab Bar hides the tab strip.
- **Simple mode, for a quieter window.** One switch shortens every menu to its essentials and leaves the
  toolbar with the Command Palette, the Simple mode toggle and Settings; every command stays in the palette.
  The toggle sits just before Settings and looks pressed while the mode is on, so the way out is where the
  way in was. Turn it on or off from that button, View → Toggle Simple Mode, the toolbar's right-click menu,
  Settings → General or the Command Palette; the choice is kept, and the toolbar keeps its height, so
  nothing under it moves.

### Changed

- **ExampleApp is built to Pauta 6.0.1,** and is still Complete. The patch edition rewords one MAY, WIN-10
  (an Expert mode now keeps only the status bar), which the template does not have; the copy of the
  edition's rules is 6.0.1's, and the `pauta-audit` skill is unchanged.
- **ExampleApp's Pauta conformance is Complete:** every MUST and SHOULD of Pauta 6.0.0 a template can meet
  is met; what is left is each app's own website, screenshots and repository settings (`app-only`).
- **The palette and every picker move with your keymap's keys** (`C-n`, `C-p`, `C-v`, `M-v` in Emacs) and
  with `Ctrl/Cmd+Home` and `End`; the tab switcher moves the same way. Pickers say what you are choosing and
  open on the current value (Pauta PAL-06, QOPEN-02, QOPEN-04, QOPEN-06, QOPEN-12).
- **The palette finds a command by the words of its id, not its description,** and a query that spells a
  title puts that command first (Pauta KBD-06).
- **The Welcome page is in the Help menu** (Help → Welcome Page, `view.welcome`), shows at start when there
  is no session to restore, is rebuilt each time it is shown, and is no pinned tab: its menu is Close alone.
  Closing the last tab leaves a hint with the ways in and their keys (Pauta WELC-03, WELC-04, WELC-05,
  WELC-11).
- **A grayed menu row says why** in its tooltip, and choosing it says so in the status bar; rows that clear
  or reset are red (Pauta KBD-04, MENU-12).
- **A file's Git status shows as its letter** (M, A, U, R, D, C) on its tab and its Documents row, not by
  color alone (Pauta ACC-05).
- **Find starts where your caret was** when the bar opened, and the find field keeps your keymap's editing
  keys (`C-a`, `C-e`, `C-k`, `C-y`… in Emacs) (Pauta FIND-05, FIND-09).
- **Settings Sync remembers which GitHub account can read your repository,** for a computer signed in to two,
  and keeps a copy of your settings before a sync replaces them (Pauta SET-18, SET-20).
- **ExampleApp is built to Pauta 6.0.0,** and its conformance is now Core: it meets every MUST of the
  edition a template can, and the SHOULDs it does not yet are listed in the Pauta window (Help → Pauta
  Conformance). The `pauta-audit` skill and the copy of the edition's rules are 6.0.0's.
- **The template marks what only an app made from it can have.** `pauta.json` says `"template": true`, and
  the rules that need an app's own website, screenshots, repository settings or certificate are
  `app-only`; the Pauta window lists them under "For each app made from it". `scripts/rename.sh` sets
  `template` to `false`, after which `./mvnw verify` lists what the app inherited and the release build
  fails until each one is met or pending on an issue of the app's own (Pauta PAUTA-16). A `checked` list
  records the rules a person checked by hand, and the test fails one gone stale (PAUTA-18).
- **Dialogs put Cancel first, on every system,** and their buttons are in your language, not the
  system's; a question before something is lost opens with Cancel as the default, so Enter keeps what you
  have (Pauta WIN-11, CONF-13, CONF-14).
- **Menus in the title bar stays in Settings on every system,** grayed with the reason where the system
  can't draw it, instead of only saying so in its description (Pauta OS-01).
- **`--report` writes the report in the current folder,** or the temp folder when that one can't be
  written, and prints where (Pauta CLI-05).
- **`--dev` wins over `EXAMPLEAPP_CONFIG_DIR`,** as a flag typed for this run should (Pauta SET-07).
- **Keyboard focus is easier to see:** the ring is 2 px wide, just outside the control, and a text field's
  border widens instead (Pauta KBD-22). Menus have rounder cards with a deeper shadow and roomier rows
  (MENU-18), every tooltip waits the same 400 ms (TBAR-14), and the status bar no longer draws a line
  under the bottom tool stripe (TBAR-06).
- **In the Emacs keymap, `C-g` closes the palette, a picker or the Message Log;** in CUA, Esc does, as
  before (Pauta PAL-07, QOPEN-07, MLOG-03).
- **The tagline, counts and modifier keys read right in every language:** "1 file", not "1 files", and
  `Ctrl`, never "Strg" (Pauta INTL-07, INTL-12, INTL-13). With no language chosen, dates and numbers
  follow your system's region (INTL-14).
- **Screen readers hear more:** every control and menu row has a name, the status bar's message and badge
  are buttons, the palette announces the row the arrows move to, and a tab's tooltip names its file's Git
  status (Pauta ACC-01, ACC-02, ACC-08, TABS-08).
- **An available update stands out in About.** "Update available" is now amber, a color nothing else in About
  uses, instead of the accent every link wears (Pauta ABOUT-10).
- **The README points to the user guide on the website,** and the contributor docs say who they are
  for, that the code wins when they disagree with it, and that docs change in the same pull request.
- **Links to Pauta point to its new home, [pauta-project.dev](https://pauta-project.dev/).**
- **New default themes: Caret & Ink, the Pauta family palette, in light and dark.** They replace
  AtlantaFX Primer; System still follows your OS. Colored text and badges read at WCAG AA in both,
  and the keyboard focus ring at 3:1 on every surface.
- **Tooltips, menus and popups use Inter too,** not only windows and dialogs.
- **The Settings sidebar has line icons,** the same glyph each command will wear everywhere.
- **Every download is checked harder before a release publishes it.** The notarized macOS app is
  launched once before it is uploaded; the Linux downloads must be signed with the family's release
  key, now committed as `packaging/linux/signing-key.asc`; every file the release attaches has a build
  provenance attestation, and a test keeps the two lists the same. A dry run (Run workflow) now goes
  as far as JReleaser with `--dry-run` and publishes nothing. CI keeps the test reports of every run,
  coverage also has a 0.79 branch floor and a line floor per package, and tests fail when `NOTICE`
  misses a bundled library or `LICENSE` and `NOTICE` stop being packaged.

### Fixed

- **git and gh are found from your login shell's PATH** on macOS and Linux, so an app opened from the Finder
  finds Homebrew's tools (Pauta CLI-10).
- **Faster to start and to type in the palette:** release builds carry precompiled stylesheets, and the
  palette's rows reuse their nodes and draw their titles in runs (Pauta PERF-17, PERF-09, PERF-10).
  `scripts/measure-startup.sh` measures a start, phase by phase, and `-Dexampleapp.fxSampler=<ms>` logs what
  stalls the window (PERF-19, PERF-20).
- **The Windows installer is signed** once `WINDOWS_CERT_PFX` is set (Pauta REL-17).
- **Settings Sync writes the same bytes on Windows as on macOS and Linux.** The settings files and the synced
  keybindings end their lines in `\n` on every OS, and the sync clone checks files out as they were pushed,
  whatever the user's `core.autocrlf`.
- **The three-OS tests pass on macOS and Windows again.** The menu tests check the in-window menu bar only
  where the app draws one (on macOS the screen's menu bar is the system's), the tests that read files
  accept a Windows checkout's line ends, and the account-pinning test's fake `gh` is a Java program, so it
  runs on Windows too.
- **The window no longer waits on files while you work.** Logging, the extra font, the About and Pauta
  windows' images and the lookup of `git` and `gh` all moved off the window's thread; the outline is
  re-read once typing pauses instead of on every key, and the Output keeps its newest 1,000 lines
  (Pauta PERF-01, PERF-06, PERF-08, PERF-11).
- **Settings files are written whole or not at all,** an import and Settings Sync's own file included, and
  a sync file from a newer version, or one that can't be read, is kept aside instead of failing every
  sync (Pauta SET-22, SET-10, SET-11).
- **One class decides which system the app runs on** (`Os`), and tests can run it as any of the three;
  one checks that the commands, menus and Settings are the same on each (Pauta OS-02, OS-03).
- **The release build runs its tests without a virtual display,** as everywhere else (Pauta PLAT-11), and
  caches its build tools when it publishes (REL-25). Every thread the app starts is a daemon (PERF-04),
  and no test sleeps (QUAL-07).
- **Every menu draws at the same size, wherever you open it.** A right-click menu opened from the toolbar
  drew a third larger than the others; menus now always use Inter at 14 px, their key chords at 11 px
  (Pauta MENU-16).
- **Key chords in menus are drawn in the keyboard's own color,** a periwinkle that reads on both themes, in
  right-click menus as in the menu bar (Pauta MENU-15, ICON-06).
- **Starting and quitting never wait on the disk.** The settings file is read on a thread of its own while
  the window's toolkit starts, and the last save at quit is waited for off the window's thread; Settings
  Sync's first run now waits until the window has drawn (Pauta PERF-01, PERF-15, PERF-16).
- **Find in the Debug Log stays smooth on a long log.** It searches in the background, and typing one more
  letter narrows the matches it has instead of searching the whole log again (Pauta PERF-01, PERF-08).
- **A configuration import that finishes late can no longer undo a newer one.** Starting a second import,
  or quitting, drops the first one's result (Pauta PERF-03).
- **A hand-edited settings file can no longer carry bad values into the app.** A window size, a sync
  interval or a tool window's size out of range is corrected as the file is read, and an unknown keymap,
  language, key chord or branch falls back to its default (Pauta SET-11).
- **Settings Sync's files carry a version number.** An older copy upgrades what it finds, and a copy that
  finds files from a newer one stops and says so instead of writing over them (Pauta SET-09, PLAT-14).
- **A sync repository address with an access token as its user name is refused,** as one with a password
  already was, with the fix: a credential helper or an SSH address (Pauta SET-15).

- **Closing the app cancels a startup update check that is still waiting.** The check waits five seconds
  after the window paints; an app closed in that time still started it, and in the tests its thread could
  show up inside a later test (`WindowLifecycleFxTest`'s "threads left running: [update-check]").
- **`scripts/rename.sh` leaves the `pauta-audit` skill as Pauta serves it.** It used to rewrite the
  skill's example path to the new package, so a renamed app's copy no longer matched its edition's word for
  word; CI's rename check skips the skill too.
- **The status bar shows the newest message.** A message from background work that came first could
  arrive after a newer one and stay on screen as the latest; messages now keep the order they were said in.
- **Import Configuration is no longer undone by a save in flight.** A setting changed just before an
  import could be written after it and put the old values back; the import now waits for it.
- **Closing the window quits the way File → Quit does,** closing Settings, the Debug Log and any dialog
  first and saving the window's size and place.
- **The session log keeps its newest lines.** When it reaches 8 MB it starts over with a note and the
  newest lines, so the moments before a crash are always in it.
- **The first window comes up sooner.** The Message Log is built when first opened, and the update
  check and the monospaced font wait until the window has painted. Settings → Keymap lists the key
  bindings in a scrolling list that stays fast however many commands there are.
- **Logs say what they cut.** The Message Log and the Debug Log keep the newest entries with a note of
  how many older ones are gone, the session log file stops at 8 MB with a note, and the Debug Log now
  captures every level the app logs.
- **The window never waits on the disk or the network.** Settings are saved, logs exported and imports
  read off the interface thread; filters wait for typing to pause, and a burst of background messages
  redraws the status bar once. Oversized settings files and update answers are refused unread.
- **An invalid sync branch is refused.** A blank or malformed branch name is marked and never saved.
- **Settings search finds a row by its buttons' text,** and ranks a title above a keyword.
- **`EXAMPLEAPP_CONFIG_DIR` wins over `--dev`,** so the order is `--config-dir`, the variable, the default.
- **The sync status turns green only once a sync has succeeded;** while one runs it is amber.
- **A theme name this version does not know no longer resets all your settings.** The theme falls
  back to System and every other setting is kept.
