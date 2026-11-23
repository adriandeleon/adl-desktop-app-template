package com.example.app.ui;

import java.util.Map;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import org.jspecify.annotations.Nullable;

/**
 * Small vector glyphs, colored by CSS so they follow the theme. Two families, as in Editora:
 *
 * <ul>
 *   <li>{@link #line} — outline glyphs in a 16-unit box, <b>stroked</b> and colored through the
 *       {@code icon-line} style class. Every command glyph ({@link #forCommand}) is one.
 *   <li>{@link #of} — solid glyphs in a 24-unit box, <b>filled</b> and colored through
 *       {@code toolbar-icon}.
 * </ul>
 *
 * <p>A glyph node carries one family's class, never both: a line glyph is colored by its stroke, and a
 * fill rule meant for the other family would paint its outline solid. A glyph that moves family moves
 * its CSS with it.
 *
 * <p>One action wears one glyph wherever it is drawn: a menu row or a button asks
 * {@link #forCommand} (or {@link #commandGlyph}) by command id instead of naming a path itself.
 *
 * <p>An icon-only button is built here too ({@link #button}), so its tooltip and
 * its accessible text are always one string (TBAR-01, ICON-21, KBD-17): a screen reader says what a
 * sighted user reads. A hand-built icon button goes through {@link #name}.
 */
public final class Icons {

    /** Style class of the stroked family. */
    public static final String LINE = "icon-line";

    /** Style class of the filled family. */
    public static final String FILLED = "toolbar-icon";

    /**
     * Simple mode: Editora's glyph, a window whose side rail and lines are short dashes, chrome stripped
     * back to the content.
     */
    public static final String SIMPLE_MODE =
            "M1.8 2.6h12.4a1 1 0 0 1 1 1v8.8a1 1 0 0 1 -1 1h-12.4a1 1 0 0 1 -1 -1v-8.8a1 1 0 0 1 1 -1z"
                    + "M1.0 5.6h14M4.4 5.6v7.8M2.6 8.2h0.6M2.6 10.2h0.6";

    /** UI zoom: a magnifier, with a plus, a minus, or a square for the actual size. */
    public static final String ZOOM_IN = "M2.5 7a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0M10.3 10.3l3.7 3.7M5 7h4M7 5v4";

    public static final String ZOOM_OUT = "M2.5 7a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0M10.3 10.3l3.7 3.7M5 7h4";

    public static final String ZOOM_RESET =
            "M2.5 7a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0M10.3 10.3l3.7 3.7M5.3 5.3h3.4v3.4h-3.4z";

    /** Quit: a door frame and an arrow out of it. */
    public static final String QUIT = "M9.5 2.5H3v11h6.5M6.5 8h8M12 5.5L14.5 8L12 10.5";

    /** Refresh: a circle's arc with its arrowhead. */
    public static final String REFRESH = "M13 8a5 5 0 1 1 -1.5 -3.6M11.8 1.8v2.8h-2.8";

    /** Check for Updates: a circle with an arrow down into it. */
    public static final String UPDATE = "M2 8a6 6 0 1 0 12 0a6 6 0 1 0 -12 0M8 4.5v6.5M5.5 8.5L8 11l2.5 -2.5";

    /** Documentation: an open book. */
    public static final String BOOK =
            "M8 4.2c-1.6 -1.3 -3.8 -1.6 -6 -1.2v9.8c2.2 -0.4 4.4 -0.1 6 1.2c1.6 -1.3 3.8 -1.6 6 -1.2V3c-2.2 -0.4 -4.4 -0.1 -6 1.2zM8 4.2v9.8";

    /** Report a Problem: a flag on its pole. */
    public static final String FLAG = "M3.5 14.5V2M3.5 2.5h9l-2 3l2 3h-9";

    /** A key binding: a single key cap with a pencil stroke across its corner. */
    public static final String BIND_KEY = "M2 4.5h8.5v8.5H2zM5 8.5h2.5M9 14.5l5 -5l-1.5 -1.5l-5 5z";

    /** Reset: an arc turning back, its arrowhead at the start. */
    public static final String RESET = "M3 8a5 5 0 1 0 1.5 -3.6M4.2 1.8v2.8h2.8";

    /** Language: a globe, its meridian and two parallels. */
    public static final String GLOBE =
            "M2 8a6 6 0 1 0 12 0a6 6 0 1 0 -12 0M2 8h12M8 2c-2 1.8 -2 10.2 0 12M8 2c2 1.8 2 10.2 0 12M3 5h10M3 11h10";

    /** The update check: a bell. */
    public static final String BELL = "M4 11.5V7.5a4 4 0 0 1 8 0v4l1.2 1.2H2.8zM6.6 14h2.8";

    /** A Git branch: two commits on a line and one off it. */
    public static final String BRANCH =
            "M4.5 2.5v11M4.5 9.5c0 -3 7 -2 7 -5M2.8 13.5a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0"
                    + "M2.8 2.5a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0M9.8 3a1.7 1.7 0 1 0 3.4 0a1.7 1.7 0 1 0 -3.4 0";

    /** The sync interval: a clock. */
    public static final String CLOCK = "M2 8a6 6 0 1 0 12 0a6 6 0 1 0 -12 0M8 4.5V8l2.5 1.5";

    /** The sync repository: a box of stacked sheets. */
    public static final String REPOSITORY = "M3 2.5h10v11H3zM3 6h10M3 9.5h10M6.5 4.25h3M6.5 7.75h3M6.5 11.25h3";

    /** Automatic sync: two arrows in a circle round the letter A's crossbar. */
    public static final String AUTO_SYNC = "M13 6.5a5.2 5.2 0 0 0 -9.6 -1.5M3 9.5a5.2 5.2 0 0 0 9.6 1.5M3.2 2.6v2.6h2.6"
            + "M12.8 13.4v-2.6h-2.6M6.2 10.5L8 5.5l1.8 5M6.8 9h2.4";

    /** The status bar: a window with a bar along its foot. */
    public static final String STATUS_BAR = "M1.8 2.6h12.4v10.8H1.8zM1.8 10.8h12.4M3.6 12.1h4";

    /** Menus in the title bar: a window whose top bar holds short lines. */
    public static final String TITLE_BAR = "M1.8 2.6h12.4v10.8H1.8zM1.8 5.4h12.4M3.4 4h1.6M6.2 4h1.6M9 4h1.6";

    /** A text file: a sheet with lines of text on it. */
    public static final String FILE_TEXT = "M4 1.75h5.5l3 3v9.5H4zM9.5 1.75v3h3M6 8h4.5M6 10.25h4.5M6 12.5h3";

    /**
     * A file's glyph from its name alone (ICON-23): the types the app knows get their own, and any other the
     * generic document glyph, so a new type never draws a blank. Pure: unit-tested without the toolkit.
     */
    public static String forFileName(String name) {
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        return switch (extension) {
            case "md", "markdown", "txt", "text" -> FILE_TEXT;
            default -> FILE;
        };
    }

    /** The Doctor: a pulse line. */
    public static final String PULSE = "M1 8.5h3l1.8 -4.5l3 9l2 -6l1.2 1.5H15";

    /** The text commands: scissors, two sheets, a clipboard, a dashed frame round lines. */
    public static final String CUT =
            "M3 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M9 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0M6.4 10.4L11.5 2M9.6 10.4L4.5 2";

    public static final String COPY = "M5.5 5.5h8v8.5h-8zM3 11V2.5h7.5";

    public static final String PASTE = "M3.5 3h2M10.5 3h2v11h-9V3M5.5 2h5v2.5h-5zM6 8h4M6 10.5h4";

    public static final String SELECT_ALL =
            "M2 2h2M6 2h4M12 2h2v2M14 6v4M14 12v2h-2M10 14H6M4 14H2v-2M2 10V6M2 4V2M5 6h6M5 8h6M5 10h4";

    /** Clear: a sheet with a line struck out. */
    public static final String CLEAR = "M3 2h10v12H3zM5.5 6.5l5 5M10.5 6.5l-5 5";

    /** Find: a magnifier; next and previous: an arrow down and up beside a short line of text. */
    public static final String FIND = "M2.5 6.5a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M9.4 9.4l4.6 4.6";

    /**
     * Settings: Editora's cog, a toothed outline (8 teeth, a 32-point polygon) round a hub. Toothed, not rays:
     * a circle with rays is the sun, and reads as a theme toggle.
     */
    public static final String COG =
            "M6.16 3.57L6.63 1.54L9.37 1.54L9.84 3.57L11.59 2.46L13.54 4.41L12.43 6.16L14.46 6.63L14.46 9.37"
                    + "L12.43 9.84L13.54 11.59L11.59 13.54L9.84 12.43L9.37 14.46L6.63 14.46L6.16 12.43L4.41 13.54"
                    + "L2.46 11.59L3.57 9.84L1.54 9.37L1.54 6.63L3.57 6.16L2.46 4.41L4.41 2.46Z"
                    + "M5.5 8a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0";

    /** Theme: Editora's Appearance glyph, a painter's palette and its paint (dots by round caps). */
    public static final String APPEARANCE =
            "M8 1.8C4.5 1.8 1.8 4.4 1.8 7.8c0 3.3 2.7 6.4 6 6.4 1.1 0 1.7-.6 1.7-1.4 0-.9-.8-1.2-.8-2"
                    + " 0-.7.6-1.2 1.4-1.2h1.4c1.7 0 2.7-1.1 2.7-2.7 0-2.9-2.8-5.1-6.2-5.1z"
                    + "M4.9 7.6h.25M6.8 4.8h.25M9.9 4.8h.25M11.7 7.3h.25";

    /**
     * The Command Palette: Editora's keyboard, its keys as dots (round caps) and a space bar, the surface that
     * answers "what can this do". The palette's alone: Editora's Keymaps page borrows it, but a row that
     * borrows another command's glyph teaches the wrong meaning (Pauta MENU-14), so the key bindings wear
     * {@link #KEYCAP}.
     */
    public static final String KEYBOARD =
            "M1.75 4.25H14.25V11.75H1.75ZM4.25 6.75H4.5M7 6.75H7.25M9.75 6.75H10M12 6.75H12.25M5 9.25H11";

    /**
     * The key bindings, Keymap and Keyboard Shortcuts: one keycap from above, its top face set high and joined
     * to the base by four bevels.
     */
    public static final String KEYCAP = "M2.25 2.25H13.75V13.75H2.25ZM4.25 3.75H11.75V10.25H4.25Z"
            + "M2.25 2.25L4.25 3.75M13.75 2.25L11.75 3.75M2.25 13.75L4.25 10.25M13.75 13.75L11.75 10.25";

    /** Sync: Editora's two arrows chasing each other round a circle. */
    public static final String SYNC =
            "M13.2 6.6A5.4 5.4 0 0 0 3.3 5.2M2.8 9.4a5.4 5.4 0 0 0 9.9 1.4M3.1 2.6v2.8h2.8M12.9 13.4v-2.8h-2.8";

    /** The config folder. */
    public static final String FOLDER = "M1.5 3.5h4.2l1.5 2h7.3v7H1.5z";

    /** Export: an arrow leaving a tray. */
    public static final String EXPORT = "M3.5 10.6v2.4h9v-2.4M8 9.3V3M5.8 5.2L8 3l2.2 2.2";

    /** Import: an arrow landing in a tray. */
    public static final String IMPORT = "M3.5 10.6v2.4h9v-2.4M8 3v6.3M5.8 7.1l2.2 2.2 2.2-2.2";

    /** About: an "i" in a circle. */
    public static final String INFO = "M1.7 8a6.3 6.3 0 1 0 12.6 0a6.3 6.3 0 1 0 -12.6 0M8 7.4V11M8 5.1v.2";

    /** The Debug Log: a page of lines. */
    public static final String LOG = "M3.5 2.5h9v11h-9zM5.5 5.5h5M5.5 8h5M5.5 10.5h3";

    /** The Message Log: a speech bubble. */
    public static final String MESSAGES = "M2.5 3h11v7.5H7.2L4.5 13v-2.5h-2zM5 5.8h6M5 7.9h4";

    /** The Pauta window: a ruled page with a tick, the rules and how the app meets them. */
    public static final String CONFORMANCE = "M3.5 2.5h9v11h-9zM5.3 6.2l1.1 1.1 2.1-2.3M5.5 9.5h5M5.5 11.5h3.5";

    /** A file: a page with a folded corner. The tab header's file glyph. */
    public static final String FILE = "M4 1.75h5.5l3 3v9.5H4zM9.5 1.75v3h3";

    /**
     * The close variants keep a smaller X as the family's anchor and add one mark, so the six read as a family
     * and stay apart at menu size (as in Editora): a square for the others, three for all, a tick for the
     * unmodified, a triangle for the direction.
     */

    /**
     * The glyph of each command that has one. A command with no fitting glyph is left out and draws
     * none: a near miss teaches the wrong meaning.
     */
    private static final Map<String, String> BY_COMMAND = Map.ofEntries(
            Map.entry("palette.show", KEYBOARD),
            Map.entry("app.settings", COG),
            Map.entry("settings.theme", APPEARANCE),
            Map.entry("settings.keymap", KEYCAP),
            Map.entry("sync.setup", SYNC),
            Map.entry("sync.now", SYNC),
            Map.entry("config.openFolder", FOLDER),
            Map.entry("config.export", EXPORT),
            Map.entry("config.import", IMPORT),
            Map.entry("help.about", INFO),
            Map.entry("help.pauta", CONFORMANCE),
            Map.entry("help.debugLog", LOG),
            Map.entry("view.messageLog", MESSAGES),
            Map.entry("view.toggleSimpleMode", SIMPLE_MODE),
            Map.entry("view.uiZoomIn", ZOOM_IN),
            Map.entry("view.uiZoomOut", ZOOM_OUT),
            Map.entry("view.uiZoomReset", ZOOM_RESET),
            Map.entry("app.quit", QUIT),
            Map.entry("help.doctor", PULSE),
            Map.entry("help.keyboardShortcuts", KEYCAP),
            Map.entry("debugLog.refresh", REFRESH),
            Map.entry("debugLog.copy", COPY),
            Map.entry("debugLog.clear", CLEAR),
            Map.entry("debugLog.export", EXPORT),
            Map.entry("debugLog.find", FIND),
            Map.entry("messageLog.copy", COPY),
            Map.entry("messageLog.clear", CLEAR),
            Map.entry("help.checkForUpdates", UPDATE),
            Map.entry("help.documentation", BOOK),
            Map.entry("help.reportProblem", FLAG),
            Map.entry("keymap.changeBinding", BIND_KEY),
            Map.entry("settings.bindKey", BIND_KEY),
            Map.entry("keymap.resetBinding", RESET),
            Map.entry("settings.language", GLOBE),
            Map.entry("settings.toggleUpdateCheck", BELL),
            Map.entry("sync.setBranch", BRANCH),
            Map.entry("sync.setInterval", CLOCK),
            Map.entry("sync.setRepository", REPOSITORY),
            Map.entry("sync.toggleAuto", AUTO_SYNC),
            Map.entry("view.toggleStatusBar", STATUS_BAR),
            Map.entry("view.toggleTitleBarMenus", TITLE_BAR),
            Map.entry("edit.cut", CUT),
            Map.entry("edit.copy", COPY),
            Map.entry("edit.paste", PASTE),
            Map.entry("edit.selectAll", SELECT_ALL));

    /** The on-screen size of a glyph, in pixels. */
    private static final double SIZE = 18;

    /** How long the pointer rests before a tooltip shows: one delay for every tooltip (TBAR-14). */
    static final Duration TOOLTIP_DELAY = Duration.millis(400);

    private Icons() {}

    /** The line-glyph path of a command, or {@code null} when it has none. */
    public static @Nullable String forCommand(String commandId) {
        return BY_COMMAND.get(commandId);
    }

    /** A command's glyph as a {@link #line} node, or {@code null} when it has none. */
    public static @Nullable Node commandGlyph(String commandId) {
        String path = forCommand(commandId);
        return path == null ? null : line(path);
    }

    /** Every command glyph, by command id (for the tests that hold one glyph per action). */
    static Map<String, String> commandGlyphs() {
        return BY_COMMAND;
    }

    /** A line glyph: a 16-unit outline, stroked, never filled (see {@code .icon-line}). */
    public static Node line(String path) {
        return box(path, LINE, SIZE, SIZE / 16);
    }

    /** A filled glyph: a 24-unit solid shape (see {@code .toolbar-icon}). */
    public static Node of(String path) {
        return box(path, FILLED, SIZE, SIZE / 24);
    }

    /**
     * An icon-only button: the glyph, the name as both its tooltip and its accessible text (TBAR-01), and
     * the action. It stays in the Tab order.
     */
    public static Button button(Node icon, String name, Runnable action, String... styleClasses) {
        Button button = new Button();
        button.setGraphic(icon);
        button.getStyleClass().addAll(styleClasses);
        name(button, name);
        button.setOnAction(e -> action.run());
        return button;
    }

    /**
     * Names a control: its tooltip and its accessible text set from one string (TBAR-01). Call it again
     * when the name changes (a keymap switch changes the chord in it); the tooltip is reused.
     */
    public static void name(Control control, String name) {
        control.setAccessibleText(name);
        Tooltip tooltip = control.getTooltip();
        if (tooltip == null) {
            tooltip = new Tooltip();
            tooltip.setShowDelay(TOOLTIP_DELAY);
            control.setTooltip(tooltip);
        }
        tooltip.setText(name);
    }

    /**
     * A command button's name (TBAR-03): its title and its chord in the active keymap, "Command Palette
     * (Ctrl+Shift+P)", or the title alone when unbound. A trailing ellipsis goes: it promises a dialog
     * in a menu row, and a tooltip is not a menu row.
     */
    public static String withChord(String title, String chord) {
        String name = title.endsWith("…") ? title.substring(0, title.length() - 1) : title;
        name = name.endsWith("...") ? name.substring(0, name.length() - 3) : name;
        return chord.isEmpty() ? name : name + " (" + chord + ")";
    }

    private static Node box(String path, String family, double size, double scale) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.setFillRule(FillRule.EVEN_ODD);
        svg.getStyleClass().add(family);
        StackPane box = new StackPane(svg);
        box.getStyleClass().add("icon-box");
        // Sized from the UI zoom, never as plain pixels (WIN-12): every glyph follows a zoom at once.
        svg.scaleXProperty().bind(UiZoom.times(scale));
        svg.scaleYProperty().bind(UiZoom.times(scale));
        UiZoom.bind(box.minWidthProperty(), size);
        UiZoom.bind(box.minHeightProperty(), size);
        UiZoom.bind(box.prefWidthProperty(), size);
        UiZoom.bind(box.prefHeightProperty(), size);
        return box;
    }
}
