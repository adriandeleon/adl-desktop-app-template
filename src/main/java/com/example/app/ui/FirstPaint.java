package com.example.app.ui;

import javafx.application.Platform;
import javafx.scene.Scene;

/**
 * Runs what nobody waits for after the first frame (PERF-15): the update check and the fonts the first
 * window does not show wait until the scene's first pulse has laid it out and handed it to the
 * renderer, then run on the FX thread's next turn.
 */
final class FirstPaint {

    private FirstPaint() {}

    /** Runs {@code action} once, after {@code scene}'s next pulse. FX thread only. */
    static void then(Scene scene, Runnable action) {
        boolean[] done = {false};
        Runnable[] listener = new Runnable[1];
        listener[0] = () -> {
            if (done[0]) return;
            done[0] = true;
            // Removed on the next turn: the scene is still walking its pulse listeners now.
            Platform.runLater(() -> {
                scene.removePostLayoutPulseListener(listener[0]);
                action.run();
            });
        };
        scene.addPostLayoutPulseListener(listener[0]);
        Platform.requestNextPulse();
    }
}
