package com.example.app.ui;

import com.example.app.Os;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ABOUT-01, WIN-15: macOS's application menu gets About and Settings, and nothing else is touched anywhere
 * else. The menu itself is Glass's, which only macOS's JavaFX has; off it, and where its package is not
 * exported, the helper says why it did nothing instead of failing.
 */
class MacAppMenuTest {

    @AfterEach
    void reset() {
        Os.override(null);
    }

    @Test
    void offMacOsTheMenuIsLeftAlone() {
        Os.override(Os.Kind.LINUX);
        assertEquals("none (not macOS)", MacAppMenu.install("About", () -> {}, () -> "Settings…", () -> {}));
        Os.override(Os.Kind.WINDOWS);
        assertEquals("none (not macOS)", MacAppMenu.install("About", () -> {}, () -> "Settings…", () -> {}));
    }

    @Test
    void withoutGlasssMacPackageItSaysSoAndNothingBreaks() {
        Os.override(Os.Kind.MAC);
        String done = MacAppMenu.install("About", () -> {}, () -> "Settings…", () -> {});
        assertTrue(done.startsWith("none ("), done);
        MacAppMenu.refresh(); // nothing installed: nothing to retitle
    }
}
