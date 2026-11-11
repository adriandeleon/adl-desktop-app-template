# TODO

Planned work, newest decisions first. A pull request that adds, finishes or drops an item here changes
this file in the same change ([docs/conventions.md](docs/conventions.md#docs-in-the-same-pr)).

## Next

- **The SHOULDs in `pauta.json` still pending.** The template meets every MUST of Pauta 6.0.0 it can
  (Core). Each `pending` exception in `src/main/resources/com/example/app/pauta.json` links an issue of
  this repository; meeting a rule removes its entry in the same change.
- **What an app made from the template inherits.** After `scripts/rename.sh`, `./mvnw verify` lists each
  `app-only` entry and each pending entry that links one of the template's issues; the release build
  (`-Pdist`) fails on them. Re-run the `pauta-audit` skill, meet what you can, open the app's own issues
  for what is still pending and point the entries at them. The `app-only` entries below are the app's to
  meet, not the template's.

- **The user documentation website** (`app-only` in `pauta.json`). `AppInfo.WEBSITE` (`https://exampleapp-project.dev`) and
  Help → Documentation point at `/docs/v-<version>/`, which the `exampleapp-website` repository serves:
  getting started, one page per feature area, the keyboard reference, Settings and an FAQ, each page's
  frontmatter giving its `category` and `order`, and a What's new generated from `CHANGELOG.md`.
- **Required checks on `master`** (`app-only`). Make **Format** and **Build & Test** required status checks in the
  repository's branch rules (a GitHub setting, not a file).
