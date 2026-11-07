package com.example.app.ui;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

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

/** A closed window releases what it held: opening and closing them again leaves nothing more behind (PERF-13). */
@Tag("fx")
class WindowLifecycleFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    private record Held(int storeListeners, int updateListeners, Set<String> threads) {}

    private Held held() throws Exception {
        return call(() -> new Held(
                app.services().store().listenerCount(),
                app.services().updates().listenerCount(),
                Thread.getAllStackTraces().keySet().stream()
                        .filter(Thread::isAlive)
                        .map(Thread::getName)
                        .filter(n -> !n.startsWith("ForkJoinPool") && !n.startsWith("junit"))
                        .collect(Collectors.toSet())));
    }

    /** Opens and closes every window the app has. */
    private void cycle() throws Exception {
        MainWindow window = app.window();
        AboutDialog about = call(() -> new AboutDialog(app.services(), window.stage()));
        run(about::show);
        run(() -> about.dialog().close());
        run(window::openDebugLog);
        run(() -> window.debugLogWindow().stage().close());
        run(() -> window.openSettings(SettingsWindow.Page.KEYMAP));
        run(() -> window.settingsWindow().stage().close());
        ReportProblemWindow report = call(() -> new ReportProblemWindow(app.services(), window.stage()));
        run(report::show);
        run(() -> report.dialog().close());
        run(window::openMessageLog);
        run(() -> window.overlays().hide());
        for (int i = 0; i < 5; i++) run(() -> {});
    }

    @Test
    void closingWindowsLeavesNoListenerOrThreadBehind() throws Exception {
        Held before = held();
        cycle();
        Held once = held();
        // About listens to the update checker only while it is open.
        assertEquals(before.updateListeners(), once.updateListeners(), "About left its update listener");
        cycle();
        cycle();
        Held thrice = held();
        assertEquals(once.storeListeners(), thrice.storeListeners(), "a window adds a store listener per opening");
        assertEquals(once.updateListeners(), thrice.updateListeners());
        Set<String> extra = thrice.threads().stream()
                .filter(t -> !once.threads().contains(t))
                .collect(Collectors.toSet());
        assertTrue(extra.isEmpty(), "threads left running: " + extra);
    }
}
