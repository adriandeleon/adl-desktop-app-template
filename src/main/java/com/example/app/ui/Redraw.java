package com.example.app.ui;

import javafx.application.Platform;

/**
 * Coalesces redraws (PERF-06): any number of requests before the FX thread is next free run the action
 * once, through a {@code pending} flag. FX thread only.
 */
final class Redraw {

    private final Runnable action;
    private boolean pending;

    Redraw(Runnable action) {
        this.action = action;
    }

    void request() {
        if (pending) return;
        pending = true;
        Platform.runLater(() -> {
            pending = false;
            action.run();
        });
    }

    boolean pending() {
        return pending;
    }
}
