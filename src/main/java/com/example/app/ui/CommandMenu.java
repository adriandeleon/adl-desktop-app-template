package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import com.example.app.command.Command;
import org.jspecify.annotations.Nullable;

/**
 * A right-click menu of commands (MENU-05): each row is a registered command, laid out like the menu
 * bar's rows (title, spacer, chord), so whatever a right-click reaches the palette and a key reach too.
 * The menu is built on its first request and set up for each showing: titles, chords and enablement are
 * re-read then (MENU-11). Dismissal is {@link ContextMenuDismissal}'s; the keyboard request is
 * {@link RowContextMenu}'s; Escape closes it and focus goes back (the stock {@code ContextMenu}).
 */
final class CommandMenu {

    private final AppServices services;
    private final List<@Nullable String> ids;
    private @Nullable ContextMenu menu;
    private final List<AppMenus.Row> rows = new ArrayList<>();

    /** {@code null} in {@code ids} is a separator. */
    CommandMenu(AppServices services, List<@Nullable String> ids) {
        this.services = services;
        this.ids = ids;
    }

    /** The menu, built on first use. */
    ContextMenu menu() {
        ContextMenu built = menu;
        if (built != null) return built;
        built = new ContextMenu();
        built.getStyleClass().add("command-menu");
        for (String id : ids) {
            Command command = id == null ? null : services.commands().get(id);
            if (command == null) {
                built.getItems().add(new SeparatorMenuItem());
                continue;
            }
            Label title = new Label();
            title.getStyleClass().add("menu-item-title");
            Label chord = new Label();
            chord.getStyleClass().add("menu-item-chord");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox box = new HBox(title, spacer, chord);
            HBox.setMargin(chord, new Insets(0, 0, 0, 24));
            box.setAlignment(Pos.CENTER_LEFT);
            CustomMenuItem item = new CustomMenuItem(box);
            item.setId(command.id());
            item.setOnAction(e -> services.commands().run(command.id()));
            built.getItems().add(item);
            rows.add(new AppMenus.Row(item, command.id(), title, chord));
        }
        built.setOnShowing(e -> refresh());
        built.setOnShown(e -> AppMenus.alignChords(rows));
        menu = built;
        return built;
    }

    /** Sets each row for this showing: its title, its chord in the active keymap, and whether it can run. */
    void refresh() {
        for (AppMenus.Row row : rows) {
            Command command = services.commands().get(row.commandId());
            Label title = row.title();
            Label chord = row.chord();
            if (title != null) title.setText(command == null ? row.commandId() : command.title());
            if (chord != null) chord.setText(services.keybindings().label(row.commandId()));
            row.item().setDisable(command == null || command.unavailableReason() != null);
        }
    }

    List<MenuItem> items() {
        return menu().getItems();
    }

    /** Opens the menu at a right-click (or a keyboard request) on {@code anchor}. */
    void show(Node anchor, ContextMenuEvent event) {
        menu().show(anchor, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    /**
     * Gives every non-empty row of a list this menu: a right-click selects the row first (MENU-10), and the
     * Menu key and Shift+F10 open the selected row's menu ({@link RowContextMenu}).
     */
    static <C extends ListCell<?>> C onRow(C cell, Supplier<CommandMenu> menu) {
        cell.setOnContextMenuRequested(e -> {
            if (cell.isEmpty()) return;
            ListView<?> list = cell.getListView();
            if (list != null && !cell.isSelected()) list.getSelectionModel().clearAndSelect(cell.getIndex());
            menu.get().show(cell, e);
        });
        return cell;
    }

    /** Gives a read-only text area (a console) this menu in place of the stock Cut/Copy/Paste one. */
    static void onConsole(TextInputControl console, CommandMenu menu) {
        console.setContextMenu(menu.menu());
    }
}
