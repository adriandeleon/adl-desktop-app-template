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

    private final AppServices services;
    private final boolean systemMenu;
    private final MenuBar bar = new MenuBar();
    private final List<List<Row>> groups = new ArrayList<>();

    AppMenus(AppServices services, boolean systemMenu) {
        this.services = services;
        this.systemMenu = systemMenu;
        bar.setUseSystemMenuBar(systemMenu);
        bar.getMenus()
                .setAll(
                        menu(
                                "menu.file",
                                "app.settings",
                                null,
                                "config.export",
                                "config.import",
                                "sync.now",
                                null,
                                "app.quit"),
                        menu(
                                "menu.view",
                                "view.toggleToolbar",
                                "view.toggleStatusBar",
                                null,
                                "settings.theme",
                                "settings.keymap",
                                "settings.language"),
                        // Pauta's Help order (DOCS-07); an app without a feature drops its row.
                        menu(
                                "menu.help",
                                "palette.show",
                                "help.documentation",
                                null,
                                "help.checkForUpdates",
                                "view.messageLog",
                                "help.debugLog",
                                "help.reportProblem",
                                null,
                                "help.pauta",
                                "help.about"));
        services.onKeybindingsChanged(this::refresh);
        refresh();
    }

    MenuBar bar() {
        return bar;
    }

    List<List<Row>> groups() {
        return groups;
    }

    /** A menu of commands; {@code null} is a separator. Unavailable commands are greyed on opening. */
    private Menu menu(String key, @Nullable String... ids) {
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
            row.item().setOnAction(e -> services.commands().run(command.id()));
            menu.getItems().add(row.item());
            rows.add(row);
        }
        groups.add(rows);
        menu.setOnShowing(e -> rows.forEach(row -> {
            Command command = services.commands().get(row.commandId());
            row.item().setDisable(command != null && command.unavailableReason() != null);
        }));
        // Measured once the popup has CSS, when the labels know their real widths.
        menu.setOnShown(e -> alignChords(rows));
        return menu;
    }

    private Row row(String id) {
        if (systemMenu) return new Row(new MenuItem(), id, null, null);
        return inWindowRow(id);
    }

    /**
     * An in-window row (MENU-02): the command's glyph (or an empty slot, so titles line up), the title, a
     * spacer and the chord. The right-click menus ({@link CommandMenu}) draw their rows the same way.
     */
    static Row inWindowRow(String id) {
        Node glyph = Icons.commandGlyph(id);
        Region slot = new Region();
        slot.setMinSize(ICON_SLOT, ICON_SLOT);
        slot.setPrefSize(ICON_SLOT, ICON_SLOT);
        Label title = new Label();
        title.getStyleClass().add("menu-item-title");
        Label chord = new Label();
        chord.getStyleClass().add("menu-item-chord");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(8, glyph != null ? glyph : slot, title, spacer, chord);
        HBox.setMargin(chord, new Insets(0, 0, 0, 16));
        box.setAlignment(Pos.CENTER_LEFT);
        CustomMenuItem item = new CustomMenuItem(box);
        item.setHideOnClick(true);
        return new Row(item, id, title, chord);
    }

    /** The width of the glyph column, so a row without a glyph keeps its title in line. */
    static final double ICON_SLOT = 18;

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
