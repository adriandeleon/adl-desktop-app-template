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
import com.example.app.config.ConfigDir;
import com.example.app.config.ConfigStore;
import com.example.app.i18n.Messages;
import com.example.app.instance.SingleInstance;
import org.jspecify.annotations.Nullable;

/**
 * Builds the running app from the command line: config folder, Debug Log file, settings, language,
 * theme, the main window, single-instance hand-off, the update check and sync. Apart from
 * {@code App}, so the headless tests start the app exactly the way a launch does.
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
            @Nullable SingleInstance instance) implements AutoCloseable {
        @Override
        public void close() {
            services.sync().stop();
            services.store().flush();
            if (instance != null) instance.close();
            DebugLog.onUncaught(null);
        }
    }

    private Startup() {}

    public static Running start(Stage stage, CommandLine cli, Host host) {
        // Before any stylesheet: CSS naming a font that is not registered yet falls back silently.
        Fonts.load();
        Themes.installUiFont();
        Path dir = ConfigDir.resolve(cli.configDir(), cli.dev());
        DebugLog.attachFile(dir);
        ConfigStore store = ConfigStore.open(dir);
        Messages.init(
                Messages.resolve(store.settings().language, Locale.getDefault().getLanguage()));
        Themes.apply(store.settings().theme);

        AppServices services = new AppServices(store, host);
        MainWindow window = new MainWindow(stage, services, !cli.noSession());
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

        SingleInstance instance = cli.newInstance()
                ? null
                : SingleInstance.listen(dir, args -> Platform.runLater(() -> window.handOff(args)));
        services.updates().scheduleStartupCheck();
        services.sync().startAutomatic();
        smokeTest(System.getProperty(SMOKE_TEST), host);
        aotTrain(System.getProperty(AOT_TRAIN), host);
        return new Running(services, window, instance);
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
