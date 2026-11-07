package com.example.app.ui;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Text-driven work waits for typing to pause (PERF-06): every request restarts the wait, and the
 * action runs once when it ends. FX thread only.
 */
final class Debounce {

    /** How long typing has to pause before a filter or a search runs. */
    static final Duration TYPING_PAUSE = Duration.millis(120);

    /**
     * Runs requests at once instead of after the pause. The headless UI tests set it, so a test that
     * types and then reads the result needs no waiting; {@code DebounceFxTest} covers the pause itself.
     */
    static volatile boolean immediate;

    private final PauseTransition pause;
    private final Runnable action;

    Debounce(Runnable action) {
        this(TYPING_PAUSE, action);
    }

    Debounce(Duration wait, Runnable action) {
        this.action = action;
        this.pause = new PauseTransition(wait);
        pause.setOnFinished(e -> action.run());
    }

    /** Asks for the action: it runs once the requests stop for the pause. */
    void request() {
        if (immediate) {
            pause.stop();
            action.run();
            return;
        }
        pause.playFromStart();
    }

    /** Whether a run is waiting for the pause to end. */
    boolean pending() {
        return pause.getStatus() == Animation.Status.RUNNING;
    }

    /** Drops a waiting action: nothing runs. */
    void cancel() {
        pause.stop();
    }

    /** Runs a waiting action now (Enter in a filter field must act on what was typed). */
    void flush() {
        if (!pending()) return;
        pause.stop();
        action.run();
    }
}
