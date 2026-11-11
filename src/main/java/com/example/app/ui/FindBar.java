package com.example.app.ui;

import java.util.List;

import javafx.application.Platform;
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
import org.jspecify.annotations.Nullable;

/**
 * A find bar over a text area (Pauta's find bar template, after Editora's {@code ConsoleFindBar}): the
 * documents' ({@code find.*}), the Output console's ({@code output.find*}) and the Debug Log's
 * ({@code debugLog.find*}). A node in the window, never a popup (FIND-01). It searches as you type, once
 * typing pauses, and shows the count (FIND-02); Enter steps to the next match and Shift+Enter to the
 * previous one, wrapping (FIND-06); Escape or Close drops a pending search, clears the match and gives
 * the keyboard back to the log (FIND-07).
 *
 * <p>The search runs off the FX thread and only its latest request is applied (PERF-01, PERF-03); a
 * query that grows is narrowed from the matches it already has, never the whole log again (PERF-08,
 * {@link LogSearch}). The steps use those matches; a step asked for while a search runs waits for it.
 * A Refresh or Clear changes the log's text, and the open bar searches the new text.
 */
final class FindBar extends HBox {

    /** More matches than this are counted as "{@value}+". */
    static final int MAX_MATCHES = LogSearch.MAX_OCCURRENCES;

    private final TextArea target;
    private final LatestOnly searches = new LatestOnly(Background::run, Platform::runLater);
    /** The log's text, and how many times it has changed: what a search and a narrowing are for. */
    private String text;

    private long textVersion;
    /** The matches of the last search applied, or null before the first. */
    private @Nullable LogSearch result;
    /** A step asked for while a search was running: true forward, false back, null none. */
    private @Nullable Boolean pendingStep;

    private final TextField query = new TextField();
    private final CheckBox matchCase = new CheckBox(Messages.tr("find.matchCase"));
    private final Label count = new Label();
    private final Debounce searching = new Debounce(this::refine);

    /** Where the caret was when the bar opened: a search starts there (FIND-05). */
    private int anchor;

    /** Gives the field the keymap's caret and editing chords (FIND-09); the window says which keymap is on. */
    void editingKeys(java.util.function.BooleanSupplier emacs) {
        TextInputKeys.install(query, emacs);
    }

    /** The commands that step this bar: Enter, Shift+Enter and the buttons run them. */
    private final String nextId;

    private final String previousId;

    /** The step commands' runner; until the window hands its registry over, the bar steps itself. */
    private java.util.function.Consumer<String> run;

    void runCommandsThrough(java.util.function.Consumer<String> runner) {
        this.run = runner;
    }

    /** A bar over {@code target}, stepped by the commands {@code nextId} and {@code previousId}. */
    FindBar(TextArea target, String nextId, String previousId) {
        super(6);
        this.target = target;
        this.nextId = nextId;
        this.previousId = previousId;
        this.run = id -> step(id.equals(nextId));
        this.text = target.getText();
        target.textProperty().addListener((o, was, now) -> {
            text = now == null ? "" : now;
            textVersion++;
            if (isOpen()) refine(); // the new text is searched, off the FX thread
        });
        getStyleClass().add("find-bar");
        setAlignment(Pos.CENTER_LEFT);
        query.setPromptText(Messages.tr("find.prompt"));
        query.setAccessibleText(Messages.tr("find.prompt")); // the prompt goes when typing starts (ACC-01)
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
        previous.setOnAction(e -> run.accept(previousId));
        next.setOnAction(e -> run.accept(nextId));
        close.setOnAction(e -> close());
        query.textProperty().addListener((o, was, now) -> searching.request());
        matchCase.selectedProperty().addListener((o, was, now) -> searching.request());
        query.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                searching.flush();
                run.accept(e.isShiftDown() ? previousId : nextId);
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

    /**
     * The bar's command (FIND-04): opens the bar, or, run while it is open, steps to the next match with the
     * focus kept in the field.
     */
    void openOrNext() {
        if (isOpen()) {
            step(true);
            query.requestFocus();
        } else {
            open();
        }
    }

    /** Shows the bar, seeding the query with a one-line selection when there is one. */
    void open() {
        // FIND-05: the search starts from where the caret was when the bar opened, so refining the query
        // never runs away down the text.
        anchor = target.getSelection().getStart();
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
        searches.cancel();
        pendingStep = null;
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

    /** Whether a search is waiting for the typing pause or running: the tests wait on it. */
    boolean searching() {
        return searching.pending() || searches.pending();
    }

    /**
     * Searches for the query as it is now: narrowed from the last search when the query only grew, on the
     * FX thread in O(matches); otherwise in the background, its result applied only if no later request
     * superseded it.
     */
    private void refine() {
        if (!isOpen()) return;
        String needle = query.getText();
        boolean matchCaseNow = matchCase.isSelected();
        LogSearch last = result;
        LogSearch narrowed = last == null ? null : last.narrow(textVersion, needle, matchCaseNow);
        if (narrowed != null || needle.isEmpty()) {
            searches.cancel();
            found(narrowed != null ? narrowed : LogSearch.of(text, textVersion, needle, matchCaseNow));
            return;
        }
        String searched = text;
        long version = textVersion;
        searches.submit(() -> LogSearch.of(searched, version, needle, matchCaseNow), this::found);
    }

    /** Applies a search: the match at or after the selection, else the first, is what typing keeps in view. */
    private void found(LogSearch search) {
        result = search;
        List<int[]> matches = search.matches();
        if (matches.isEmpty()) {
            show(search, -1);
        } else {
            int from = anchor;
            int at = 0;
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i)[0] >= from) {
                    at = i;
                    break;
                }
            }
            select(search, at);
        }
        Boolean step = pendingStep;
        pendingStep = null;
        if (step != null) step(step);
    }

    /** Whether the last search applied is for the text, the query and the case setting as they are now. */
    private boolean current(@Nullable LogSearch search) {
        return search != null
                && search.version() == textVersion
                && search.needle().equals(query.getText())
                && search.matchCase() == matchCase.isSelected();
    }

    /** The next match after the selection ({@code forward}) or the last one before it, wrapping around. */
    void step(boolean forward) {
        LogSearch search = result;
        if (search == null || searches.pending() || !current(search)) {
            pendingStep = forward; // taken up when the search lands
            if (!searches.pending()) refine();
            return;
        }
        List<int[]> matches = search.matches();
        if (matches.isEmpty()) {
            show(search, -1);
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
        select(search, at);
    }

    private void select(LogSearch search, int at) {
        int[] match = search.matches().get(at);
        target.selectRange(match[0], match[1]);
        show(search, at);
    }

    private void show(LogSearch search, int at) {
        List<int[]> matches = search.matches();
        if (search.needle().isEmpty()) count.setText("");
        else if (matches.isEmpty()) count.setText(Messages.tr("find.none"));
        else {
            String total = search.capped() ? matches.size() + "+" : Integer.toString(matches.size());
            count.setText(Messages.tr("find.count", at + 1, total));
        }
    }

    CheckBox matchCase() {
        return matchCase;
    }
}
