package com.example.app.ui;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The toolbar's pure rules, without a toolkit: the gaps a hidden group leaves, and a button's name. */
class AppToolbarTest {

    private static boolean[] shown(@Nullable Boolean... items) {
        List<@Nullable Boolean> list = Arrays.asList(items);
        return AppToolbar.separatorsShown(list);
    }

    /** TBAR-05: with everything showing, every gap stays. */
    @Test
    void everyGapStaysWhenNothingIsHidden() {
        assertArrayEquals(new boolean[] {true, true, true, true, true}, shown(true, null, true, null, true));
    }

    /** TBAR-05: a hidden middle group leaves one gap, not two side by side. */
    @Test
    void aHiddenMiddleGroupLeavesOneGap() {
        assertArrayEquals(
                new boolean[] {true, true, false, false, false, true}, shown(true, null, false, false, null, true));
    }

    /** TBAR-05: a hidden first or last group leaves no gap leading or trailing the bar. */
    @Test
    void noGapLeadsOrTrailsTheBar() {
        assertArrayEquals(new boolean[] {false, false, true}, shown(false, null, true));
        assertArrayEquals(new boolean[] {true, false, false}, shown(true, null, false));
        assertArrayEquals(new boolean[] {false, false, false}, shown(null, null, false));
    }

    /** TBAR-03: the title and the chord; the title alone when unbound; a menu row's ellipsis dropped. */
    @Test
    void aButtonsNameIsItsTitleAndItsChord() {
        assertEquals("Command Palette (Ctrl+Shift+P)", Icons.withChord("Command Palette", "Ctrl+Shift+P"));
        assertEquals("Message Log", Icons.withChord("Message Log", ""));
        assertEquals("Settings (Ctrl+,)", Icons.withChord("Settings…", "Ctrl+,"));
        assertEquals("Theme", Icons.withChord("Theme...", ""));
    }

    /** TBAR-08: every command on the bar has a glyph to draw, and the cluster opens and closes on one. */
    @Test
    void everyCommandOnTheBarHasAGlyph() {
        for (String id : AppToolbar.CLUSTER) {
            if (id != null) assertEquals(true, Icons.forCommand(id) != null, id + " has no glyph");
        }
        for (String id : AppToolbar.TAIL) assertEquals(true, Icons.forCommand(id) != null, id + " has no glyph");
        assertEquals(true, AppToolbar.CLUSTER.getFirst() != null);
        assertEquals(true, AppToolbar.CLUSTER.getLast() != null);
    }
}
