package com.example.app.ui;

import com.example.app.command.KeyStroke;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FIND-09: which chords a text field keeps for itself, in each notation. */
class TextInputKeysTest {

    private static KeyStroke stroke(String text) {
        return java.util.Objects.requireNonNull(KeyStroke.parse(text)).resolve(false);
    }

    @Test
    void emacsKeepsItsCaretAndEditingChordsInAField() {
        for (String chord : new String[] {"C-a", "C-e", "C-f", "C-b", "C-d", "C-k", "C-y", "M-f", "M-b", "M-d"}) {
            assertTrue(TextInputKeys.isEditing(stroke(chord), true), chord);
        }
        // Find's own chords stay global: next, previous, the palette, cancel.
        for (String chord : new String[] {"C-s", "C-r", "C-g", "M-x", "C-x"}) {
            assertFalse(TextInputKeys.isEditing(stroke(chord), true), chord);
        }
    }

    @Test
    void cuaKeepsTheFieldsOwnClipboardAndUndoChords() {
        for (String chord : new String[] {"Ctrl+A", "Ctrl+C", "Ctrl+V", "Ctrl+X", "Ctrl+Z", "Ctrl+Y"}) {
            assertTrue(TextInputKeys.isEditing(stroke(chord), false), chord);
        }
        for (String chord : new String[] {"Ctrl+F", "F3", "Shift+F3", "Ctrl+Shift+P", "Esc"}) {
            assertFalse(TextInputKeys.isEditing(stroke(chord), false), chord);
        }
    }
}
