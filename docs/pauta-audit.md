# Pauta audit

ExampleApp at 814867a, audited against [Pauta 6.1.0](https://pauta-project.dev/6.1.0/) on 2026-11-15. Scope: every rule. Rules from https://pauta-project.dev/6.1.0/rules.json.

|        | pass | fail | manual | not-checked | pending | deviation | not-applicable | app-only | limited |
|--------|-----:|-----:|-----:|-----:|-----:|-----:|-----:|-----:|-----:|
| MUST | 164 | 0 | 7 | 0 | 0 | 0 | 0 | 5 | 18 |
| SHOULD | 128 | 0 | 17 | 0 | 0 | 0 | 0 | 4 | 52 |
| MAY | 4 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 4 |

A failed MUST blocks a release. A `manual` or `not-checked` MUST does not: it is reported until someone settles it.

## Failed MUSTs

None.

## Problems with pauta.json

None.

## Other failures

| ID | Level | Status | Evidence | Rule |
|----|-------|--------|----------|------|
| DOCS-11 | MAY | fail | MAY: no 'Help for' link on Settings pages and no page-to-slug map (no such key or map in SettingsWindow.java or messages.properties). | https://pauta-project.dev/6.1.0/documentation/#docs-11 |
| WIN-10 | MAY | fail | MAY: only Simple mode (Settings.simpleMode, Commands.java:47-50); no Zen or Expert command and no truth table of chrome per mode. | https://pauta-project.dev/6.1.0/window-shell/#win-10 |

## Manual checks

| ID | Level | Status | Evidence | Rule |
|----|-------|--------|----------|------|
| PAUTA-08 | MUST | manual | Read each reason in Help -> Pauta Conformance as a user would; the source refutes none: every reason in pauta.json is one sentence with no rule ID or class name (held by PautaConformanceTest.java:259-261) ; not recorded | https://pauta-project.dev/6.1.0/conformance/#pauta-08 |
| PAUTA-11 | SHOULD | manual | The change raising pauta.version (pom.xml:34; template PR 45, c16a51f) also replaces src/test/resources/pauta-rules.json (identical to 6.1.0 rules.json) and .claude/skills/pauta-audit/SKILL.md (identical to the 6.1.0 skill), touches pauta.json, README.md and AGENTS.md, and leaves no checked entry; confirm it landed as one change ; not recorded | https://pauta-project.dev/6.1.0/conformance/#pauta-11 |
| DOCS-08 | SHOULD | manual | Open Help → Keyboard Shortcuts (help.keyboardShortcuts, src/main/java/com/example/app/ui/AppMenus.java:97), search for a command and find its key in Settings → Keymap; not recorded | https://pauta-project.dev/6.1.0/documentation/#docs-08 |
| DOCS-16 | MUST | manual | Read the most recently merged feature pull requests on GitHub and check each touches CHANGELOG.md, the guides it concerns and TODO.md, and names the website's PR where a user sees the change; stated in CONTRIBUTING.md:38-41, AGENTS.md:60-62; not recorded | https://pauta-project.dev/6.1.0/documentation/#docs-16 |
| QUAL-10 | SHOULD | manual | Break what a drift or parity test guards (e.g. a floor in docs/testing.md for BuildHygieneTest.java:152, a NOTICE entry for NoticeCoverageTest.java:64), run it and see it fail; not recorded | https://pauta-project.dev/6.1.0/quality/#qual-10 |
| PERF-18 | SHOULD | manual | Read the repository's recent merged pull requests that touch a hot path and find the cost stated in each; not recorded | https://pauta-project.dev/6.1.0/performance/#perf-18 |
| PERF-22 | SHOULD | manual | Read the merged pull request that fixes each suspected regression and find a before and after number; not recorded | https://pauta-project.dev/6.1.0/performance/#perf-22 |
| KBD-12 | SHOULD | manual | Open Settings > Keymap, search, and bind a chord that is taken: one row per command with its keys (SettingsWindow.java:296-325), filtered by the Settings search, conflict tooltip and invalid style (SettingsWindow.java:383-390); not recorded | https://pauta-project.dev/6.1.0/keyboard/#kbd-12 |
| KBD-14 | SHOULD | manual | Press C-x in the Emacs keymap and wait: the status bar lists the next keys (KeyDispatcher.java:46-55, Keybindings.continuations); not recorded | https://pauta-project.dev/6.1.0/keyboard/#kbd-14 |
| KBD-15 | MUST | manual | Tab through every window and dialog and work each control with Space/Enter. Source finds no mouse-only control: the empty state's buttons are plain Buttons in the Tab order (src/main/java/com/example/app/ui/MainWindow.java:190-196); Settings switches take focus and toggle on Space/Enter (SettingSwitch.java:29-31); the status bar's update link (StatusBar.java:62-64) runs help.openNewRelease and its labels run view.messageLog (StatusBar.java:23-24,59-61); the overlay scrim's click is edit.cancel/Esc (OverlayHost.java:41-47); find-bar buttons (FindBar.java:98-101) per FIND-16; picker list driven from its field (Picker.java:97,118-119); not recorded | https://pauta-project.dev/6.1.0/keyboard/#kbd-15 |
| ACC-05 | SHOULD | manual | Look at a grayscale screenshot of each window: the echo's error and warning colors (app.css:65-70) come with the message's own words, the unread-error count is a number (src/main/java/com/example/app/ui/StatusBar.java:94-99); Doctor pills carry words (src/main/java/com/example/app/ui/DoctorWindow.java:63-73); the Sync pill says its state (SettingsWindow.java:552); grayed menu rows keep a tooltip with the reason (AppMenus.java:245-262); check that an unparsable key binding, marked .invalid with no word (SettingsWindow.java:409-411), still reads as one; not recorded | https://pauta-project.dev/6.1.0/accessibility/#acc-05 |
| ACC-07 | SHOULD | manual | docs/release.md:20-21 has the step naming Narrator and VoiceOver; the pass itself is a person's, recorded as a checked entry with the version ; not recorded | https://pauta-project.dev/6.1.0/accessibility/#acc-07 |
| MENU-17 | SHOULD | manual | On Windows or Linux, in CUA and Emacs, reach the first menu with F10 or a lone Alt and walk the bar with the arrows; no keymap binds F10 (keymaps/*.json); menu bar in its band or in the HeaderBar (MainWindow.java:95-101, 237-240); not recorded | https://pauta-project.dev/6.1.0/menus/#menu-17 |
| MENU-08 | MUST | manual | Open a right-click menu, press Escape and type: the keys reach the control that had focus (stock ContextMenu, CommandMenu.java:81-84); not recorded | https://pauta-project.dev/6.1.0/menus/#menu-08 |
| MENU-10 | SHOULD | manual | In a list (Message Log, Settings → Keymap), select one row and right-click another: its menu opens and acts on the clicked row (src/main/java/com/example/app/ui/CommandMenu.java:92-98); no editor in the app, so the lists alone are judged; not recorded | https://pauta-project.dev/6.1.0/menus/#menu-10 |
| SET-02 | MUST | manual | Open Settings: sidebar of pages with icons left, page right, search on top: src/main/java/com/example/app/ui/SettingsWindow.java:157-195, page icons :820 (search sits over the sidebar column, as in Editora); not recorded | https://pauta-project.dev/6.1.0/settings/#set-02 |
| SET-15 | MUST | manual | Paste https://user:token@host/repo.git into Settings → Sync: refused with the credential-helper/SSH message: src/main/java/com/example/app/sync/SyncEngine.java:74-79,86 (checkUrl, hasCredentials), ui/SettingsWindow.java:491-502, messages.properties:291; not recorded | https://pauta-project.dev/6.1.0/settings/#set-15 |
| SET-21 | SHOULD | manual | Point Sync at a missing repository: pill, echo and Message Log show the failure: src/main/java/com/example/app/ui/SettingsWindow.java:550-562 (pill), ui/SyncController.java:165 echo (AppServices.echo writes to the Message Log); not recorded | https://pauta-project.dev/6.1.0/settings/#set-21 |
| DIAG-07 | SHOULD | manual | Open the Doctor (Settings → Advanced or help.doctor) with git missing and find its row Failed with what to install and a link: src/main/java/com/example/app/sync/Doctor.java:47-71, src/main/java/com/example/app/ui/DoctorWindow.java:55-90 (gh missing shows Warning, not Failed); not recorded | https://pauta-project.dev/6.1.0/diagnostics/#diag-07 |
| INTL-04 | SHOULD | manual | Run each language at 1024 px wide, open each window and Settings page, find no row cut off: src/main/java/com/example/app/ui/SettingsWindow.java:604-645 (rows), messages_de.properties; not recorded | https://pauta-project.dev/6.1.0/i18n/#intl-04 |
| WELC-02 | SHOULD | manual | Start the app and read the empty state: app name, tagline, the palette hint with its chord, and four buttons (Command Palette, Settings, Documentation, What's New) each labeled with its title and chord from the active keymap, followed on a keymap change (src/main/java/com/example/app/ui/MainWindow.java:182-219,132; app.css:39-46); src/test/java/com/example/app/ui/MainWindowFxTest.java:115 fires two of them; not recorded | https://pauta-project.dev/6.1.0/templates/welcome/#welc-02 |
| PWIN-02 | MUST | manual | Open the Pauta window and compare it with pauta.json entry by entry (edition, conformance sentence, counts by kind incl. app-only, ID, level, kind, reason, link): src/main/java/com/example/app/ui/PautaWindow.java:53-144. Not recorded; not recorded | https://pauta-project.dev/6.1.0/templates/pauta-window/#pwin-02 |
| PWIN-08 | SHOULD | manual | With an empty exceptions array, open the Pauta window: src/main/java/com/example/app/ui/PautaWindow.java:104-108 pauta.none names the app and edition; copy still works (:213-217); not recorded | https://pauta-project.dev/6.1.0/templates/pauta-window/#pwin-08 |
| SYNC-01 | SHOULD | manual | Open Settings, search "sync", land on the Sync page (key bindings are the data carried): src/main/java/com/example/app/ui/SettingsWindow.java:54,422-489, messages.properties:249; not recorded | https://pauta-project.dev/6.1.0/templates/settings-sync/#sync-01 |

## Not checked

None.

## Exceptions

| ID | Level | Status | Evidence | Rule |
|----|-------|--------|----------|------|
| PAUTA-13 | MUST | app-only | pauta.json: Each app's user guide has its own page listing its conformance and exceptions. | https://pauta-project.dev/6.1.0/conformance/#pauta-13 |
| PAUTA-14 | SHOULD | app-only | pauta.json: Each app's website makes its conformance page from the app's own list of exceptions. | https://pauta-project.dev/6.1.0/conformance/#pauta-14 |
| REPO-01 | MUST | app-only | pauta.json: Each app's README shows its own screenshots; the template's show the template, so an app retakes them from its own window. | https://pauta-project.dev/6.1.0/repository/#repo-01 |
| DOCS-01 | MUST | app-only | pauta.json: The user guide lives on each app's own website, which a template does not have. | https://pauta-project.dev/6.1.0/documentation/#docs-01 |
| DOCS-02 | MUST | app-only | pauta.json: The user guide's pages under /docs/ belong to each app's own website. | https://pauta-project.dev/6.1.0/documentation/#docs-02 |
| DOCS-03 | SHOULD | app-only | pauta.json: Each app's website serves its own user guide for each version. | https://pauta-project.dev/6.1.0/documentation/#docs-03 |
| DOCS-04 | SHOULD | app-only | pauta.json: Each app's website makes its own command and settings pages and What's new from the app. | https://pauta-project.dev/6.1.0/documentation/#docs-04 |
| DOCS-18 | SHOULD | app-only | pauta.json: Each app retakes its README's and its website's screenshots from its own window; the template has no website, and its screenshots show only the template. | https://pauta-project.dev/6.1.0/documentation/#docs-18 |
| REL-19 | MUST | app-only | pauta.json: Each app's repository requires its own format and test checks before a change is merged. | https://pauta-project.dev/6.1.0/release/#rel-19 |
