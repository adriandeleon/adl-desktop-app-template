package com.example.app.ui;

import java.io.IOException;
import java.io.InputStream;

import javafx.scene.text.Font;

/**
 * The bundled fonts: Inter for the interface and JetBrains Mono for the About environment block, so
 * the app looks the same on every OS. {@link #load} runs before any stylesheet: CSS naming a font that
 * is not registered yet silently falls back to the system face.
 */
public final class Fonts {

    public static final String UI = "Inter";
    public static final String MONO = "JetBrains Mono";

    /** The interface faces: registered before any stylesheet, so the first window is drawn in Inter. */
    private static final String[] FILES = {
        "inter/Inter-Regular.ttf", "inter/Inter-Bold.ttf", "inter/Inter-Italic.ttf", "inter/Inter-BoldItalic.ttf",
    };

    /**
     * Faces the first window does not show (the About environment block, the Debug Log): registered after
     * the first frame has painted (PERF-15), by {@link #loadDeferred}.
     */
    private static final String[] DEFERRED = {"jetbrains-mono/JetBrainsMono-Regular.ttf"};

    private static volatile boolean deferredLoaded;

    private Fonts() {}

    /** Registers the interface faces; returns how many loaded. */
    public static int load() {
        return load(FILES);
    }

    /** Registers the faces the first window does not need; returns how many loaded. */
    public static int loadDeferred() {
        deferredLoaded = true;
        return load(DEFERRED);
    }

    /** Whether {@link #loadDeferred} has run. */
    static boolean deferredLoaded() {
        return deferredLoaded;
    }

    private static int load(String[] files) {
        int loaded = 0;
        for (String file : files) {
            try (InputStream in = Fonts.class.getResourceAsStream("/com/example/app/fonts/" + file)) {
                if (in != null && Font.loadFont(in, 12) != null) loaded++;
            } catch (IOException e) {
                // one unreadable font is not worth failing a launch over
            }
        }
        return loaded;
    }
}
