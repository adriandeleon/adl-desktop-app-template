package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The order arithmetic behind dragging a stripe button, within a stripe or across to another (TABS-17), as
 * in Editora. The display order is one flat list of ids across the three stripes; a window's rank is only
 * compared with its own side's, so moving an id in that one list is all a re-dock needs. Both return a new
 * list, so a caller computes first and commits once. Pure.
 */
final class ToolWindowDock {

    private ToolWindowDock() {}

    /**
     * Places {@code srcId} just before (or after) {@code targetId}. Removing the source first is what makes
     * the index meaningful: taken from the original list it would be off by one whenever the source sat
     * ahead of the target.
     */
    static List<String> dropOnto(List<String> order, String srcId, String targetId, boolean after) {
        List<String> next = new ArrayList<>(order);
        if (srcId.equals(targetId)) return next;
        next.remove(srcId);
        int t = next.indexOf(targetId);
        if (t < 0) next.add(srcId);
        else next.add(after ? t + 1 : t, srcId);
        return next;
    }

    /** Sends {@code srcId} to the end: last on whichever side it now sits (a drop on empty stripe space). */
    static List<String> dropAtEnd(List<String> order, String srcId) {
        List<String> next = new ArrayList<>(order);
        next.remove(srcId);
        next.add(srcId);
        return next;
    }
}
