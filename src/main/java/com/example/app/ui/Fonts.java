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

    private static final String[] FILES = {
        "inter/Inter-Regular.ttf",
        "inter/Inter-Bold.ttf",
        "inter/Inter-Italic.ttf",
        "inter/Inter-BoldItalic.ttf",
        "jetbrains-mono/JetBrainsMono-Regular.ttf",
    };

    private Fonts() {}

    /** Registers every bundled font; returns how many loaded. */
    public static int load() {
        int loaded = 0;
        for (String file : FILES) {
            try (InputStream in = Fonts.class.getResourceAsStream("/com/example/app/fonts/" + file)) {
                if (in != null && Font.loadFont(in, 12) != null) loaded++;
            } catch (IOException e) {
                // one unreadable font is not worth failing a launch over
            }
        }
        return loaded;
    }
}
