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
            @Nullable String unavailable) {}

    record Ranked<T>(Item<T> item, int[] positions, int score) {}

    static final double WIDTH = 620;
    static final double ROW_HEIGHT = 36;
    static final int VISIBLE_ROWS = 8;

    private final OverlayHost host;
    private final List<Item<T>> items;
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
        HBox inputRow = new HBox(8, query);
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
        Label hint = new Label(Messages.tr("palette.hint"));
        hint.getStyleClass().add("palette-hint");

        card = new VBox(6, inputRow, list, description, hint);
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
        host.show(card, Pos.TOP_CENTER, new Insets(60, 0, 0, 0), null);
        query.requestFocus();
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
            FuzzyMatch.Match title = FuzzyMatch.match(query, item.title());
            FuzzyMatch.Match desc = FuzzyMatch.match(query, item.description());
            int score = Integer.MIN_VALUE;
            int[] positions = new int[0];
            if (title != null) {
                score = title.score() * 3 + FuzzyMatch.phraseBonus(query, item.title());
                positions = title.positions();
            }
            if (desc != null) score = Math.max(score, desc.score());
            if (score != Integer.MIN_VALUE) out.add(new Ranked<>(item, positions, score));
        }
        if (!query.isBlank()) out.sort(Comparator.comparingInt((Ranked<T> r) -> -r.score()));
        return out;
    }

    private void refresh() {
        list.getItems().setAll(rank(items, query.getText()));
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
        boolean ctrl = e.isControlDown();
        KeyCode code = e.getCode();
        if (code == KeyCode.DOWN || (ctrl && code == KeyCode.N)) move(1);
        else if (code == KeyCode.UP || (ctrl && code == KeyCode.P)) move(-1);
        else if (code == KeyCode.PAGE_DOWN) move(VISIBLE_ROWS);
        else if (code == KeyCode.PAGE_UP) move(-VISIBLE_ROWS);
        else if (code == KeyCode.ENTER) choose();
        else return;
        e.consume();
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
            TextFlow title = new TextFlow();
            String text = row.item().title();
            int next = 0;
            for (int i = 0; i < text.length(); i++) {
                boolean hit = next < row.positions().length && row.positions()[next] == i;
                if (hit) next++;
                Text piece = new Text(String.valueOf(text.charAt(i)));
                piece.getStyleClass().add(hit ? "match" : "plain");
                title.getChildren().add(piece);
            }
            HBox.setHgrow(title, Priority.ALWAYS);
            HBox line = new HBox(8, title);
            line.setAlignment(Pos.CENTER_LEFT);
            if (!row.item().chip().isEmpty())
                line.getChildren().add(chip(row.item().chip(), "row-chip"));
            String unavailable = row.item().unavailable();
            if (unavailable != null) {
                getStyleClass().add("unavailable");
                setTooltip(new Tooltip(unavailable));
            }
            // The row reads as its title, its keys, and why it can't run when it can't (ACC-08).
            line.setAccessibleText(
                    Icons.withChord(text, row.item().chip()) + (unavailable == null ? "" : ". " + unavailable));
            setText(null);
            setGraphic(line);
        }
    }
}
