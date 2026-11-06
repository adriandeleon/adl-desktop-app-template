# 1. Start from the Pauta template

- Status: accepted
- Date: 2026-11-06

## Context

The family's apps (Editora, Torrenta, Termina, Modula, Mirada, nux) share a platform and a set of
MUSTs, written down as [Pauta (the adl desktop apps guidelines)](https://pauta-project.dev/): one command registry and palette, versioned config, Message and Debug Logs, Report a Problem,
an update check, six languages, NullAway, headless FX tests and a 0.90 coverage floor. Each app grew
them separately.

## Decision

ExampleApp starts from `adriandeleon/adl-desktop-app-template`, which already meets every MUST with a
small, tested implementation of each, and is renamed with `scripts/rename.sh`.

## Consequences

The app's own features go into `MainWindow`'s placeholder and new packages from day one, with the
gates already on. Improvements to the shared pieces are copied between apps by hand; the template
is the reference for new apps, Editora for depth.
