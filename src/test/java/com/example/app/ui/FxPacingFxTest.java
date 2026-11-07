package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.util.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Work reaches the FX thread coalesced: debounced typing, one redraw per burst, batched streams (PERF-06/07). */
@Tag("fx")
class FxPacingFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
        Debounce.immediate = false;
    }

    @AfterEach
    void immediateAgain() {
        Debounce.immediate = false;
    }

    @Test
    void typingRunsTheWorkOnceItPauses() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        Debounce debounce = call(() -> new Debounce(Duration.millis(80), runs::incrementAndGet));
        run(() -> {
            for (int i = 0; i < 10; i++) debounce.request();
        });
        assertEquals(0, runs.get(), "nothing runs while keys keep coming");
        assertTrue(call(debounce::pending));
        FxTestSupport.waitUntil(() -> runs.get() == 1);
        assertFalse(call(debounce::pending));
        run(debounce::request);
        run(debounce::flush);
        assertEquals(2, runs.get(), "Enter flushes what was typed");
        run(debounce::flush);
        assertEquals(2, runs.get(), "nothing waiting, nothing run");
        Debounce.immediate = true;
        run(debounce::request);
        assertEquals(3, runs.get());
    }

    @Test
    void aBurstOfRedrawRequestsRedrawsOnce() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        Redraw redraw = new Redraw(runs::incrementAndGet);
        run(() -> {
            for (int i = 0; i < 100; i++) redraw.request();
            assertTrue(redraw.pending());
        });
        run(() -> {});
        assertEquals(1, runs.get());
        assertFalse(call(redraw::pending));
    }

    @Test
    void linesFromAnotherThreadArriveInOrderInFewBatches() throws Exception {
        List<List<Integer>> batches = new CopyOnWriteArrayList<>();
        FxBatcher<Integer> batcher = new FxBatcher<>(batches::add);
        Thread producer = Thread.ofPlatform().start(() -> {
            for (int i = 0; i < 5_000; i++) batcher.offer(i);
        });
        producer.join();
        FxTestSupport.waitUntil(() -> batches.stream().mapToInt(List::size).sum() == 5_000);
        List<Integer> all = new ArrayList<>();
        batches.forEach(all::addAll);
        for (int i = 0; i < all.size(); i++) assertEquals(i, all.get(i));
        assertTrue(batches.size() < 5_000 / 10, "one runLater per line: " + batches.size() + " batches");
    }
}
