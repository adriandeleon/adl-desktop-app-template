# Changelog

All notable changes to ExampleApp are documented in this file.

The format is based on [Keep a Changelog 1.1](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each entry opens with a bold
one-line summary written for users.

## [Unreleased]

### Added

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
  rules (*Working toward Pauta 4.1.0* while the conformance is Partial), a *Pauta conformance* link opens
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
  first. The skill is the copy Pauta 4.3.1 serves, word for word.
- **`AGENTS.md` points coding agents at the app's own Pauta edition.** A Pauta section gives the edition's
  `llms.txt` and `llms-full.txt`, says to cite rules as "KBD-07 (Pauta 4.1.0)" and to read the numbered
  edition, never `latest/`.

### Changed

- **The `pauta-audit` skill is Pauta 5.0.0's.** It now decides how to audit and how to write the report for any
  edition, says which `rules.json` fields each edition has, asks which edition to use when the app names none, and
  treats a command id or key that a rule's statement names as required. The app is still built to Pauta 4.1.0;
  the skill audits it against that edition.
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
