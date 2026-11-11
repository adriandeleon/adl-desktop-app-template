package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The arithmetic of a tab strip with a pinned group at its front, as pure decisions (QUAL-05): which tabs
 * a bulk close takes, where a pinned or unpinned tab lands, where a dragged one may go, which tab is next.
 * {@link DocumentTabs} only asks. Named after Editora's {@code MainController} and {@code EditorArea}.
 */
final class TabStrip {

    private TabStrip() {}

    /** Close Other Tabs: every tab but {@code keep} and the pinned ones (TABS-04). */
    static <T> List<T> closeOtherTabs(List<T> tabs, T keep, Predicate<T> pinned) {
        return tabs.stream().filter(t -> t != keep && !pinned.test(t)).toList();
    }

    /** Close All Tabs: every tab that is not pinned (TABS-04). */
    static <T> List<T> closeAllTabs(List<T> tabs, Predicate<T> pinned) {
        return tabs.stream().filter(t -> !pinned.test(t)).toList();
    }

    /** Close Unmodified Tabs: every tab with nothing unsaved that is not pinned (TABS-04). */
    static <T> List<T> closeUnmodifiedTabs(List<T> tabs, Predicate<T> pinned, Predicate<T> dirty) {
        return tabs.stream().filter(t -> !pinned.test(t) && !dirty.test(t)).toList();
    }

    /** Close Tabs to the Left: the unpinned tabs before {@code tab} (TABS-04). */
    static <T> List<T> eligibleToLeft(List<T> tabs, T tab, Predicate<T> pinned) {
        int at = tabs.indexOf(tab);
        if (at < 0) return List.of();
        return tabs.subList(0, at).stream().filter(t -> !pinned.test(t)).toList();
    }

    /** Close Tabs to the Right: the unpinned tabs after {@code tab} (TABS-04). */
    static <T> List<T> eligibleToRight(List<T> tabs, T tab, Predicate<T> pinned) {
        int at = tabs.indexOf(tab);
        if (at < 0) return List.of();
        return tabs.subList(at + 1, tabs.size()).stream()
                .filter(t -> !pinned.test(t))
                .toList();
    }

    /**
     * Where {@code tab} goes once its pin changed (TABS-05): a tab just pinned to the end of the pinned group,
     * one just unpinned to just after it. Both are the count of the <em>other</em> pinned tabs, the index in
     * the strip with {@code tab} taken out.
     */
    static <T> int moveToPinBoundary(List<T> tabs, T tab, Predicate<T> pinned) {
        int others = 0;
        for (T t : tabs) {
            if (t != tab && pinned.test(t)) others++;
        }
        return others;
    }

    /**
     * Where a dragged tab lands when dropped beside {@code target} (TABS-07), as an index in the strip with
     * the dragged tab taken out, held to its own group: a pinned tab stays among the pinned, an unpinned one
     * after them. Returns -1 when there is nothing to move.
     */
    static <T> int dropIndex(List<T> tabs, T dragged, T target, boolean after, Predicate<T> pinned) {
        if (dragged == target || !tabs.contains(dragged) || !tabs.contains(target)) return -1;
        List<T> rest = new ArrayList<>(tabs);
        rest.remove(dragged);
        int at = rest.indexOf(target) + (after ? 1 : 0);
        int boundary = moveToPinBoundary(tabs, dragged, pinned);
        return pinned.test(dragged) ? Math.min(at, boundary) : Math.max(at, boundary);
    }

    /** The index {@code delta} steps from {@code index}, wrapping round the strip; -1 for an empty strip. */
    static int cycle(int index, int size, int delta) {
        if (size <= 0) return -1;
        return Math.floorMod(index + delta, size);
    }
}
