package com.example.app.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Platform;
import javafx.stage.Stage;

import com.example.app.AppInfo;
import com.example.app.cli.CommandLine;
import com.example.app.command.Keymap;
import com.example.app.config.ConfigDir;
import com.example.app.config.ConfigStore;
import com.example.app.i18n.Messages;
import com.example.app.instance.SingleInstance;
import org.jspecify.annotations.Nullable;

/**
 * Builds the running app from the command line in two steps: {@link #prepare}, the file work (config
 * folder, Debug Log file, settings, language, single-instance socket) off the FX thread, and
 * {@link #start}, the theme and the main window on it, with the update check and sync after the first
 * paint. Apart from {@code App}, so the headless tests start the app exactly the way a launch does.
 */
public final class Startup {

    /** {@code -D<app>.smokeTest=<file>}: write {@code window-shown} there once the window is up, then exit. */
    public static final String SMOKE_TEST = AppInfo.ID + ".smokeTest";

    /**
     * {@code -D<app>.aotTrain=1}: the AOT training run ({@code scripts/aot-train.sh}). Exit once the
     * window has been up long enough to load what a real start loads: the JavaFX scene, control and
     * CSS classes that are worth archiving are not touched until something paints.
     */
    public static final String AOT_TRAIN = AppInfo.ID + ".aotTrain";

    static final javafx.util.Duration AOT_SETTLE = javafx.util.Duration.millis(2500);

    private static final Logger LOG = Logger.getLogger(Startup.class.getName());

    /** What is running, so {@code App.stop} can close it. */
    public record Running(
            AppServices services,
            MainWindow window,
            @Nullable SingleInstance instance,
            java.util.concurrent.atomic.AtomicBoolean afterFirstPaint)
            implements AutoCloseable {
        /**
         * Stops what runs in the background. {@code App.stop} calls it on the FX thread, so the last save is
         * waited for on a thread of its own (PERF-01), which keeps the JVM up until it is on disk.
         */
        @Override
        public void close() {
            services.updates().cancel(); // a startup check still waiting would run after the app closed
            services.sync().stop();
            services.store().flushOnExit();
            if (instance != null) instance.close();
            DebugLog.onUncaught(null);
        }
    }

    /**
     * The file work of a start, done before the window is built and off the FX thread (PERF-01, PERF-16):
     * the config folder, the session log, the settings file, the language and the single-instance socket.
     */
    public record Prepared(
            CommandLine cli,
            Path dir,
            ConfigStore store,
            @Nullable SingleInstance instance,
            HandOff handOff) {}

    /** Where another launch's arguments go: the main window once it exists, always on the FX thread. */
    public static final class HandOff implements java.util.function.Consumer<java.util.List<String>> {
        private volatile @Nullable MainWindow window;

        @Override
        public void accept(java.util.List<String> args) {
            Platform.runLater(() -> {
                MainWindow to = window;
                if (to != null) to.handOff(args);
            });
        }
    }

    /** The read {@code main} started before {@code launch}, or null. */
    private static java.util.concurrent.@Nullable CompletableFuture<Prepared> readAhead;

    private Startup() {}

    /**
     * Starts {@link #prepare} on a thread of its own, so the settings file is read while the toolkit starts
     * (PERF-16). {@code main} calls it just before {@code launch}.
     */
    public static synchronized void readAhead(CommandLine cli) {
        readAhead = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> prepare(cli),
                runnable -> Thread.ofPlatform().name("read-ahead").start(runnable));
    }

    /**
     * What {@link #readAhead} read, or a read done now when there was none. {@code App.init} calls it on
     * the launcher thread, never the FX thread.
     */
    public static synchronized Prepared takePrepared(CommandLine cli) {
        java.util.concurrent.CompletableFuture<Prepared> ahead = readAhead;
        readAhead = null;
        return ahead != null ? ahead.join() : prepare(cli);
    }

    /** The start's file work. Blocking: never on the FX thread, which it refuses (PERF-01). */
    public static Prepared prepare(CommandLine cli) {
        if (Platform.isFxApplicationThread()) throw new IllegalStateException("the start's file work on the FX thread");
        Path dir = ConfigDir.resolve(cli.configDir(), cli.dev());
        DebugLog.attachFile(dir);
        ConfigStore store = ConfigStore.open(dir);
        Messages.init(
                Messages.resolve(store.settings().language, Locale.getDefault().getLanguage()));
        HandOff handOff = new HandOff();
        SingleInstance instance = cli.newInstance() ? null : SingleInstance.listen(dir, handOff);
        return new Prepared(cli, dir, store, instance, handOff);
    }

    /** Builds and shows the app from what {@link #prepare} read. FX thread; no file work. */
    public static Running start(Stage stage, Prepared prepared, Host host) {
        // Before any stylesheet: CSS naming a font that is not registered yet falls back silently.
        Fonts.load();
        Themes.installUiFont();
        Path dir = prepared.dir();
        ConfigStore store = prepared.store();
        Themes.apply(store.settings().theme);

        AppServices services = new AppServices(store, host);
        MainWindow window = new MainWindow(stage, services, !prepared.cli().noSession());
        prepared.handOff().window = window;
        DebugLog.onUncaught(error -> services.echoText(
                Messages.tr("status.uncaughtError", String.valueOf(error)), MessageLog.Severity.ERROR));
        // A press anywhere outside a right-click menu closes it, in every window (MENU-06/07).
        ContextMenuDismissal.install();
        window.show();
        LOG.info(AppInfo.NAME + " " + AppInfo.VERSION + " started, config " + AppInfo.tildeHome(dir.toString()));

        switch (store.loadResult()) {
            case MIGRATED -> services.echo("status.config.migrated");
            case NEWER_BACKED_UP -> services.echo("status.config.cannotReadNewer", String.valueOf(store.backup()));
            case BROKEN_BACKED_UP -> services.echo("status.config.invalidBackedUp", String.valueOf(store.backup()));
            default -> services.echo("status.ready");
        }

        // What nobody waits for starts after the first frame has painted (PERF-15, DIAG-05): the extra
        // fonts, the keymaps, the update check and Settings Sync's first run, which starts git.
        java.util.concurrent.atomic.AtomicBoolean afterFirstPaint = new java.util.concurrent.atomic.AtomicBoolean();
        FirstPaint.then(stage.getScene(), () -> {
            afterFirstPaint.set(true);
            Fonts.loadDeferred();
            // Every keymap parsed now, off the FX thread, so picking one later parses nothing (PERF-01).
            Background.run(() -> Keymap.PRESETS.forEach(Keymap::load));
            services.updates().scheduleStartupCheck();
            services.sync().startAutomatic();
        });
        smokeTest(System.getProperty(SMOKE_TEST), host);
        aotTrain(System.getProperty(AOT_TRAIN), host);
        return new Running(services, window, prepared.instance(), afterFirstPaint);
    }

    /** Ends an AOT training run after the window has settled. */
    static void aotTrain(@Nullable String flag, Host host) {
        if (flag == null || flag.isBlank()) return;
        javafx.animation.PauseTransition settle = new javafx.animation.PauseTransition(AOT_SETTLE);
        settle.setOnFinished(e -> host.exit());
        settle.play();
    }

    /** The packaged-app smoke test: proves the JVM, JavaFX and the first window came up. */
    static void smokeTest(@Nullable String file, Host host) {
        if (file == null || file.isBlank()) return;
        Platform.runLater(() -> Background.run(() -> {
            try {
                Files.writeString(Path.of(file), "window-shown\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Smoke test could not write " + file, e);
            }
            Platform.runLater(host::exit);
        }));
    }
}
