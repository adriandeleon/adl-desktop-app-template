package com.example.app.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The tab switcher (TABS-09), after Editora's: the open files in tab order, the current one highlighted.
 * The pickers' keys move it ({@link PickerKeys}: the arrows, the page keys and the keymap's line chords, {@code C-n} / {@code C-p} in Emacs), Tab too; Enter, or releasing Ctrl (Cmd on macOS) after moving
 * with it held, activates; {@code C-g} or Esc cancels ({@link OverlayHost}); {@code C-d}, Backspace or Delete closes the
 * highlighted file. Its width is fixed to the longest path on each open, so it never resizes while you move.
 */
final class Switcher {

    static final double CELL_HEIGHT = 28;
    static final int MAX_VISIBLE = 12;
    static final double MIN_WIDTH = 360;

    private final DocumentTabs tabs;
    private final OverlayHost host;
    private final ListView<Tab> list = new ListView<>();
    private final Label path = new Label();
    private final VBox card;
    /**
     * Whether releasing Ctrl (or Cmd) activates: set once the highlight moved with it held, as Ctrl+Tab does.
     * Not on its own: {@code C-x C-b} is typed with Ctrl down, and letting go of it must not close the list.
     */
    private boolean armed;

    Switcher(DocumentTabs tabs, OverlayHost host) {
        this.tabs = tabs;
        this.host = host;
        list.setAccessibleText(Messages.tr("switcher.header")); // ACC-01
        UiZoom.bind(list.fixedCellSizeProperty(), CELL_HEIGHT); // sizes follow the UI zoom (WIN-12)
        list.setCellFactory(v -> new Row());
        list.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> updateFooter());
        list.setOnMouseClicked(e -> commit());

        Label header = new Label(Messages.tr("switcher.header"));
        header.getStyleClass().add("switcher-header");
        path.getStyleClass().add("switcher-path");
        path.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label(Messages.tr("switcher.hint"));
        hint.getStyleClass().add("hint");

        card = new VBox(6, header, list, path, hint);
        card.getStyleClass().addAll("command-palette", "switcher");
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        card.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            KeyCode code = e.getCode();
            if (armed && (code == KeyCode.CONTROL || code == KeyCode.META || code == KeyCode.COMMAND)) {
                commit();
                e.consume();
            }
        });
    }

    /** Opens over the window with the current tab highlighted. */
    void show() {
        armed = false;
        list.getItems().setAll(tabs.tabs());
        int rows = Math.max(1, Math.min(list.getItems().size(), MAX_VISIBLE));
        list.setPrefHeight((rows * CELL_HEIGHT + 2) * UiZoom.factor().get());
        Tab current = tabs.selected();
        list.getSelectionModel().select(Math.max(0, list.getItems().indexOf(current)));
        double width = widthFor();
        card.setMinWidth(width);
        card.setPrefWidth(width);
        card.setMaxWidth(width);
        host.show(card, Pos.TOP_CENTER, new Insets(60, 0, 0, 0), null);
        list.requestFocus();
    }

    /** The card's width: the longest path among the open files, so it never resizes while you move. */
    double widthFor() {
        double max = 0;
        for (Tab tab : list.getItems()) max = Math.max(max, textWidth(pathOf(tab)));
        double zoom = UiZoom.factor().get(); // the path is measured in the zoomed font already (WIN-12)
        return Math.max(MIN_WIDTH * zoom, Math.ceil(max) + 48 * zoom);
    }

    private double textWidth(String text) {
        Text probe = new Text(text);
        probe.setFont(path.getFont());
        return probe.getLayoutBounds().getWidth();
    }

    private String pathOf(Tab tab) {
        DocumentView document = tabs.document(tab);
        return document == null ? tabs.title(tab) : document.path().toString();
    }

    private java.util.function.@Nullable Supplier<com.example.app.command.Keybindings> keys;
    private boolean mac;

    /** The keymap the moves come from: the pickers' keys, the same helper (QOPEN-12). */
    void keymap(java.util.function.Supplier<com.example.app.command.Keybindings> keys, boolean mac) {
        this.keys = keys;
        this.mac = mac;
    }

    private void onKey(KeyEvent e) {
        boolean ctrl = e.isControlDown();
        KeyCode code = e.getCode();
        boolean held = ctrl || e.isMetaDown();
        PickerKeys.Move move = PickerKeys.resolve(e, keys == null ? null : keys.get(), mac);
        int size = list.getItems().size();
        if (move == PickerKeys.Move.LINE_DOWN || (code == KeyCode.TAB && !e.isShiftDown())) {
            move(1);
            armed |= held;
        } else if (move == PickerKeys.Move.LINE_UP || code == KeyCode.TAB) {
            move(-1);
            armed |= held;
        } else if (move == PickerKeys.Move.PAGE_DOWN || move == PickerKeys.Move.LAST) {
            list.getSelectionModel()
                    .select(
                            move == PickerKeys.Move.LAST
                                    ? size - 1
                                    : Math.min(
                                            size - 1, list.getSelectionModel().getSelectedIndex() + MAX_VISIBLE));
            list.scrollTo(list.getSelectionModel().getSelectedIndex());
        } else if (move == PickerKeys.Move.PAGE_UP || move == PickerKeys.Move.FIRST) {
            list.getSelectionModel()
                    .select(
                            move == PickerKeys.Move.FIRST
                                    ? 0
                                    : Math.max(0, list.getSelectionModel().getSelectedIndex() - MAX_VISIBLE));
            list.scrollTo(list.getSelectionModel().getSelectedIndex());
        } else if (code == KeyCode.ENTER) {
            commit();
        } else if (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE || (ctrl && code == KeyCode.D)) {
            closeSelected();
        } else {
            return;
        }
        e.consume();
    }

    private void move(int delta) {
        int size = list.getItems().size();
        if (size == 0) return;
        list.getSelectionModel().select(TabStrip.cycle(list.getSelectionModel().getSelectedIndex(), size, delta));
        list.scrollTo(list.getSelectionModel().getSelectedIndex());
    }

    /** Activates the highlighted tab and closes the switcher. */
    void commit() {
        Tab tab = list.getSelectionModel().getSelectedItem();
        armed = false;
        host.hide();
        if (tab != null) tabs.tabPane().getSelectionModel().select(tab);
    }

    /** Closes the highlighted file; the switcher stays open on the next one, at the same width. */
    void closeSelected() {
        Tab tab = list.getSelectionModel().getSelectedItem();
        if (tab == null) return;
        int at = list.getSelectionModel().getSelectedIndex();
        tabs.closeTab(tab);
        list.getItems().remove(tab);
        if (!list.getItems().isEmpty())
            list.getSelectionModel().select(Math.min(at, list.getItems().size() - 1));
    }

    private void updateFooter() {
        Tab tab = list.getSelectionModel().getSelectedItem();
        path.setText(tab == null ? " " : pathOf(tab));
    }

    ListView<Tab> list() {
        return list;
    }

    VBox card() {
        return card;
    }

    /** A row: the file's name, with the unsaved marker the tab strip shows. */
    private final class Row extends ListCell<Tab> {
        @Override
        protected void updateItem(@Nullable Tab tab, boolean empty) {
            super.updateItem(tab, empty);
            if (empty || tab == null) {
                setText(null);
                return;
            }
            setText((tabs.isDirty(tab) ? "• " : "") + tabs.title(tab));
        }
    }
}
