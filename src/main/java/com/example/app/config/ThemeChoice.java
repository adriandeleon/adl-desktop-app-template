package com.example.app.config;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.jspecify.annotations.Nullable;

/**
 * The app theme. {@link #SYSTEM}, the default, follows the operating system's light or dark mode;
 * {@link #LIGHT} and {@link #DARK} are the Caret &amp; Ink pair ({@code editora-light.css},
 * {@code editora-dark.css}).
 */
public enum ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK;

    /** The theme a first run starts with, and the one an unknown saved name falls back to. */
    public static final ThemeChoice DEFAULT = SYSTEM;

    /**
     * The theme a saved name stands for. A name this version does not know (a theme from a newer
     * version, a hand-edited file, a synced file from elsewhere) is the {@link #DEFAULT}: one unknown
     * value must not make the whole settings file unreadable.
     */
    @JsonCreator
    public static ThemeChoice of(@Nullable String name) {
        if (name == null) return DEFAULT;
        try {
            return valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}
