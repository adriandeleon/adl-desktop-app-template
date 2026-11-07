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

    /** Why Debug Log: Refresh cannot run, or null when it can. */
    static @Nullable String debugLogRefreshUnavailable(boolean open) {
        return open ? null : Messages.tr("palette.disabled.debugLogClosed");
    }
}
