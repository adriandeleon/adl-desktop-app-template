package com.example.app.ui;

import javafx.scene.control.Dialog;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

/**
 * The one way to make a dialog: a dialog is its own scene and inherits neither the app stylesheet nor
 * the UI font, so every one goes through {@link #styled}.
 */
public final class Dialogs {

    private Dialogs() {}

    public static <T extends Dialog<?>> T styled(T dialog, @Nullable Window owner) {
        if (owner != null) dialog.initOwner(owner);
        Themes.style(dialog.getDialogPane().getScene());
        return dialog;
    }
}
