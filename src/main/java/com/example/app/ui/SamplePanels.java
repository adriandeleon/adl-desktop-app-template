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
        private final VBox node;

        Documents(DocumentTabs tabs) {
            list.getStyleClass().add("documents-list");
            list.getItems().setAll(SampleFiles.all());
            list.setCellFactory(v -> new ListCell<>() {
                @Override
                protected void updateItem(SampleFiles.@Nullable Sample sample, boolean empty) {
                    super.updateItem(sample, empty);
                    getStyleClass().removeIf(c -> c.startsWith("git-status-"));
                    if (empty || sample == null) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }
                    setText(sample.name());
                    setGraphic(Icons.line(Icons.FILE));
                    String status = TabGitStatus.classFor(sample);
                    if (status != null) getStyleClass().add(status);
                }
            });
            Runnable openSelected = () -> {
                SampleFiles.Sample sample = list.getSelectionModel().getSelectedItem();
                if (sample == null) return;
                Tab tab = tabs.open(sample);
                DocumentView document = tabs.document(tab);
                if (document != null) document.text().requestFocus();
            };
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

        ListView<SampleFiles.Sample> list() {
            return list;
        }
    }

    /** The active document's headings; a click puts the caret on one. */
    static final class Outline {
        private final DocumentTabs tabs;
        private final ListView<String> list = new ListView<>();
        private final VBox node;

        Outline(DocumentTabs tabs) {
            this.tabs = tabs;
            list.getStyleClass().add("outline-list");
            list.setPlaceholder(new Label(Messages.tr("outline.empty")));
            list.setOnMouseClicked(e -> reveal(list.getSelectionModel().getSelectedItem()));
            list.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    reveal(list.getSelectionModel().getSelectedItem());
                    e.consume();
                }
            });
            VBox.setVgrow(list, Priority.ALWAYS);
            node = new VBox(list);
            refresh();
        }

        /** Lists the active document's headings again (TABS-23). */
        void refresh() {
            DocumentView document = tabs.activeDocument();
            list.getItems()
                    .setAll(
                            document == null
                                    ? List.of()
                                    : SampleFiles.headings(document.text().getText()));
        }

        private void reveal(@Nullable String heading) {
            DocumentView document = tabs.activeDocument();
            if (heading == null || document == null) return;
            String text = document.text().getText();
            int at = 0;
            for (String line : text.split("\n", -1)) {
                if (line.startsWith("#") && line.replaceFirst("^#+\\s*", "").equals(heading)) break;
                at += line.length() + 1;
            }
            document.text().positionCaret(Math.min(at, text.length()));
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

        private final TextArea console = new TextArea();
        private final BooleanProperty running = new SimpleBooleanProperty(this, "running");
        private final VBox node;
        private @Nullable Timeline task;

        Output() {
            console.setEditable(false);
            console.getStyleClass().addAll("output-console", "debug-log-text");
            console.setPromptText(Messages.tr("output.empty"));
            Label hint = new Label(Messages.tr("output.hint"));
            hint.getStyleClass().add("hint");
            HBox bar = new HBox(8, hint);
            bar.setAlignment(Pos.CENTER_LEFT);
            bar.getStyleClass().add("output-bar");
            VBox.setVgrow(console, Priority.ALWAYS);
            node = new VBox(bar, console);
        }

        /** Runs the sample task: a line now and then, the window busy until the last (TABS-22). */
        void run() {
            if (running.get()) return;
            running.set(true);
            console.appendText(Messages.tr("output.started") + "\n");
            Timeline timeline = new Timeline();
            for (int i = 1; i <= STEPS; i++) {
                int step = i;
                timeline.getKeyFrames().add(new KeyFrame(Duration.millis(600.0 * i), e -> {
                    console.appendText(Messages.tr("output.step", step, STEPS) + "\n");
                    if (step == STEPS) finish();
                }));
            }
            task = timeline;
            timeline.play();
        }

        /** Ends the task now: the last line, and the window no longer busy. */
        void finish() {
            Timeline timeline = task;
            task = null;
            if (timeline != null) timeline.stop();
            if (!running.get()) return;
            console.appendText(Messages.tr("output.done") + "\n");
            running.set(false);
        }

        BooleanProperty runningProperty() {
            return running;
        }

        TextArea console() {
            return console;
        }

        Region node() {
            return node;
        }
    }
}
