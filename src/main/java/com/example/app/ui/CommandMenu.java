package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.ContextMenuEvent;

import com.example.app.command.Command;
import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * A right-click menu of commands (MENU-05): each row is a registered command, laid out like the menu
 * bar's rows (glyph, title, spacer, chord), so whatever a right-click reaches the palette and a key reach too.
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
            AppMenus.Row row = AppMenus.inWindowRow(command.id());
            row.item().setId(command.id());
            row.item().setOnAction(e -> AppMenus.runOrSay(services, command.id()));
            built.getItems().add(row.item());
            rows.add(row);
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
            AppMenus.showAvailability(
                    row, command == null ? Messages.tr("palette.disabled.unknown") : command.unavailableReason());
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

    /**
     * Gives a text area, an editor or a read-only console, this menu in place of the stock Cut/Copy/Paste one,
     * whose rows are no commands.
     */
    static void onConsole(TextInputControl console, CommandMenu menu) {
        console.setContextMenu(menu.menu());
    }
}
