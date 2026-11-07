package com.example.app.ui;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Logger;

import javafx.animation.PauseTransition;
import javafx.application.Platform;

import com.example.app.AppInfo;
import com.example.app.update.ReleaseInfo;
import com.example.app.update.UpdateCheck;
import com.example.app.update.UpdateService;
import org.jspecify.annotations.Nullable;

/**
 * The update check, once per app session: at most once a day, a few seconds after the first frame,
 * and only while the setting is on. A newer release shows a status-bar notice and a line in About;
 * "Check for Updates" runs it by hand. It never downloads or installs anything.
 */
public final class UpdateCoordinator {

    /** What About shows. */
    public enum State {
        NEVER,
        CHECKING,
        AVAILABLE,
        UP_TO_DATE,
        FAILED
    }

    /** The system property the test suite sets to {@code off}, so no test reaches GitHub. */
    public static final String PROPERTY = AppInfo.ID + ".updateCheck";

    static final Duration STARTUP_DELAY = Duration.ofSeconds(5);

    private static final Logger LOG = Logger.getLogger(UpdateCoordinator.class.getName());

    private final AppServices services;
    private final Function<String, UpdateService.Outcome> check;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private State state = State.NEVER;
    private @Nullable ReleaseInfo latest;
    private @Nullable String error;
    private boolean scheduled;
    /** The current request; an answer to an older one is dropped (PERF-03). */
    private final AtomicLong generation = new AtomicLong();

    private final Duration startupDelay;

    UpdateCoordinator(AppServices services, Function<String, UpdateService.Outcome> check) {
        this(services, check, STARTUP_DELAY);
    }

    UpdateCoordinator(AppServices services, Function<String, UpdateService.Outcome> check, Duration startupDelay) {
        this.services = services;
        this.check = check;
        this.startupDelay = startupDelay;
    }

    public State state() {
        return state;
    }

    public @Nullable ReleaseInfo latest() {
        return latest;
    }

    public @Nullable String error() {
        return error;
    }

    /** When the last check ran (epoch ms), or 0. */
    public long lastChecked() {
        return services.settings().lastUpdateCheck;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** Whether the automatic check may run at all: the setting, and not switched off for tests. */
    public boolean automaticAllowed() {
        return services.settings().checkForUpdates && !"off".equals(System.getProperty(PROPERTY));
    }

    /** Schedules the automatic check once per session, if allowed and a day has passed. Returns whether it did. */
    public boolean scheduleStartupCheck() {
        if (scheduled || !automaticAllowed()) return false;
        if (!UpdateCheck.isDue(lastChecked(), System.currentTimeMillis())) return false;
        scheduled = true;
        PauseTransition delay = new PauseTransition(javafx.util.Duration.millis(startupDelay.toMillis()));
        delay.setOnFinished(e -> checkNow(false));
        delay.play();
        return true;
    }

    /** Runs a check now, off the FX thread. {@code manual} checks also report "up to date" and failures. */
    public void checkNow(boolean manual) {
        if (state == State.CHECKING) return;
        state = State.CHECKING;
        fire();
        long mine = generation.incrementAndGet();
        Thread.ofPlatform().daemon().name("update-check").start(() -> {
            UpdateService.Outcome outcome = check.apply(AppInfo.VERSION);
            Platform.runLater(() -> {
                if (mine == generation.get()) finish(outcome, manual);
            });
        });
    }

    /** Drops the check in flight, if any: its answer is never applied. */
    public void cancel() {
        generation.incrementAndGet();
        if (state == State.CHECKING) {
            state = State.NEVER;
            fire();
        }
    }

    /** How many listeners are registered (a closed About must leave none, PERF-13). */
    int listenerCount() {
        return listeners.size();
    }

    void finish(UpdateService.Outcome outcome, boolean manual) {
        services.store().update(s -> s.lastUpdateCheck = System.currentTimeMillis());
        latest = outcome.newer();
        error = outcome.error();
        if (latest != null) {
            state = State.AVAILABLE;
            services.echo("status.updateAvailable", latest.version());
        } else if (error != null) {
            state = State.FAILED;
            LOG.info("Update check failed: " + error);
            if (manual) services.echo("status.updateCheckFailed", error);
        } else {
            state = State.UP_TO_DATE;
            if (manual) services.echo("status.upToDate");
        }
        fire();
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
