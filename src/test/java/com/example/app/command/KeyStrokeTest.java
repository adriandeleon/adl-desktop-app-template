package com.example.app.command;

import java.util.List;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyStrokeTest {

    private static KeyStroke parse(String text) {
        KeyStroke stroke = KeyStroke.parse(text);
        assertNotNull(stroke, text);
        return stroke;
    }

    @Test
    void parsesEmacsNotation() {
        assertEquals(new KeyStroke(false, true, false, false, false, KeyCode.X), parse("C-x"));
        assertEquals(new KeyStroke(false, false, true, false, false, KeyCode.X), parse("M-x"));
        assertEquals(new KeyStroke(false, true, false, true, true, KeyCode.P), parse("C-S-s-p"));
        assertEquals(KeyCode.MINUS, parse("C--").code());
        assertEquals(KeyCode.COMMA, parse("C-,").code());
    }

    @Test
    void parsesCuaNotation() {
        assertEquals(new KeyStroke(true, false, false, true, false, KeyCode.P), parse("Shortcut+Shift+P"));
        assertEquals(new KeyStroke(false, true, true, false, true, KeyCode.Q), parse("Ctrl+Alt+Cmd+Q"));
        assertEquals(KeyCode.PLUS, parse("Ctrl++").code());
        assertEquals(KeyCode.F1, parse("F1").code());
        assertEquals(KeyCode.ESCAPE, parse("Esc").code());
        assertEquals(KeyCode.ENTER, parse("RET").code());
        assertEquals(KeyCode.SPACE, parse("C-SPC").code());
        assertEquals(KeyCode.PLUS, parse("+").code());
    }

    @Test
    void rejectsWhatIsNotAKey() {
        assertNull(KeyStroke.parse("Hyper+X"));
        assertNull(KeyStroke.parse("C-nosuchkey"));
        assertNull(KeyStroke.parse(""));
        assertNull(KeyStroke.parseSequence("   "));
        assertNull(KeyStroke.parseSequence("C-x bogus-key"));
        assertEquals(2, KeyStroke.parseSequence("C-x  C-c").size());
    }

    @Test
    void shortcutIsCmdOnMacAndCtrlElsewhere() {
        KeyStroke s = parse("Shortcut+P");
        assertTrue(s.resolve(true).meta());
        assertTrue(s.resolve(false).ctrl());
        assertEquals(parse("C-x"), parse("C-x").resolve(true));
    }

    @Test
    void labelsInEitherNotation() {
        List<KeyStroke> seq = KeyStroke.parseSequence("C-x C-c");
        assertEquals("C-x C-c", KeyStroke.label(seq, true, false));
        assertEquals("Ctrl+X Ctrl+C", KeyStroke.label(seq, false, false));
        assertEquals("⇧⌘P", parse("Shortcut+Shift+P").label(false, true));
        assertEquals("⌃⌥A", parse("Ctrl+Alt+A").label(false, true));
        assertEquals("Shift+Meta+F1", parse("Shift+Meta+F1").label(false, false));
        assertEquals("M-s-S-1", parse("M-s-S-1").label(true, false));
        assertEquals("C-,", parse("C-,").label(true, false));
        assertEquals("Esc", parse("Esc").label(true, false));
    }

    @Test
    void readsKeyEvents() {
        KeyEvent press = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.P, true, true, false, false);
        assertEquals(new KeyStroke(false, true, false, true, false, KeyCode.P), KeyStroke.of(press));
        assertNull(KeyStroke.of(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SHIFT, true, false, false, false)));
        assertNull(KeyStroke.of(
                new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.UNDEFINED, false, false, false, false)));
    }
}
