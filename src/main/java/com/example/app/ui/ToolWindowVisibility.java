package com.example.app.ui;

/**
 * Pure visibility decisions for the tool window stripes, as in Editora. The two preferences are kept apart
 * (TABS-20): {@code visible} is the user's kept show/hide choice for a stripe button, while
 * {@code unavailable} is a passing, context-driven hide (the Outline with no document to outline) that must
 * never clobber that choice.
 */
final class ToolWindowVisibility {

    private ToolWindowVisibility() {}

    /** A stripe button shows only when the user has it visible and it is not unavailable just now. */
    static boolean buttonShown(boolean visiblePref, boolean unavailable) {
        return visiblePref && !unavailable;
    }

    /**
     * A stripe shows when it has a button, and an empty one also while a stripe button is being dragged, so
     * it can be dropped there (TABS-17): an unmanaged, invisible stripe receives no drag events at all.
     */
    static boolean stripeShown(boolean stripeEmpty, boolean dragging) {
        return !stripeEmpty || dragging;
    }
}
