package com.example.app.command;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

/**
 * The effective bindings: the base keymap, then the user's own bindings on top, so the user always has
 * the last word: when a user binding takes keys the base keymap gives another command, that base
 * binding is dropped and listed in {@link #conflicts}. A blank user binding unbinds the command. Pure: rebuilt whenever either layer changes.
 */
public final class Keybindings {

    private final Keymap keymap;
    private final boolean mac;
    private final Map<String, List<KeyStroke>> byCommand = new LinkedHashMap<>();
    private final Map<List<KeyStroke>, String> bySequence = new HashMap<>();
    private final Set<List<KeyStroke>> prefixes = new HashSet<>();
    private final Map<String, String> conflicts = new LinkedHashMap<>();

    public Keybindings(Keymap keymap, Map<String, String> user, boolean mac) {
        this.keymap = keymap;
        this.mac = mac;
        // The user's bindings first, in id order, so on a clash the user's binding wins and the base
        // keymap's is the one dropped; then the base keymap in its file's order.
        new TreeMap<>(user).forEach((id, text) -> bind(id, text));
        keymap.bindings().forEach((id, text) -> {
            if (!user.containsKey(id)) bind(id, text);
        });
    }

    private void bind(String id, String text) {
        List<KeyStroke> parsed = KeyStroke.parseSequence(text);
        if (parsed == null) return;
        List<KeyStroke> sequence = parsed.stream().map(s -> s.resolve(mac)).toList();
        String previous = bySequence.putIfAbsent(sequence, id);
        if (previous != null) {
            conflicts.put(id, previous);
            return;
        }
        byCommand.put(id, sequence);
        for (int i = 1; i < sequence.size(); i++) prefixes.add(sequence.subList(0, i));
    }

    public Keymap keymap() {
        return keymap;
    }

    /** The command bound to exactly this sequence, or null. */
    public @Nullable String commandFor(List<KeyStroke> sequence) {
        return bySequence.get(sequence);
    }

    /** Whether this sequence starts a longer binding ({@code C-x} before {@code C-x C-c}). */
    public boolean isPrefix(List<KeyStroke> sequence) {
        return prefixes.contains(sequence);
    }

    /** The commands whose binding continues this prefix, with the remaining keys' label: a which-key hint. */
    public Map<String, String> continuations(List<KeyStroke> prefix) {
        Map<String, String> out = new LinkedHashMap<>();
        byCommand.forEach((id, sequence) -> {
            if (sequence.size() > prefix.size()
                    && sequence.subList(0, prefix.size()).equals(prefix)) {
                out.put(id, KeyStroke.label(sequence.subList(prefix.size(), sequence.size()), keymap.emacs(), mac));
            }
        });
        return out;
    }

    /** The label of a command's binding in the active notation, or empty when unbound. */
    public String label(String commandId) {
        List<KeyStroke> sequence = byCommand.get(commandId);
        return sequence == null ? "" : KeyStroke.label(sequence, keymap.emacs(), mac);
    }

    /** Commands whose binding was dropped because another command already had the same keys. */
    public Map<String, String> conflicts() {
        return Map.copyOf(conflicts);
    }
}
