# Contributing to ExampleApp

Thank you for helping. Bug reports are easiest from the app itself: **Help → Report a Problem** opens
a prefilled issue with a redacted report you can read first.

## Build and run

You need **JDK 25** (Temurin is what CI and the releases use). The Maven Wrapper fetches Maven.

```bash
./mvnw javafx:run          # run from source
./mvnw verify              # what CI runs: compile with NullAway, all tests, coverage floors, format check
```

`./mvnw javafx:run` uses your real config folder `~/.exampleapp`. For experiments, launch with
`EXAMPLEAPP_CONFIG_DIR=/tmp/scratch-config ./mvnw javafx:run`.

## Tests

- Unit tests: plain JUnit 5. Put a decision in a pure class and test it there.
- FX tests: tagged `@Tag("fx")`, run on JavaFX's built-in Headless glass platform
  (`-Dglass.platform=Headless`, set in the pom): no display, no xvfb. `FxTestSupport` starts the app
  the way a launch does, in a temp config folder, with a fake desktop (`FakeHost`).
- `./mvnw -DexcludedGroups=fx test` runs the pure suite alone.
- The build fails below 0.90 line and method coverage. [docs/testing.md](docs/testing.md) has more.

## Style

- `./mvnw spotless:apply` formats Java with Palantir Java Format (run it on JDK 25). CI rejects
  unformatted code.
- Every package is `@NullMarked`; NullAway fails the build on a possible null dereference.
- UI text comes from the message catalogs, in all six languages. Translate for real.
- [docs/conventions.md](docs/conventions.md) has the rest.

## Pull requests

1. Branch from `master` (`feat/…`, `fix/…`, `docs/…`).
2. Keep one feature in one pull request, with its tests, strings, docs and a `CHANGELOG.md` entry
   under `## [Unreleased]`.
3. `./mvnw verify` passes locally.
4. CI runs **Format** and **Build & Test**; both must pass. Add the `full-ci` label to run the suite on
   Windows and macOS too.

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
