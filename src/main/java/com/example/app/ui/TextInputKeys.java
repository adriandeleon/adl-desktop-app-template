package com.example.app.ui;

import java.util.function.BooleanSupplier;

import javafx.scene.control.IndexRange;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import com.example.app.command.KeyStroke;

/**
 * The keymap's caret and editing chords in a text field (FIND-09): in the Emacs keymap {@code C-a}, {@code C-e},
 * {@code C-f}, {@code C-b}, {@code C-d}, {@code C-k}, {@code C-y} and {@code M-f}, {@code M-b} act on the field;
 * in CUA the field's own {@code Ctrl/Cmd+A/C/V/X/Z} do. {@link KeyDispatcher} leaves these chords to a text
 * field, so they never run a command of the document; find's own chords stay global.
 */
final class TextInputKeys {

    private TextInputKeys() {}

    /** Whether {@code stroke} is a caret or editing chord a text field keeps, in this notation. Pure. */
    static boolean isEditing(KeyStroke stroke, boolean emacs) {
        KeyCode code = stroke.code();
        if (emacs) {
            if (stroke.ctrl() && !stroke.alt() && !stroke.meta() && !stroke.shift()) {
                return switch (code) {
                    case A, E, F, B, D, K, Y -> true; // C-h stays Emacs's help prefix
                    default -> false;
                };
            }
            boolean meta = (stroke.alt() || stroke.meta()) && !stroke.ctrl();
            return meta && (code == KeyCode.F || code == KeyCode.B || code == KeyCode.D);
        }
        boolean shortcut = (stroke.ctrl() || stroke.meta() || stroke.shortcut()) && !stroke.alt();
        return shortcut
                && switch (code) {
                    case A, C, V, X, Z, Y -> true;
                    default -> false;
                };
    }

    /** Gives {@code field} the Emacs keymap's caret and editing chords while {@code emacs} says it is active. */
    static void install(TextInputControl field, BooleanSupplier emacs) {
        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!emacs.getAsBoolean() || !act(field, e)) return;
            e.consume();
        });
    }

    /** Runs one Emacs chord on the field; false when the press is none of them. */
    private static boolean act(TextInputControl field, KeyEvent e) {
        boolean ctrl = e.isControlDown() && !e.isAltDown() && !e.isMetaDown() && !e.isShiftDown();
        boolean meta = (e.isAltDown() || e.isMetaDown()) && !e.isControlDown();
        if (ctrl) {
            switch (e.getCode()) {
                case A -> field.home();
                case E -> field.end();
                case F -> field.forward();
                case B -> field.backward();
                case D -> field.deleteNextChar();
                case K -> kill(field);
                case Y -> field.paste();
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (meta) {
            switch (e.getCode()) {
                case F -> field.endOfNextWord();
                case B -> field.previousWord();
                case D -> {
                    int from = field.getCaretPosition();
                    field.endOfNextWord();
                    field.deleteText(from, field.getCaretPosition());
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** {@code C-k}: the rest of the line to the clipboard, then deleted, as Emacs kills it. */
    private static void kill(TextInputControl field) {
        int from = field.getCaretPosition();
        int to = field.getLength();
        if (from >= to) return;
        field.selectRange(from, to);
        field.cut();
        field.selectRange(from, from);
        IndexRange none = field.getSelection();
        if (none.getLength() > 0) field.deselect();
    }
}
