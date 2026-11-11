# 2. The tab strip sits over the documents, not in the title bar

- Status: accepted
- Date: 2026-11-10

## Context

TABS-11 (Pauta 4.1.0) says tabs SHOULD sit in the title bar through JavaFX 27's `HeaderBar`, as Termina's
do. Termina has no menu bar and no toolbar: its title bar has room for a tab strip and nothing to keep in
order above the terminal.

ExampleApp's title bar already holds the menus and the window title (`MainWindow.buildHeaderBar`), and the
toolbar sits under it in one chrome band (TBAR-06). Moving the tab strip into the title bar would put the
toolbar between the tabs and the documents they label, and on macOS, or with the menus taken out of the
title bar, there is no `HeaderBar` to hold them at all.

## Decision

The tab strip stays at the top of the document area, under the toolbar and over the documents, as in
Editora. TABS-11 is a `deviation` in `pauta.json` that links here.

## Consequences

The window keeps one layout on every platform and with either title bar setting: menus, toolbar, tabs,
documents, top to bottom. An app with no menus in the window and no toolbar (a terminal) should put its
tabs in the title bar and remove this deviation.
