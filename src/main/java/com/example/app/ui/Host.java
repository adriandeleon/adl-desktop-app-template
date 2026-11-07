package com.example.app.ui;

import java.nio.file.Path;

import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

/**
 * What the app asks of the desktop: a browser, file choosers, exiting. One seam, so the headless tests
 * replace it and nothing in a test opens a browser or a native dialog.
 */
public interface Host {

    /** Opens a web page, or a file or folder ({@code file:} URI) in the system's handler. */
    void openUrl(String url);

    /** Asks where to save; null when cancelled. */
    @Nullable
    Path chooseSaveFile(Window owner, String title, String initialName);

    /** Asks for a file to open; null when cancelled. */
    @Nullable
    Path chooseOpenFile(Window owner, String title);

    /**
     * Asks a question before a destructive action ({@link Confirm}); true only when the user pressed
     * the action button. The tests answer it instead of pressing a native button.
     */
    boolean confirm(@Nullable Window owner, Confirmations.Question question);

    /** Ends the app. */
    void exit();
}
