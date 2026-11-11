package com.example.app.ui;

import java.util.List;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import com.example.app.command.KeyStroke;
import com.example.app.command.Keybindings;
import org.jspecify.annotations.Nullable;

/**
 * The keys of every list overlay, the palette, the pickers and the tab switcher alike (PAL-06, QOPEN-06,
 * QOPEN-12), after Editora's {@code PickerKeys}: the active keymap's line and page chords (the {@code nav.*}
 * commands, {@code C-n}, {@code C-p}, {@code C-v} and {@code M-v} in Emacs) move the selection, as do the keys
 * that always work: ↑ ↓, Page Up and Down, and Ctrl+Home and Ctrl+End (Cmd on macOS). Plain Home and End stay
 * with the field's caret. Pure but for reading the event.
 */
final class PickerKeys {

    /** What a press does in a list overlay. */
    enum Move {
        LINE_DOWN,
        LINE_UP,
        PAGE_DOWN,
        PAGE_UP,
        FIRST,
        LAST,
        NONE
    }

    /** The commands whose chords move a list overlay's selection, in the keymaps that bind them. */
    static final String LINE_DOWN = "nav.lineDown";

    static final String LINE_UP = "nav.lineUp";
    static final String PAGE_DOWN = "nav.pageDown";
    static final String PAGE_UP = "nav.pageUp";

    /** The help chords a palette tries, in order: the first the keymap leaves free is its own (PAL-08). */
    static final List<String> HELP_CHORDS = List.of("Ctrl+H", "F1", "Shift+F1");

    private PickerKeys() {}

    /** What {@code event} does in a list overlay under {@code bindings}, or NONE. */
    static Move resolve(KeyEvent event, @Nullable Keybindings bindings, boolean mac) {
        KeyCode code = event.getCode();
        boolean shortcut = mac ? event.isMetaDown() : event.isControlDown();
        if (code == KeyCode.DOWN) return Move.LINE_DOWN;
        if (code == KeyCode.UP) return Move.LINE_UP;
        if (code == KeyCode.PAGE_DOWN) return Move.PAGE_DOWN;
        if (code == KeyCode.PAGE_UP) return Move.PAGE_UP;
        if (shortcut && code == KeyCode.HOME) return Move.FIRST;
        if (shortcut && code == KeyCode.END) return Move.LAST;
        if (bindings == null) return Move.NONE;
        KeyStroke stroke = KeyStroke.of(event);
        if (stroke == null) return Move.NONE;
        String id = bindings.commandFor(List.of(stroke));
        if (id == null) return Move.NONE;
        return switch (id) {
            case LINE_DOWN -> Move.LINE_DOWN;
            case LINE_UP -> Move.LINE_UP;
            case PAGE_DOWN -> Move.PAGE_DOWN;
            case PAGE_UP -> Move.PAGE_UP;
            default -> Move.NONE;
        };
    }

    /**
     * The palette's help chord (PAL-08): the first of {@link #HELP_CHORDS} that the keymap neither binds nor
     * starts a longer chord with, or null when it takes all three.
     */
    static @Nullable KeyStroke helpChord(Keybindings bindings, boolean mac) {
        for (String text : HELP_CHORDS) {
            KeyStroke stroke =
                    java.util.Objects.requireNonNull(KeyStroke.parse(text)).resolve(mac);
            List<KeyStroke> sequence = List.of(stroke);
            if (bindings.commandFor(sequence) == null && !bindings.isPrefix(sequence)) return stroke;
        }
        return null;
    }

    /** Whether {@code event} is the help chord. */
    static boolean isHelp(KeyEvent event, @Nullable KeyStroke help) {
        return help != null && help.equals(KeyStroke.of(event));
    }
}
