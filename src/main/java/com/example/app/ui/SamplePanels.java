package com.example.app.ui;

import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The content of the template's three tool windows: placeholders that give each tool panel rule something
 * to show. An app replaces them with its own (a project tree, a structure view, a console).
 *
 * <ul>
 *   <li>{@link Documents} (left): the sample project, each file in its Git status color, the same classes
 *       the tabs wear (TABS-08). Enter or a double-click opens one.
 *   <li>{@link Outline} (right): the headings of the active tab's document; it follows the active tab
 *       (TABS-23), and is unavailable on a tab with no document (TABS-20).
 *   <li>{@link Output} (bottom): a console for a sample task that runs a few seconds, busy meanwhile
 *       (TABS-22); too short at the bottom's default height, it asks for more on its first open (TABS-21).
 * </ul>
 */
final class SamplePanels {

    private SamplePanels() {}

    /** The sample project, a row per file in its Git status color. */
    static final class Documents {
        private final ListView<SampleFiles.Sample> list = new ListView<>();
        private final DocumentTabs tabs;
        private final VBox node;

        /** {@code rowMenu} is each row's right-click menu (MENU-05): {@code documents.open} and its kin. */
        Documents(DocumentTabs tabs, java.util.function.Supplier<CommandMenu> rowMenu) {
            this.tabs = tabs;
            list.getStyleClass().add("documents-list");
            list.setAccessibleText(Messages.tr("tool.documents"));
            list.getItems().setAll(SampleFiles.all());
            list.setCellFactory(v -> CommandMenu.onRow(
                    new ListCell<>() {
                        @Override
                        protected void updateItem(SampleFiles.@Nullable Sample sample, boolean empty) {
                            super.updateItem(sample, empty);
                            getStyleClass().removeIf(c -> c.startsWith("git-status-"));
                            if (empty || sample == null) {
                                setText(null);
                                setAccessibleText(null);
                                setGraphic(null);
                                return;
                            }
                            // The Git status as its letter after the name, never color alone (ACC-05).
                            SampleFiles.GitStatus git = sample.status();
                            setText(git == null ? sample.name() : sample.name() + "   " + git.letter());
                            setAccessibleText(git == null ? sample.name() : sample.name() + ", " + git.words());
                            setGraphic(Icons.line(Icons.forFileName(sample.name())));
                            String status = TabGitStatus.classFor(sample);
                            if (status != null) getStyleClass().add(status);
                        }
                    },
                    rowMenu));
            Runnable openSelected = this::openSelected;
            list.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) openSelected.run();
            });
            list.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    openSelected.run();
                    e.consume();
                }
            });
            VBox.setVgrow(list, Priority.ALWAYS);
            node = new VBox(list);
        }

        Region node() {
            return node;
        }

        /** Opens the selected file in a tab and gives it the keyboard ({@code documents.open}). */
        void openSelected() {
            SampleFiles.Sample sample = list.getSelectionModel().getSelectedItem();
            if (sample == null) return;
            Tab tab = tabs.open(sample);
            DocumentView document = tabs.document(tab);
            if (document != null) document.text().requestFocus();
        }

        boolean hasSelection() {
            return list.getSelectionModel().getSelectedItem() != null;
        }

        ListView<SampleFiles.Sample> list() {
            return list;
        }
    }

    /** The active document's headings; a click puts the caret on one. */
    static final class Outline {
        private final DocumentTabs tabs;
        private final ListView<String> list = new ListView<>();
        private final VBox node;
        /** Where each listed heading's line starts, in the list's order. */
        private List<Integer> offsets = List.of();
        /** The scan of the text runs off the FX thread; only the latest one lands (PERF-01, PERF-03). */
        private final LatestOnly scans = new LatestOnly(Background::run, javafx.application.Platform::runLater);

        Outline(DocumentTabs tabs) {
            this.tabs = tabs;
            list.getStyleClass().add("outline-list");
            list.setAccessibleText(Messages.tr("tool.outline"));
            list.setPlaceholder(new Label(Messages.tr("outline.empty")));
            list.setOnMouseClicked(e -> reveal(list.getSelectionModel().getSelectedIndex()));
            list.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    reveal(list.getSelectionModel().getSelectedIndex());
                    e.consume();
                }
            });
            VBox.setVgrow(list, Priority.ALWAYS);
            node = new VBox(list);
            refresh();
        }

        /**
         * Lists the active document's headings again (TABS-23): the text is scanned off the FX thread, and the
         * list and the offsets are set together when the scan lands.
         */
        void refresh() {
            DocumentView document = tabs.activeDocument();
            if (document == null) {
                scans.cancel();
                offsets = List.of();
                list.getItems().clear();
                return;
            }
            String text = document.text().getText();
            scans.submit(() -> SampleFiles.headingsAt(text), found -> {
                offsets = found.stream().map(SampleFiles.Heading::offset).toList();
                list.getItems()
                        .setAll(found.stream().map(SampleFiles.Heading::title).toList());
            });
        }

        /** Whether a scan is still running: the tests wait on it. */
        boolean scanning() {
            return scans.pending();
        }

        private void reveal(int index) {
            DocumentView document = tabs.activeDocument();
            if (index < 0 || index >= offsets.size() || document == null) return;
            document.text()
                    .positionCaret(Math.min(offsets.get(index), document.text().getLength()));
            document.text().requestFocus();
        }

        Region node() {
            return node;
        }

        ListView<String> list() {
            return list;
        }
    }

    /** A console and a sample task that writes to it for a few seconds. */
    static final class Output {
        /** The lines the sample task writes, one per step. */
        static final int STEPS = 5;

        /** The console keeps this many lines; past it the oldest go first (PERF-11). */
        static final int MAX_LINES = 1000;

        private final TextArea console = new TextArea();
        /** The console's find bar (FIND-01): {@code output.find} opens it. */
        private final FindBar findBar = new FindBar(console, "output.findNext", "output.findPrevious");

        private final BooleanProperty running = new SimpleBooleanProperty(this, "running");
        private final VBox node;
        private @Nullable Timeline task;

        Output() {
            console.setEditable(false);
            console.setAccessibleText(Messages.tr("tool.output"));
            console.getStyleClass().addAll("output-console", "debug-log-text");
            console.setPromptText(Messages.tr("output.empty"));
            Label hint = new Label(Messages.tr("output.hint"));
            hint.getStyleClass().add("hint");
            HBox bar = new HBox(8, hint);
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.getStyleClass().add("output-bar");
            VBox.setVgrow(console, Priority.ALWAYS);
            node = new VBox(bar, findBar, console);
        }

        /** Runs the sample task: a line now and then, the window busy until the last (TABS-22). */
        void run() {
            if (running.get()) return;
            running.set(true);
            append(Messages.tr("output.started") + "\n");
            Timeline timeline = new Timeline();
            for (int i = 1; i <= STEPS; i++) {
                int step = i;
                timeline.getKeyFrames().add(new KeyFrame(Duration.millis(600.0 * i), e -> {
                    append(Messages.tr("output.step", step, STEPS) + "\n");
                    if (step == STEPS) finish();
                }));
            }
            task = timeline;
            timeline.play();
        }

        /** Adds a line, then drops the oldest lines past {@link #MAX_LINES}: the newest are kept (PERF-11). */
        void append(String line) {
            console.appendText(line);
            int extra = console.getParagraphs().size() - 1 - MAX_LINES; // the text ends with a newline
            if (extra <= 0) return;
            String text = console.getText();
            int cut = 0;
            for (int i = 0; i < extra; i++) cut = text.indexOf('\n', cut) + 1;
            console.deleteText(0, cut);
        }

        /** Empties the console ({@code output.clear}). */
        void clear() {
            console.clear();
        }

        /** Ends the task now: the last line, and the window no longer busy. */
        void finish() {
            Timeline timeline = task;
            task = null;
            if (timeline != null) timeline.stop();
            if (!running.get()) return;
            append(Messages.tr("output.done") + "\n");
            running.set(false);
        }

        BooleanProperty runningProperty() {
            return running;
        }

        FindBar findBar() {
            return findBar;
        }

        TextArea console() {
            return console;
        }

        Region node() {
            return node;
        }
    }
}
