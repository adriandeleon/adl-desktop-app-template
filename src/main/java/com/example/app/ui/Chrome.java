package com.example.app.ui;

import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The window's visibility and enablement rules, as pure decisions (QUAL-05): no JavaFX here, so each
 * is unit-tested without a toolkit, and the windows only ask.
 */
final class Chrome {

    private Chrome() {}

    /**
     * Whether a window draws its own title bar with the menus in it: JavaFX 27's extended stage style.
     * Never under a screen menu bar (macOS: there are no menus in the window to put there), and only
     * where the platform supports the style: JavaFX quietly downgrades an unsupported {@code EXTENDED}
     * to {@code DECORATED}, which would draw the header under a native title bar as a second row.
     */
    static boolean usesHeaderBar(boolean systemMenu, boolean setting, boolean extendedSupported) {
        return !systemMenu && setting && extendedSupported;
    }

    /**
     * Why the menus can't go in the title bar here, or null when they can (OS-01): the setting and its command
     * exist on every OS and stay in place, disabled with this reason where the system can't do it.
     */
    static @Nullable String titleBarMenusUnavailable(boolean systemMenu, boolean extendedSupported) {
        if (systemMenu) return Messages.tr("palette.disabled.titleBarScreenMenu");
        if (!extendedSupported) return Messages.tr("palette.disabled.titleBarUnsupported");
        return null;
    }

    /** {@link #titleBarMenusUnavailable(boolean, boolean)} for this OS and this toolkit. */
    static @Nullable String titleBarMenusUnavailable() {
        return titleBarMenusUnavailable(
                com.example.app.Os.isMac(),
                javafx.application.Platform.isSupported(javafx.application.ConditionalFeature.EXTENDED_WINDOW));
    }

    /** Why Zoom In cannot run: the UI is at its largest. */
    static @Nullable String uiZoomInUnavailable(int percent) {
        return percent >= com.example.app.config.Settings.MAX_UI_ZOOM
                ? Messages.tr("palette.disabled.uiZoomLargest", com.example.app.config.Settings.MAX_UI_ZOOM)
                : null;
    }

    /** Why Zoom Out cannot run: the UI is at its smallest. */
    static @Nullable String uiZoomOutUnavailable(int percent) {
        return percent <= com.example.app.config.Settings.MIN_UI_ZOOM
                ? Messages.tr("palette.disabled.uiZoomSmallest", com.example.app.config.Settings.MIN_UI_ZOOM)
                : null;
    }

    /** Why Reset Zoom cannot run: the UI is at its default size already. */
    static @Nullable String uiZoomResetUnavailable(int percent) {
        return percent == com.example.app.config.Settings.DEFAULT_UI_ZOOM
                ? Messages.tr("palette.disabled.uiZoomDefault")
                : null;
    }

    /**
     * Why a text command cannot run: no text has the keyboard, or (for Cut and Paste, which {@code edits})
     * the text is read-only.
     */
    static @Nullable String editUnavailable(javafx.scene.control.@Nullable TextInputControl text, boolean edits) {
        if (text == null) return Messages.tr("palette.disabled.noText");
        if (edits && !text.isEditable()) return Messages.tr("palette.disabled.readOnlyText");
        return null;
    }

    /** Why Open cannot run in the Documents window: no file is selected. */
    static @Nullable String documentsOpenUnavailable(boolean selected) {
        return selected ? null : Messages.tr("palette.disabled.noDocumentSelected");
    }

    /** Why Clear cannot run in the Output: there is nothing in it. */
    static @Nullable String outputClearUnavailable(boolean any) {
        return any ? null : Messages.tr("palette.disabled.outputEmpty");
    }

    /** Why Cancel cannot run: no palette, picker or Message Log is open. */
    static @Nullable String cancelUnavailable(boolean overlay) {
        return overlay ? null : Messages.tr("palette.disabled.nothingToCancel");
    }

    /** Why Find Next or Find Previous cannot run: their find bar is not open. */
    static @Nullable String findStepUnavailable(boolean open) {
        return open ? null : Messages.tr("palette.disabled.findBarClosed");
    }

    /** Why Find cannot run: the active tab is the Welcome page, which has no text to search. */
    static @Nullable String findUnavailable(boolean document) {
        return document ? null : Messages.tr("palette.disabled.noDocument");
    }

    /** Why Sync Now cannot run, or null when it can. */
    static @Nullable String syncNowUnavailable(boolean configured) {
        return configured ? null : Messages.tr("palette.disabled.syncNotSetUp");
    }

    /** Why Check for Updates cannot run, or null when it can. */
    static @Nullable String checkForUpdatesUnavailable(boolean checking) {
        return checking ? Messages.tr("palette.disabled.checking") : null;
    }

    /** Why a key-binding row's command cannot run: no row is selected in Settings → Keymap. */
    static @Nullable String bindingRowUnavailable(boolean selected) {
        return selected ? null : Messages.tr("palette.disabled.noBindingSelected");
    }

    /** Why Open the New Release cannot run: no newer release has been found. */
    static @Nullable String newReleaseUnavailable(boolean found) {
        return found ? null : Messages.tr("palette.disabled.noNewRelease");
    }

    /** Why Find Next or Previous cannot run: the Debug Log's find bar is not open. */
    static @Nullable String findBarUnavailable(boolean open) {
        return open ? null : Messages.tr("palette.disabled.findClosed");
    }

    /** Why a command on a tool window cannot run: none is open to act on. */
    static @Nullable String toolWindowUnavailable(boolean target) {
        return target ? null : Messages.tr("palette.disabled.noToolWindow");
    }

    /** Why Open in Split cannot run: no closed window's side has room beside an open one. */
    static @Nullable String splitUnavailable(boolean possible) {
        return possible ? null : Messages.tr("palette.disabled.noSplit");
    }

    /** Why Show Hidden Stripe Buttons cannot run: none is hidden. */
    static @Nullable String restoreStripeButtonsUnavailable(boolean anyHidden) {
        return anyHidden ? null : Messages.tr("palette.disabled.noHiddenButtons");
    }

    /** Why Run Sample Task cannot run: it is running. */
    static @Nullable String sampleTaskUnavailable(boolean running) {
        return running ? Messages.tr("palette.disabled.taskRunning") : null;
    }

    /** Why Debug Log: Refresh cannot run, or null when it can. */
    static @Nullable String debugLogRefreshUnavailable(boolean open) {
        return open ? null : Messages.tr("palette.disabled.debugLogClosed");
    }
}
