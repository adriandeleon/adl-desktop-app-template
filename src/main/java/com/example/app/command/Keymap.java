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
 *
 * @param emacs whether labels use Emacs notation ({@code C-x}) rather than CUA ({@code Ctrl+X})
 */
public record Keymap(String id, String name, boolean emacs, Map<String, String> bindings) {

    /** The bundled presets, in picker order. CUA is the default. */
    public static final List<String> PRESETS = List.of("cua", "emacs");

    public static final String DEFAULT = "cua";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Loads a bundled preset; an unknown id loads the default. */
    public static Keymap load(String id) {
        String chosen = PRESETS.contains(id) ? id : DEFAULT;
        try (InputStream in = Keymap.class.getResourceAsStream("/com/example/app/keymaps/" + chosen + ".json")) {
            if (in == null) throw new IOException("missing keymap resource " + chosen);
            JsonNode tree = JSON.readTree(in);
            Map<String, String> bindings = new LinkedHashMap<>();
            tree.path("bindings")
                    .properties()
                    .forEach(e -> bindings.put(e.getKey(), e.getValue().asText()));
            return new Keymap(
                    chosen,
                    tree.path("name").asText(chosen),
                    "emacs".equals(tree.path("notation").asText()),
                    java.util.Collections.unmodifiableMap(bindings));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read keymap " + chosen, e);
        }
    }
}
