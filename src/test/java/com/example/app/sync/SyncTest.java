package com.example.app.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncTest {

    @BeforeAll
    static void needsGit(@TempDir Path dir) {
        Assumptions.assumeTrue(new Git().run(dir, "--version").ok(), "git is not installed");
    }

    @Test
    void thisMachineWinsAndRemoteDeletionsApply() {
        Map<String, String> base = Map.of("a", "1", "b", "1", "c", "1");
        Map<String, String> local = Map.of("a", "local", "b", "1", "c", "1", "new", "n");
        Map<String, String> remote = Map.of("a", "remote", "b", "remote", "d", "r");
        assertEquals(Map.of("a", "local", "b", "remote", "d", "r", "new", "n"), SyncMerge.merge(base, local, remote));
    }

    @Test
    void urlsWithCredentialsAreRefused() {
        assertEquals("empty", SyncEngine.checkUrl(" "));
        // SET-15, SYNC-02: a password, or a token as the user name, in any case of scheme.
        for (String url : java.util.List.of(
                "https://me:token@github.com/me/sync.git",
                "https://user:token@host/repo.git",
                "HTTPS://me:secret@github.com/me/sync.git",
                "ssh://git:hunter2@host/repo.git",
                "https://ghp_abcdefghijklmnopqrstuvwxyz0123456789@github.com/me/sync.git",
                "https://github_pat_11ABCDEFG_abcdefghijklmnop@github.com/me/sync.git",
                "https://glpat-abcdefghijklmnop@gitlab.com/me/sync.git",
                "http://0123456789abcdef0123456789abcdef01234567@host/repo.git",
                "  https://me:token@github.com/me/sync.git  ")) {
            assertEquals("credentials", SyncEngine.checkUrl(url), url);
        }
        // An SSH user, a plain user name and a user with an empty password carry no secret.
        for (String url : java.util.List.of(
                "git@github.com:me/sync.git",
                "ssh://git@github.com/me/sync.git",
                "https://github.com/me/sync.git",
                "https://me@github.com/me/sync.git",
                "https://me:@github.com/me/sync.git",
                "https://github.com/me/sync.git?ref=a@b",
                "file:///home/me/sync.git")) {
            assertNull(SyncEngine.checkUrl(url), url);
        }
    }

    @Test
    void theSyncFilesCarryAVersionAndAnOldBareFileIsMigrated(@TempDir Path root) throws Exception {
        assertTrue(
                SyncData.STEPS
                        .keySet()
                        .containsAll(java.util.stream.IntStream.range(SyncData.UNVERSIONED, SyncData.SCHEMA_VERSION)
                                .boxed()
                                .toList()),
                "every version below the current one has a step (SET-09)");
        Path file = root.resolve("keybindings.json");
        // Version 1, as the first builds wrote it: the bare map.
        Files.writeString(file, "{\"app.quit\": \"C-q\", \"help.about\": \"\"}");
        assertEquals(Map.of("app.quit", "C-q", "help.about", ""), SyncData.read(file));
        SyncData.write(file, Map.of("app.quit", "C-q"));
        String written = Files.readString(file);
        assertTrue(written.contains("\"schemaVersion\" : " + SyncData.SCHEMA_VERSION), written);
        assertEquals(Map.of("app.quit", "C-q"), SyncData.read(file));
        // Never trusted: a binding with no chord is dropped; something that is not an object is refused.
        Files.writeString(file, "{\"schemaVersion\": 2, \"keybindings\": {\"app.quit\": null, \"x\": \"F2\"}}");
        assertEquals(Map.of("x", "F2"), SyncData.read(file));
        Files.writeString(file, "[1, 2]");
        assertThrows(java.io.IOException.class, () -> SyncData.read(file));
        Files.writeString(file, "{\"schemaVersion\": 9}");
        SyncData.NewerException newer = assertThrows(SyncData.NewerException.class, () -> SyncData.read(file));
        assertEquals(9, newer.version());
        assertEquals(Map.of(), SyncData.read(root.resolve("absent.json")));
    }

    /** SET-10, SET-11: a base from a newer build, or one that can't be read, is kept aside and the sync goes on. */
    @Test
    void aBaseFromANewerBuildOrUnreadableIsBackedUpAndTheSyncGoesOn(@TempDir Path dir) throws Exception {
        Path base = dir.resolve("base.json");
        String newer = "{\"schemaVersion\": 99, \"keybindings\": {}}";
        Files.writeString(base, newer);
        assertTrue(SyncData.readBase(base).isEmpty());
        assertEquals(newer, Files.readString(dir.resolve("base.json.v99.bak")));
        assertFalse(Files.exists(base), "the next sync writes a base of this version");

        Files.writeString(base, "{broken");
        assertTrue(SyncData.readBase(base).isEmpty());
        assertEquals("{broken", Files.readString(dir.resolve("base.json.broken.bak")));

        SyncData.write(base, Map.of("app.quit", "C-q"));
        assertEquals(Map.of("app.quit", "C-q"), SyncData.readBase(base));
        try (var files = Files.list(dir)) {
            assertTrue(files.noneMatch(f -> f.toString().endsWith(".tmp")), "SET-22: no temporary file is left");
        }
    }

    @Test
    void aRepositoryFromANewerBuildIsLeftAsItIs(@TempDir Path root) throws Exception {
        Path remote = root.resolve("remote.git");
        Files.createDirectories(remote);
        assertTrue(new Git().run(remote, "init", "-q", "--bare").ok());
        String url = remote.toUri().toString();
        SyncEngine newer = new SyncEngine(root.resolve("newer/sync"), new Git());
        assertTrue(newer.sync(url, "main", Map.of("app.quit", "C-q")).ok());
        // The newer computer's build writes schema 99 and pushes it.
        Path repo = newer.repoDir();
        String future = "{\"schemaVersion\": 99, \"keybindings\": {}, \"more\": true}\n";
        Files.writeString(repo.resolve(SyncEngine.DATA_FILE), future);
        Git git = new Git();
        assertTrue(git.run(repo, "-c", "user.name=t", "-c", "user.email=t@localhost", "commit", "-qam", "newer")
                .ok());
        assertTrue(git.run(repo, "push", "-q", "origin", "HEAD:refs/heads/main").ok());

        SyncEngine older = new SyncEngine(root.resolve("older/sync"), new Git());
        SyncEngine.Result result = older.sync(url, "main", Map.of("help.about", "F1"));
        assertFalse(result.ok());
        assertEquals("data.newer", result.error());
        assertEquals(
                future, Files.readString(older.repoDir().resolve(SyncEngine.DATA_FILE)), "nothing written over it");
        assertFalse(Files.exists(root.resolve("older/sync").resolve(SyncEngine.BASE_FILE)));
    }

    @Test
    void twoComputersConvergeThroughOneRepository(@TempDir Path root) throws Exception {
        Path remote = root.resolve("remote.git");
        Files.createDirectories(remote);
        assertTrue(new Git().run(remote, "init", "-q", "--bare").ok());
        String url = remote.toUri().toString();

        SyncEngine first = new SyncEngine(root.resolve("one/sync"), new Git());
        SyncEngine second = new SyncEngine(root.resolve("two/sync"), new Git());

        SyncEngine.Result r1 = first.sync(url, "main", Map.of("app.quit", "C-q"));
        assertTrue(r1.ok(), r1.error());
        SyncEngine.Result r2 = second.sync(url, "main", Map.of("help.about", "F1"));
        assertTrue(r2.ok(), r2.error());
        assertEquals(Map.of("app.quit", "C-q", "help.about", "F1"), r2.merged());

        // The first computer deletes its binding; the second still has both.
        SyncEngine.Result r3 = first.sync(url, "main", Map.of());
        assertTrue(r3.ok(), r3.error());
        assertEquals(Map.of("help.about", "F1"), r3.merged());
        SyncEngine.Result r4 = second.sync(url, "main", Map.of("app.quit", "C-q", "help.about", "F1"));
        assertEquals(Map.of("help.about", "F1"), r4.merged());
        assertTrue(Files.isDirectory(first.repoDir().resolve(".git")));
    }

    @Test
    void failuresComeBackAsErrors(@TempDir Path root) {
        SyncEngine engine = new SyncEngine(root.resolve("sync"), new Git());
        SyncEngine.Result refused = engine.sync("https://a:b@host/x.git", "main", Map.of());
        assertFalse(refused.ok());
        assertEquals("url.credentials", refused.error());
        SyncEngine.Result missing = engine.sync(root.resolve("nowhere").toUri().toString(), "main", Map.of());
        assertFalse(missing.ok());
        assertNotNull(missing.error());
    }

    @Test
    void aMissingGitIsAnError(@TempDir Path root) {
        Git.Result result = new Git("no-such-git-binary").run(root, "status");
        assertEquals(-1, result.exit());
        assertFalse(result.ok());
        SyncEngine engine = new SyncEngine(root.resolve("sync"), new Git("no-such-git-binary"));
        assertFalse(engine.sync("git@example.com:x.git", "main", Map.of()).ok());
    }
}
