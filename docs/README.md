# ExampleApp documentation

This folder is for **contributors**: how the app is built, the conventions it keeps and how it is
tested and released. The user documentation (getting started, the keyboard reference, Settings, the
FAQ) lives on the app's website, under `/docs/` (`AppInfo.WEBSITE`, from the `exampleapp-website`
repository), and Help → Documentation opens it. This folder never carries a second copy.

**When these documents and the code disagree, the code wins**, and the document is fixed in the same
change.

| Document | What it covers |
|---|---|
| [architecture.md](architecture.md) | Packages, start-up, how a command, a setting and a message flow |
| [conventions.md](conventions.md) | Commands, strings, settings, nullness, CSS, logging |
| [testing.md](testing.md) | The pure and headless FX suites, guard tests, coverage |
| [gotchas.md](gotchas.md) | Things that cost real time and do not announce themselves |
| [building-and-packaging.md](building-and-packaging.md) | jpackage, installers, icons, signing locally |
| [release.md](release.md) | Cutting a release, secrets, what the workflow checks |
| [decisions/](decisions/) | Architecture decision records |
