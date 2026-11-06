package com.example.app.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.example.app.cli.CommandLine;
import com.example.app.config.ThemeChoice;
import com.example.app.i18n.Messages;
import org.testfx.api.FxToolkit;

/**
 * Plumbing for the headless FX tests (tagged {@code fx}): boots JavaFX once on the built-in Headless
 * glass platform ({@code -Dglass.platform=Headless}, no display), runs work on the FX thread, and
 * starts the app the way a launch does, in a temp config folder with a {@link FakeHost}.
 */
final class FxTestSupport {

    private static boolean booted;

    private FxTestSupport() {}

    static synchronized void boot() throws Exception {
        if (booted) return;
        DebugLog.install(); // as Main does first: the Debug Log tests need its handler
        FxToolkit.registerPrimaryStage();
        run(() -> {
            Fonts.load();
            Messages.init("en");
            Themes.apply(ThemeChoice.LIGHT);
        });
        booted = true;
    }

    /** Runs {@code task} on the FX thread and waits, rethrowing its failure. */
    static void run(Runnable task) throws Exception {
        call(() -> {
            task.run();
            return null;
        });
    }

    static <T> T call(Callable<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) return task.call();
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(task.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("FX task timed out");
        if (error.get() instanceof Exception e) throw e;
        if (error.get() != null) throw new RuntimeException(error.get());
        return result.get();
    }

    /** Waits (bounded) for background work that reports back on the FX thread. */
    static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!call(condition::getAsBoolean)) {
            if (System.nanoTime() > deadline) throw new AssertionError("condition not met in 30 s");
            Thread.onSpinWait();
            run(() -> {});
        }
    }

    /** Starts the app in {@code dir}, as {@code App.start} would. */
    static Startup.Running start(Path dir, FakeHost host, String... extraArgs) throws Exception {
        List<String> args = new ArrayList<>(List.of("--config-dir", dir.toString(), "--new-instance"));
        args.addAll(List.of(extraArgs));
        return call(() -> Startup.start(new Stage(), CommandLine.parse(args.toArray(String[]::new)), host));
    }

    /** Stops the app and closes every window it opened. */
    static void stop(Startup.Running running) throws Exception {
        run(() -> {
            running.close();
            for (Window window : new ArrayList<>(Window.getWindows())) {
                if (window instanceof Stage stage && stage != primary()) stage.close();
                else if (!(window instanceof Stage)) window.hide();
            }
            Messages.init("en");
        });
    }

    private static Stage primary() {
        try {
            return FxToolkit.toolkitContext().getRegisteredStage();
        } catch (RuntimeException e) {
            return new Stage();
        }
    }

    static KeyEvent key(KeyCode code, boolean shift, boolean ctrl, boolean alt, boolean meta) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, alt, meta);
    }

    static KeyEvent press(EventTarget target, KeyCode code, boolean shift, boolean ctrl, boolean alt, boolean meta) {
        KeyEvent event = key(code, shift, ctrl, alt, meta);
        Event.fireEvent(target, event);
        return event;
    }
}
