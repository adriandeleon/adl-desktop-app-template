---
name: pauta-audit
description: >-
  Audits this JavaFX app against the Pauta edition it implements (the adl desktop apps guidelines,
  pauta-project.dev): checks every rule (KBD-07, WIN-03, …) by code, test or by hand, reads the app's
  pauta.json exceptions, and writes docs/pauta-audit.json and docs/pauta-audit.md with a status and file:line
  evidence for each rule, failed MUSTs first. Use when asked to "audit against Pauta", "check Pauta
  compliance", "which Pauta rules does this app fail", "is this app Pauta compliant", or to audit one Pauta
  page or rule prefix (for example "audit KBD" or "audit the keyboard rules"). Not for writing new features
  from Pauta (read the rule's page for that).
argument-hint: "[prefix | page | rule ID …] [edition=<x.y.z>] [print]"
---

# Pauta audit

Audit this app against the Pauta edition it says it implements, one result per rule, and write the report to
`docs/pauta-audit.json` and `docs/pauta-audit.md`. Pauta is at https://pauta-project.dev/; each edition lives at
`https://pauta-project.dev/<edition>/`. Editora (`../Editora`) is its reference implementation.

This skill is part of Pauta: every edition from 4.3.1 serves its own copy at
`https://pauta-project.dev/<edition>/skills/pauta-audit/SKILL.md`. Where this copy and the audited edition's
`llms.txt` disagree on how to judge a rule, the edition's `llms.txt` wins.

## Arguments

All optional, in any order:

- **Scope.** A rule ID prefix (`KBD`, `win`), a page slug (`keyboard`, `window-shell`, `about` for
  `templates/about`), a page title (`Keyboard first`) or single IDs (`KBD-07 WIN-03`). Several are allowed; a rule
  is in scope if it matches any. No scope means every rule in the edition.
- **`edition=<x.y.z>`** overrides the edition the app names (step 1).
- **`print`** prints the Markdown report in the reply instead of writing the two files.

## 1. Find the edition

In this order, and stop at the first that names one:

1. The `pauta.version` property in `pom.xml`: `grep -n '<pauta.version>' pom.xml`.
2. The README's "Built to Pauta X.Y.Z" (or "Working toward") line: `grep -n -i 'pauta' README.md`.

If neither names an edition, **ask the user which edition to audit against**, and offer the latest from
https://pauta-project.dev/versions.json. If the two disagree, use `pom.xml` and report the mismatch in `problems`
(the README line is stale).

## 2. Load the edition's rules

Fetch into `target/pauta/<edition>/` (ignored by Git, wiped by `mvn clean`; never commit these files):

```bash
mkdir -p target/pauta/$EDITION
for f in rules.json llms.txt audit.schema.json; do
  curl -fsSL -o target/pauta/$EDITION/$f https://pauta-project.dev/$EDITION/$f
done
```

`audit.schema.json` exists from 4.3.1 on; for an older edition, use the latest edition's.

**Offline:** a checkout of the guidelines at `../adl-desktop-apps-guidelines` holds every published edition on its
`versions` branch. Read them from there without building anything:
`git -C ../adl-desktop-apps-guidelines show origin/versions:$EDITION/rules.json > target/pauta/$EDITION/rules.json`
(the same for `llms.txt`). An edition that is not on that branch is not published yet: ask the user.

**The edition has no `rules.json`:** the 1.x editions, published before rules had IDs, don't serve one. Say so,
and offer to audit against the oldest edition that does (2.0.0) or the latest (list them from `versions.json`).
Then set `edition` to the one audited and `appEdition` to the app's, and report a failed rule that the app's own
edition didn't have as upgrade work in its evidence: the `changelog/` page says what each edition added or made
stronger.

`rules.json` holds the edition's `levels`, `checks` and `statuses` (what each one means), `pages[]` and `rules[]`,
each rule with `id`, `level` (MUST, SHOULD, MAY), `page`, `section`, `appliesTo`, `url`, `text`, `why`,
`check.kind` (`code`, `test` or `by hand`), `check.text` and `reference`; and `retired[]`, the IDs no longer in use.
The rules in scope:

```bash
jq -r --arg p "KBD" '.rules[]
  | select((.id | ascii_upcase | startswith(($p | ascii_upcase) + "-")) or .page == $p or (.page | split("/") | last) == $p)
  | [.id, .level, .check.kind, (.appliesTo // ""), .check.text] | @tsv' target/pauta/$EDITION/rules.json
```

When a rule leans on its page (a template's mockup or spec table), read the page as Markdown at the page's
address with `.md` in place of the trailing `/` (`https://pauta-project.dev/<edition>/templates/about.md`, from
4.3.1 on), or its HTML page.

## 3. Read the app's exceptions

Read `src/main/resources/com/<app>/pauta.json` (Conformance, PAUTA-09). Each entry names a `rule`, its `kind`
(`not-applicable`, `deviation`, `pending`, or in a template `app-only`), a `reason` and, for a deviation or pending
entry, a `link`. `"template": true` marks a template repository. No file is a finding on PAUTA-01, and every unmet
rule is then `fail`.

## 4. Judge each rule

Read the whole rule (`text`, `why`, `check`, `appliesTo`) before judging it. Then, in this order:

1. **Limited.** A rule is limited when its own text names the apps it covers ("An app with user documents…", "An app
   with a tray icon…"), when it has an `appliesTo` (its page or section opens with an **Applies to** line), or when
   it details a rule that is limited (the rules after a limited one on the same subject). Decide from the code
   whether this app is one of them. If it isn't, the status is **`limited`**, and the evidence names what the app
   lacks ("no tray: no `SystemTray` or `TrayIcon` in `src/`"). A limited rule needs no exception; a `pauta.json`
   entry for one is a problem (PAUTA-05).
2. **Met?** Check it as its kind says:
   - **`code`.** Read or search the source as `check.text` says (it names files, classes, properties or style
     classes as Editora spells them; find this app's equivalent). It is met only if everything the check names is
     there. Evidence is the `file:line` that satisfies it; for an unmet rule, the closest place it should be and what
     is missing.
   - **`test`.** The check names a test as Editora calls it. Find that test or this app's equivalent in `src/test/`,
     read it to confirm it asserts what the rule says (a test with the right name that checks something else does not
     meet it), and confirm `./mvnw verify` runs it: not `@Disabled`, not excluded by the Surefire configuration in
     `pom.xml` (`excludes`, `excludedGroups`, `groups`). Evidence is the test's `file:line`.
   - **`by hand`.** Source alone can't settle it: **`manual`**, and the evidence says what to look at in the running
     app (from `check.text`) and the `file:line` of the code involved, if any. When the source shows the feature
     isn't there at all, it is unmet, not manual.

   Partly met is unmet: say which part is missing. Don't guess: when the source doesn't settle a `code` or `test`
   rule, say what you searched for and treat it as unmet, with "not found" in the evidence.
3. **Met:** **`pass`**. If `pauta.json` has an entry for it, report the stale entry in the result's `problem`.
4. **Unmet, and `pauta.json` lists it:** its status is the entry's kind (`pending`, `deviation`, `not-applicable`,
   `app-only`) and the evidence is the entry's `reason`. Report in `problem` a deviation on a MUST, a deviation or
   pending entry with no `link`, an `app-only` entry outside a template, and a `level` or `url` that differs from the
   edition's.
5. **Unmet, and not listed:** **`fail`**, for a MUST or SHOULD. A rule whose subject this app doesn't have at all
   (no tabs, no toolbar) is still `fail` when the rule reads as applying to every app: the fix is a `not-applicable`
   entry, so say so in the evidence. A missing part that a rule *requires* (the Command Palette, a status bar,
   About) fails that rule and every rule about how it behaves. An unmet MAY is `fail` with no consequence and needs no
   entry.

`pauta.json` entries that name an ID the edition does not have go in the report's `problems`: for one in the
edition's `retired` list, name the rule in its `see`.

Cite rules as "KBD-07 (Pauta <edition>)". Paths in evidence are relative to the repository root, as
`path/File.java:123`, so they are clickable.

**Large audits.** A whole edition is hundreds of rules. If you can start subagents, split the rules in scope by page
and give each subagent its pages, the path to `rules.json`, the app's `pauta.json` and this section; ask each for its
results as JSON objects in the schema's `results` shape, and merge them in the edition's rule order. Otherwise work
page by page and write the results as you go.

## 5. Write the report

Replace any earlier report: it is a snapshot, regenerated rather than edited by hand.

**`docs/pauta-audit.json`** follows the edition's `audit.schema.json`:

```json
{
  "$schema": "https://pauta-project.dev/<edition>/audit.schema.json",
  "app": "<App>",
  "commit": "<short SHA>",
  "edition": "<edition>",
  "date": "<YYYY-MM-DD>",
  "scope": [],
  "rules": "https://pauta-project.dev/<edition>/rules.json",
  "results": [
    { "id": "KBD-07", "level": "MUST", "status": "pass", "evidence": "src/main/java/com/example/app/ui/CommandPalette.java:57" },
    { "id": "TRAY-01", "level": "MUST", "status": "limited", "evidence": "no tray: no SystemTray or TrayIcon in src/" },
    { "id": "MENU-14", "level": "MUST", "status": "pending", "evidence": "Some menu rows have no icon yet." }
  ],
  "problems": []
}
```

One result per rule in scope, in the edition's rule order. Check it against the schema if a validator is at hand.

**`docs/pauta-audit.md`** is the same report for people:

```markdown
# Pauta audit

<App name> at <short commit SHA>, audited against [Pauta <edition>](https://pauta-project.dev/<edition>/)
on <YYYY-MM-DD>. Scope: <every rule | the prefixes, pages or IDs given>. Rules from <URL or local path>.

|        | pass | fail | manual | pending | deviation | not-applicable | app-only | limited |
|--------|-----:|-----:|-------:|--------:|----------:|---------------:|---------:|--------:|
| MUST   |      |      |        |         |           |                |          |         |
| SHOULD |      |      |        |         |           |                |          |         |
| MAY    |      |      |        |         |           |                |          |         |

A failed MUST blocks a release.

## Failed MUSTs

| ID | Level | Status | Evidence | Rule |
|----|-------|--------|----------|------|
| TBAR-01 | MUST | fail | `src/main/java/…/ui/MainToolbar.java:88`: builds an icon button by hand | https://pauta-project.dev/<edition>/toolbars-and-tooltips/#tbar-01 |

## Problems with pauta.json

## Other failures

(SHOULD, then MAY.)

## Manual checks

## Exceptions

(pending, deviation, not-applicable, app-only.)

## Passed and limited

(In rule order.)
```

Omit a section, or a column of the summary, that would be empty. Keep each evidence cell to one line, and escape `|`
inside cells as `\|`.

## 6. Offer fixes

After the report, list the failed MUSTs in the reply (ID, one line each) and **offer to fix them**; don't start
without a yes. For each fix:

- Copy from the rule's `reference`, which names files in Editora (`../Editora`) or one of the edition's templates;
  adapt names to this app rather than inventing a new design.
- Follow `AGENTS.md`'s change checklist: a command for each action, strings in every catalog, tests, docs and a
  `CHANGELOG.md` entry, and `./mvnw verify` passing.
- A rule the app will not meet now is recorded instead: `pending` with an issue, or for a SHOULD a `deviation` with
  a decision record (Conformance, PAUTA-06 and PAUTA-07).
- Re-run the audit for the fixed IDs only (`/pauta-audit TBAR-01 WIN-03`) and update their results.

Failed SHOULDs are worth fixing too; offer them separately, after the MUSTs.
