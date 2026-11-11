package com.example.app.command;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A preset keymap, shipped as a JSON resource ({@code keymaps/<id>.json}):
 * {@code {"name": "CUA", "notation": "cua", "bindings": {"palette.show": "Shortcut+Shift+P"}}}.
 * The bindings keep the file's order, so which of two clashing bindings wins never depends on hashing.
 * A preset may have a macOS variant, {@code <id>.mac.json}, where Cmd replaces Ctrl (KBD-09); on a Mac
 * it is loaded in place of the plain file.
 *
 * @param emacs whether labels use Emacs notation ({@code C-x}) rather than CUA ({@code Ctrl+X})
 */
public record Keymap(
        String id, String name, boolean emacs, Map<String, String> bindings, Map<String, List<String>> alsoBound) {

    /** The bundled presets, in picker order. CUA is the default. */
    /** The bundled keymaps (KBD-10): CUA and Emacs, and IntelliJ, VS Code and Sublime Text, each with a macOS form. */
    public static final List<String> PRESETS = List.of("cua", "emacs", "intellij", "vscode", "sublime");

    public static final String DEFAULT = "cua";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Loads a bundled preset for this OS; an unknown id loads the default. */
    public static Keymap load(String id) {
        return load(id, com.example.app.Os.isMac());
    }

    /** Each preset parsed once: a keymap or settings change on the FX thread parses no JSON (PERF-01). */
    private static final Map<String, Keymap> LOADED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Loads a bundled preset, its {@code .mac.json} variant when {@code mac} and there is one. */
    public static Keymap load(String id, boolean mac) {
        String chosen = PRESETS.contains(id) ? id : DEFAULT;
        return LOADED.computeIfAbsent(chosen + (mac ? ".mac" : ""), key -> read(chosen, mac));
    }

    private static Keymap read(String chosen, boolean mac) {
        String base = "/com/example/app/keymaps/" + chosen;
        String resource =
                mac && Keymap.class.getResource(base + ".mac.json") != null ? base + ".mac.json" : base + ".json";
        try (InputStream in = Keymap.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing keymap resource " + chosen);
            JsonNode tree = JSON.readTree(in);
            Map<String, String> bindings = new LinkedHashMap<>();
            Map<String, List<String>> also = new LinkedHashMap<>();
            // A command takes one chord, or a list: the first is the one menus show, the rest also run it
            // (find.show is Ctrl/Cmd+F and F3 in CUA: FIND-04).
            tree.path("bindings").properties().forEach(e -> {
                JsonNode value = e.getValue();
                if (value.isArray() && !value.isEmpty()) {
                    bindings.put(e.getKey(), value.get(0).asText());
                    List<String> rest = new java.util.ArrayList<>();
                    for (int i = 1; i < value.size(); i++) rest.add(value.get(i).asText());
                    also.put(e.getKey(), List.copyOf(rest));
                } else {
                    bindings.put(e.getKey(), value.asText());
                }
            });
            return new Keymap(
                    chosen,
                    tree.path("name").asText(chosen),
                    "emacs".equals(tree.path("notation").asText()),
                    java.util.Collections.unmodifiableMap(bindings),
                    java.util.Collections.unmodifiableMap(also));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read keymap " + chosen, e);
        }
    }
}
