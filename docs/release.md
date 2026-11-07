# Releasing

The pom version is the single source of truth: `X.Y.Z-SNAPSHOT` on `master`, and a tag `vX.Y.Z` on a
commit whose pom says exactly `X.Y.Z` releases it. `.github/workflows/release.yml` does the rest.

## Steps

1. **Dry run first** (Actions > Release > Run workflow, or `gh workflow run release.yml --ref master`).
   It runs the full tests, builds all five targets, smoke-tests and signs them (ad hoc and with a
   throw-away key when secrets are missing), runs the release-asset check, attests the downloads and
   runs JReleaser with `--dry-run`, which renders the release, the cask and the winget manifest and
   publishes nothing (the `jreleaser-logs` artifact holds them). A dry run from a `-SNAPSHOT` pom names
   its downloads with the plain version, which JReleaser needs; the app inside keeps its `-SNAPSHOT`.
   Read the run summary.
2. **Set the version** in a pull request: `./mvnw versions:set -DnewVersion=X.Y.Z -DgenerateBackupPoms=false`.
3. **Write the changelog**: rename `## [Unreleased]` in `CHANGELOG.md` to `## [X.Y.Z] - YYYY-MM-DD`
   (and start a new empty Unreleased section). The release notes are that section, after the install
   instructions in `.github/release-notes-header.md`; a tag with no section fails before publishing.
4. **Add a `<release>`** for the version to `packaging/linux/io.github.adriandeleon.ExampleApp.metainfo.xml`.
5. Merge, then **tag the merge commit** and push the tag:
   ```bash
   git switch master && git pull
   git tag -a vX.Y.Z -m "ExampleApp X.Y.Z" && git push origin vX.Y.Z
   ```
6. The workflow publishes the GitHub release, then opens a `chore/reopen-…` pull request setting the
   pom to the next patch `-SNAPSHOT`. **Close and reopen it** to run its checks (a pull request opened
   with `GITHUB_TOKEN` starts no workflows), then merge it.

A tag with a suffix (`v1.2.0-rc1`, pom `1.2.0-rc1`) is published as a pre-release and does not bump.

GitHub releases are immutable: a published release cannot gain or replace a file. That is why the
**release-asset check** fails the run before publishing when any expected download is missing. When
you add or drop a format, update the list in that job, the globs in `jreleaser.yml` and the
attestation's `subject-path` (`ReleaseSupplyChainTest` fails until the last two agree). To redo a
release, delete it (`gh release delete vX.Y.Z`) and the tag, and tag again.

## What runs

| Job | What it does |
|---|---|
| Preflight | The tag must equal the pom version (`./mvnw help:evaluate`), and not be a snapshot |
| Full tests | `full-tests.yml` on Linux, macOS and Windows (also daily and on PRs labelled `full-ci`) |
| Build (x5) | `-Pdist -Dskip.installers=false verify`, smoke test, macOS notarization (then the notarized app is launched once), fat jar, upload |
| Sign Linux packages | OpenPGP signatures for every Linux download, in a job that runs no Maven |
| Release-asset check | Every expected file present; the release notes can be written |
| Publish | Build provenance attestations, then JReleaser: the release, `checksums_sha256.txt`, notes. A dry run attests and runs JReleaser with `--dry-run` |
| Reopen master (tag only) | The pull request to the next `-SNAPSHOT` |

## Secrets

Set them with `gh secret set NAME` (type the value at the prompt; a piped `echo` adds a newline).

| Secret | What | Without it |
|---|---|---|
| `MACOS_CERT_P12` | Base64 of a `.p12` holding the Developer ID Application certificate, its key and Apple's G2 intermediate (`openssl pkcs12 -export -legacy …`) | Dry run: ad-hoc signed, not notarized. Tag: fails |
| `MACOS_CERT_PASSWORD` | The `.p12` password | As above |
| `APPLE_API_KEY_P8` | Base64 of the App Store Connect API key (`AuthKey_XXXXXXXXXX.p8`) used by `notarytool` | As above |
| `APPLE_API_KEY_ID` | The key's 10-character ID | As above |
| `APPLE_API_ISSUER` | The Issuer ID (UUID) for a team key; empty for an individual key | Team keys fail notarization |
| `LINUX_GPG_KEY` | The shared release key, ASCII-armoured secret key (`gpg --armor --export-secret-keys <fpr>`) | Dry run: a throw-away key. Tag: fails |
| `LINUX_GPG_PASSPHRASE` | Its passphrase, if it has one | |
| `PACKAGES_TOKEN` | Classic PAT with `repo` scope, for `adriandeleon/homebrew-tap` and the `winget-pkgs` fork | The GitHub release still publishes; no cask, no winget PR |

Signing therefore skips cleanly on forks and dry runs, and a tag without the secrets fails rather than
publishing unsigned downloads. Windows MSIs are not Authenticode signed yet (an open decision for the
family); the build provenance attestation is their only signature.

## The Linux signing key

The family shares one OpenPGP release key, "Adrian De Leon (release signing)", fingerprint
`8486A7AF4FC8FEE93D83CDB4EA5FA138C2C09C4A`. Its public half is committed as
`packaging/linux/signing-key.asc`, and `sign-linux` refuses a `LINUX_GPG_KEY` that is any other key.
An app that signs with its own key replaces that file with its key's public half:

```bash
gpg --armor --export <fingerprint> > packaging/linux/signing-key.asc
```

Every release also attaches the key as `exampleapp-signing-key.asc`.

Users verify a download with `gpg --import exampleapp-signing-key.asc` and `gpg --verify <file>.asc
<file>`; `rpm --import` the same key and `rpm -K` checks the RPM's embedded signature. Any download's
provenance: `gh attestation verify <file> --repo adriandeleon/ExampleApp`.

## Package managers

With `PACKAGES_TOKEN` set, JReleaser also writes the Homebrew cask `exampleapp` to
`adriandeleon/homebrew-tap` (Apple Silicon DMG) and opens a pull request for `AdrianDeLeon.ExampleApp`
on `microsoft/winget-pkgs` from the `adriandeleon` fork. The cask and winget templates are in
`src/jreleaser/`. Render them locally without releasing:

```bash
JRELEASER_PROJECT_VERSION=X.Y.Z jreleaser package --basedir .
```
