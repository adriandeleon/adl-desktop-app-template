package com.example.app.ui;

import java.nio.file.Path;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.jspecify.annotations.Nullable;

/**
 * A tab's document: a sample file in a text area. Unsaved means its text differs from the file's; read-only
 * means it cannot be edited (TABS-03). An app's editor takes this place.
 *
 * <p>An edit marks the document unsaved at once and at no cost; whether the text has come back to the file's
 * is a whole-document comparison, so it waits for typing to pause, never runs per keystroke (PERF-08).
 */
final class DocumentView {

    private final SampleFiles.Sample sample;
    private final String saved;
    private final TextArea text = new TextArea();
    /** The document's find bar, over its text (FIND-01): {@code find.show} opens it. */
    private final FindBar findBar = new FindBar(text, "find.next", "find.previous");

    private final VBox node = new VBox(findBar, text);
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper(this, "dirty");
    private final Debounce compare = new Debounce(this::compareWithSaved);

    /** Opens {@code sample} with {@code content} (its text, or what it held when its tab closed). */
    DocumentView(SampleFiles.Sample sample, @Nullable String content, int caret) {
        this.sample = sample;
        this.saved = sample.text();
        text.getStyleClass().add("document-text");
        text.setAccessibleText(sample.name()); // a screen reader says which document (ACC-01)
        text.setText(content == null ? saved : content);
        text.setEditable(!sample.readOnly());
        text.setWrapText(true);
        text.positionCaret(Math.min(Math.max(0, caret), text.getLength()));
        VBox.setVgrow(text, Priority.ALWAYS);
        findBar.getStyleClass().add("document-find-bar");
        compareWithSaved();
        text.textProperty().addListener((o, was, now) -> {
            dirty.set(true);
            compare.request();
        });
    }

    /** Whether the text is the file's again, after an undo or a retyped word. */
    private void compareWithSaved() {
        dirty.set(!saved.equals(text.getText()));
    }

    SampleFiles.Sample sample() {
        return sample;
    }

    Path path() {
        return sample.path();
    }

    String title() {
        return sample.name();
    }

    TextArea text() {
        return text;
    }

    /** What the tab shows: the find bar, when open, over the text. */
    VBox node() {
        return node;
    }

    FindBar findBar() {
        return findBar;
    }

    boolean isDirty() {
        return dirty.get();
    }

    ReadOnlyBooleanProperty dirtyProperty() {
        return dirty.getReadOnlyProperty();
    }

    boolean isReadOnly() {
        return sample.readOnly();
    }

    int caret() {
        return text.getCaretPosition();
    }
}
