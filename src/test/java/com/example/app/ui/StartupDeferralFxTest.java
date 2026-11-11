package com.example.app.ui;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import com.example.app.cli.CommandLine;
import com.example.app.update.UpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first frame comes first: only what the first window shows is built before it (PERF-14), and the
 * update check, the extra font and Settings Sync wait until it has painted (PERF-15), the check a short delay longer,
 * once a session and only when allowed and due (DIAG-05).
 */
@Tag("fx")
class StartupDeferralFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @AfterEach
    void stop() throws Exception {
        if (app != null) FxTestSupport.stop(app);
    }

    @Test
    void closedPanelsAreUnbuiltAndDeferredWorkWaitsForTheFirstPaint() throws Exception {
        Startup.Prepared prepared =
                Startup.prepare(CommandLine.parse(new String[] {"--config-dir", dir.toString(), "--new-instance"}));
        List<Object> beforePaint = call(() -> {
            app = Startup.start(new Stage(), prepared, host);
            // Still inside the turn that showed the window: nothing has painted yet.
            List<Object> seen = new ArrayList<>();
            seen.add(app.afterFirstPaint().get());
            seen.add(app.window().builtMessageLog());
            seen.add(app.window().settingsWindow());
            seen.add(app.window().debugLogWindow());
            seen.add(app.services().sync().automaticStarted());
            return seen;
        });
        assertEquals(false, beforePaint.get(0), "deferred work ran before the first paint");
        assertNull(beforePaint.get(1), "the Message Log was built before it was opened");
        assertNull(beforePaint.get(2), "Settings was built before it was opened");
        assertNull(beforePaint.get(3), "the Debug Log was built before it was opened");
        assertEquals(false, beforePaint.get(4), "Settings Sync started before the first paint");

        FxTestSupport.waitUntil(() -> app.afterFirstPaint().get());
        assertTrue(Fonts.deferredLoaded(), "the extra font loads after the first paint");
        assertTrue(call(() -> app.services().sync().automaticStarted()), "Settings Sync starts after it");
        assertNull(call(() -> app.window().builtMessageLog()), "painting builds no closed panel");
        run(app.window()::openMessageLog);
        assertTrue(call(() -> app.window().builtMessageLog() != null));
    }

    @Test
    void theStartsFileWorkIsReadAheadOffTheFxThreadAndTheExitWaitsOnAThreadOfItsOwn() throws Exception {
        CommandLine cli = CommandLine.parse(new String[] {"--config-dir", dir.toString(), "--new-instance"});
        IllegalStateException refused = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> call(() -> Startup.prepare(cli)));
        assertTrue(refused.getMessage().contains("FX thread"));

        Startup.readAhead(cli); // as main does, before launch
        Startup.Prepared prepared = Startup.takePrepared(cli);
        assertEquals(dir, prepared.dir());
        assertEquals(
                com.example.app.config.ConfigStore.LoadResult.DEFAULTS,
                prepared.store().loadResult());
        app = call(() -> Startup.start(new Stage(), prepared, host));
        run(() -> app.services().store().update(s -> s.showStatusBar = false));
        // stop() makes the last save itself, on the FX thread, the one wait PERF-01 allows there; every thread
        // the app starts is a daemon (PERF-04), so nothing else keeps the JVM up.
        run(app::close);
        assertTrue(java.nio.file.Files.readString(prepared.store().file()).contains("\"showStatusBar\" : false"));
    }

    @Test
    void theUpdateCheckRunsAfterTheFirstPaintAndTheDelayOnceASession() throws Exception {
        app = FxTestSupport.start(dir, host);
        AppServices services = app.services();
        String saved = System.getProperty(UpdateCoordinator.PROPERTY);
        AtomicInteger checks = new AtomicInteger();
        UpdateCoordinator updates = new UpdateCoordinator(
                services,
                version -> {
                    checks.incrementAndGet();
                    return new UpdateService.Outcome(null, null);
                },
                Duration.ofMillis(150));
        try {
            System.clearProperty(UpdateCoordinator.PROPERTY);
            run(() -> services.store().update(s -> s.lastUpdateCheck = 0));
            Stage second = call(() -> {
                Stage stage = new Stage();
                stage.setScene(new Scene(new StackPane(), 200, 100));
                FirstPaint.then(stage.getScene(), updates::scheduleStartupCheck);
                stage.show();
                return stage;
            });
            assertEquals(0, checks.get(), "checked before the first paint");
            FxTestSupport.waitUntil(() -> checks.get() == 1);
            FxTestSupport.waitUntil(() -> updates.state() == UpdateCoordinator.State.UP_TO_DATE);
            // Once a session: a second window does not check again, nor does a later schedule.
            assertFalse(call(updates::scheduleStartupCheck));
            run(second::close);
            assertEquals(1, checks.get());

            // Not when switched off, and not when the last check is under a day old.
            UpdateCoordinator off = new UpdateCoordinator(services, v -> null, Duration.ZERO);
            run(() -> services.store().update(s -> {
                s.checkForUpdates = false;
                s.lastUpdateCheck = 0;
            }));
            assertFalse(call(off::scheduleStartupCheck));
            run(() -> services.store().update(s -> {
                s.checkForUpdates = true;
                s.lastUpdateCheck = System.currentTimeMillis();
            }));
            assertFalse(call(off::scheduleStartupCheck));

            // Cancelled while it waits (the app closing): it never runs.
            AtomicInteger late = new AtomicInteger();
            UpdateCoordinator closing = new UpdateCoordinator(
                    services,
                    version -> {
                        late.incrementAndGet();
                        return new UpdateService.Outcome(null, null);
                    },
                    Duration.ofMillis(100));
            run(() -> services.store().update(s -> s.lastUpdateCheck = 0));
            assertTrue(call(closing::scheduleStartupCheck));
            run(closing::cancel);
            assertFalse(call(closing::startupCheckPending), "the startup check's timer was stopped");
            // Past the 100 ms delay, waited for on the FX thread's own timeline, never a sleep (QUAL-07).
            java.util.concurrent.CountDownLatch past = new java.util.concurrent.CountDownLatch(1);
            run(() -> {
                javafx.animation.PauseTransition wait =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
                wait.setOnFinished(e -> past.countDown());
                wait.play();
            });
            assertTrue(past.await(5, java.util.concurrent.TimeUnit.SECONDS));
            run(() -> {});
            assertEquals(0, late.get(), "a cancelled startup check ran");
        } finally {
            if (saved != null) System.setProperty(UpdateCoordinator.PROPERTY, saved);
        }
    }
}
