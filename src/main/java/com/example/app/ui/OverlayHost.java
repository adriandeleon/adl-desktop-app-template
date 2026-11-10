package com.example.app.ui;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import org.jspecify.annotations.Nullable;

/**
 * In-scene overlays (the Command Palette, pickers, the Message Log) in the window's root
 * {@code StackPane}, over a faint scrim. Not a {@code javafx.stage.Popup}: on Windows a popup does
 * not reliably take keyboard focus and the whole app can stop receiving keystrokes.
 */
public final class OverlayHost {

    private final StackPane root;
    private final Region scrim = new Region();
    private @Nullable Node card;
    private @Nullable Node returnFocus;
    private @Nullable Runnable onHide;
    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper();

    public OverlayHost(Node content) {
        root = new StackPane(content);
        root.getStyleClass().add("overlay-root");
        scrim.getStyleClass().add("overlay-backdrop");
        scrim.setOnMouseClicked(e -> hide());
        root.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (isShowing() && (e.getCode() == KeyCode.ESCAPE || (e.isControlDown() && e.getCode() == KeyCode.G))) {
                hide();
                e.consume();
            }
        });
    }

    public StackPane root() {
        return root;
    }

    public boolean isShowing() {
        return card != null;
    }

    /** Whether an overlay is up: what a toolbar button watches to look pressed while its own is (TBAR-07). */
    public ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /** Shows {@code node} over the scrim, replacing any overlay already open. */
    public void show(Node node, Pos position, Insets margin, @Nullable Runnable onHide) {
        hide();
        Scene scene = root.getScene();
        returnFocus = scene == null ? null : scene.getFocusOwner();
        card = node;
        this.onHide = onHide;
        StackPane.setAlignment(node, position);
        StackPane.setMargin(node, margin);
        root.getChildren().addAll(scrim, node);
        showing.set(true);
    }

    /** Closes the overlay and gives focus back to where it was. */
    public void hide() {
        Node open = card;
        if (open == null) return;
        card = null;
        root.getChildren().removeAll(scrim, open);
        showing.set(false);
        Runnable after = onHide;
        onHide = null;
        if (after != null) after.run();
        Node focus = returnFocus;
        returnFocus = null;
        if (focus != null) focus.requestFocus();
    }
}
