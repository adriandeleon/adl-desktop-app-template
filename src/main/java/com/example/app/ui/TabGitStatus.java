package com.example.app.ui;

import javafx.collections.ObservableList;
import javafx.scene.control.Tab;

import org.jspecify.annotations.Nullable;

/**
 * Tints a tab's title by its file's Git status (TABS-08) with the style class the Documents window's rows
 * wear, so a file reads the same in both places: modified accent, added green, untracked olive, renamed
 * violet, deleted muted, conflict red. The class goes on the {@link Tab}, not its header, so it survives
 * the header being rebuilt; a read-only tab keeps it, and an unsaved one shows amber over it ({@code app.css}).
 */
final class TabGitStatus {

    private TabGitStatus() {}

    /** The style class for a file's status, or null for a clean file or a tab with none. Pure. */
    static @Nullable String classFor(SampleFiles.@Nullable Sample sample) {
        if (sample == null) return null;
        SampleFiles.GitStatus status = sample.status();
        return status == null ? null : status.cssClass();
    }

    /** Puts {@code wanted} on {@code tab} in place of any status class it wore. */
    static void apply(Tab tab, @Nullable String wanted) {
        ObservableList<String> classes = tab.getStyleClass();
        classes.removeIf(c -> c.startsWith("git-status-") && !c.equals(wanted));
        if (wanted != null && !classes.contains(wanted)) classes.add(wanted);
    }
}
