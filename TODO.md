# TODO

Planned work, newest decisions first. A pull request that adds, finishes or drops an item here changes
this file in the same change ([docs/conventions.md](docs/conventions.md#docs-in-the-same-pr)).

## Next

- **The rules in `pauta.json` still pending.** Each `pending` exception in
  `src/main/resources/com/example/app/pauta.json` links an issue; meeting a rule removes its entry in the
  same change. An app made from the template inherits the list: re-run the `pauta-audit` skill, open
  its own issues for what is still pending and point the entries at them, and meet every pending MUST
  before its first release, which must be Core or Complete.

- **The user documentation website.** `AppInfo.WEBSITE` (`https://exampleapp-project.dev`) and
  Help → Documentation point at `/docs/v-<version>/`, which the `exampleapp-website` repository serves:
  getting started, one page per feature area, the keyboard reference, Settings and an FAQ, each page's
  frontmatter giving its `category` and `order`, and a What's new generated from `CHANGELOG.md`.
- **Required checks on `master`.** Make **Format** and **Build & Test** required status checks in the
  repository's branch rules (a GitHub setting, not a file).
