package com.example.app.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Work that must stay off the JavaFX application thread: file writes, reads and parsing. Each task gets a
 * virtual thread, so nothing outlives its task and a closed window leaves no thread behind. Results come
 * back with {@code Platform.runLater} or {@code AppServices.echo}, which is safe from any thread.
 */
final class Background {

    private static final Logger LOG = Logger.getLogger(Background.class.getName());
    private static final ExecutorService EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("background-", 0).factory());

    private Background() {}

    static void run(Runnable task) {
        EXECUTOR.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Background task failed", e);
            }
        });
    }
}
