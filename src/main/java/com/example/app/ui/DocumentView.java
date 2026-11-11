package com.example.app.ui;

import java.nio.file.Path;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.TextArea;

import org.jspecify.annotations.Nullable;

/**
 * A tab's document: a sample file in a text area. Unsaved means its text differs from the file's; read-only
 * means it cannot be edited (TABS-03). An app's editor takes this place.
 */
final class DocumentView {

    private final SampleFiles.Sample sample;
    private final String saved;
    private final TextArea text = new TextArea();
    private final BooleanBinding dirty;

    /** Opens {@code sample} with {@code content} (its text, or what it held when its tab closed). */
    DocumentView(SampleFiles.Sample sample, @Nullable String content, int caret) {
        this.sample = sample;
        this.saved = sample.text();
        text.getStyleClass().add("document-text");
        text.setText(content == null ? saved : content);
        text.setEditable(!sample.readOnly());
        text.setWrapText(true);
        text.positionCaret(Math.min(Math.max(0, caret), text.getLength()));
        dirty = Bindings.createBooleanBinding(() -> !saved.equals(text.getText()), text.textProperty());
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

    boolean isDirty() {
        return dirty.get();
    }

    BooleanBinding dirtyProperty() {
        return dirty;
    }

    boolean isReadOnly() {
        return sample.readOnly();
    }

    int caret() {
        return text.getCaretPosition();
    }
}
