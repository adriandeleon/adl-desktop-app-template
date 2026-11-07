package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javafx.application.Platform;

/**
 * Items made off the FX thread (status messages from a sync or an update check, log lines) reach it in
 * batches, never one {@code runLater} each (PERF-07): any thread appends to a queue, and the FX thread
 * drains it at most once every {@link #MIN_DRAIN_INTERVAL_MILLIS}.
 */
final class FxBatcher<T> {

    static final long MIN_DRAIN_INTERVAL_MILLIS = 16;

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("fx-batcher").factory());

    private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Consumer<List<T>> sink;
    private volatile long lastDrain = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(MIN_DRAIN_INTERVAL_MILLIS);

    FxBatcher(Consumer<List<T>> sink) {
        this.sink = sink;
    }

    /** Queues an item; safe from any thread. */
    void offer(T item) {
        queue.add(item);
        if (!scheduled.compareAndSet(false, true)) return;
        long wait = TimeUnit.MILLISECONDS.toNanos(MIN_DRAIN_INTERVAL_MILLIS) - (System.nanoTime() - lastDrain);
        if (wait <= 0) Platform.runLater(this::drain);
        else TIMER.schedule(() -> Platform.runLater(this::drain), wait, TimeUnit.NANOSECONDS);
    }

    private void drain() {
        lastDrain = System.nanoTime();
        scheduled.set(false); // before polling: an item offered from now on schedules the next drain
        List<T> batch = new ArrayList<>();
        for (T item; (item = queue.poll()) != null; ) batch.add(item);
        if (!batch.isEmpty()) sink.accept(batch);
    }
}
