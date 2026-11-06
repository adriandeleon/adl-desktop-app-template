package com.example.app.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzyMatchTest {

    @Test
    void matchesCharactersInOrder() {
        assertNotNull(FuzzyMatch.match("cmdp", "Command Palette"));
        assertNull(FuzzyMatch.match("xyz", "Command Palette"));
        assertEquals(0, FuzzyMatch.match("  ", "anything").score());
    }

    @Test
    void prefersWordStartsAndRuns() {
        var wordStarts = FuzzyMatch.match("cp", "Command Palette");
        assertArrayEquals(new int[] {0, 8}, wordStarts.positions());
        assertTrue(FuzzyMatch.match("set", "Settings").score()
                > FuzzyMatch.match("set", "Reset").score());
    }

    @Test
    void aQueryNamingTheTitleGetsTheBonus() {
        assertEquals(100, FuzzyMatch.phraseBonus("palette", "Command Palette"));
        assertEquals(100, FuzzyMatch.phraseBonus("comm", "Command"));
        assertEquals(0, FuzzyMatch.phraseBonus("ette", "Command Palette"));
        assertEquals(0, FuzzyMatch.phraseBonus(" ", "Command"));
    }
}
