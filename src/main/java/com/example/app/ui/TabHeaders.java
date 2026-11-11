package com.example.app.ui;

import java.util.function.Predicate;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import org.jspecify.annotations.Nullable;

/**
 * The graphic header of a tab, as in Editora: the header is the title (TABS-02). The Tab's own text stays
 * empty; the graphic carries the pin, the file glyph and the title in a {@code .tab-title} label, and the
 * tooltip is the file's full path. The one header carries the states (TABS-03): a {@code • } prefix and
 * {@code .dirty} (amber italic) for unsaved, the pin and {@code .pinned} (bold) for pinned, {@code .read-only}
 * (muted italic) for read-only. The graphic is also what makes the header a drag handle (TABS-07).
 */
final class TabHeaders {

    /** Moves a dragged tab beside a target: {@code after} is the target's right half. */
    interface Mover {
        void moveBeside(Tab dragged, Tab target, boolean after);
    }

    private final Predicate<Tab> pinned;
    private final Mover mover;

    /** The tab whose header is being dragged. */
    private @Nullable Tab draggedTab;

    TabHeaders(Predicate<Tab> pinned, Mover mover) {
        this.pinned = pinned;
        this.mover = mover;
    }

    @Nullable
    Tab draggedTab() {
        return draggedTab;
    }

    /** Renders a document tab's header from its state now; called again whenever a state changes. */
    void documentHeader(Tab tab, DocumentView document) {
        boolean dirty = document.isDirty();
        boolean isPinned = pinned.test(tab);
        Label title = new Label((dirty ? "• " : "") + document.title());
        title.getStyleClass().add("tab-title");
        HBox header = header(tab);
        toggle(header, "dirty", dirty);
        toggle(header, "pinned", isPinned);
        toggle(header, "read-only", document.isReadOnly());
        if (isPinned) header.getChildren().add(Icons.line(Icons.PIN));
        Node glyph = Icons.line(Icons.forFileName(document.title()));
        glyph.getStyleClass().add("tab-file-icon");
        header.getChildren().addAll(glyph, title);
        // The Git status as its letter after the name, and in words in the tooltip: never color alone (ACC-05).
        SampleFiles.GitStatus status = document.sample().status();
        if (status != null) {
            Label letter = new Label(status.letter());
            letter.getStyleClass().addAll("tab-title", "git-letter");
            letter.setAccessibleText(status.words());
            header.getChildren().add(letter);
        }
        tab.setText("");
        tab.setGraphic(header);
        tooltip(tab, status == null ? document.path().toString() : document.path() + " — " + status.words());
        TabGitStatus.apply(tab, TabGitStatus.classFor(document.sample()));
    }

    /** Renders a tab that holds no file (Welcome): its title alone, as its tooltip too. */
    void contentHeader(Tab tab, String text) {
        Label title = new Label(text);
        title.getStyleClass().add("tab-title");
        HBox header = header(tab);
        toggle(header, "pinned", pinned.test(tab));
        if (pinned.test(tab)) header.getChildren().add(Icons.line(Icons.PIN));
        header.getChildren().add(title);
        tab.setText("");
        tab.setGraphic(header);
        tooltip(tab, text);
    }

    private HBox header(Tab tab) {
        HBox header = new HBox(6);
        header.getStyleClass().add("tab-header");
        header.setAlignment(Pos.CENTER_LEFT);
        enableTabDrag(header, tab);
        return header;
    }

    private static void tooltip(Tab tab, String text) {
        Tooltip tip = tab.getTooltip();
        if (tip == null) {
            tip = new Tooltip();
            tip.setShowDelay(Icons.TOOLTIP_DELAY);
            tab.setTooltip(tip);
        }
        tip.setText(text);
    }

    /**
     * Drag to reorder (TABS-07): the dragged header dims ({@code .tab-dragging}) and the target shows an
     * accent line on the edge where it will land ({@code .tab-drop-before} / {@code .tab-drop-after}).
     */
    private void enableTabDrag(Node header, Tab tab) {
        header.setOnDragDetected(e -> {
            Dragboard db = header.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("tab"); // a marker; the dragged tab is tracked in draggedTab
            db.setContent(content);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            db.setDragView(header.snapshot(params, null), e.getX(), e.getY());
            draggedTab = tab;
            header.getStyleClass().add("tab-dragging");
            e.consume();
        });
        header.setOnDragOver(e -> {
            Tab dragged = draggedTab;
            if (dragged != null && dragged != tab) {
                e.acceptTransferModes(TransferMode.MOVE);
                boolean after = e.getX() > header.getBoundsInLocal().getWidth() / 2;
                toggle(header, "tab-drop-after", after);
                toggle(header, "tab-drop-before", !after);
            }
            e.consume();
        });
        header.setOnDragExited(e -> clearDropMarkers(header));
        header.setOnDragDropped(e -> {
            clearDropMarkers(header);
            Tab dragged = draggedTab;
            boolean done = dragged != null && dragged != tab;
            if (dragged != null && done) {
                mover.moveBeside(
                        dragged, tab, e.getX() > header.getBoundsInLocal().getWidth() / 2);
            }
            e.setDropCompleted(done);
            e.consume();
        });
        header.setOnDragDone(e -> {
            header.getStyleClass().remove("tab-dragging");
            clearDropMarkers(header);
            draggedTab = null;
        });
    }

    private static void clearDropMarkers(Node header) {
        header.getStyleClass().removeAll("tab-drop-before", "tab-drop-after");
    }

    static void toggle(Node node, String styleClass, boolean on) {
        node.getStyleClass().remove(styleClass);
        if (on) node.getStyleClass().add(styleClass);
    }
}
