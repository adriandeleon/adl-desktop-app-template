# Pauta audit

ExampleApp at 4aa2356, audited against [Pauta 6.0.0](https://pauta-project.dev/6.0.0/) on 2026-11-11. Scope: every rule. Rules from https://pauta-project.dev/6.0.0/rules.json.

|        | pass | fail | manual | not-checked | pending | deviation | not-applicable | app-only | limited |
|--------|-----:|-----:|-----:|-----:|-----:|-----:|-----:|-----:|-----:|
| MUST | 174 | 0 | 7 | 0 | 0 | 0 | 0 | 5 | 9 |
| SHOULD | 155 | 0 | 25 | 0 | 0 | 0 | 0 | 4 | 17 |
| MAY | 3 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 3 |

A failed MUST blocks a release. A `manual` or `not-checked` MUST does not: it is reported until someone settles it.

## Failed MUSTs

None.

## Problems with pauta.json

None.

## Other failures

| ID | Level | Status | Evidence | Rule |
|----|-------|--------|----------|------|
| DOCS-11 | MAY | fail | MAY: no 'Help for' link on Settings pages and no page-to-slug map (no such key or map in SettingsWindow.java or messages.properties). | https://pauta-project.dev/6.0.0/documentation/#docs-11 |
| WIN-10 | MAY | fail | MAY: only Simple mode (Settings.simpleMode, Commands.java:74-77); no Zen or Expert command and no truth table of chrome per mode. | https://pauta-project.dev/6.0.0/window-shell/#win-10 |
| TBAR-09 | MAY | fail | The main toolbar's layout is fixed (src/main/java/com/example/app/ui/AppToolbar.java:64-66 CLUSTER, :90-118): no reorder, remove or restore of its buttons, nothing saved in Settings. (Stripe buttons can be reordered and hidden, but the toolbar cannot.) MAY: no consequence. | https://pauta-project.dev/6.0.0/toolbars-and-tooltips/#tbar-09 |

## Manual checks

| ID | Level | Status | Evidence | Rule |
|----|-------|--------|----------|------|
| PAUTA-08 | MUST | manual | Read each reason in Help -> Pauta Conformance as a user would; the source refutes none: every reason in pauta.json is one sentence with no rule ID or class name (held by PautaConformanceTest.java:259-261) ; not recorded | https://pauta-project.dev/6.0.0/conformance/#pauta-08 |
| PAUTA-11 | SHOULD | manual | The change raising pauta.version (pom.xml:34, uncommitted) also replaces src/test/resources/pauta-rules.json (identical to 6.0.0 rules.json) and .claude/skills/pauta-audit/SKILL.md (identical to the 6.0.0 skill), touches pauta.json, README.md and AGENTS.md, and leaves no checked entry; confirm it lands as one change ; not recorded | https://pauta-project.dev/6.0.0/conformance/#pauta-11 |
| DOCS-08 | SHOULD | manual | Open Help → Keyboard Shortcuts (help.keyboardShortcuts, src/main/java/com/example/app/ui/AppMenus.java:144), search for a command and find its key in Settings → Keymap; not recorded | https://pauta-project.dev/6.0.0/documentation/#docs-08 |
| DOCS-16 | MUST | manual | Read the most recently merged feature pull requests on GitHub and check each touches CHANGELOG.md, the guides it concerns and TODO.md, and names the website's PR where a user sees the change; stated in CONTRIBUTING.md:38-41, AGENTS.md:60-62; not recorded | https://pauta-project.dev/6.0.0/documentation/#docs-16 |
| QUAL-10 | SHOULD | manual | Break what a drift or parity test guards (e.g. a floor in docs/testing.md for BuildHygieneTest.java:152, a NOTICE entry for NoticeCoverageTest.java:64), run it and see it fail; not recorded | https://pauta-project.dev/6.0.0/quality/#qual-10 |
| PERF-18 | SHOULD | manual | Read the repository's recent merged pull requests that touch a hot path and find the cost stated in each; not recorded | https://pauta-project.dev/6.0.0/performance/#perf-18 |
| PERF-22 | SHOULD | manual | Read the merged pull request that fixes each suspected regression and find a before and after number; not recorded | https://pauta-project.dev/6.0.0/performance/#perf-22 |
| KBD-12 | SHOULD | manual | Open Settings > Keymap, search, and bind a chord that is taken: one row per command with its keys (SettingsWindow.java:268-300), filtered by the Settings search, conflict tooltip and invalid style (SettingsWindow.java:363-369); not recorded | https://pauta-project.dev/6.0.0/keyboard/#kbd-12 |
| KBD-14 | SHOULD | manual | Press C-x in the Emacs keymap and wait: the status bar lists the next keys (KeyDispatcher.java:46-55, Keybindings.continuations); not recorded | https://pauta-project.dev/6.0.0/keyboard/#kbd-14 |
| KBD-15 | MUST | manual | Tab through every window and dialog and work each control with Space/Enter. Source finds no mouse-only control: toolbar buttons (Icons.java:424-427), tool-window header buttons (ToolWindowManager.java:890-905) and the status bar's update link (StatusBar.java:62-64) run commands; status labels run view.messageLog (StatusBar.java:23-24,59-61); find-bar buttons (FindBar.java:95-104) per FIND-16; picker list driven from its field (Picker.java:119-122) ; not recorded | https://pauta-project.dev/6.0.0/keyboard/#kbd-15 |
| ACC-05 | SHOULD | manual | Look at a grayscale screenshot of each window: Git status now also shows its letter on tabs (src/main/java/com/example/app/ui/TabHeaders.java:63-69) and Documents rows (SamplePanels.java:66-70); Doctor pills carry words (DoctorWindow.java:63-73); grayed menu rows keep a tooltip; not recorded | https://pauta-project.dev/6.0.0/accessibility/#acc-05 |
| ACC-07 | SHOULD | manual | docs/release.md:20-21 has the step naming Narrator and VoiceOver; the pass itself is a person's, recorded as a checked entry with the version ; not recorded | https://pauta-project.dev/6.0.0/accessibility/#acc-07 |
| TBAR-06 | SHOULD | manual | Look at the top and bottom bands in light and dark: menu row and toolbar share -color-bg-subtle in .chrome-band with one rule under the whole band (app.css:327-337, src/main/java/com/example/app/ui/MainWindow.java:116-118); the tab strip below it is on the theme's -color-bg-default (editora-light.css:4291-4299), so check whether that seam reads as a second band; bottom: the status bar's top border is gone (app.css:59-64) and the bottom stripe sits on -color-bg-subtle (app.css:512-534). Not recorded; not recorded | https://pauta-project.dev/6.0.0/toolbars-and-tooltips/#tbar-06 |
| TBAR-16 | SHOULD | manual | Look at the bar's right end with Simple mode off, then on: Simple mode toggle then Settings in the tail (AppToolbar.java:71,104); not recorded | https://pauta-project.dev/6.0.0/toolbars-and-tooltips/#tbar-16 |
| TBAR-19 | SHOULD | manual | Turn Simple mode on and off and watch the top of the tabs: the row keeps steadyHeight (AppToolbar.java:110-121,221-231); not recorded | https://pauta-project.dev/6.0.0/toolbars-and-tooltips/#tbar-19 |
| TABS-07 | SHOULD | manual | Drag a tab along the strip and watch the dimmed header and the accent line: src/main/java/com/example/app/ui/TabHeaders.java:104-144, app.css:461-471; not recorded | https://pauta-project.dev/6.0.0/tabs-and-panels/#tabs-07 |
| TABS-09 | SHOULD | manual | Open the switcher with several tabs; move with arrows and Ctrl+N/P, close with Backspace/Delete/Ctrl+D, cancel with Esc/C-g, release Ctrl to activate; width fixed per open: src/main/java/com/example/app/ui/Switcher.java:68-120 (line chords are hard-coded, not read from the keymap); not recorded | https://pauta-project.dev/6.0.0/tabs-and-panels/#tabs-09 |
| TABS-17 | SHOULD | manual | Drag a stripe button to the opposite stripe and watch the feedback and the dashed empty stripe: src/main/java/com/example/app/ui/ToolWindowDock.java, ui/ToolWindowVisibility.java:22; not recorded | https://pauta-project.dev/6.0.0/tabs-and-panels/#tabs-17 |
| TABS-23 | SHOULD | manual | Switch between tabs with the Outline open and see it follow; no Git window in the template: src/main/java/com/example/app/ui/MainWindow.java:143-144, :196-200; not recorded | https://pauta-project.dev/6.0.0/tabs-and-panels/#tabs-23 |
| MENU-17 | SHOULD | manual | On Windows or Linux, in CUA and Emacs, reach the first menu with F10 or a lone Alt and walk the bar with the arrows; no keymap binds F10 (keymaps/cua.json, emacs.json); menu bar in the HeaderBar (MainWindow.java:109-115); not recorded | https://pauta-project.dev/6.0.0/menus/#menu-17 |
| MENU-08 | MUST | manual | Open a right-click menu, press Escape and type: the keys reach the control that had focus (stock ContextMenu, CommandMenu.java:81-84); not recorded | https://pauta-project.dev/6.0.0/menus/#menu-08 |
| MENU-10 | SHOULD | manual | In a list, select one row and right-click another: its menu acts on the clicked row (CommandMenu.java:92-98; tabs DocumentTabs.java:330); in the editor (its own command menu in place of the stock one, DocumentTabs.java:71-74,283) check the caret moves to a right-click outside the selection; not recorded | https://pauta-project.dev/6.0.0/menus/#menu-10 |
| SET-02 | MUST | manual | Open Settings: sidebar of pages with icons left, page right, search on top: src/main/java/com/example/app/ui/SettingsWindow.java:166-186, page icons :793 (search sits over the sidebar column, as in Editora); not recorded | https://pauta-project.dev/6.0.0/settings/#set-02 |
| SET-15 | MUST | manual | Paste https://user:token@host/repo.git into Settings → Sync: refused with the credential-helper/SSH message: src/main/java/com/example/app/sync/SyncEngine.java:62-83, ui/SettingsWindow.java:469-480, messages.properties:293; not recorded | https://pauta-project.dev/6.0.0/settings/#set-15 |
| SET-21 | SHOULD | manual | Point Sync at a missing repository: pill, echo and Message Log show the failure: src/main/java/com/example/app/ui/SettingsWindow.java:572-573 (pill), ui/SyncController.java:165 echo (AppServices.echo writes to the Message Log); not recorded | https://pauta-project.dev/6.0.0/settings/#set-21 |
| DIAG-07 | SHOULD | manual | Open the Doctor (Settings → Advanced or help.doctor) with git missing and find its row Failed with what to install and a link: src/main/java/com/example/app/sync/Doctor.java:47-71, src/main/java/com/example/app/ui/DoctorWindow.java:55-90 (gh missing shows Warning, not Failed); not recorded | https://pauta-project.dev/6.0.0/diagnostics/#diag-07 |
| CLI-04 | SHOULD | manual | Launch with --no-session and find only what the command line names: CommandLine.java:52 sets noSession, MainWindow is built with restoreSession=false (Startup.java:142) and DocumentTabs skips the last session's documents (DocumentTabs.java:102-114) and opens only the Welcome page, the samples being a first run's alone (DocumentTabs.java:115-124); not recorded | https://pauta-project.dev/6.0.0/command-line/#cli-04 |
| INTL-04 | SHOULD | manual | Run each language at 1024 px wide, open each window and Settings page, find no row cut off: src/main/java/com/example/app/ui/SettingsWindow.java:607-650 (rows), messages_de.properties. Not recorded; not recorded | https://pauta-project.dev/6.0.0/i18n/#intl-04 |
| PWIN-02 | MUST | manual | Open the Pauta window and compare it with pauta.json entry by entry (edition, conformance sentence, counts by kind incl. app-only, ID, level, kind, reason, link): src/main/java/com/example/app/ui/PautaWindow.java:53-144. Not recorded; not recorded | https://pauta-project.dev/6.0.0/templates/pauta-window/#pwin-02 |
| PWIN-08 | SHOULD | manual | With an empty exceptions array, open the Pauta window: src/main/java/com/example/app/ui/PautaWindow.java:104-108 pauta.none names the app and edition; copy still works (:213-217); not recorded | https://pauta-project.dev/6.0.0/templates/pauta-window/#pwin-08 |
| CONF-07 | SHOULD | manual | Close a clean tab and reopen it with Reopen Closed Tab: src/main/java/com/example/app/ui/DocumentTabs.java:204, 268; not recorded | https://pauta-project.dev/6.0.0/templates/confirm-dialog/#conf-07 |
| SYNC-01 | SHOULD | manual | Open Settings, search "sync", land on the Sync page (key bindings are the data carried): src/main/java/com/example/app/ui/SettingsWindow.java:54,404-454, messages.properties:249; not recorded | https://pauta-project.dev/6.0.0/templates/settings-sync/#sync-01 |

## Not checked

None.

## Exceptions

| ID | Level | Status | Evidence | Rule |
|----|-------|--------|----------|------|
| PAUTA-13 | MUST | app-only | pauta.json: Each app's user guide has its own page listing its conformance and exceptions. | https://pauta-project.dev/6.0.0/conformance/#pauta-13 |
| PAUTA-14 | SHOULD | app-only | pauta.json: Each app's website makes its conformance page from the app's own list of exceptions. | https://pauta-project.dev/6.0.0/conformance/#pauta-14 |
| REPO-01 | MUST | app-only | pauta.json: Each app's README shows its own screenshots; the template's are placeholders. | https://pauta-project.dev/6.0.0/repository/#repo-01 |
| DOCS-01 | MUST | app-only | pauta.json: The user guide lives on each app's own website, which a template does not have. | https://pauta-project.dev/6.0.0/documentation/#docs-01 |
| DOCS-02 | MUST | app-only | pauta.json: The user guide's pages under /docs/ belong to each app's own website. | https://pauta-project.dev/6.0.0/documentation/#docs-02 |
| DOCS-03 | SHOULD | app-only | pauta.json: Each app's website serves its own user guide for each version. | https://pauta-project.dev/6.0.0/documentation/#docs-03 |
| DOCS-04 | SHOULD | app-only | pauta.json: Each app's website makes its own command and settings pages and What's new from the app. | https://pauta-project.dev/6.0.0/documentation/#docs-04 |
| DOCS-18 | SHOULD | app-only | pauta.json: Each app captures its own screenshots from itself; the template's are placeholders. | https://pauta-project.dev/6.0.0/documentation/#docs-18 |
| REL-19 | MUST | app-only | pauta.json: Each app's repository requires its own format and test checks before a change is merged. | https://pauta-project.dev/6.0.0/release/#rel-19 |
