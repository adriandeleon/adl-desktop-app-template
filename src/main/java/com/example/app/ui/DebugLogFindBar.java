package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import com.example.app.i18n.Messages;

/**
 * The find bar of the Debug Log, a read-only console (Pauta's find bar template, after Editora's
 * {@code ConsoleFindBar}): a node in the window, never a popup (FIND-01). It searches as you type, once
 * typing pauses, and shows the count (FIND-02); Enter steps to the next match and Shift+Enter to the
 * previous one, wrapping (FIND-06); Escape or Close drops a pending search, clears the match and gives
 * the keyboard back to the log (FIND-07).
 *
 * <p>Stateless between steps: each search scans the log's current text, so lines that arrive while the
 * bar is open are searched too.
 */
final class DebugLogFindBar extends HBox {

    /** More matches than this are counted as "{@value}+". */
    static final int MAX_MATCHES = 10_000;

    private final TextArea target;
    private final TextField query = new TextField();
    private final CheckBox matchCase = new CheckBox(Messages.tr("find.matchCase"));
    private final Label count = new Label();
    private final Debounce searching = new Debounce(this::refine);

    /** The step commands' runner: the buttons and Enter run {@code debugLog.findNext}/{@code findPrevious}. */
    private java.util.function.Consumer<String> run = id -> step(id.equals("debugLog.findNext"));

    void runCommandsThrough(java.util.function.Consumer<String> runner) {
        this.run = runner;
    }

    DebugLogFindBar(TextArea target) {
        super(6);
        this.target = target;
        getStyleClass().add("find-bar");
        setAlignment(Pos.CENTER_LEFT);
        query.setPromptText(Messages.tr("find.prompt"));
        query.setPrefColumnCount(22);
        count.getStyleClass().add("find-count");
        count.setMinWidth(Region.USE_PREF_SIZE);
        Button previous = new Button(Messages.tr("find.previous"));
        Button next = new Button(Messages.tr("find.next"));
        Button close = new Button(Messages.tr("find.close"));
        for (Button button : List.of(previous, next, close)) {
            button.getStyleClass().add("flat");
            button.setFocusTraversable(false);
        }
        previous.setOnAction(e -> run.accept("debugLog.findPrevious"));
        next.setOnAction(e -> run.accept("debugLog.findNext"));
        close.setOnAction(e -> close());
        query.textProperty().addListener((o, was, now) -> searching.request());
        matchCase.selectedProperty().addListener((o, was, now) -> searching.request());
        query.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                searching.flush();
                run.accept(e.isShiftDown() ? "debugLog.findPrevious" : "debugLog.findNext");
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
        getChildren().addAll(query, matchCase, count, previous, next, close);
        setVisible(false);
        setManaged(false);
    }

    /** Shows the bar, seeding the query with a one-line selection when there is one. */
    void open() {
        String seed = target.getSelectedText();
        if (!seed.isBlank() && !seed.contains("\n")) query.setText(seed);
        setVisible(true);
        setManaged(true);
        refine();
        query.requestFocus();
        query.selectAll();
    }

    boolean isOpen() {
        return isVisible();
    }

    /** Closes the bar: the pending search is dropped, the match cleared, and the log has the keyboard. */
    void close() {
        searching.cancel();
        setVisible(false);
        setManaged(false);
        target.deselect();
        target.requestFocus();
    }

    TextField field() {
        return query;
    }

    String countText() {
        return count.getText();
    }

    /** The match at or after the selection, else the first: what typing keeps in view. */
    private void refine() {
        if (!isOpen()) return;
        List<int[]> matches = matches(target.getText());
        if (matches.isEmpty()) {
            show(matches, -1);
            return;
        }
        int from = target.getSelection().getStart();
        int at = 0;
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i)[0] >= from) {
                at = i;
                break;
            }
        }
        select(matches, at);
    }

    /** The next match after the selection ({@code forward}) or the last one before it, wrapping around. */
    void step(boolean forward) {
        List<int[]> matches = matches(target.getText());
        if (matches.isEmpty()) {
            show(matches, -1);
            return;
        }
        IndexRange selection = target.getSelection();
        int at = forward ? 0 : matches.size() - 1;
        if (forward) {
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i)[0] > selection.getStart()) {
                    at = i;
                    break;
                }
            }
        } else {
            for (int i = matches.size() - 1; i >= 0; i--) {
                if (matches.get(i)[0] < selection.getStart()) {
                    at = i;
                    break;
                }
            }
        }
        select(matches, at);
    }

    private void select(List<int[]> matches, int at) {
        int[] match = matches.get(at);
        target.selectRange(match[0], match[1]);
        show(matches, at);
    }

    private void show(List<int[]> matches, int at) {
        if (query.getText().isEmpty()) count.setText("");
        else if (matches.isEmpty()) count.setText(Messages.tr("find.none"));
        else {
            String total = matches.size() >= MAX_MATCHES ? MAX_MATCHES + "+" : Integer.toString(matches.size());
            count.setText(Messages.tr("find.count", at + 1, total));
        }
    }

    /** Every {@code [start, end)} of the query in {@code text}, at most {@link #MAX_MATCHES}. */
    List<int[]> matches(String text) {
        String needle = query.getText();
        List<int[]> out = new ArrayList<>();
        if (needle.isEmpty() || text.isEmpty()) return out;
        // A quoted pattern, not a lower-cased indexOf: case folding can change a string's length.
        int flags = matchCase.isSelected() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Matcher m = Pattern.compile(Pattern.quote(needle), flags).matcher(text);
        while (m.find() && out.size() < MAX_MATCHES) out.add(new int[] {m.start(), m.end()});
        return out;
    }

    CheckBox matchCase() {
        return matchCase;
    }
}
