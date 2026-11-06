# ExampleApp: rules for coding agents

ExampleApp is a JavaFX desktop app: JDK 25+, JavaFX 27+, Maven, one JPMS module (`com.example.app`).
It follows [Pauta (the adl desktop apps guidelines)](https://pauta-project.dev/), edition [1.1.0](https://github.com/adriandeleon/adl-desktop-apps-guidelines/blob/v1.1.0/CHANGELOG.md).
The edition is named in this line, the README's Pauta badge and its "Built to" line (and in the template
box until `rename.sh` removes it). Moving to a newer edition means reading that edition's CHANGELOG for new
or stronger MUSTs, meeting them, then changing every place together.
This file is operational rules, not a changelog: how the app is built lives in [`docs/`](docs/README.md).

## Essential commands

Run Maven from the repository root, **on JDK 25** (Palantir Java Format breaks on newer javac).

```bash
./mvnw javafx:run                 # run the app
./mvnw test                       # unit + headless FX tests
./mvnw -DexcludedGroups=fx test   # the pure suite only, fast
./mvnw spotless:apply             # format; CI fails unformatted code
./mvnw verify                     # the full gate CI runs: tests, NullAway, coverage floors, format
./mvnw clean -Pdist package       # native app image (clean is mandatory)
```

## How to work

- **One task, one branch, one worktree.** `git worktree add ../exampleapp-<topic> -b <type>/<topic>`;
  never switch branches under another session's feet, never stash someone else's work.
- **A whole feature is one pull request**, with its tests, its strings in every catalog, its docs and
  its CHANGELOG entry.
- **Before you push:** `./mvnw spotless:apply` and `./mvnw verify` pass locally.
- Conventional commit subjects: `feat(ui): …`, `fix(sync): …`, `docs: …`, `test: …`, `chore: …`.

## Change checklist

- [ ] Every user-facing action is a **command** registered in `ui/Commands` with an id
      (`area.verb`), and `command.<id>` + `command.<id>.desc` in **all six** catalogs.
- [ ] No raw English in a control: `Messages.tr("key")`. A value with `{0}` doubles its apostrophes.
- [ ] A new setting touches five places (`docs/conventions.md#adding-a-setting`): model field +
      schema bump + migration step, control, row with keywords, load line, palette command.
- [ ] A new package has a `package-info.java` with `@NullMarked`. Fix NullAway reports; never suppress.
- [ ] A new dialog or window goes through `Dialogs.styled` / `Themes.style`. Overlays use
      `OverlayHost`, never `javafx.stage.Popup`.
- [ ] CSS colours are AtlantaFX `-color-*` tokens, never hex literals.
- [ ] Failures reach the status bar (`services.echo`, key ending in `Failed`/`Error`) or the Debug Log
      (`java.util.logging`). Never a stack-trace dialog, never silence.
- [ ] Tests: a pure helper plus a unit test first; an FX test (`@Tag("fx")`) when toolkit behaviour is
      the point. No sleeps; no writes outside temp folders; no network.
- [ ] `CHANGELOG.md` under `## [Unreleased]`, an entry opening with a bold one-line summary for users.
- [ ] Coverage stays at or above 0.90 lines and methods; ratchet floors up, never down.

## Things that will bite you

See [`docs/gotchas.md`](docs/gotchas.md). The short list: `-Pdist` needs `clean`; Error Prone needs
`.mvn/jvm.config`; `java.awt.headless=true` is the first statement of `main`; a Popup loses keyboard
focus on Windows; Spotless must run on JDK 25.
