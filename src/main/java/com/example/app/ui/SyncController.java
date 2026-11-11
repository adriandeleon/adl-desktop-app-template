package com.example.app.ui;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

import com.example.app.AppInfo;
import com.example.app.i18n.Messages;
import com.example.app.sync.GitHubCli;
import com.example.app.sync.SyncEngine;
import org.jspecify.annotations.Nullable;

/**
 * Runs Settings Sync off the FX thread: by hand, after startup, shortly after a local change, and on
 * an interval. Automatic runs never prompt; a failure is said once in the status bar, not once per
 * interval.
 */
public final class SyncController {

    /** The status pill: green, amber or red in Settings. */
    public enum Pill {
        NOT_CONNECTED,
        CONNECTED,
        SYNCING,
        FAILING
    }

    static final Duration DIRTY_DELAY = Duration.seconds(30);

    private final AppServices services;
    private final SyncEngine engine;
    private final GitHubCli gitHub;
    private boolean creating;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final PauseTransition dirty = new PauseTransition(DIRTY_DELAY);
    private @Nullable Timeline interval;
    private boolean running;
    /** The current sync or create request; a result for an older one is dropped (PERF-03). */
    private final AtomicLong generation = new AtomicLong();

    private @Nullable String lastError;
    private String lastBindings;

    SyncController(AppServices services, SyncEngine engine, GitHubCli gitHub) {
        this.services = services;
        this.engine = engine;
        this.gitHub = gitHub;
        this.lastBindings = String.valueOf(services.settings().keybindings);
        dirty.setOnFinished(e -> syncNow(false));
        services.store().addListener(settings -> {
            String now = String.valueOf(settings.keybindings);
            if (!now.equals(lastBindings) && !running) {
                lastBindings = now;
                if (isConfigured() && settings.sync.auto) dirty.playFromStart();
            }
        });
    }

    public boolean isConfigured() {
        return !services.settings().sync.repoUrl.isBlank();
    }

    public @Nullable String lastError() {
        return lastError;
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public Pill pill() {
        if (running) return Pill.SYNCING;
        if (lastError != null) return Pill.FAILING;
        return isConfigured() ? Pill.CONNECTED : Pill.NOT_CONNECTED;
    }

    /** The pill's text: "Connected · synced 3 min ago", "Not connected", "Sync failing", "syncing…". */
    public String pillText(long nowMs) {
        return switch (pill()) {
            case NOT_CONNECTED -> Messages.tr("settings.sync.pill.notConnected");
            case SYNCING -> Messages.tr("settings.sync.pill.syncing");
            case FAILING -> Messages.tr("settings.sync.pill.failing");
            case CONNECTED -> {
                long last = services.settings().sync.lastSync;
                yield last <= 0
                        ? Messages.tr("settings.sync.pill.connected")
                        : Messages.tr("settings.sync.pill.synced", Math.max(0, (nowMs - last) / 60_000));
            }
        };
    }

    /** Starts the automatic runs: one now (after startup) and then every interval. */
    public void startAutomatic() {
        if (interval != null) interval.stop();
        int minutes = Math.max(1, services.settings().sync.intervalMinutes);
        Timeline timeline = new Timeline(new KeyFrame(Duration.minutes(minutes), e -> syncNow(false)));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        interval = timeline;
        if (isConfigured() && services.settings().sync.auto) syncNow(false);
    }

    /** Whether the automatic runs have started: not before the first paint (PERF-15). */
    boolean automaticStarted() {
        return interval != null;
    }

    /** Stops the automatic runs; a sync or create still in flight is never applied. */
    public void stop() {
        if (interval != null) interval.stop();
        dirty.stop();
        generation.incrementAndGet();
        running = false;
        creating = false;
    }

    /** Runs a sync now. A manual run with nothing set up says how to set it up. */
    public void syncNow(boolean manual) {
        if (running) return;
        if (!isConfigured()) {
            if (manual) services.echo("status.sync.notSetUp");
            return;
        }
        if (!manual && !services.settings().sync.auto) return;
        running = true;
        fire();
        String url = services.settings().sync.repoUrl;
        String branch = services.settings().sync.branch;
        Map<String, String> local = new TreeMap<>(services.settings().keybindings);
        long mine = generation.incrementAndGet();
        Thread.ofPlatform().daemon().name("settings-sync").start(() -> {
            SyncEngine.Result result = engine.sync(url, branch, local);
            // SET-20: before a sync replaces this machine's settings, a copy of them is kept.
            if (result.merged() != null && !result.merged().equals(local)) backUp();
            Platform.runLater(() -> {
                if (mine == generation.get()) finish(result, manual);
            });
        });
    }

    void finish(SyncEngine.Result result, boolean manual) {
        boolean wasFailing = lastError != null;
        Map<String, String> merged = result.merged();
        if (merged != null) {
            lastError = null;
            lastBindings = String.valueOf(new TreeMap<>(merged));
            services.store().update(s -> {
                s.keybindings = new TreeMap<>(merged);
                s.sync.lastSync = System.currentTimeMillis();
            });
            if (manual) services.echo("status.sync.done");
        } else {
            String error = result.error() == null ? "?" : result.error();
            boolean known = error.startsWith("url.") || error.startsWith("data.");
            lastError = known ? Messages.tr("settings.sync." + error, AppInfo.NAME) : error;
            if (manual || !wasFailing) services.echo("status.sync.syncFailed", lastError);
        }
        running = false;
        fire();
    }

    /** How many backups a sync keeps; the oldest go first. */
    static final int BACKUPS_KEPT = 10;

    /**
     * Copies {@code settings.json} to {@code backups/settings-<time>.json} before a sync replaces what this
     * machine has (SET-20), keeping the newest {@link #BACKUPS_KEPT}. Off the FX thread.
     */
    private void backUp() {
        java.nio.file.Path file = services.store().file();
        java.nio.file.Path folder = services.store().dir().resolve("backups");
        try {
            if (!java.nio.file.Files.isRegularFile(file)) return;
            java.nio.file.Files.createDirectories(folder);
            String stamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
            java.nio.file.Files.copy(file, folder.resolve("settings-" + stamp + ".json"));
            try (var files = java.nio.file.Files.list(folder)) {
                List<java.nio.file.Path> kept = files.filter(
                                f -> f.getFileName().toString().startsWith("settings-"))
                        .sorted(java.util.Comparator.reverseOrder())
                        .toList();
                for (java.nio.file.Path old : kept.subList(Math.min(BACKUPS_KEPT, kept.size()), kept.size())) {
                    java.nio.file.Files.deleteIfExists(old);
                }
            }
        } catch (java.io.IOException e) {
            java.util.logging.Logger.getLogger(SyncController.class.getName())
                    .log(java.util.logging.Level.WARNING, "Could not back up " + file + " before a sync", e);
        }
    }

    /** The name "Create on GitHub" suggests: {@code <app>-sync}. */
    public static String suggestedRepoName() {
        return AppInfo.ID + "-sync";
    }

    /**
     * Creates a private repository on GitHub with the user's gh, connects sync to it and runs the first
     * sync. Off the FX thread; the outcome is said in the status bar.
     */
    public void createOnGitHub(String name) {
        if (creating) return;
        creating = true;
        services.echo("status.sync.creating", name);
        String description = Messages.tr("settings.sync.repoDescription", AppInfo.NAME);
        long mine = generation.incrementAndGet();
        Thread.ofPlatform().daemon().name("github-create").start(() -> {
            GitHubCli.Created created = gitHub.createPrivate(name.strip(), description);
            Platform.runLater(() -> {
                if (mine == generation.get()) created(created);
            });
        });
    }

    void created(GitHubCli.Created created) {
        creating = false;
        switch (created.problem()) {
            case NONE -> {
                services.store().update(s -> s.sync.repoUrl = created.url());
                services.echo("status.sync.created", created.url());
                syncNow(true);
            }
            case BAD_NAME -> services.echo("status.sync.invalidRepoName", created.detail());
            case NOT_INSTALLED -> services.echo("status.sync.cannotFindGh");
            case NOT_SIGNED_IN -> services.echo("status.sync.cannotUseGh");
            case FAILED -> services.echo("status.sync.createFailed", created.detail());
        }
        fire();
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
