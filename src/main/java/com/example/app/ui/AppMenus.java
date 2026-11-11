package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import com.example.app.command.Command;
import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The menu bar: menus of commands, each row showing the command's key binding in the active keymap.
 *
 * <p><b>No accelerators.</b> A JavaFX accelerator is one keystroke, so an Emacs sequence such as
 * {@code C-x C-c} cannot be one; and under the macOS screen menu bar the native menu claims an
 * accelerator before the key reaches {@link KeyDispatcher}, which would break its prefix state. So the
 * binding is drawn, never bound, in one of two ways:
 *
 * <ul>
 *   <li><b>In the window</b> (Windows, Linux): each row is a {@link CustomMenuItem} with a title, a
 *       spacer and a right-aligned chord, every title in a menu as wide as the widest so the chords form
 *       one column.
 *   <li><b>Screen menu bar</b> (macOS): AppKit draws the menus and renders no JavaFX node; a single
 *       {@code CustomMenuItem} makes JavaFX refuse the screen bar for the whole menu bar. Rows there are
 *       plain items with the chord appended to their text.
 * </ul>
 */
final class AppMenus {

    /** One command row: its item, and the labels the in-window rendering draws (null on macOS). */
    record Row(
            MenuItem item,
            String commandId,
            @Nullable Label title,
            @Nullable Label chord) {}

    /** One menu: its title key and its command ids, {@code null} for a separator. */
    record Spec(String key, List<@Nullable String> ids) {}

    private final AppServices services;
    private final boolean systemMenu;
    private final MenuBar bar = new MenuBar();
    private final List<List<Row>> groups = new ArrayList<>();
    /** Whether the bar shows the Simple mode table (MENU-04). */
    private boolean simple;

    AppMenus(AppServices services, boolean systemMenu) {
        this.services = services;
        this.systemMenu = systemMenu;
        bar.setUseSystemMenuBar(systemMenu);
        build();
        services.onKeybindingsChanged(this::refresh);
        refresh();
    }

    /**
     * The menu bar for a mode (MENU-04): every row in the full table, or the shorter Simple mode one. Both
     * keep the Simple mode toggle in the same place, the end of View, so the mode is never a one-way door.
     */
    static List<Spec> menus(boolean simple) {
        return simple ? SIMPLE : FULL;
    }

    private static final List<Spec> FULL = List.of(
            spec("menu.file", "app.settings", null, "config.export", "config.import", "sync.now", null, "app.quit"),
            spec(
                    "menu.edit",
                    "edit.cut",
                    "edit.copy",
                    "edit.paste",
                    null,
                    "edit.selectAll",
                    null,
                    "find.show",
                    "find.next",
                    "find.previous",
                    null,
                    "output.find"),
            spec(
                    "menu.view",
                    "view.toggleToolbar",
                    "view.toggleTabBar",
                    "view.toggleStatusBar",
                    null,
                    "settings.theme",
                    "settings.keymap",
                    "settings.language",
                    null,
                    "view.uiZoomIn",
                    "view.uiZoomOut",
                    "view.uiZoomReset",
                    null,
                    "view.toggleSimpleMode"),
            // The tabs and the tool windows: every one of their commands has a row (TABS-01).
            spec(
                    "menu.window",
                    "switcher.show",
                    "buffer.next",
                    "buffer.previous",
                    null,
                    "buffer.close",
                    "buffer.closeOthers",
                    "buffer.closeLeft",
                    "buffer.closeRight",
                    "buffer.closeUnmodified",
                    "buffer.closeAll",
                    null,
                    "buffer.togglePin",
                    "buffer.reopenClosed",
                    null,
                    "tool.documents",
                    "tool.outline",
                    "tool.output",
                    "output.runSample",
                    null,
                    "toolWindow.openInSplit",
                    "view.floatToolWindow",
                    "view.maximizeToolWindow",
                    "view.resizeHorizontalToolWindowGreater",
                    "view.resizeHorizontalToolWindowLess",
                    "view.resizeBottomToolWindow",
                    "view.resizeBottomToolWindowLess",
                    "view.closeFocusedToolWindow",
                    null,
                    "toolWindow.hide",
                    "view.restoreStripeButtons"),
            // Pauta's Help order (DOCS-07); an app without a feature drops its row.
            spec(
                    "menu.help",
                    "palette.show",
                    "help.documentation",
                    "help.keyboardShortcuts",
                    "view.welcome",
                    null,
                    "help.checkForUpdates",
                    "view.messageLog",
                    "help.debugLog",
                    "help.reportProblem",
                    null,
                    "help.pauta",
                    "help.about"));

    /**
     * Simple mode's menus: the same four, each cut to what a person new to the app reaches for. Every
     * button the toolbar keeps in Simple mode has its row here too (TBAR-08), and every command is still
     * in the palette.
     */
    private static final List<Spec> SIMPLE = List.of(
            spec("menu.file", "app.settings", null, "app.quit"),
            spec("menu.edit", "edit.copy", "edit.paste", null, "find.show"),
            spec(
                    "menu.view",
                    "view.toggleToolbar",
                    "view.toggleTabBar",
                    "view.toggleStatusBar",
                    null,
                    "view.toggleSimpleMode"),
            spec(
                    "menu.window",
                    "switcher.show",
                    null,
                    "buffer.close",
                    "buffer.reopenClosed",
                    null,
                    "tool.documents",
                    "tool.outline",
                    "tool.output"),
            spec("menu.help", "palette.show", "help.documentation", "view.welcome", null, "help.about"));

    private static Spec spec(String key, @Nullable String... ids) {
        return new Spec(key, java.util.Collections.unmodifiableList(java.util.Arrays.<@Nullable String>asList(ids)));
    }

    /** Every command id either table names, separators left out: the tests that check each row read this. */
    static List<String> allCommandIds() {
        return java.util.stream.Stream.concat(FULL.stream(), SIMPLE.stream())
                .flatMap(spec -> spec.ids().stream())
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .toList();
    }

    /**
     * Switches to the full or the Simple mode table (MENU-04). The menus are built again only when the mode
     * changes: a settings save that leaves it as it was keeps every menu, open or not, as it is.
     */
    void setSimple(boolean on) {
        if (on == simple) return;
        simple = on;
        build();
        refresh();
    }

    boolean isSimple() {
        return simple;
    }

    private void build() {
        groups.clear();
        List<Menu> built = new ArrayList<>();
        for (Spec spec : menus(simple)) built.add(menu(spec.key(), spec.ids()));
        bar.getMenus().setAll(built);
    }

    MenuBar bar() {
        return bar;
    }

    List<List<Row>> groups() {
        return groups;
    }

    /** A menu of commands; {@code null} is a separator. Unavailable commands are greyed on opening. */
    private Menu menu(String key, List<@Nullable String> ids) {
        Menu menu = new Menu(Messages.tr(key));
        List<Row> rows = new ArrayList<>();
        for (String id : ids) {
            Command command = id == null ? null : services.commands().get(id);
            if (command == null) {
                menu.getItems().add(new SeparatorMenuItem());
                continue;
            }
            Row row = row(command.id());
            row.item().setId(command.id());
            row.item().setOnAction(e -> runOrSay(services, command.id()));
            menu.getItems().add(row.item());
            rows.add(row);
        }
        groups.add(rows);
        menu.setOnShowing(e -> rows.forEach(row -> {
            Command command = services.commands().get(row.commandId());
            showAvailability(row, command == null ? null : command.unavailableReason());
        }));
        // Measured once the popup has CSS, when the labels know their real widths.
        menu.setOnShown(e -> alignChords(rows));
        return menu;
    }

    private Row row(String id) {
        if (systemMenu) {
            MenuItem item = new MenuItem();
            if (DESTRUCTIVE.contains(id)) item.getStyleClass().add("danger");
            return new Row(item, id, null, null);
        }
        return inWindowRow(id);
    }

    /**
     * An in-window row (MENU-02): the command's glyph (or an empty slot, so titles line up), the title, a
     * spacer and the chord. The right-click menus ({@link CommandMenu}) draw their rows the same way.
     */
    /**
     * The rows that delete, discard or reset, styled as dangerous (MENU-12): their title in the red. Whether
     * each asks first is the confirm dialog's business, not the row's.
     */
    static final java.util.Set<String> DESTRUCTIVE = java.util.Set.of(
            "messageLog.clear", "debugLog.clear", "output.clear", "keymap.resetBinding", "config.import");

    static Row inWindowRow(String id) {
        Node glyph = Icons.commandGlyph(id);
        Region slot = new Region();
        UiZoom.bind(slot.minWidthProperty(), ICON_SLOT); // the slot follows the UI zoom (WIN-12, MENU-16)
        UiZoom.bind(slot.minHeightProperty(), ICON_SLOT);
        UiZoom.bind(slot.prefWidthProperty(), ICON_SLOT);
        UiZoom.bind(slot.prefHeightProperty(), ICON_SLOT);
        Label title = new Label();
        title.getStyleClass().add("menu-item-title");
        Label chord = new Label();
        chord.getStyleClass().add("menu-item-chord");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        // The icon column is 22 px wide whatever the glyph's own size, so every title starts in one line.
        javafx.scene.layout.StackPane column = new javafx.scene.layout.StackPane(glyph != null ? glyph : slot);
        column.getStyleClass().add("menu-item-icon");
        UiZoom.bind(column.minWidthProperty(), ICON_SLOT);
        UiZoom.bind(column.prefWidthProperty(), ICON_SLOT);
        HBox box = new HBox(8, column, title, spacer, chord);
        HBox.setMargin(chord, new Insets(0, 0, 0, 18)); // the chord starts 18 px or more after the title (MENU-18)
        box.setAlignment(Pos.CENTER_LEFT);
        // A row drawn from nodes reads as its title and chord to a screen reader (ACC-01).
        box.accessibleTextProperty()
                .bind(javafx.beans.binding.Bindings.createStringBinding(
                        () -> Icons.withChord(title.getText(), chord.getText()),
                        title.textProperty(),
                        chord.textProperty()));
        CustomMenuItem item = new CustomMenuItem(box);
        if (DESTRUCTIVE.contains(id)) item.getStyleClass().add("danger");
        item.setHideOnClick(true);
        return new Row(item, id, title, chord);
    }

    /**
     * A command that can't run now stays in its menu, grayed, and says why (KBD-04): the row keeps working as a
     * row, so its tooltip shows the reason and a screen reader hears it, and choosing it says the reason in the
     * status bar instead of running. A row on the macOS system menu bar can only be disabled.
     */
    static void showAvailability(Row row, @Nullable String reason) {
        MenuItem item = row.item();
        if (!(item instanceof CustomMenuItem custom)) {
            item.setDisable(reason != null);
            return;
        }
        item.setDisable(false);
        item.getStyleClass().remove("unavailable");
        Node content = custom.getContent();
        Object old = content.getProperties().remove(REASON_TIP);
        if (old instanceof javafx.scene.control.Tooltip tip) javafx.scene.control.Tooltip.uninstall(content, tip);
        content.setAccessibleHelp(reason);
        if (reason == null) return;
        item.getStyleClass().add("unavailable");
        javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(reason);
        javafx.scene.control.Tooltip.install(content, tip);
        content.getProperties().put(REASON_TIP, tip);
    }

    private static final String REASON_TIP = AppMenus.class.getName() + ".reason";

    /** Runs the command, or, when it can't run now, says why in the status bar (KBD-04). */
    static void runOrSay(AppServices services, String id) {
        if (services.commands().run(id)) return;
        Command command = services.commands().get(id);
        String reason = command == null ? null : command.unavailableReason();
        if (command != null && reason != null) services.echo("status.unavailable", command.title(), reason);
    }

    /** The width of the glyph column, so a row without a glyph keeps its title in line. */
    static final double ICON_SLOT = 22;

    /** Re-labels every row with its title and current binding: after a keymap switch or a rebinding. */
    void refresh() {
        for (List<Row> rows : groups) {
            for (Row row : rows) {
                Command command = services.commands().get(row.commandId());
                String title = command == null ? row.commandId() : command.title();
                String chord = services.keybindings().label(row.commandId());
                Label titleLabel = row.title();
                Label chordLabel = row.chord();
                if (titleLabel != null && chordLabel != null) {
                    titleLabel.setText(title);
                    chordLabel.setText(chord);
                } else {
                    row.item().setText(chord.isEmpty() ? title : title + "   " + chord);
                }
            }
        }
    }

    /** Gives a menu's titles one width, so its chords start in the same column. */
    static void alignChords(List<Row> rows) {
        double widest = 0;
        for (Row row : rows) {
            Label title = row.title();
            if (title != null) {
                title.setMinWidth(Region.USE_PREF_SIZE);
                widest = Math.max(widest, title.prefWidth(-1));
            }
        }
        for (Row row : rows) {
            Label title = row.title();
            if (title != null) title.setMinWidth(widest);
        }
    }

    /** What a row shows, either rendering: the title and the chord. */
    static String displayText(Row row) {
        Label title = row.title();
        Label chord = row.chord();
        if (title == null || chord == null) return String.valueOf(row.item().getText());
        return chord.getText().isEmpty() ? title.getText() : title.getText() + "   " + chord.getText();
    }
}
