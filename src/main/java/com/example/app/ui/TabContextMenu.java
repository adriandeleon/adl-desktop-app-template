package com.example.app.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The right-click menu of a tab (TABS-01, MENU-05): the tab commands that act on a tab, each a registered
 * command, so the palette, the Window menu and a chord reach the same action. A right-click selects the tab
 * first, so each command acts on the tab that was clicked ({@link DocumentTabs#showMenu}).
 */
final class TabContextMenu {

    private TabContextMenu() {}

    /** The menu's commands; {@code null} is a separator. */
    static final List<@Nullable String> COMMANDS = Collections.unmodifiableList(Arrays.<@Nullable String>asList(
            "buffer.close",
            "buffer.closeOthers",
            "buffer.closeLeft",
            "buffer.closeRight",
            "buffer.closeUnmodified",
            "buffer.closeAll",
            null,
            "buffer.togglePin"));
}
