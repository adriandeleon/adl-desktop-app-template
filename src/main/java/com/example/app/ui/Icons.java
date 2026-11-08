package com.example.app.ui;

import java.util.Map;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

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
 * <p>One action wears one glyph wherever it is drawn: a sidebar, a menu row or a toolbar asks
 * {@link #forCommand} (or {@link #commandGlyph}) by command id instead of naming a path itself.
 */
public final class Icons {

    /** Style class of the stroked family. */
    public static final String LINE = "icon-line";

    /** Style class of the filled family. */
    public static final String FILLED = "toolbar-icon";

    /** General settings: three sliders. */
    public static final String SLIDERS = "M2 4.5h12M2 8h12M2 11.5h12M5 3v3M10.5 6.5v3M7 10v3";

    /** Theme: a circle, one half ruled. */
    public static final String CONTRAST = "M2 8a6 6 0 1 0 12 0a6 6 0 1 0 -12 0M8 2v12M8 4.6h4.4M8 8h6M8 11.4h4.4";

    /** Keymap: a keyboard, its keys as dots (round caps) and a space bar. */
    public static final String KEYBOARD =
            "M1.75 4.25H14.25V11.75H1.75ZM4.25 6.75H4.5M7 6.75H7.25M9.75 6.75H10M12 6.75H12.25M5 9.25H11";

    /** Sync: two arrows chasing each other round a circle. */
    public static final String SYNC =
            "M12.7 6.3A5 5 0 0 0 3.5 5.5M3.3 2.8v2.9h2.9M3.3 9.7A5 5 0 0 0 12.5 10.5M12.7 13.2v-2.9h-2.9";

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

    /**
     * The glyph of each command that has one. A command with no fitting glyph is left out and draws
     * none: a near miss teaches the wrong meaning.
     */
    private static final Map<String, String> BY_COMMAND = Map.ofEntries(
            Map.entry("app.settings", SLIDERS),
            Map.entry("settings.theme", CONTRAST),
            Map.entry("settings.keymap", KEYBOARD),
            Map.entry("sync.setup", SYNC),
            Map.entry("sync.now", SYNC),
            Map.entry("config.openFolder", FOLDER),
            Map.entry("config.export", EXPORT),
            Map.entry("config.import", IMPORT),
            Map.entry("help.about", INFO),
            Map.entry("help.pauta", CONFORMANCE),
            Map.entry("help.debugLog", LOG),
            Map.entry("view.messageLog", MESSAGES));

    /** The on-screen size of a glyph, in pixels. */
    private static final double SIZE = 18;

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
        return box(path, LINE, SIZE / 16);
    }

    /** A filled glyph: a 24-unit solid shape (see {@code .toolbar-icon}). */
    public static Node of(String path) {
        return box(path, FILLED, SIZE / 24);
    }

    private static Node box(String path, String family, double scale) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.setFillRule(FillRule.EVEN_ODD);
        svg.getStyleClass().add(family);
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        StackPane box = new StackPane(svg);
        box.getStyleClass().add("icon-box");
        box.setMinSize(SIZE, SIZE);
        box.setPrefSize(SIZE, SIZE);
        return box;
    }
}
