# TODO

Planned work, newest decisions first. A pull request that adds, finishes or drops an item here changes
this file in the same change ([docs/conventions.md](docs/conventions.md#docs-in-the-same-pr)).

## Next

- **Nothing of Pauta 6.0.1 is pending in the template.** It meets every MUST and SHOULD a template can
  (Complete); what is left is `app-only`: each app's own website, screenshots and repository settings.
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
