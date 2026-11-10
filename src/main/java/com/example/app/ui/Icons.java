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
 * <p>One action wears one glyph wherever it is drawn: a sidebar, a menu row or a toolbar asks
 * {@link #forCommand} (or {@link #commandGlyph}) by command id instead of naming a path itself.
 *
 * <p>An icon-only button is built here too ({@link #button}, {@link #toolbarButton}), so its tooltip and
 * its accessible text are always one string (TBAR-01, ICON-21, KBD-17): a screen reader says what a
 * sighted user reads. A hand-built icon button goes through {@link #name}.
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

    /** The Command Palette: a prompt in a box. */
    public static final String PALETTE = "M1.75 2.75H14.25V13.25H1.75ZM4.5 6.25L6.5 8L4.5 9.75M8.5 9.75H11.5";

    /** Toggle Toolbar: a window whose top row is a bar of buttons. */
    public static final String TOOLBAR =
            "M1.75 2.75H14.25V13.25H1.75ZM1.75 6.25H14.25M4.25 4.5h.2M6.75 4.5h.2M9.25 4.5h.2";

    /** The Pauta window: a ruled page with a tick, the rules and how the app meets them. */
    public static final String CONFORMANCE = "M3.5 2.5h9v11h-9zM5.3 6.2l1.1 1.1 2.1-2.3M5.5 9.5h5M5.5 11.5h3.5";

    /**
     * The glyph of each command that has one. A command with no fitting glyph is left out and draws
     * none: a near miss teaches the wrong meaning.
     */
    private static final Map<String, String> BY_COMMAND = Map.ofEntries(
            Map.entry("palette.show", PALETTE),
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
            Map.entry("view.messageLog", MESSAGES),
            Map.entry("view.toggleToolbar", TOOLBAR));

    /** The on-screen size of a glyph, in pixels. */
    private static final double SIZE = 18;

    /** A glyph on a toolbar button: inside the 28 px button with its 3 px padding (TBAR spec). */
    static final double TOOLBAR_SIZE = 20;

    /** How long the pointer rests before a tooltip shows: one delay for every tooltip (TBAR-14, Pauta 5.0.0). */
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

    /** A line glyph at the toolbar's size. */
    static Node toolbarLine(String path) {
        return box(path, LINE, TOOLBAR_SIZE, TOOLBAR_SIZE / 16);
    }

    /** A filled glyph: a 24-unit solid shape (see {@code .toolbar-icon}). */
    public static Node of(String path) {
        return box(path, FILLED, SIZE, SIZE / 24);
    }

    /**
     * An icon-only button: the glyph, the name as both its tooltip and its accessible text (TBAR-01), and
     * the action. It stays in the Tab order; a toolbar's buttons use {@link #toolbarButton}.
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
     * A toolbar button: {@link #button}, flat, 28 px, and out of the Tab order (TBAR-02): the command, its
     * menu row and the palette reach the action, and Tab is for the content.
     */
    public static Button toolbarButton(Node icon, String name, Runnable action) {
        Button button = button(icon, name, action, "button-icon", "flat", "toolbar-button");
        button.setFocusTraversable(false);
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
        svg.setScaleX(scale);
        svg.setScaleY(scale);
        StackPane box = new StackPane(svg);
        box.getStyleClass().add("icon-box");
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        return box;
    }
}
