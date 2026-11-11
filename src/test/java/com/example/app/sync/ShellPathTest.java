package com.example.app.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** CLI-10: the tools run with the login shell's PATH before the inherited one. */
class ShellPathTest {

    private static final String SEP = java.io.File.pathSeparator;

    @Test
    void theLoginShellsFoldersComeFirstEachOnce() {
        assertEquals(
                String.join(SEP, "/opt/homebrew/bin", "/usr/bin", "/bin"),
                ShellPath.merged(
                        String.join(SEP, "/opt/homebrew/bin", "/usr/bin"), String.join(SEP, "/usr/bin", "/bin")));
        assertEquals(
                String.join(SEP, "/usr/bin", "/bin"), ShellPath.merged(null, String.join(SEP, "/usr/bin", "", "/bin")));
    }

    /** A shell that isn't there says nothing, and the inherited PATH is used as it is: on every OS. */
    @Test
    void aMissingShellGivesNoPath(@TempDir Path dir) {
        assertNull(ShellPath.read(dir.resolve("no-such-shell").toString()));
        assertEquals(
                String.join(SEP, "/usr/bin", "/bin"), ShellPath.merged(null, String.join(SEP, "/usr/bin", "/bin")));
    }

    @Test
    void theLoginShellIsAskedForItsPath(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(
                System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows"));
        Path shell = dir.resolve("login-sh");
        Files.writeString(shell, "#!/bin/sh\n[ \"$1\" = -l ] && printf %s /from/login:/usr/bin\n");
        Files.setPosixFilePermissions(
                shell,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        assertEquals("/from/login:/usr/bin", ShellPath.read(shell.toString()));
        assertNull(ShellPath.read(dir.resolve("no-such-shell").toString()));
    }
}
