package com.example.app.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import com.example.app.command.Command;
import org.jspecify.annotations.Nullable;

/**
 * The main toolbar (Pauta's Toolbars and tooltips): a fast path to commands that are also in the menus
 * and the palette, never the only way to one (TBAR-08). Every button is a command, so its glyph, its
 * name and its chord come from the registry, {@link Icons} and the active keymap.
 *
 * <p>Two containers side by side, as in Editora (TBAR-15, Pauta 5.0.0): the <b>icon cluster</b>, a
 * {@link ToolBar} whose last icons go into its overflow chevron when the window narrows, and the
 * <b>pinned tail</b>, which keeps Settings at the right end at any width (TBAR-16, Pauta 5.0.0).
 *
 * <p>What each rule asks of it, and where:
 *
 * <ul>
 *   <li>TBAR-01: every button is named by {@link Icons#toolbarButton}, tooltip and accessible text one
 *       string; TBAR-02: out of the Tab order, the same helper.
 *   <li>TBAR-03: the name is {@link Icons#withChord} of the title and the keymap's chord, rebuilt by
 *       {@link #refresh} on every keymap change.
 *   <li>TBAR-04: groups are {@link Separator}s whose line the stylesheet makes transparent.
 *   <li>TBAR-05: {@link #setGroupVisible} and every other visibility change end in one pass over the
 *       separators ({@link #separatorsShown}), so no gap stands on its own.
 *   <li>TBAR-06: the bar sits on the menu bar's ground with no rule between them ({@code .chrome-band}).
 *   <li>TBAR-07: a button whose overlay is up wears {@code :open} ({@link OverlayToggles}).
 *   <li>TBAR-10: one right-click menu, reused, closed by a press anywhere ({@link CommandMenu},
 *       {@link ContextMenuDismissal}).
 *   <li>TBAR-17 (Pauta 5.0.0): a button is disabled while its command can't run; {@link #refresh} reads
 *       that from the command and runs on the events that change it, never on a timer.
 * </ul>
 *
 * <p>An app replaces {@link #CLUSTER} and {@link #TAIL} with its own commands; each one needs a glyph in
 * {@link Icons} and a row in {@link AppMenus} ({@code AppToolbarFxTest} checks both).
 */
final class AppToolbar {

    /** The icon cluster, in groups; {@code null} is a group gap. */
    static final List<@Nullable String> CLUSTER = Collections.unmodifiableList(Arrays.<@Nullable String>asList(
            "palette.show", null, "settings.theme", "settings.keymap", null, "sync.now", "view.messageLog"));

    /** The pinned tail: Settings closes the bar at its right end. */
    static final List<String> TAIL = List.of("app.settings");

    /** The bar's right-click menu: its commands (MENU-05), one menu reused (TBAR-10). */
    static final List<@Nullable String> MENU =
            Collections.unmodifiableList(Arrays.<@Nullable String>asList("view.toggleToolbar"));

    private final AppServices services;
    private final ToolBar bar = new ToolBar();
    private final HBox tail = new HBox();
    private final HBox row;
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final CommandMenu menu;

    AppToolbar(AppServices services, OverlayToggles toggles) {
        this.services = services;
        for (String id : CLUSTER) {
            if (id == null) {
                bar.getItems().add(new Separator(Orientation.VERTICAL));
            } else {
                bar.getItems().add(button(id, toggles));
            }
        }
        for (String id : TAIL) tail.getChildren().add(button(id, toggles));
        tail.getStyleClass().add("toolbar-tail");
        tail.setAlignment(Pos.CENTER_RIGHT);
        tail.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        bar.setMinWidth(0);
        HBox.setHgrow(bar, Priority.ALWAYS);
        row = new HBox(bar, tail);
        row.getStyleClass().add("toolbar-row");
        row.setAlignment(Pos.CENTER_LEFT);

        menu = new CommandMenu(services, MENU);
        row.setOnContextMenuRequested(e -> menu.show(row, e));

        services.onKeybindingsChanged(this::refresh);
        // A setting can change what a command can do (Sync Now waits for a repository): read it again.
        services.store().addListener(s -> refresh());
        refresh();
    }

    private Button button(String id, OverlayToggles toggles) {
        String path = Icons.forCommand(id);
        Node glyph = Icons.toolbarLine(path == null ? "" : path);
        Button button = Icons.toolbarButton(glyph, id, () -> services.commands().run(id));
        button.setId("toolbar-" + id);
        toggles.track(button, id);
        buttons.put(id, button);
        return button;
    }

    /** The whole bar: the cluster and the tail, in one row. */
    HBox node() {
        return row;
    }

    ToolBar bar() {
        return bar;
    }

    HBox tail() {
        return tail;
    }

    CommandMenu menu() {
        return menu;
    }

    /** The button that runs {@code commandId}, or null when the bar has none. */
    @Nullable
    Button button(String commandId) {
        return buttons.get(commandId);
    }

    List<Button> buttons() {
        return List.copyOf(buttons.values());
    }

    /**
     * Names every button again from its command's title and chord (TBAR-03), sets whether it can run
     * (TBAR-17), and tidies the gaps (TBAR-05). Runs on a keymap change and a settings change; an app calls
     * it too from whatever event changes what a command on the bar can do.
     */
    void refresh() {
        buttons.forEach((id, button) -> {
            Command command = services.commands().get(id);
            String title = command == null ? id : command.title();
            Icons.name(button, Icons.withChord(title, services.keybindings().label(id)));
            button.setDisable(command == null || command.unavailableReason() != null);
        });
        collapseSeparators();
    }

    /**
     * Shows or hides the group that holds {@code commandId} (as Simple mode or a missing tool would), then
     * tidies the gaps it leaves (TBAR-05).
     */
    void setGroupVisible(String commandId, boolean visible) {
        List<Node> items = bar.getItems();
        int at = items.indexOf(buttons.get(commandId));
        if (at < 0) return;
        int start = at;
        while (start > 0 && !(items.get(start - 1) instanceof Separator)) start--;
        for (int i = start; i < items.size() && !(items.get(i) instanceof Separator); i++) {
            items.get(i).setVisible(visible);
            items.get(i).setManaged(visible);
        }
        collapseSeparators();
    }

    /** One pass over the separators: none leads, none trails, none follows another (TBAR-05). */
    void collapseSeparators() {
        List<Node> items = bar.getItems();
        List<@Nullable Boolean> shape = new ArrayList<>();
        for (Node item : items) shape.add(item instanceof Separator ? null : item.isVisible());
        boolean[] shown = separatorsShown(shape);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof Separator separator) {
                separator.setVisible(shown[i]);
                separator.setManaged(shown[i]);
            }
        }
    }

    /**
     * Which separators stay, given the bar's items in order: {@code null} for a separator, else whether
     * that item is visible. A separator stays only with a visible item somewhere before it, back to the
     * last separator kept, and one somewhere after it. Entries for items echo their own visibility.
     */
    static boolean[] separatorsShown(List<@Nullable Boolean> items) {
        boolean[] shown = new boolean[items.size()];
        boolean itemSinceKept = false;
        for (int i = 0; i < items.size(); i++) {
            Boolean item = items.get(i);
            if (item != null) {
                shown[i] = item;
                itemSinceKept |= item;
                continue;
            }
            boolean itemAfter = false;
            for (int j = i + 1; j < items.size() && !itemAfter; j++) {
                itemAfter = Boolean.TRUE.equals(items.get(j));
            }
            shown[i] = itemSinceKept && itemAfter;
            if (shown[i]) itemSinceKept = false;
        }
        return shown;
    }
}
