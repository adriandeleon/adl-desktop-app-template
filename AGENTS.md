# ExampleApp: rules for coding agents

ExampleApp is a JavaFX desktop app: JDK 25+, JavaFX 27+, Maven, one JPMS module (`com.example.app`).
It follows [Pauta (the adl desktop apps guidelines)](https://pauta-project.dev/), edition [6.0.0](https://pauta-project.dev/6.0.0/).
This file is operational rules, not a changelog: how the app is built lives in [`docs/`](docs/README.md).
When documentation and code disagree, **the code wins**, and the documentation is fixed in the same change.
User-facing feature documentation goes to the separate website repository (`exampleapp-website`), never here.

## Pauta

This app is built to Pauta 6.0.0. Its rules: https://pauta-project.dev/6.0.0/llms.txt
(every rule in one file: https://pauta-project.dev/6.0.0/llms-full.txt).

- Before building a feature, read the rules on its page and copy from the code each rule's Reference names.
- Cite a rule by its ID and edition: "KBD-07 (Pauta 6.0.0)".
- Read the numbered edition, never latest/. Moving to a newer edition is its own change: pauta.version in
  pom.xml, this file and the README change together.

The edition's one source is `pauta.version` in `pom.xml`; About, `--version` and the environment block read
it through `AppInfo`, and the first lines of this file, the README's Pauta badge and its "Built to" line (and
the template box until `rename.sh` removes it) name the same number (`EditionTest` checks).
Every rule the app does not meet is an exception in `src/main/resources/com/example/app/pauta.json`:
`not-applicable` with a reason, `deviation` (SHOULDs only) with a decision record in `docs/decisions/`,
`pending` with an issue, or, in the template alone (`"template": true`), `app-only` with a reason: a rule
only an app made from it can meet, such as its website or its screenshots. `rename.sh` sets `template` to
`false`; from then on `./mvnw verify` lists each `app-only` entry and each issue inherited from the
template, and the release build (`-Pdist`) fails until each one is met or pending on an issue of this
repository (PAUTA-16). The file's `checked` list records the by-hand rules a person checked in the running
app and found met (rule, edition, date); it never changes the conformance (PAUTA-18).
`PautaConformance` computes Complete, Core or Partial from the exceptions; About and the Pauta window show
it, and `PautaConformanceTest` holds the file to `src/test/resources/pauta-rules.json` (the edition's
`rules.json`), a `checked` entry to its rule's `changed`, and the README's badge and line to the computed
conformance. Meeting a rule removes its entry in the same change. Moving to a newer edition is one change
(PAUTA-11): replace `pauta-rules.json` and the skill below with the new edition's, remove the `checked`
entries the test calls stale, audit again, update `pauta.json`, then `pauta.version`, the README and this
file.

To check the app against its edition, run the `pauta-audit` skill ([`.claude/skills/pauta-audit/`](.claude/skills/pauta-audit/SKILL.md)):
it writes `docs/pauta-audit.json` (to the edition's `audit.schema.json`) and `docs/pauta-audit.md`, a status
and evidence for each rule, failed MUSTs first. `/pauta-audit KBD` audits one prefix. The skill is Pauta's,
not this app's: each edition from 4.3.1 on serves it at `https://pauta-project.dev/<edition>/skills/pauta-audit/SKILL.md`.
Update it by replacing it with that copy, never by editing it here.

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
- **A whole feature is one pull request**, with its tests and its strings in every catalog. **Docs
  change in the same PR** (`docs/conventions.md#docs-in-the-same-pr`): `CHANGELOG.md`, `TODO.md`, the
  guides in `docs/` it touches and the website's user docs, never as a follow-up.
- **Before you push:** `./mvnw spotless:apply` and `./mvnw verify` pass locally.
- Conventional commit subjects: `feat(ui): …`, `fix(sync): …`, `docs: …`, `test: …`, `chore: …`.

## Change checklist

- [ ] Every user-facing action is a **command** registered in `ui/Commands` with an id
      (`area.verb`), and `command.<id>` + `command.<id>.desc` in **all six** catalogs.
- [ ] No raw English in a control: `Messages.tr("key")`. A value with `{0}` doubles its apostrophes.
- [ ] A new setting touches five places (`docs/conventions.md#adding-a-setting`): model field +
      schema bump + migration step, control, row with keywords, load line, palette command.
- [ ] A new package has a `package-info.java` with `@NullMarked`. Fix NullAway reports; never suppress.
- [ ] A new dialog or window goes through `Dialogs.styled` / `Themes.style` (one button order, Cancel
      first; stock buttons worded from the catalog). Overlays use `OverlayHost`, never `javafx.stage.Popup`.
- [ ] A command a menu lists has a glyph in `Icons.forCommand`; a control has a name a screen reader says
      (`NamedControlsFxTest`); a size set in code is bound to the UI zoom (`UiZoom.bind`). The OS is asked
      through `Os`, never `os.name`.
- [ ] Anything that removes or overwrites what the user made asks first through the one confirm helper:
      `services.host().confirm(owner, Confirmations.<question>(…))`, its words in `Confirmations`, the
      button the action's own verb (never OK or Yes). Tests answer it with `FakeHost.confirmAnswer`.
- [ ] CSS colors: a state (error, warning, verified, accent) is a `-state-*` token from the `.root` block
      at the top of `app.css`, everything else a `-color-*` role; never a hex outside `.root`
      (`StyleContrastTest` checks, contrast included). A glyph is `Icons.line` or `Icons.of`, never both,
      and a command's glyph comes from `Icons.forCommand`. An icon-only button is built by `Icons.button`
      or `Icons.toolbarButton` (or named by `Icons.name`): tooltip and accessible text are one string.
- [ ] Failures reach the status bar (`services.echo`, a `status.*` key whose last part has `Failed` or
      `Error`; `cannot`/`invalid` for a warning) or the Debug Log
      (`java.util.logging`). Never a stack-trace dialog, never silence.
- [ ] Tests: a pure helper plus a unit test first; an FX test (`@Tag("fx")`) when toolkit behavior is
      the point. No sleeps; no writes outside temp folders; no network.
- [ ] `CHANGELOG.md` under `## [Unreleased]`, an entry opening with a bold one-line summary for users.
- [ ] Coverage stays at or above 0.90 lines and methods; ratchet floors up, never down.

## Things that will bite you

See [`docs/gotchas.md`](docs/gotchas.md). The short list: `-Pdist` needs `clean`; Error Prone needs
`.mvn/jvm.config`; `java.awt.headless=true` is the first statement of `main`; a Popup loses keyboard
focus on Windows; Spotless must run on JDK 25.
