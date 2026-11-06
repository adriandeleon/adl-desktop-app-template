package com.example.app.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Create on GitHub, against a fake gh: no test ever runs the developer's real, signed-in one. */
class GitHubCliTest {

    @Test
    void namesAndRemotes() {
        assertTrue(GitHubCli.isName("exampleapp-sync"));
        assertFalse(GitHubCli.isName("-rf"));
        assertFalse(GitHubCli.isName(".."));
        assertFalse(GitHubCli.isName("has space"));
        String printed = "✓ Created repository me/sync on GitHub\n  https://github.com/me/sync\n";
        assertEquals("https://github.com/me/sync.git", GitHubCli.remoteUrl(printed, "https"));
        assertEquals("git@github.com:me/sync.git", GitHubCli.remoteUrl(printed, "ssh\n"));
        assertEquals("", GitHubCli.remoteUrl("nothing here", ""));
    }

    @Test
    void problemsAreNamed() {
        assertEquals(
                GitHubCli.Problem.BAD_NAME,
                new GitHubCli("gh").createPrivate("bad name", "d").problem());
        GitHubCli.Created missing = new GitHubCli("no-such-gh-binary").createPrivate("ok", "d");
        assertEquals(GitHubCli.Problem.NOT_INSTALLED, missing.problem());
        assertFalse(missing.ok());
        assertEquals("no-such-gh-in-tests", System.getProperty(GitHubCli.PROPERTY));
        assertEquals(
                GitHubCli.Problem.NOT_INSTALLED,
                new GitHubCli().createPrivate("ok", "d").problem());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void createsWithAFakeGh(@TempDir Path dir) throws Exception {
        Path gh = fakeGh(dir, 0, 0, "https://github.com/me/exampleapp-sync", "ssh");
        GitHubCli.Created created = new GitHubCli(gh.toString()).createPrivate("exampleapp-sync", "desc");
        assertTrue(created.ok(), created.detail());
        assertEquals("git@github.com:me/exampleapp-sync.git", created.url());

        assertEquals(
                GitHubCli.Problem.NOT_SIGNED_IN,
                new GitHubCli(fakeGh(dir, 1, 0, "", "").toString())
                        .createPrivate("x", "d")
                        .problem());
        assertEquals(
                GitHubCli.Problem.FAILED,
                new GitHubCli(fakeGh(dir, 0, 1, "name already exists", "").toString())
                        .createPrivate("x", "d")
                        .problem());
        assertEquals(
                GitHubCli.Problem.FAILED,
                new GitHubCli(fakeGh(dir, 0, 0, "no address", "").toString())
                        .createPrivate("x", "d")
                        .problem());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void findsToolsOnThePathAndInTheUsualPlaces(@TempDir Path dir) throws Exception {
        Path tool = Files.writeString(dir.resolve("mytool"), "#!/bin/sh\n");
        Files.setPosixFilePermissions(tool, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertEquals(tool, Executables.find("mytool", "/nowhere:" + dir, false));
        assertNull(Executables.find("no-such-tool-anywhere", dir.toString(), false));
        Path exe = Files.writeString(dir.resolve("other.exe"), "");
        Files.setPosixFilePermissions(exe, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertEquals(exe, Executables.find("other", ":" + dir, true));
        assertEquals("no-such-tool-anywhere", Executables.find("no-such-tool-anywhere"));
    }

    /** A gh that answers auth status, repo create and config get the way it is told to. */
    private static Path fakeGh(Path dir, int authExit, int createExit, String createOutput, String protocol)
            throws Exception {
        Path script = Files.createTempFile(dir, "gh", ".sh");
        Files.writeString(script, """
                #!/bin/sh
                case "$1 $2" in
                  "auth status") exit %d ;;
                  "repo create") echo "%s"; exit %d ;;
                  "config get") echo "%s" ;;
                esac
                """.formatted(authExit, createOutput, createExit, protocol));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }
}
