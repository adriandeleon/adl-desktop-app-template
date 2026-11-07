# Testing

```bash
./mvnw test                       # everything
./mvnw -DexcludedGroups=fx test   # the pure suite only
./mvnw -Dtest=SettingsWindowFxTest test
./mvnw verify                     # plus the coverage floors and the format check
```

## Two kinds of test

- **Pure** (most of them): JUnit 5, no toolkit. Decisions are pulled into pure classes
  (`KeyStroke`, `FuzzyMatch`, `SettingsSearch`, `UpdateCheck`, `SyncMerge`, `Redactor`…) so they are
  tested directly.
- **Headless FX**, tagged `@Tag("fx")`: JavaFX's built-in Headless glass platform
  (`-Dglass.platform=Headless`, `prism.order=sw`, set in the pom's surefire config), so they need no
  display, no xvfb and no Monocle. TestFX's `FxToolkit` only boots the toolkit.
  `FxTestSupport.start(dir, host)` starts the app exactly as a launch does, in a temp config folder,
  with `FakeHost` standing in for the browser, file choosers and exit.

## Rules

- **No sleeps.** Run work on the FX thread with `FxTestSupport.run/call` and wait for background
  results with the bounded `waitUntil`.
- **A test writes only inside its temp folders.** The suite sets `exampleapp.configDir` to
  `target/test-config` so nothing defaults to the real home folder.
- **No real `gh`.** `exampleapp.gh` points "Create on GitHub" at a command that does not exist;
  `GitHubCliTest` and `ShellFeaturesFxTest` pass a fake `gh` script explicitly (skipped on Windows).
- **No network.** `exampleapp.updateCheck=off` stops the automatic check, and `exampleapp.updateUrl`
  points manual checks at a closed local port. `UpdateServiceTest` serves releases from the JDK's own
  HTTP server; `SyncTest` syncs through a local bare Git repository (skipped when git is missing).

## Guard tests

| Test | Guards |
|---|---|
| `CommandSweepFxTest` | Every registered command runs in a real headless window, and has a title and description |
| `MessagesTest` | Every catalog has every key; apostrophes follow the pattern rule; translations are not English copies |
| `ConfigStoreTest#everySettingSurvivesARestart` | The settings round trip |
| `MigrationsTest` | Every schema version has a migration step |
| `NoticeCoverageTest` | Every runtime dependency in the pom has a `NOTICE` entry; the jar carries `LICENSE` and `NOTICE` |
| `BuildHygieneTest` | The jar, the app image and the installers are handed `LICENSE` and `NOTICE`; the coverage floors below match the pom |
| `ReleaseSupplyChainTest` | Signing, notarization and attestation steps stay where they are; the attested files are the files JReleaser attaches; a dry run publishes nothing |

## Coverage

JaCoCo's `check` in `verify` fails below **0.90 lines**, **0.90 methods** or **0.79 branches** for
the whole build, and below each package's own line floor:

| Package | Line floor |
|---|---|
| `com.example.app` | 0.72 |
| `com.example.app.cli` | 0.97 |
| `com.example.app.command` | 0.96 |
| `com.example.app.config` | 0.94 |
| `com.example.app.i18n` | 0.94 |
| `com.example.app.instance` | 0.95 |
| `com.example.app.report` | 0.84 |
| `com.example.app.sync` | 0.89 |
| `com.example.app.ui` | 0.93 |
| `com.example.app.update` | 0.90 |

Floors are a regression net, not a target: each sits two to three points below the package's measured
level. A new package gets its own floor in the pom; ratchet a floor up as coverage rises, never down.
`BuildHygieneTest` fails when this table and the pom disagree. CI writes the totals to the job summary
and uploads the JaCoCo report and the surefire reports, whether the tests passed or not.
