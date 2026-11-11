package com.example.app.ui;

import java.util.function.Supplier;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.layout.Region;

import org.jspecify.annotations.Nullable;

/**
 * One tool window, after Editora's: its id, title, home side, glyph, the command that toggles it
 * ({@code tool.<id>}, TABS-13) and its content, built on first use (TABS-14).
 */
final class ToolWindow {

    /** The three sides a window docks on (TABS-16). */
    enum Side {
        LEFT,
        RIGHT,
        BOTTOM
    }

    private final String id;
    private final StringProperty title = new SimpleStringProperty();
    /** Something this window shows is under way; its stripe button badges a spinner (TABS-22). */
    private final BooleanProperty busy = new SimpleBooleanProperty(this, "busy");

    private final Side side;
    private final String glyph;
    /** Builds the content on first use; null once it has. */
    private @Nullable Supplier<? extends Region> contentSupplier;

    private @Nullable Region content;
    /** The share of the window's height this bottom window takes on its first open; 0 for its side's. */
    private double firstOpenShare;

    ToolWindow(String id, String title, Side side, String glyph, Supplier<? extends Region> content) {
        this.id = id;
        this.title.set(title);
        this.side = side;
        this.glyph = glyph;
        this.contentSupplier = content;
    }

    String id() {
        return id;
    }

    /** The command that toggles this window: {@code tool.<id>} (TABS-13). */
    String commandId() {
        return "tool." + id;
    }

    String title() {
        return title.get();
    }

    StringProperty titleProperty() {
        return title;
    }

    BooleanProperty busyProperty() {
        return busy;
    }

    /** The side it docks on until the user moves it. */
    Side homeSide() {
        return side;
    }

    String glyph() {
        return glyph;
    }

    /**
     * A bottom window that is unusable at the default height asks for more on its first open (TABS-21);
     * the user's own size wins afterwards.
     */
    ToolWindow firstOpenShare(double share) {
        this.firstOpenShare = Math.max(0, Math.min(0.8, share));
        return this;
    }

    double firstOpenShare() {
        return firstOpenShare;
    }

    /** The content, building it now if this is its first use (TABS-14). */
    Region content() {
        Supplier<? extends Region> build = contentSupplier;
        if (build != null) {
            contentSupplier = null;
            content = build.get();
        }
        return java.util.Objects.requireNonNull(content);
    }

    /** The content if it has been built, else null: for a lookup that must not be what builds it. */
    @Nullable
    Region contentIfBuilt() {
        return content;
    }
}
