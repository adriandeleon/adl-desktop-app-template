package com.example.app.sync;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.app.i18n.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DIAG-07: with a tool missing, its row fails and says what to install, with a link. */
class DoctorTest {

    @Test
    void aMissingToolFailsItsRowWithWhatToInstall(@TempDir Path dir) {
        Messages.init("en");
        List<Doctor.Check> checks =
                Doctor.run(dir, new Git("no-such-git-for-the-doctor"), new GitHubCli("no-such-gh-for-the-doctor"));
        Map<String, Doctor.Check> byName = checks.stream().collect(Collectors.toMap(Doctor.Check::name, c -> c));
        Doctor.Check git = byName.get("git");
        assertEquals(Doctor.Status.FAILED, git.status());
        assertTrue(git.fix().startsWith("Install git"), git.fix());
        assertEquals("https://git-scm.com/downloads", git.link());
        assertEquals(Doctor.Status.WARNING, byName.get("gh").status(), "gh is optional: a warning");
        assertNotNull(byName.get("gh").link());
        assertEquals(Doctor.Status.OK, byName.get("Java").status(), "the tests run on Java 25 or newer");
        assertEquals(Doctor.Status.OK, byName.get("Config folder").status(), "a temp folder can be written");
    }
}
