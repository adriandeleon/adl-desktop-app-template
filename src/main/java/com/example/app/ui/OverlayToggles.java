package com.example.app.ui;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.scene.control.ButtonBase;

import com.example.app.command.CommandRegistry;
import org.jspecify.annotations.Nullable;

/**
 * Which command's overlay is up, so the button that opens it looks pressed while it is (TBAR-07): the
 * way back out does not look like the way in. The overlay is credited to the command that was running
 * when it opened, so the palette's key, its menu row and its button all light the same button.
 */
final class OverlayToggles {

    /** The pressed look: an accent-subtle fill and the glyph in the accent color ({@code .button:open}). */
    static final PseudoClass OPEN = PseudoClass.getPseudoClass("open");

    private final ReadOnlyObjectWrapper<@Nullable String> opener = new ReadOnlyObjectWrapper<>();

    OverlayToggles(OverlayHost overlays, CommandRegistry commands) {
        overlays.showingProperty().addListener((o, was, up) -> opener.set(up ? commands.running() : null));
    }

    /** The command whose overlay is up, or null when none is (or it was opened outside a command). */
    ReadOnlyObjectProperty<@Nullable String> openerProperty() {
        return opener.getReadOnlyProperty();
    }

    /** Lights {@code button} while the overlay {@code commandId} opened is up. */
    void track(ButtonBase button, String commandId) {
        button.pseudoClassStateChanged(OPEN, commandId.equals(opener.get()));
        opener.addListener((o, was, now) -> button.pseudoClassStateChanged(OPEN, commandId.equals(now)));
    }
}
