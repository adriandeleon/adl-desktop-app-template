package com.example.app.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SET-18: the account that can read the sync repository is found once, recorded, and used from then on. */
class AccountPinTest {

    @Test
    void onlyAnHttpsGithubRemoteHasAnAccountToPin() {
        assertEquals("me/app-sync", AccountPin.ownerRepo("https://github.com/me/app-sync.git"));
        assertEquals("me/app-sync", AccountPin.ownerRepo("https://github.com/me/app-sync"));
        assertNull(AccountPin.ownerRepo("git@github.com:me/app-sync.git"));
        assertNull(AccountPin.ownerRepo("https://gitlab.com/me/app-sync.git"));
        assertEquals(List.of("work", "home"), GitHubCli.accounts("""
                        github.com
                          ✓ Logged in to github.com account work (keyring)
                          - Active account: true
                          ✓ Logged in to github.com account home (keyring)
                        """));
        assertEquals(List.of("old"), GitHubCli.accounts("✓ Logged in to github.com as old (oauth_token)"));
    }

    /**
     * With two accounts signed in, the second can read the repository: it is the one recorded, and the next sync
     * uses it without asking the API again.
     */
    @Test
    void theFirstAccountThatCanReadIsPinnedAndKept(@TempDir Path dir) throws Exception {
        Assumptions.assumeFalse(
                System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows"));
        Path calls = dir.resolve("api-calls");
        Path gh = dir.resolve("gh.sh");
        Files.writeString(gh, """
                #!/bin/sh
                case "$1 $2" in
                  "auth status") echo "  ✓ Logged in to github.com account work (keyring)"
                                 echo "  ✓ Logged in to github.com account home (keyring)" ;;
                  "auth token") eval last=\\$$#; echo "token-$last" ;;
                  "api repos/home/app-sync") echo x >> "%s"; [ "$GH_TOKEN" = "token-home" ] ;;
                  *) exit 1 ;;
                esac
                """.formatted(calls));
        Files.setPosixFilePermissions(
                gh,
                Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        GitHubCli cli = new GitHubCli(gh.toString());
        String url = "https://github.com/home/app-sync.git";
        Path sync = dir.resolve("sync");
        Files.createDirectories(sync);

        assertEquals(Map.of("GH_TOKEN", "token-home"), AccountPin.env(sync, url, cli));
        assertEquals("home", AccountPin.pinned(sync, url));
        int asked = Files.readAllLines(calls).size();
        assertEquals(2, asked, "work was asked, then home");

        assertEquals(Map.of("GH_TOKEN", "token-home"), AccountPin.env(sync, url, cli));
        assertEquals(asked, Files.readAllLines(calls).size(), "pinned: the API is not asked again");
        assertNull(AccountPin.pinned(sync, "https://github.com/home/other.git"), "a pin is for its own URL");
        assertTrue(AccountPin.env(sync, "git@github.com:home/app-sync.git", cli).isEmpty());
    }
}
