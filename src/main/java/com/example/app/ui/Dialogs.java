package com.example.app.ui;

import java.util.Map;

import javafx.collections.ListChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The one way to make a dialog (WIN-04): a dialog is its own scene and inherits neither the app stylesheet
 * nor the UI font, so every one goes through {@link #styled}. It also gives every dialog the same button
 * order on every OS, Cancel left of every action (WIN-11), and words JavaFX's stock buttons from the
 * catalog instead of the JVM's locale (CONF-14).
 */
public final class Dialogs {

    /**
     * One {@code ButtonBar} order for every OS (WIN-11): help at the left, then Cancel ({@code C}) before
     * every action, so an unsaved-changes question reads Cancel, Save ({@code Y}), Discard ({@code N})
     * (CONF-05). JavaFX's own default changes with the OS.
     */
    public static final String BUTTON_ORDER = "L_HE+CUYNBXIAO_R";

    /** JavaFX's stock buttons and the catalog key that words each one (CONF-14). */
    private static final Map<ButtonType, String> STOCK = Map.of(
            ButtonType.OK, "button.ok",
            ButtonType.CANCEL, "button.cancel",
            ButtonType.YES, "button.yes",
            ButtonType.NO, "button.no",
            ButtonType.APPLY, "button.apply",
            ButtonType.CLOSE, "button.close");

    private Dialogs() {}

    public static <T extends Dialog<?>> T styled(T dialog, @Nullable Window owner) {
        if (owner != null) dialog.initOwner(owner);
        DialogPane pane = dialog.getDialogPane();
        if (pane.lookup(".button-bar") instanceof ButtonBar bar) bar.setButtonOrder(BUTTON_ORDER);
        wordStockButtons(pane);
        // A button added after this call is worded too: DialogPane makes a new Button for each change.
        pane.getButtonTypes().addListener((ListChangeListener<ButtonType>) change -> wordStockButtons(pane));
        Themes.style(pane.getScene());
        return dialog;
    }

    /** Sets each stock button's text from the catalog; a button the app named itself is left alone. */
    private static void wordStockButtons(DialogPane pane) {
        for (ButtonType type : pane.getButtonTypes()) {
            String key = STOCK.get(type);
            if (key != null && pane.lookupButton(type) instanceof Button button) button.setText(Messages.tr(key));
        }
    }
}
