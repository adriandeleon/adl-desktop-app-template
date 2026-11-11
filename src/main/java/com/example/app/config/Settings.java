package com.example.app.config;

import java.util.ArrayList;
import java.util.List;
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
    public static final int SCHEMA_VERSION = 7;

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

    /** The main toolbar under the menus (TBAR-18). */
    public boolean showToolbar = true;

    public boolean showStatusBar = true;

    /** The strip of document tabs (TABS-12). */
    public boolean showTabBar = true;

    /**
     * The menus and the title in one bar drawn by the app (JavaFX's {@code HeaderBar}), where the
     * platform supports it and the menus are in the window (not macOS). Applies on the next start.
     */
    public boolean titleBarMenus = true;

    /**
     * Simple mode (MENU-04, TBAR-16): a shorter menu bar and fewer toolbar groups. It hides, it never
     * changes another preference, so turning it off brings back the window as it was.
     */
    public boolean simpleMode;

    /** The UI zoom (WIN-12), in percent: it scales the whole chrome, the menus included. */
    public int uiZoom = DEFAULT_UI_ZOOM;

    /** The UI zoom's default and its range, in percent. */
    public static final int DEFAULT_UI_ZOOM = 100;

    public static final int MIN_UI_ZOOM = 80;

    public static final int MAX_UI_ZOOM = 200;

    /** {@code percent} within the UI zoom's range. */
    public static int clampUiZoom(int percent) {
        return Math.min(MAX_UI_ZOOM, Math.max(MIN_UI_ZOOM, percent));
    }

    /** Machine-local: never synced. */
    public Window window = new Window();

    public Sync sync = new Sync();

    /** The window's size as read from the file is kept within these, in pixels (SET-11). */
    public static final double MIN_WINDOW_SIZE = 320;

    public static final double MAX_WINDOW_SIZE = 16_384;

    /** A saved position further from the origin than this is dropped, and the OS places the window. */
    public static final double MAX_WINDOW_POSITION = 100_000;

    /** The longest sync interval: a day. */
    public static final int MAX_SYNC_INTERVAL_MINUTES = 24 * 60;

    /** A tool window's share of the window, and a side's split, are kept within these. */
    public static final double MIN_SHARE = 0.05;

    public static final double MAX_SHARE = 0.95;

    /** The sides a tool window can be moved to ({@code ToolWindow.Side}'s names). */
    static final java.util.Set<String> SIDES = java.util.Set.of("LEFT", "RIGHT", "BOTTOM");

    /**
     * Brings every value read from the file into its range (SET-11): the file is plain JSON that people edit
     * by hand, so a number out of range is clamped, and a value this version does not know (a keymap, a
     * language, a chord, a branch name, a side) falls back to its default, as does a {@code null} that
     * Jackson writes into a field when the file says so. Called on every read, not only when a value is set
     * from the UI. Returns this.
     */
    public Settings clamped() {
        if (theme == null) theme = ThemeChoice.DEFAULT;
        if (language == null
                || !(language.isEmpty()
                        || com.example.app.i18n.Messages.available().containsKey(language))) {
            language = "";
        }
        if (keymap == null || !com.example.app.command.Keymap.PRESETS.contains(keymap)) {
            keymap = com.example.app.command.Keymap.DEFAULT;
        }
        Map<String, String> bindings = new TreeMap<>();
        if (keybindings != null) {
            keybindings.forEach((id, chord) -> {
                // A blank chord unbinds; any other must be keys this version knows.
                if (id == null || id.isBlank() || chord == null) return;
                if (chord.isBlank() || com.example.app.command.KeyStroke.parseSequence(chord) != null) {
                    bindings.put(id, chord);
                }
            });
        }
        keybindings = bindings;
        lastUpdateCheck = Math.max(0, lastUpdateCheck);
        uiZoom = clampUiZoom(uiZoom);
        if (window == null) window = new Window();
        window.clamp();
        if (sync == null) sync = new Sync();
        sync.clamp();
        return this;
    }

    /** {@code value} within {@code [min, max]}; {@code fallback} when it is not a number at all. */
    static double clamp(double value, double min, double max, double fallback) {
        if (Double.isNaN(value)) return fallback;
        return Math.min(max, Math.max(min, value));
    }

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
        c.uiZoom = uiZoom;
        c.keybindings = new TreeMap<>(keybindings);
        c.checkForUpdates = checkForUpdates;
        c.lastUpdateCheck = lastUpdateCheck;
        c.showToolbar = showToolbar;
        c.showStatusBar = showStatusBar;
        c.showTabBar = showTabBar;
        c.titleBarMenus = titleBarMenus;
        c.simpleMode = simpleMode;
        c.window.x = window.x;
        c.window.y = window.y;
        c.window.width = window.width;
        c.window.height = window.height;
        c.window.maximized = window.maximized;
        c.window.toolWindows = window.toolWindows.copy();
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

        /** The tool windows' arrangement, restored on the next launch (TABS-15). */
        public ToolWindows toolWindows = new ToolWindows();

        void clamp() {
            Window defaults = new Window();
            width = Settings.clamp(width, MIN_WINDOW_SIZE, MAX_WINDOW_SIZE, defaults.width);
            height = Settings.clamp(height, MIN_WINDOW_SIZE, MAX_WINDOW_SIZE, defaults.height);
            if (!onScreen(x) || !onScreen(y)) {
                x = null;
                y = null;
            }
            if (toolWindows == null) toolWindows = new ToolWindows();
            toolWindows.clamp();
        }

        private static boolean onScreen(@Nullable Double position) {
            return position != null && Double.isFinite(position) && Math.abs(position) <= MAX_WINDOW_POSITION;
        }
    }

    /**
     * The workspace state of the tool windows (TABS-15): which are open, on which side, in what order,
     * hidden or shown, floating, and every size. Machine-local, with the window it belongs to.
     */
    public static final class ToolWindows {
        /** The windows open, docked or floating, in the order they opened. */
        public List<String> open = new ArrayList<>();

        /** A window's side where the user moved it from its home: {@code LEFT}, {@code RIGHT} or {@code BOTTOM}. */
        public Map<String, String> sides = new TreeMap<>();

        /** The stripe buttons' order, one list across the three stripes; empty for the app's own order. */
        public List<String> order = new ArrayList<>();

        /** The stripe buttons the user hid. */
        public List<String> hidden = new ArrayList<>();

        /** The windows that float in a window of their own. */
        public List<String> floating = new ArrayList<>();

        /** Each window's share of the window's width (left, right) or height (bottom), once it has one. */
        public Map<String, Double> sizes = new TreeMap<>();

        /** Each side's split between its two stacked windows, by side. */
        public Map<String, Double> splits = new TreeMap<>();

        /** The maximized window's id, or blank. */
        public String maximized = "";

        void clamp() {
            open = known(open);
            order = known(order);
            hidden = known(hidden);
            floating = known(floating);
            Map<String, String> keptSides = new TreeMap<>();
            if (sides != null) {
                sides.forEach((id, side) -> {
                    if (id != null && side != null && SIDES.contains(side)) keptSides.put(id, side);
                });
            }
            sides = keptSides;
            sizes = shares(sizes);
            splits = shares(splits);
            if (maximized == null || !open.contains(maximized)) maximized = "";
        }

        /** The list without nulls, blanks or repeats. */
        private static List<String> known(@Nullable List<String> ids) {
            List<String> kept = new ArrayList<>();
            if (ids != null) {
                for (String id : ids) {
                    if (id != null && !id.isBlank() && !kept.contains(id)) kept.add(id);
                }
            }
            return kept;
        }

        /** Each share within {@link #MIN_SHARE} and {@link #MAX_SHARE}; one that is not a number is dropped. */
        private static Map<String, Double> shares(@Nullable Map<String, Double> raw) {
            Map<String, Double> kept = new TreeMap<>();
            if (raw != null) {
                raw.forEach((id, share) -> {
                    if (id != null && share != null && !share.isNaN()) {
                        kept.put(id, Settings.clamp(share, MIN_SHARE, MAX_SHARE, share));
                    }
                });
            }
            return kept;
        }

        public ToolWindows copy() {
            ToolWindows c = new ToolWindows();
            c.open = new ArrayList<>(open);
            c.sides = new TreeMap<>(sides);
            c.order = new ArrayList<>(order);
            c.hidden = new ArrayList<>(hidden);
            c.floating = new ArrayList<>(floating);
            c.sizes = new TreeMap<>(sizes);
            c.splits = new TreeMap<>(splits);
            c.maximized = maximized;
            return c;
        }
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

        void clamp() {
            if (repoUrl == null) repoUrl = "";
            if (branch == null || com.example.app.sync.SyncEngine.checkBranch(branch) != null) branch = "main";
            intervalMinutes = Math.min(MAX_SYNC_INTERVAL_MINUTES, Math.max(1, intervalMinutes));
            lastSync = Math.max(0, lastSync);
        }
    }
}
