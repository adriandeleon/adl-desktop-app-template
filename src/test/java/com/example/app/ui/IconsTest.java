package com.example.app.ui;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.shape.SVGPath;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The two glyph families and one glyph per action, without a toolkit. */
class IconsTest {

    /** The path a glyph node draws. */
    static SVGPath path(Node glyph) {
        if (glyph instanceof SVGPath svg) return svg;
        for (Node child : ((Parent) glyph).getChildrenUnmodifiable()) return path(child);
        throw new AssertionError("no path in " + glyph);
    }

    /** ICON-19: every line glyph is tagged as a line glyph and never as a filled one. */
    @Test
    void aLineGlyphIsNeverAlsoTaggedAsAFilledOne() {
        for (Map.Entry<String, String> glyph : Icons.commandGlyphs().entrySet()) {
            SVGPath svg = path(Icons.line(glyph.getValue()));
            assertTrue(svg.getStyleClass().contains(Icons.LINE), glyph.getKey());
            assertFalse(svg.getStyleClass().contains(Icons.FILLED), glyph.getKey() + " is tagged both ways");
            assertEquals(glyph.getValue(), svg.getContent());
        }
        SVGPath filled = path(Icons.of(Icons.FOLDER));
        assertTrue(filled.getStyleClass().contains(Icons.FILLED));
        assertFalse(filled.getStyleClass().contains(Icons.LINE), "a filled glyph is tagged as a line glyph");
    }

    /** ICON-20: a command's glyph is the same path and family each time it is drawn. */
    @Test
    void aCommandsGlyphIsTheSameEverywhere() {
        for (String command : Icons.commandGlyphs().keySet()) {
            SVGPath first = path(Objects.requireNonNull(Icons.commandGlyph(command)));
            SVGPath again = path(Icons.line(Objects.requireNonNull(Icons.forCommand(command))));
            assertEquals(first.getContent(), again.getContent(), command);
            assertEquals(first.getStyleClass(), again.getStyleClass(), command);
        }
        assertEquals(Icons.forCommand("sync.setup"), Icons.forCommand("sync.now"), "one action, one glyph");
        for (SettingsWindow.Page page : SettingsWindow.Page.values()) {
            assertNotNull(Icons.forCommand(page.command), page + " has no glyph");
        }
        assertEquals(Icons.SLIDERS, Icons.forCommand(SettingsWindow.Page.GENERAL.command));
        assertEquals(Icons.CONTRAST, Icons.forCommand(SettingsWindow.Page.APPEARANCE.command));
        assertEquals(Icons.KEYBOARD, Icons.forCommand(SettingsWindow.Page.KEYMAP.command));
        assertEquals(Icons.SYNC, Icons.forCommand(SettingsWindow.Page.SYNC.command));
        assertEquals(Icons.FOLDER, Icons.forCommand(SettingsWindow.Page.ADVANCED.command));
    }

    /** A command no menu lists, or no such command, draws none. */
    @Test
    void aCommandWithNoGlyphDrawsNone() {
        assertNull(Icons.forCommand("debugLog.findNext"));
        assertNull(Icons.commandGlyph("no.such.command"));
    }

    /** Every glyph belongs to a real command: its name is in the catalog. */
    @Test
    void everyGlyphNamesACommandInTheCatalog() throws Exception {
        Properties catalog = new Properties();
        try (InputStream in = Objects.requireNonNull(
                IconsTest.class.getResourceAsStream("/com/example/app/i18n/messages.properties"))) {
            catalog.load(in);
        }
        for (String command : Icons.commandGlyphs().keySet()) {
            assertTrue(catalog.containsKey("command." + command), command);
        }
    }

    /** ICON-23: a file's glyph comes from its name; a type the app does not know gets the generic one. */
    @Test
    void aFilesGlyphComesFromItsName() {
        org.junit.jupiter.api.Assertions.assertEquals(Icons.FILE_TEXT, Icons.forFileName("README.md"));
        org.junit.jupiter.api.Assertions.assertEquals(Icons.FILE_TEXT, Icons.forFileName("notes.TXT"));
        org.junit.jupiter.api.Assertions.assertEquals(Icons.FILE, Icons.forFileName("photo.webp"));
        org.junit.jupiter.api.Assertions.assertEquals(Icons.FILE, Icons.forFileName("Makefile"));
        org.junit.jupiter.api.Assertions.assertEquals(Icons.FILE, Icons.forFileName(""));
    }
}
