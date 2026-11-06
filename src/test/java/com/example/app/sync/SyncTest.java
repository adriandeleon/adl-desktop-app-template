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
        assertEquals("credentials", SyncEngine.checkUrl("https://me:token@github.com/me/sync.git"));
        assertNull(SyncEngine.checkUrl("git@github.com:me/sync.git"));
        assertNull(SyncEngine.checkUrl("https://github.com/me/sync.git"));
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
