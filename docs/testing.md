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

## Coverage

JaCoCo's `check` in `verify` fails below **0.90 lines** or **0.90 methods** for the whole build.
Floors are a regression net, not a target: when a package grows past 300 lines, give it its own floor
about three points below its measured level in the pom, and ratchet it up as coverage rises. CI writes
the totals to the job summary.
