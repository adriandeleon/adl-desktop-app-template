package com.example.app.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import com.example.app.command.FuzzyMatch;
import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The one picker for every list choice: the Command Palette, themes, keymaps, languages. A 620 px card
 * with the query on top, fuzzy-ranked rows with their key chip, the selected row's description and the
 * key legend. Unavailable rows are listed greyed, with a tooltip saying why, and are never selected.
 *
 * @param <T> what a row stands for
 */
public final class Picker<T> {

    /** One row. {@code unavailable} is why it cannot be chosen, or null. */
    public record Item<T>(
            T value,
            String title,
            String description,
            String chip,
            @Nullable String unavailable,
            String keywords) {

        /** A row with no words to match beyond its title. */
        public Item(T value, String title, String description, String chip, @Nullable String unavailable) {
            this(value, title, description, chip, unavailable, "");
        }
    }

    /** A command id's words, matched as well as its title (KBD-06): {@code view.toggleStatusBar} gives "view toggle status bar". */
    static String idWords(String id) {
        return id.replace('.', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(java.util.Locale.ROOT);
    }

    record Ranked<T>(Item<T> item, int[] positions, int score) {}

    static final double WIDTH = 620;
    static final double ROW_HEIGHT = 36;
    static final int VISIBLE_ROWS = 8;

    private final OverlayHost host;
    private List<Item<T>> items;
    private final Label title = new Label();
    private final HBox inputRow;
    private final Label hint = new Label();
    private java.util.function.@Nullable Supplier<com.example.app.command.Keybindings> keys;
    private boolean mac;
    private @Nullable Consumer<T> onHelp;
    private com.example.app.command.@Nullable KeyStroke helpChord;
    private @Nullable T current;
    private final Consumer<T> onChoose;
    private final TextField query = new TextField();
    private final ListView<Ranked<T>> list = new ListView<>();
    private final Label description = new Label();
    private final VBox card;

    public Picker(OverlayHost host, String prompt, String opener, List<Item<T>> items, Consumer<T> onChoose) {
        this.host = host;
        this.items = List.copyOf(items);
        this.onChoose = onChoose;

        query.setPromptText(prompt);
        query.setAccessibleText(prompt); // the prompt goes when typing starts; the name stays (ACC-01)
        list.setAccessibleText(prompt);
        query.getStyleClass().add("palette-query");
        HBox.setHgrow(query, Priority.ALWAYS);
        inputRow = new HBox(8, query);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        if (!opener.isEmpty()) inputRow.getChildren().addFirst(chip(opener, "opener-chip"));

        list.setCellFactory(v -> new Row());
        UiZoom.bind(list.fixedCellSizeProperty(), ROW_HEIGHT); // sizes follow the UI zoom (WIN-12)
        UiZoom.bind(list.prefHeightProperty(), ROW_HEIGHT * VISIBLE_ROWS + 2);
        list.setFocusTraversable(false);
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 1) choose();
        });

        description.getStyleClass().add("palette-description");
        description.setMinHeight(Region.USE_PREF_SIZE);
        hint.setText(Messages.tr("palette.hint"));
        hint.getStyleClass().add("palette-hint");
        title.getStyleClass().add("picker-title");
        title.setVisible(false);
        title.setManaged(false);

        card = new VBox(6, title, inputRow, list, description, hint);
        card.getStyleClass().add("command-palette");
        UiZoom.bind(card.prefWidthProperty(), WIDTH);
        UiZoom.bind(card.maxWidthProperty(), WIDTH);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        // Ranking waits for typing to pause (PERF-06); a key that acts on the list flushes it first.
        Debounce ranking = new Debounce(this::refresh);
        query.textProperty().addListener((obs, old, text) -> ranking.request());
        query.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            ranking.flush();
            onKey(e);
        });
        list.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, row) -> description.setText(
                        row == null
                                ? " "
                                : row.item().description().isEmpty()
                                        ? " "
                                        : row.item().description()));
        refresh();
    }

    /** Opens the picker over the window. */
    public void show() {
        host.show(card, Pos.TOP_CENTER, new Insets(60, 0, 0, 0), onHidden);
        query.requestFocus();
    }

    private @Nullable Runnable onHidden;

    /** Runs {@code action} when the picker closes, however it closes. */
    public Picker<T> onHidden(Runnable action) {
        this.onHidden = action;
        return this;
    }

    /** A picker's title over its field, naming the command that opened it (QOPEN-02). */
    public Picker<T> titled(String text) {
        title.setText(text);
        title.setVisible(!text.isEmpty());
        title.setManaged(!text.isEmpty());
        return this;
    }

    /**
     * The keymap the keys come from (PAL-06, QOPEN-06): its line and page chords move the selection, as the
     * fixed keys always do.
     */
    public Picker<T> keymap(java.util.function.Supplier<com.example.app.command.Keybindings> keys, boolean mac) {
        this.keys = keys;
        this.mac = mac;
        return this;
    }

    /** What the help chord opens for the selected row (PAL-08); the chord is the first the keymap leaves free. */
    public Picker<T> help(Consumer<T> onHelp) {
        this.onHelp = onHelp;
        return this;
    }

    /** With nothing typed, the value in force is the one selected (QOPEN-04). */
    public Picker<T> current(@Nullable T value) {
        this.current = value;
        refresh();
        return this;
    }

    /**
     * A keymap switch while the picker is open (PAL-09): the opener chip, the key legend and the rows' chips are
     * drawn again for the new keymap; the query and the selection stay.
     */
    public void rebind(String opener, List<Item<T>> fresh, String legend) {
        T selected = list.getSelectionModel().getSelectedItem() == null
                ? null
                : list.getSelectionModel().getSelectedItem().item().value();
        inputRow.getChildren().removeIf(n -> n.getStyleClass().contains("opener-chip"));
        if (!opener.isEmpty()) inputRow.getChildren().addFirst(chip(opener, "opener-chip"));
        hint.setText(legend);
        items = List.copyOf(fresh);
        list.getItems().setAll(rank(items, query.getText()));
        for (int i = 0; i < list.getItems().size(); i++) {
            if (list.getItems().get(i).item().value().equals(selected)) select(i);
        }
        helpChord = null;
    }

    Label hint() {
        return hint;
    }

    Label title() {
        return title;
    }

    TextField query() {
        return query;
    }

    ListView<Ranked<T>> list() {
        return list;
    }

    /** Ranks every item against a query: title hits weigh most, a query naming the title most of all. */
    static <T> List<Ranked<T>> rank(List<Item<T>> items, String query) {
        List<Ranked<T>> out = new ArrayList<>();
        for (Item<T> item : items) {
            if (query.isBlank()) {
                out.add(new Ranked<>(item, new int[0], 0));
                continue;
            }
            // KBD-06: the title, fuzzily, and the words of the id or the keywords; the description is drawn,
            // never matched. A query that spells the title outranks every fuzzy hit.
            FuzzyMatch.Match title = FuzzyMatch.match(query, item.title());
            FuzzyMatch.Match words = item.keywords().isEmpty() ? null : FuzzyMatch.match(query, item.keywords());
            int score = Integer.MIN_VALUE;
            int[] positions = new int[0];
            if (title != null) {
                score = title.score() * 3 + FuzzyMatch.phraseBonus(query, item.title());
                positions = title.positions();
                if (item.title()
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains(query.strip().toLowerCase(java.util.Locale.ROOT))) {
                    score += 1_000_000;
                }
            }
            if (words != null) score = Math.max(score, words.score());
            if (score != Integer.MIN_VALUE) out.add(new Ranked<>(item, positions, score));
        }
        if (!query.isBlank()) out.sort(Comparator.comparingInt((Ranked<T> r) -> -r.score()));
        return out;
    }

    private void refresh() {
        list.getItems().setAll(rank(items, query.getText()));
        if (query.getText().isBlank() && current != null) {
            for (int i = 0; i < list.getItems().size(); i++) {
                if (list.getItems().get(i).item().value().equals(current)) {
                    select(i);
                    return;
                }
            }
        }
        select(enabledFrom(0, 1));
    }

    /** The first enabled row from {@code start}, stepping by {@code step}, or -1. */
    private int enabledFrom(int start, int step) {
        List<Ranked<T>> rows = list.getItems();
        for (int i = start; i >= 0 && i < rows.size(); i += step) {
            if (rows.get(i).item().unavailable() == null) return i;
        }
        return -1;
    }

    private void select(int index) {
        if (index < 0) {
            list.getSelectionModel().clearSelection();
            return;
        }
        list.getSelectionModel().select(index);
        list.getFocusModel().focus(index);
        list.scrollTo(Math.max(0, index - VISIBLE_ROWS / 2));
        // The focus stays in the field: the list tells a screen reader which row the arrows moved to (ACC-08).
        list.notifyAccessibleAttributeChanged(javafx.scene.AccessibleAttribute.FOCUS_ITEM);
    }

    private void move(int delta) {
        int size = list.getItems().size();
        if (size == 0) return;
        int step = delta > 0 ? 1 : -1;
        int target = Math.clamp((long) list.getSelectionModel().getSelectedIndex() + delta, 0, size - 1);
        int found = enabledFrom(target, step);
        select(found >= 0 ? found : enabledFrom(target, -step));
    }

    private void onKey(KeyEvent e) {
        var bindings = keys == null ? null : keys.get();
        Consumer<T> help = onHelp;
        if (help != null && bindings != null) {
            if (helpChord == null) helpChord = PickerKeys.helpChord(bindings, mac);
            if (PickerKeys.isHelp(e, helpChord)) {
                Ranked<T> row = list.getSelectionModel().getSelectedItem();
                if (row != null) help.accept(row.item().value());
                e.consume();
                return;
            }
        }
        switch (PickerKeys.resolve(e, bindings, mac)) {
            case LINE_DOWN -> move(1);
            case LINE_UP -> move(-1);
            case PAGE_DOWN -> move(VISIBLE_ROWS);
            case PAGE_UP -> move(-VISIBLE_ROWS);
            case FIRST -> select(enabledFrom(0, 1));
            case LAST -> select(enabledFrom(list.getItems().size() - 1, -1));
            case NONE -> {
                if (e.getCode() != KeyCode.ENTER) return;
                choose();
            }
        }
        e.consume();
    }

    /** The help chord's label for the legend, or empty when the keymap takes all three (PAL-08). */
    String helpLabel(com.example.app.command.Keybindings bindings) {
        var chord = PickerKeys.helpChord(bindings, mac);
        return chord == null ? "" : chord.label(bindings.keymap().emacs(), mac);
    }

    /** Runs the selected row: the picker closes first, then the choice runs. */
    void choose() {
        Ranked<T> row = list.getSelectionModel().getSelectedItem();
        if (row == null || row.item().unavailable() != null) return;
        host.hide();
        onChoose.accept(row.item().value());
    }

    private static Label chip(String text, String style) {
        Label chip = new Label(text);
        chip.getStyleClass().addAll("key-chip", style);
        chip.setMinWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    /** A row: the title with the matched characters bold in the accent colour, then the key chip. */
    private final class Row extends ListCell<Ranked<T>> {
        // Built once per cell and reused as the cell shows other rows (PERF-09): a query typed into the palette
        // re-labels these nodes, it does not make new ones.
        private final TextFlow title = new TextFlow();
        private final List<Text> runs = new ArrayList<>();
        private final Label rowChip = chip("", "row-chip");
        private final HBox line = new HBox(8, title, rowChip);
        private final Tooltip tip = new Tooltip();

        Row() {
            HBox.setHgrow(title, Priority.ALWAYS);
            line.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(@Nullable Ranked<T> row, boolean empty) {
            super.updateItem(row, empty);
            getStyleClass().remove("unavailable");
            setTooltip(null);
            if (empty || row == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            String text = row.item().title();
            fill(text, row.positions());
            rowChip.setText(row.item().chip());
            rowChip.setVisible(!row.item().chip().isEmpty());
            rowChip.setManaged(!row.item().chip().isEmpty());
            String unavailable = row.item().unavailable();
            if (unavailable != null) {
                getStyleClass().add("unavailable");
                tip.setText(unavailable);
                setTooltip(tip);
            }
            // The row reads as its title, its keys, and why it can't run when it can't (ACC-08).
            line.setAccessibleText(
                    Icons.withChord(text, row.item().chip()) + (unavailable == null ? "" : ". " + unavailable));
            setText(null);
            setGraphic(line);
        }

        /**
         * The title as runs of matched and plain text, a run per change of style, never a node per letter
         * (PERF-10); the Text nodes are kept and reused.
         */
        private void fill(String text, int[] positions) {
            int used = 0;
            int start = 0;
            int next = 0;
            while (start < text.length()) {
                boolean hit = next < positions.length && positions[next] == start;
                int end = start;
                while (end < text.length()) {
                    boolean endHit = next < positions.length && positions[next] == end;
                    if (endHit != hit) break;
                    if (endHit) next++;
                    end++;
                }
                if (used == runs.size()) runs.add(new Text());
                Text run = runs.get(used++);
                run.setText(text.substring(start, end));
                run.getStyleClass().setAll(hit ? "match" : "plain");
                start = end;
            }
            if (title.getChildren().size() != used) title.getChildren().setAll(runs.subList(0, used));
        }
    }
}
