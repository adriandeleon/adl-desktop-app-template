package com.example.app.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.jspecify.annotations.Nullable;

/**
 * One key press with its modifiers. Parses both notations the keymaps use: Emacs ({@code C-x},
 * {@code M-x}, {@code C-S-p}) and CUA ({@code Shortcut+Shift+P}, {@code Ctrl+Q}, {@code F1}).
 *
 * <p>{@code Shortcut} is Cmd on macOS and Ctrl elsewhere, so one CUA keymap serves every OS; it is
 * resolved by {@link #resolve}, the one place the platform matters.
 */
public record KeyStroke(boolean shortcut, boolean ctrl, boolean alt, boolean shift, boolean meta, KeyCode code) {

    /** Parses a space-separated sequence such as {@code C-x C-c}; null when any part is not a key. */
    public static @Nullable List<KeyStroke> parseSequence(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return null;
        List<KeyStroke> strokes = new ArrayList<>();
        for (String part : trimmed.split("\\s+")) {
            KeyStroke stroke = parse(part);
            if (stroke == null) return null;
            strokes.add(stroke);
        }
        return List.copyOf(strokes);
    }

    /** Parses one stroke in either notation; null when the key is unknown. */
    public static @Nullable KeyStroke parse(String token) {
        boolean shortcut = false, ctrl = false, alt = false, shift = false, meta = false;
        String rest = token;
        if (rest.length() > 2 && rest.matches("^([CMSs]-)+.+")) {
            while (rest.length() > 2 && rest.charAt(1) == '-' && "CMSs".indexOf(rest.charAt(0)) >= 0) {
                switch (rest.charAt(0)) {
                    case 'C' -> ctrl = true;
                    case 'M' -> alt = true;
                    case 'S' -> shift = true;
                    default -> meta = true;
                }
                rest = rest.substring(2);
            }
        } else if (rest.length() > 1 && rest.contains("+")) {
            String key = rest.endsWith("++") ? "+" : rest.substring(rest.lastIndexOf('+') + 1);
            String mods = rest.substring(0, rest.length() - key.length() - 1);
            for (String mod : mods.split("\\+")) {
                switch (mod.toLowerCase(Locale.ROOT)) {
                    case "shortcut" -> shortcut = true;
                    case "ctrl", "control" -> ctrl = true;
                    case "alt", "option" -> alt = true;
                    case "shift" -> shift = true;
                    case "meta", "cmd", "command", "super" -> meta = true;
                    default -> {
                        return null;
                    }
                }
            }
            rest = key;
        }
        KeyCode code = keyCode(rest);
        return code == null ? null : new KeyStroke(shortcut, ctrl, alt, shift, meta, code);
    }

    /** A key by its character ({@code ,}), its JavaFX name ({@code Comma}) or its enum name ({@code COMMA}). */
    static @Nullable KeyCode keyCode(String key) {
        if (key.isEmpty()) return null;
        switch (key.toLowerCase(Locale.ROOT)) {
            case "esc" -> {
                return KeyCode.ESCAPE;
            }
            case "ret", "return" -> {
                return KeyCode.ENTER;
            }
            case "spc" -> {
                return KeyCode.SPACE;
            }
            case "+" -> {
                return KeyCode.PLUS;
            }
            default -> {}
        }
        for (KeyCode code : KeyCode.values()) {
            if (code.getChar().equalsIgnoreCase(key)
                    || code.getName().equalsIgnoreCase(key)
                    || code.name().equalsIgnoreCase(key)) {
                return code;
            }
        }
        return null;
    }

    /** This stroke with {@code Shortcut} turned into Cmd (macOS) or Ctrl. */
    public KeyStroke resolve(boolean mac) {
        if (!shortcut) return this;
        return new KeyStroke(false, ctrl || !mac, alt, shift, meta || mac, code);
    }

    /** The stroke a key event makes, or null for a bare modifier press. */
    public static @Nullable KeyStroke of(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code.isModifierKey() || code == KeyCode.UNDEFINED) return null;
        return new KeyStroke(
                false, event.isControlDown(), event.isAltDown(), event.isShiftDown(), event.isMetaDown(), code);
    }

    /** A label for menus and the palette, in the notation the keymap uses. */
    public String label(boolean emacs, boolean mac) {
        StringBuilder out = new StringBuilder();
        KeyStroke s = resolve(mac);
        if (emacs) {
            if (s.ctrl) out.append("C-");
            if (s.alt) out.append("M-");
            if (s.meta) out.append("s-");
            if (s.shift) out.append("S-");
            return out.append(keyLabel(true)).toString();
        }
        if (s.ctrl) out.append(mac ? "⌃" : "Ctrl+");
        if (s.alt) out.append(mac ? "⌥" : "Alt+");
        if (s.shift) out.append(mac ? "⇧" : "Shift+");
        if (s.meta) out.append(mac ? "⌘" : "Meta+");
        return out.append(keyLabel(false)).toString();
    }

    /** Punctuation keys, labelled by their character; every other non-letter key by its name. */
    private static final java.util.Set<KeyCode> PUNCTUATION = java.util.EnumSet.of(
            KeyCode.COMMA,
            KeyCode.PERIOD,
            KeyCode.MINUS,
            KeyCode.EQUALS,
            KeyCode.PLUS,
            KeyCode.SLASH,
            KeyCode.BACK_SLASH,
            KeyCode.SEMICOLON,
            KeyCode.QUOTE,
            KeyCode.BACK_QUOTE,
            KeyCode.OPEN_BRACKET,
            KeyCode.CLOSE_BRACKET);

    private String keyLabel(boolean emacs) {
        String ch = code.getChar();
        if (code.isLetterKey()) return emacs ? ch.toLowerCase(Locale.ROOT) : ch.toUpperCase(Locale.ROOT);
        if (code.isDigitKey() || PUNCTUATION.contains(code)) return ch;
        return code.getName();
    }

    /** A whole sequence's label: strokes joined by spaces. */
    public static String label(List<KeyStroke> sequence, boolean emacs, boolean mac) {
        StringBuilder out = new StringBuilder();
        for (KeyStroke stroke : sequence) {
            if (!out.isEmpty()) out.append(' ');
            out.append(stroke.label(emacs, mac));
        }
        return out.toString();
    }
}
