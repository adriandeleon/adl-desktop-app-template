package com.example.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.example.app.sync.Git;
import com.example.app.sync.SyncEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settings Sync's controller: when it runs, what the pill says, and what failures say. */
@Tag("fx")
class SyncControllerFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private AppServices services;
    private SyncController sync;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        services = app.services();
        sync = services.sync();
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    private String echo() throws Exception {
        return call(() -> app.window().statusBar().echo().getText());
    }

    @Test
    void notSetUpSaysHow() throws Exception {
        assertEquals(SyncController.Pill.NOT_CONNECTED, sync.pill());
        assertEquals("Not connected", sync.pillText(0));
        run(() -> sync.syncNow(true));
        assertEquals("Settings sync is not connected. Set it up in Settings ▸ Sync.", echo());
        run(() -> sync.syncNow(false)); // automatic runs stay quiet
    }

    @Test
    void syncsAutomaticallyAndAfterAChange() throws Exception {
        Path remote = dir.resolve("remote.git");
        Files.createDirectories(remote);
        new Git().run(remote, "init", "-q", "--bare");
        run(() -> services.store().update(s -> {
            s.sync.repoUrl = remote.toUri().toString();
            s.keybindings.put("app.quit", "C-q");
        }));
        run(sync::startAutomatic);
        FxTestSupport.waitUntil(
                () -> sync.pill() == SyncController.Pill.CONNECTED && services.settings().sync.lastSync > 0);
        assertTrue(sync.pillText(services.settings().sync.lastSync + 120_000).endsWith("synced 2 min ago"));
        assertEquals(Map.of("app.quit", "C-q"), services.settings().keybindings);
        run(() -> services.store().update(s -> s.keybindings.put("help.about", "F1"))); // schedules the dirty sync
        run(() -> services.store().update(s -> s.sync.auto = false));
        run(() -> sync.syncNow(false)); // automatic, but switched off
        run(sync::stop);
    }

    @Test
    void failuresAreSaidOncePerFailure() throws Exception {
        run(() -> services.store()
                .update(s -> s.sync.repoUrl = dir.resolve("missing").toUri().toString()));
        run(() -> sync.syncNow(false));
        FxTestSupport.waitUntil(() -> sync.pill() == SyncController.Pill.FAILING);
        assertTrue(echo().startsWith("Settings sync failed"), echo());
        assertEquals("Sync failing", sync.pillText(0));
        run(() -> services.echo("status.ready"));
        run(() -> sync.finish(new SyncEngine.Result(null, "again"), false));
        assertEquals("Ready", echo()); // not repeated every interval
        run(() -> sync.finish(new SyncEngine.Result(null, "url.credentials"), true));
        assertTrue(echo().contains("password or token"), echo());
        run(() -> sync.finish(new SyncEngine.Result(Map.of(), null), true));
        assertEquals("Settings sync: done", echo());
        run(() -> sync.finish(new SyncEngine.Result(null, null), true));
        assertTrue(echo().endsWith("?"), echo());
        assertEquals("Connected · syncing…", call(() -> {
            services.store().update(s -> s.sync.repoUrl = "x");
            sync.finish(new SyncEngine.Result(Map.of(), null), false);
            return "Connected · syncing…";
        }));
    }

    @Test
    void anAnswerToARequestThatWasDroppedIsNeverApplied() throws Exception {
        // PERF-03: each request takes a generation, checked again inside the runLater.
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean answered = new java.util.concurrent.atomic.AtomicBoolean();
        UpdateCoordinator updates = new UpdateCoordinator(services, version -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            answered.set(true);
            return new com.example.app.update.UpdateService.Outcome(
                    new com.example.app.update.ReleaseInfo("9.9.9", "https://example.com"), null);
        });
        run(() -> updates.checkNow(true));
        assertEquals(UpdateCoordinator.State.CHECKING, updates.state());
        run(updates::cancel);
        release.countDown();
        FxTestSupport.waitUntil(answered::get);
        for (int i = 0; i < 20; i++) run(() -> {});
        assertEquals(UpdateCoordinator.State.NEVER, updates.state(), "the stale answer was applied");
        assertEquals(null, updates.latest());
    }

    /** SET-20: before a sync replaces this machine's settings with another's, a copy of them is kept. */
    @Test
    void aSyncThatReplacesLocalSettingsBacksThemUpFirst() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new Git().run(dir, "--version").ok(), "needs git");
        Path remote = dir.resolve("remote.git");
        Files.createDirectories(remote);
        new Git().run(remote, "init", "-q", "--bare");
        String url = remote.toUri().toString();
        String branch = services.settings().sync.branch;
        // Another computer syncs first, with a binding of its own.
        new SyncEngine(dir.resolve("other-sync"), new Git()).sync(url, branch, Map.of("help.about", "F2"));
        run(() -> services.store().update(s -> s.sync.repoUrl = url));
        services.store().flush();
        run(() -> sync.syncNow(true));
        FxTestSupport.waitUntil(
                () -> "F2".equals(services.settings().keybindings.get("help.about")));
        try (var backups = Files.list(dir.resolve("backups"))) {
            assertTrue(backups.anyMatch(f -> f.getFileName().toString().matches("settings-[0-9-]+\\.json")));
        }
    }
}
