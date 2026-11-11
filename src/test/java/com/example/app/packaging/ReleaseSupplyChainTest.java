package com.example.app.packaging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the trust boundaries of the CI and release workflows (Pauta QUAL-15, REL-15, REL-16, REL-18,
 * REL-21, REL-27, REL-28).
 *
 * <p>None of this is exercised by a local build, and a regression is silent: a workflow with a floating
 * action tag, a signing step that moved after the upload, or an attestation that misses a download runs
 * exactly as well as a correct one, until the day it matters. The checks are textual on purpose (the
 * workflows are YAML that no dependency of this project parses), and each message says what the rule
 * protects.
 */
class ReleaseSupplyChainTest {

    private static final Path REPO = Path.of(System.getProperty("user.dir"));
    private static final Path WORKFLOWS = REPO.resolve(Path.of(".github", "workflows"));

    private static String read(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static List<Path> workflows() throws IOException {
        try (var files = Files.list(WORKFLOWS)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".yml"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * {@code job id -> the text of that job}, split on the two-space-indented keys under {@code jobs:}.
     * Whole-line comments are dropped: the comment above a job would otherwise belong to the one before.
     */
    private static Map<String, String> jobs(String workflow) {
        Map<String, String> jobs = new LinkedHashMap<>();
        String body = workflow.substring(workflow.indexOf("\njobs:\n") + "\njobs:\n".length())
                .replaceAll("(?m)^\\s*#.*\\n", "");
        Matcher m = Pattern.compile("(?m)^  ([A-Za-z0-9_-]+):\\s*$").matcher(body);
        List<int[]> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (m.find()) {
            starts.add(new int[] {m.start(), m.end()});
            names.add(m.group(1));
        }
        for (int i = 0; i < names.size(); i++) {
            int end = i + 1 < names.size() ? starts.get(i + 1)[0] : body.length();
            jobs.put(names.get(i), body.substring(starts.get(i)[1], end));
        }
        return jobs;
    }

    private static Map<String, String> releaseJobs() throws IOException {
        return jobs(read(WORKFLOWS.resolve("release.yml")));
    }

    private static String job(Map<String, String> jobs, String name) {
        String body = jobs.get(name);
        assertNotNull(body, "release.yml lost its `" + name + "` job");
        return body;
    }

    /** The text of the step with this name, up to the next step. */
    private static String step(String job, String name) {
        int at = job.indexOf("- name: " + name + "\n");
        assertTrue(at >= 0, "the step `" + name + "` is gone");
        int next = job.indexOf("\n      - ", at + 1);
        return job.substring(at, next < 0 ? job.length() : next);
    }

    // ---- every workflow ---------------------------------------------------------------------------------

    @Test
    void everyActionIsPinnedToAFullCommitShaWithItsVersionInAComment() throws IOException {
        Pattern uses = Pattern.compile("(?m)^\\s*(?:- )?uses:\\s*(.+)$");
        Pattern pinned = Pattern.compile("[\\w.-]+/[\\w./-]+@[0-9a-f]{40}\\s+#\\s*v?\\d\\S*");
        List<String> floating = new ArrayList<>();
        int seen = 0;
        for (Path workflow : workflows()) {
            Matcher m = uses.matcher(read(workflow));
            while (m.find()) {
                String action = m.group(1).trim();
                if (action.startsWith("./")) {
                    continue; // a workflow of this repository, at this commit
                }
                seen++;
                if (!pinned.matcher(action).matches()) {
                    floating.add(workflow.getFileName() + ": " + action);
                }
            }
        }
        assertTrue(seen > 10, "found only " + seen + " `uses:` lines; the scan is broken");
        assertTrue(
                floating.isEmpty(),
                "a tag or branch is mutable; pin each action as `owner/repo@<40-hex sha> # vX.Y.Z`: " + floating);
    }

    @Test
    void theWorkflowTokenIsReadOnlyUnlessAJobAsksForMore() throws IOException {
        for (Path workflow : workflows()) {
            assertTrue(
                    read(workflow).contains("\npermissions:\n  contents: read\n"),
                    workflow.getFileName() + " must default the token to `contents: read`");
        }
        for (var job : releaseJobs().entrySet()) {
            boolean writes = job.getValue().contains("contents: write");
            boolean mayWrite = job.getKey().equals("publish") || job.getKey().equals("bump");
            assertEquals(mayWrite, writes, "release.yml job `" + job.getKey() + "`: unexpected token scope");
        }
    }

    @Test
    void onlyTheJobThatPushesKeepsItsCheckoutCredentials() throws IOException {
        for (Path workflow : workflows()) {
            for (var job : jobs(read(workflow)).entrySet()) {
                String body = job.getValue();
                if (!body.contains("actions/checkout@")) {
                    continue;
                }
                assertEquals(
                        !body.contains("git push"),
                        body.contains("persist-credentials: false"),
                        workflow.getFileName() + " job `" + job.getKey()
                                + "`: a checkout keeps the token in .git/config unless persist-credentials is false,"
                                + " and only a job that pushes needs it");
            }
        }
    }

    @Test
    void everyJobHasATimeout() throws IOException {
        List<String> unbounded = new ArrayList<>();
        for (Path workflow : workflows()) {
            jobs(read(workflow)).forEach((name, body) -> {
                // A job that calls another workflow takes no timeout; that workflow's jobs carry theirs.
                boolean calls = body.contains("\n    uses: ./");
                if (!calls && !body.contains("\n    timeout-minutes:")) {
                    unbounded.add(workflow.getFileName() + ":" + name);
                }
            });
        }
        assertTrue(unbounded.isEmpty(), "a hung job otherwise holds a runner for six hours: " + unbounded);
    }

    @Test
    void ciUploadsTheTestReportsAndCoverageEvenWhenTheTestsFail() throws IOException {
        String build = job(jobs(read(WORKFLOWS.resolve("ci.yml"))), "build");
        for (String upload : List.of("Upload test reports", "Upload coverage report", "Summarize coverage")) {
            assertTrue(
                    step(build, upload).contains("if: always()"),
                    "ci.yml `" + upload + "` must run when the tests fail: that is when it is needed");
        }
        assertTrue(step(build, "Upload test reports").contains("target/surefire-reports"));
        assertTrue(step(build, "Summarize coverage").contains("$GITHUB_STEP_SUMMARY"));
    }

    // ---- release.yml ------------------------------------------------------------------------------------

    @Test
    void aTagPublishesAndADispatchIsADryRunThatPublishesNothing() throws IOException {
        String release = read(WORKFLOWS.resolve("release.yml"));
        assertTrue(release.contains("  push:\n    tags: ['v"), "a release starts from a version tag");
        assertTrue(release.contains("\n  workflow_dispatch:"), "a dry run starts from workflow_dispatch");
        Map<String, String> jobs = jobs(release);
        String publish = job(jobs, "publish");
        assertFalse(
                publish.contains("\n    if: "),
                "the publish job must run on a dry run too, so JReleaser is exercised before a tag");
        String decide = step(publish, "Decide what JReleaser runs");
        int dispatch = decide.indexOf("\"$GITHUB_EVENT_NAME\" = workflow_dispatch");
        assertTrue(
                dispatch >= 0 && decide.indexOf("args=full-release --dry-run") > dispatch,
                "a workflow_dispatch run must run JReleaser with --dry-run");
        assertTrue(
                publish.contains("arguments: ${{ steps.packagers.outputs.args }}"),
                "JReleaser must run with the arguments that step decided");
        assertTrue(
                Pattern.compile("\\n\\s+version: \\d+\\.\\d+\\.\\d+\\n")
                        .matcher(publish)
                        .find(),
                "the JReleaser version the action downloads must be pinned (its default is `latest`)");
        assertTrue(
                job(jobs, "bump").contains("if: github.event_name == 'push' && github.ref_type == 'tag'"),
                "a dry run must not reopen master");
    }

    @Test
    void nothingIsPublishedBeforeEveryAssetIsChecked() throws IOException {
        Map<String, String> jobs = releaseJobs();
        assertTrue(job(jobs, "build").contains("needs: [preflight, full-tests]"), "build waits for the tests");
        assertTrue(job(jobs, "sign-linux").contains("needs: build"), "sign-linux signs what the build uploaded");
        assertTrue(
                job(jobs, "check-assets").contains("needs: [preflight, sign-linux]"),
                "the asset check must see the signed Linux files");
        assertTrue(
                job(jobs, "publish").contains("needs: [preflight, check-assets]"),
                "JReleaser publishes an immutable release: only after the asset check passed");
        String check = step(job(jobs, "check-assets"), "Check the expected files");
        assertTrue(check.contains("exampleapp-signing-key.asc"), "the release must attach the public key");
    }

    @Test
    void theMacOsDmgIsSignedBeforeTheBuildThenNotarizedStapledCheckedAndLaunched() throws IOException {
        String release = read(WORKFLOWS.resolve("release.yml"));
        String build = job(jobs(release), "build");
        int importCert = build.indexOf("- name: Import signing certificate (macOS)");
        int dist = build.indexOf("- name: Build, test and package");
        int notarize = build.indexOf("- name: Notarize the dmg (macOS)");
        int name = build.indexOf("- name: Name the downloads");
        int upload = build.indexOf("- name: Upload\n");
        assertTrue(importCert >= 0 && notarize >= 0, "the macOS legs no longer sign and notarize the dmg");
        assertTrue(
                importCert < dist && dist < notarize && notarize < name && name < upload,
                "the keychain must exist before -Pdist signs the app, and the dmg must be notarized before it"
                        + " is uploaded for the release");

        String importStep = step(build, "Import signing certificate (macOS)");
        for (String secret : List.of("MACOS_CERT_P12", "MACOS_CERT_PASSWORD")) {
            assertTrue(importStep.contains("secrets." + secret), "the import step no longer reads " + secret);
        }
        int checked = importStep.indexOf("if [ -z \"$MACOS_CERT_P12\" ]");
        assertTrue(
                checked >= 0 && checked < importStep.indexOf("security create-keychain"),
                "the secrets are checked before anything uses them");
        assertTrue(
                importStep.contains("\"$GITHUB_REF_TYPE\" = tag") && importStep.contains("exit 1"),
                "a tag without the signing secrets must fail, not publish an unsigned dmg");
        assertTrue(
                importStep.contains("echo \"enabled=false\"") && importStep.contains("exit 0"),
                "a dry run or a fork without the secrets must build unsigned rather than fail");
        assertTrue(
                importStep.contains("EXAMPLEAPP_MAC_SIGN_IDENTITY="), "the build signs only when handed an identity");

        String notarizeStep = step(build, "Notarize the dmg (macOS)");
        assertTrue(
                notarizeStep.contains("if: steps.macsign.outputs.enabled == 'true'"),
                "notarization runs exactly when the certificate was imported");
        for (String secret : List.of("APPLE_API_KEY_P8", "APPLE_API_KEY_ID", "APPLE_API_ISSUER")) {
            assertTrue(notarizeStep.contains("secrets." + secret), "notarization no longer reads " + secret);
        }
        int submit = notarizeStep.indexOf("xcrun notarytool submit");
        int staple = notarizeStep.indexOf("xcrun stapler staple");
        int assess = notarizeStep.indexOf("spctl --assess --type execute");
        int launch = notarizeStep.indexOf("scripts/smoke-test.sh");
        assertTrue(
                submit >= 0 && submit < staple && staple < assess && assess < launch,
                "notarize, staple, check the result the way Gatekeeper will, then launch the notarized app");
        assertTrue(
                build.contains("if: always() && steps.macsign.outputs.enabled == 'true'")
                        && build.contains("security delete-keychain"),
                "the keychain holding the private key must be removed even when the leg fails");
    }

    @Test
    void theLinuxPackagesAreSignedOffTheBuildRunnersWithTheCommittedKey() throws IOException {
        Map<String, String> jobs = releaseJobs();
        String sign = job(jobs, "sign-linux");
        assertFalse(
                job(jobs, "build").contains("LINUX_GPG_KEY"),
                "the key must never reach a build leg, which runs whatever Maven resolves");
        assertTrue(
                sign.contains("secrets.LINUX_GPG_KEY") && sign.contains("secrets.LINUX_GPG_PASSPHRASE"),
                "sign-linux reads the key and its passphrase from the secrets");
        assertTrue(
                sign.contains("\"$GITHUB_REF_TYPE\" = tag") && sign.contains("exit 1"),
                "a tag without the signing key must fail, not publish unsigned packages");
        assertTrue(
                sign.contains("EXAMPLEAPP_GPG_EXPECTED_KEY=packaging/linux/signing-key.asc"),
                "a release must be signed by the committed key and no other");
        int signs = sign.indexOf("scripts/sign-linux-packages.sh");
        assertTrue(
                signs >= 0 && signs < sign.indexOf("scripts/check-linux-signatures.sh"),
                "what was signed is checked against the public key alone before it is uploaded");
        for (String target : List.of("linux-x64", "linux-arm64")) {
            assertTrue(
                    sign.contains("name: ExampleApp-" + target + "\n"),
                    "the signed " + target + " files must replace the build leg's unsigned ones");
        }
        assertTrue(sign.contains("overwrite: true"), "the signed files must replace the unsigned ones");

        String key = read(REPO.resolve(Path.of("packaging", "linux", "signing-key.asc")));
        assertTrue(
                key.startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----")
                        && key.strip().endsWith("-----END PGP PUBLIC KEY BLOCK-----"),
                "packaging/linux/signing-key.asc must be the release key's armored public half");
        assertFalse(key.contains("PRIVATE"), "never commit the private key");
    }

    @Test
    void everyReleaseAssetGetsAProvenanceAttestationBeforeItIsPublished() throws IOException {
        Map<String, String> jobs = releaseJobs();
        for (var job : jobs.entrySet()) {
            assertEquals(
                    job.getKey().equals("publish"),
                    job.getValue().contains("id-token: write"),
                    "release.yml job `" + job.getKey() + "`: only the publish job may mint an OIDC token");
        }
        String publish = job(jobs, "publish");
        assertTrue(publish.contains("attestations: write"), "the publish job cannot store its attestations");
        int attest = publish.indexOf("actions/attest-build-provenance@");
        int jreleaser = publish.indexOf("jreleaser/release-action@");
        assertTrue(attest >= 0, "the release assets are no longer attested");
        assertTrue(attest < jreleaser, "attest before JReleaser publishes the immutable release");

        assertEquals(
                attachedByJReleaser(read(REPO.resolve("jreleaser.yml"))),
                attested(publish),
                "every file JReleaser attaches must be attested, and nothing else: keep the attestation's"
                        + " subject-path and jreleaser.yml's distribution artifacts and files.globs the same list");
    }

    /** The attestation step's {@code subject-path} patterns, sorted. */
    static List<String> attested(String publishJob) {
        Matcher subjects = Pattern.compile("subject-path: \\|\\n((?: {12}\\S.*\\n)+)")
                .matcher(step(publishJob, "Attest build provenance") + "\n");
        assertTrue(subjects.find(), "the attestation step lost its `subject-path: |` list");
        return subjects.group(1).lines().map(String::trim).sorted().toList();
    }

    /**
     * What JReleaser attaches, as globs, sorted: each distribution's artifact {@code path} (its
     * {@code {{projectVersion}}} read as {@code *}) and each {@code files.globs} pattern.
     */
    static List<String> attachedByJReleaser(String jreleaser) {
        List<String> out = new ArrayList<>();
        int distributions = jreleaser.indexOf("\ndistributions:\n");
        int files = jreleaser.indexOf("\nfiles:\n");
        assertTrue(distributions >= 0 && files > distributions, "jreleaser.yml lost `distributions:` or `files:`");
        Matcher paths = Pattern.compile("(?m)^\\s+- path: '(.+)'$").matcher(jreleaser.substring(distributions, files));
        while (paths.find()) {
            out.add(paths.group(1).replace("{{projectVersion}}", "*"));
        }
        Matcher globs = Pattern.compile("(?m)^    - pattern: '(.+)'$").matcher(jreleaser.substring(files));
        int patterns = 0;
        while (globs.find()) {
            out.add(globs.group(1));
            patterns++;
        }
        assertTrue(patterns > 0, "found no `files.globs` in jreleaser.yml; the scan is broken");
        return out.stream().sorted().toList();
    }

    @Test
    void theAttestedListMatchesWhatJReleaserAttachesAndNothingElse() {
        String jreleaser = """
                distributions:
                  app:
                    artifacts:
                      - path: 'artifacts/App-{{projectVersion}}-macos-arm64.dmg'
                files:
                  globs:
                    - pattern: 'artifacts/*.deb'
                """;
        String publish = """
                      - name: Attest build provenance
                        uses: actions/attest-build-provenance@0000000000000000000000000000000000000000 # v4.2.2
                        with:
                          subject-path: |
                            artifacts/*.deb
                            artifacts/App-*-macos-arm64.dmg

                      - name: Publish release
                """;
        assertEquals(attachedByJReleaser("\n" + jreleaser), attested(publish));
        String missing = publish.replace("            artifacts/*.deb\n", "");
        assertFalse(
                attachedByJReleaser("\n" + jreleaser).equals(attested(missing)),
                "a download JReleaser attaches but nothing attests must fail the check");
    }

    /** REL-17: the Windows leg signs the .msi, once a certificate is set, before it is uploaded. */
    @Test
    void theWindowsInstallerIsSignedBeforeItIsUploaded() throws IOException {
        String release = java.nio.file.Files.readString(java.nio.file.Path.of(".github/workflows/release.yml"));
        int sign = release.indexOf("- name: Sign the installer (Windows)");
        int upload = release.indexOf("- name: Upload\n");
        org.junit.jupiter.api.Assertions.assertTrue(sign > 0, "a Windows signing step");
        org.junit.jupiter.api.Assertions.assertTrue(sign < upload, "before the upload");
        String step = release.substring(sign, upload);
        org.junit.jupiter.api.Assertions.assertTrue(
                step.contains("signtool\" sign") && step.contains("WINDOWS_CERT_PFX"));
        org.junit.jupiter.api.Assertions.assertTrue(step.contains("verify /pa"));
    }
}
