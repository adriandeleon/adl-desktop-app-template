package com.example.app.sync;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;

import org.junit.jupiter.api.Test;
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
    void findsToolsOnThePathAndInTheUsualPlaces(@TempDir Path dir) throws Exception {
        String separator = File.pathSeparator; // ':' here, ';' on Windows, whose paths carry ':'
        Path tool = executable(Files.writeString(dir.resolve("mytool"), "#!/bin/sh\n"));
        assertEquals(tool, Executables.find("mytool", dir.resolve("nowhere") + separator + dir, false));
        assertNull(Executables.find("no-such-tool-anywhere", dir.toString(), false));
        Path exe = executable(Files.writeString(dir.resolve("other.exe"), ""));
        assertEquals(exe, Executables.find("other", separator + dir, true));
        assertEquals("no-such-tool-anywhere", Executables.find("no-such-tool-anywhere"));
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** Marks a file executable where that is a file mode; on Windows every file already is. */
    private static Path executable(Path file) throws Exception {
        if (!windows()) Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        return file;
    }

    /**
     * A gh that answers auth status, repo create and config get the way it is told to: a shell script, or
     * on Windows a batch file (CRLF, {@code echo.} so an empty answer prints an empty line).
     */
    private static Path fakeGh(Path dir, int authExit, int createExit, String createOutput, String protocol)
            throws Exception {
        if (windows()) {
            Path script = Files.createTempFile(dir, "gh", ".cmd");
            String text = String.join(
                    "\r\n",
                    "@echo off",
                    "if \"%~1 %~2\"==\"auth status\" exit /b " + authExit,
                    "if \"%~1 %~2\"==\"repo create\" (",
                    "  echo." + createOutput,
                    "  exit /b " + createExit,
                    ")",
                    "if \"%~1 %~2\"==\"config get\" echo." + protocol,
                    "exit /b 0",
                    "");
            return Files.writeString(script, text);
        }
        Path script = Files.createTempFile(dir, "gh", ".sh");
        Files.writeString(script, """
                #!/bin/sh
                case "$1 $2" in
                  "auth status") exit %d ;;
                  "repo create") echo "%s"; exit %d ;;
                  "config get") echo "%s" ;;
                esac
                """.formatted(authExit, createOutput, createExit, protocol));
        return executable(script);
    }
}
