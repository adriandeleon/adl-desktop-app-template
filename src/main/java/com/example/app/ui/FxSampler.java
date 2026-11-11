package com.example.app.ui;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.example.app.AppInfo;
import org.jspecify.annotations.Nullable;

/**
 * Samples the FX thread when something stalls it (PERF-20). Off unless asked: {@code -D<app>.fxSampler=<ms>} or
 * {@code <APP>_FX_SAMPLER=<ms>}. A daemon thread pings the FX thread every {@code ms}; a ping not answered within
 * that time logs the FX thread's stack, once per stall, to the Debug Log, so what kept the window busy is there
 * to read and to attach to a report.
 */
public final class FxSampler {

    /** The system property that turns the sampler on, with its threshold in milliseconds. */
    public static final String PROPERTY = AppInfo.ID + ".fxSampler";

    /** The environment variable that does the same, for a packaged app's launcher. */
    public static final String ENV = AppInfo.ID.toUpperCase(java.util.Locale.ROOT) + "_FX_SAMPLER";

    /** How many frames of the stack a stall logs. */
    static final int FRAMES = 25;

    private final long thresholdMillis;
    private final Executor fx;
    private final Consumer<String> report;
    private final AtomicLong answered = new AtomicLong();
    private volatile @Nullable Thread fxThread;
    private volatile boolean running;

    FxSampler(long thresholdMillis, Executor fx, Consumer<String> report) {
        this.thresholdMillis = thresholdMillis;
        this.fx = fx;
        this.report = report;
    }

    /** The threshold the property or the variable asks for, or 0 for off. Pure. */
    static long threshold(@Nullable String property, @Nullable String env) {
        for (String value : new String[] {property, env}) {
            if (value == null || value.isBlank()) continue;
            try {
                long ms = Long.parseLong(value.strip());
                if (ms > 0) return Math.min(ms, 60_000);
            } catch (NumberFormatException e) {
                // not a number: not asked
            }
        }
        return 0;
    }

    /** Starts a sampler when the property or the variable asks for one; null otherwise. */
    static @Nullable FxSampler startIfAsked(Executor fx, Consumer<String> report) {
        long threshold = threshold(System.getProperty(PROPERTY), System.getenv(ENV));
        if (threshold == 0) return null;
        FxSampler sampler = new FxSampler(threshold, fx, report);
        sampler.start();
        return sampler;
    }

    void start() {
        running = true;
        fx.execute(() -> fxThread = Thread.currentThread());
        Thread.ofPlatform().daemon().name("fx-sampler").start(this::loop);
    }

    void stop() {
        running = false;
    }

    private void loop() {
        while (running) {
            long ping = System.nanoTime();
            fx.execute(() -> answered.set(ping));
            if (!pause(thresholdMillis)) return;
            if (answered.get() == ping) continue;
            Thread thread = fxThread;
            if (thread != null) report.accept(describe(thread, thresholdMillis));
            // One report per stall: wait for the FX thread to answer before pinging again.
            while (running && answered.get() != ping) {
                if (!pause(Math.max(10, thresholdMillis / 4))) return;
            }
        }
    }

    private static boolean pause(long millis) {
        try {
            Thread.sleep(millis); // the sampler's own thread, never the FX thread
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The report of a stall: how long at least, and the FX thread's stack. */
    static String describe(Thread thread, long millis) {
        return "FX thread busy for more than " + millis + " ms:\n"
                + Arrays.stream(thread.getStackTrace())
                        .limit(FRAMES)
                        .map(f -> "    at " + f)
                        .collect(Collectors.joining("\n"));
    }
}
