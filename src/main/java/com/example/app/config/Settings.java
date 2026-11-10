package com.example.app.config;

import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * Preferences, stored as {@code settings.json} in the config folder.
 *
 * <p>A plain Jackson POJO with public fields: the file is the documentation. Adding a setting touches
 * five places (docs/conventions.md): this field and its default with a {@link #SCHEMA_VERSION} bump and
 * a {@link Migrations} step, the control, its row on a Settings page, the line that loads the control,
 * and a palette command, plus its strings in every catalog.
 */
public final class Settings {

    /** Bump with every change to the shape of this file, and add the matching {@link Migrations} step. */
    public static final int SCHEMA_VERSION = 4;

    public int schemaVersion = SCHEMA_VERSION;

    public ThemeChoice theme = ThemeChoice.SYSTEM;

    /** A bundled language code, or blank to follow the system. */
    public String language = "";

    /** The base keymap: {@code cua} or {@code emacs}. */
    public String keymap = "cua";

    /** The user's own bindings, command id to chord, over the base keymap. A blank chord unbinds. */
    public Map<String, String> keybindings = new TreeMap<>();

    /** The daily update check. Off means no automatic network call at all. */
    public boolean checkForUpdates = true;

    /** When the update check last ran, epoch milliseconds; 0 for never. */
    public long lastUpdateCheck;

    /** The main toolbar under the menus (TBAR-18, Pauta 5.0.0). */
    public boolean showToolbar = true;

    public boolean showStatusBar = true;

    /**
     * The menus and the title in one bar drawn by the app (JavaFX's {@code HeaderBar}), where the
     * platform supports it and the menus are in the window (not macOS). Applies on the next start.
     */
    public boolean titleBarMenus = true;

    /** Machine-local: never synced. */
    public Window window = new Window();

    public Sync sync = new Sync();

    /**
     * A deep copy, made on the FX thread so the writer serializes a snapshot off it (PERF-01). A new
     * field is copied here too: {@code ConfigStoreTest} fails when the copy's JSON differs.
     */
    public Settings copy() {
        Settings c = new Settings();
        c.schemaVersion = schemaVersion;
        c.theme = theme;
        c.language = language;
        c.keymap = keymap;
        c.keybindings = new TreeMap<>(keybindings);
        c.checkForUpdates = checkForUpdates;
        c.lastUpdateCheck = lastUpdateCheck;
        c.showToolbar = showToolbar;
        c.showStatusBar = showStatusBar;
        c.titleBarMenus = titleBarMenus;
        c.window.x = window.x;
        c.window.y = window.y;
        c.window.width = window.width;
        c.window.height = window.height;
        c.window.maximized = window.maximized;
        c.sync.repoUrl = sync.repoUrl;
        c.sync.branch = sync.branch;
        c.sync.auto = sync.auto;
        c.sync.intervalMinutes = sync.intervalMinutes;
        c.sync.lastSync = sync.lastSync;
        return c;
    }

    /** The main window's last size and position. */
    public static final class Window {
        /** Null until the window has been placed once; then the OS chooses. */
        public @Nullable Double x;

        public @Nullable Double y;

        public double width = 960;
        public double height = 640;
        public boolean maximized;
    }

    /** Settings Sync through a Git repository the user owns. */
    public static final class Sync {
        /** Blank when sync is not set up. */
        public String repoUrl = "";

        public String branch = "main";
        public boolean auto = true;
        public int intervalMinutes = 15;

        /** The interval a text means, or null for anything but a whole number of minutes from 1. */
        public static @Nullable Integer parseInterval(String text) {
            try {
                int minutes = Integer.parseInt(text.trim());
                return minutes >= 1 ? minutes : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        /** The last successful sync, epoch milliseconds; 0 for never. */
        public long lastSync;
    }
}
