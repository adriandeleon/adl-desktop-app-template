package com.example.app.ui;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-20: off unless asked, and when the thread it watches stalls, its stack is reported. */
class FxSamplerTest {

    @Test
    void itIsOffUnlessAskedForWithAThreshold() {
        assertEquals(0, FxSampler.threshold(null, null));
        assertEquals(0, FxSampler.threshold("", "nope"));
        assertEquals(250, FxSampler.threshold("250", null));
        assertEquals(300, FxSampler.threshold(null, "300"));
        assertEquals(60_000, FxSampler.threshold("999999", null));
    }

    /** A stand-in for the FX thread, blocked on a latch: the report names the method that holds it. */
    @Test
    void aStallReportsTheStackOfTheThreadItWatches() throws Exception {
        ExecutorService watched = Executors.newSingleThreadExecutor(
                r -> Thread.ofPlatform().daemon().unstarted(r));
        LinkedBlockingQueue<String> reports = new LinkedBlockingQueue<>();
        FxSampler sampler = new FxSampler(50, watched, reports::add);
        sampler.start();
        CountDownLatch release = new CountDownLatch(1);
        watched.execute(() -> holdTheThread(release));
        String report = reports.poll(10, TimeUnit.SECONDS);
        release.countDown();
        sampler.stop();
        watched.shutdown();
        assertNotNull(report, "a stall was reported");
        assertTrue(report.startsWith("FX thread busy for more than 50 ms"), report);
        assertTrue(report.contains("holdTheThread"), report);
    }

    private static void holdTheThread(CountDownLatch release) {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
