package com.example.app.sync;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

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
        Path calls = dir.resolve("api-calls");
        Files.createFile(calls);
        GitHubCli cli = FakeGh.cli(calls);
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

        // SET-10: a pin a newer build wrote is kept aside, and the account is found again.
        Files.writeString(
                sync.resolve(AccountPin.FILE), "{\"schemaVersion\": 9, \"url\": \"" + url + "\", \"account\": \"x\"}");
        assertNull(AccountPin.pinned(sync, url));
        assertTrue(Files.exists(sync.resolve(AccountPin.FILE + ".v9.bak")));
        // SET-11: one that can't be read is ignored.
        Files.writeString(sync.resolve(AccountPin.FILE), "{broken");
        assertNull(AccountPin.pinned(sync, url));
    }

    /**
     * A fake gh in Java, run by this JDK's launcher, so the test runs the same on Windows, macOS and Linux: two
     * accounts signed in, a token per account, and only home's token can read {@code home/app-sync}. Each API call
     * adds a line to the file named first.
     */
    static final class FakeGh {

        static GitHubCli cli(Path calls) throws Exception {
            String java =
                    Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classes = Path.of(FakeGh.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toString();
            return new GitHubCli(java, List.of("-cp", classes, FakeGh.class.getName(), calls.toString()));
        }

        public static void main(String[] args) throws Exception {
            java.io.PrintStream out = new java.io.PrintStream(
                    new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8);
            String command = args.length > 2 ? args[1] + " " + args[2] : "";
            switch (command) {
                case "auth status" -> {
                    out.println("  ✓ Logged in to github.com account work (keyring)");
                    out.println("  ✓ Logged in to github.com account home (keyring)");
                }
                case "auth token" -> out.println("token-" + args[args.length - 1]);
                case "api repos/home/app-sync" -> {
                    Files.writeString(Path.of(args[0]), "x\n", StandardOpenOption.APPEND);
                    if (!"token-home".equals(System.getenv("GH_TOKEN"))) System.exit(1);
                }
                default -> System.exit(1);
            }
        }
    }
}
