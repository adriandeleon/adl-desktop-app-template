package com.example.app.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.app.config.ConfigFiles;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A file Settings Sync keeps: {@code keybindings.json} in the repository and {@code base.json} beside the
 * clone. Versioned like {@code settings.json} (SET-09, PLAT-14): a {@link #SCHEMA_VERSION} and one step
 * per version in {@link #STEPS}, so a later change of shape upgrades what an earlier build wrote. A file
 * from a newer build is refused, never overwritten: the computer that wrote it keeps its data.
 */
public final class SyncData {

    /** Bump with every change to the shape of these files, and add the matching step to {@link #STEPS}. */
    public static final int SCHEMA_VERSION = 2;

    /** Version 1 is the bare map of bindings the first builds wrote, with no version in it. */
    static final int UNVERSIONED = 1;

    /** A synced data file larger than this is refused before it is read (PERF-12): bindings are tiny. */
    public static final long MAX_BYTES = 1024 * 1024;

    /** Step {@code v} turns a version-{@code v} tree into version {@code v + 1}. */
    static final Map<Integer, UnaryOperator<ObjectNode>> STEPS = Map.of(1, SyncData::v1ToV2);

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setDefaultPrettyPrinter(ConfigFiles.indented());

    private static final Logger LOG = Logger.getLogger(SyncData.class.getName());

    public int schemaVersion = SCHEMA_VERSION;

    /** The user's own bindings, command id to chord; a blank chord unbinds. */
    public Map<String, String> keybindings = new TreeMap<>();

    /** A file written by a newer build than this one: it is left as it is. */
    public static final class NewerException extends IOException {
        private final int version;

        NewerException(Path file, int version) {
            super(file.getFileName() + " was written by a newer version (schema " + version + ")");
            this.version = version;
        }

        public int version() {
            return version;
        }
    }

    /** The bindings in {@code file}, upgraded to this version; empty when there is no file. */
    public static Map<String, String> read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return new TreeMap<>();
        if (Files.size(file) > MAX_BYTES) throw new IOException(file.getFileName() + " is larger than 1 MB");
        JsonNode tree = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
        if (!(tree instanceof ObjectNode object)) throw new IOException(file.getFileName() + " is not a JSON object");
        int version = versionOf(object);
        if (version > SCHEMA_VERSION) throw new NewerException(file, version);
        SyncData data = JSON.treeToValue(migrate(object), SyncData.class);
        Map<String, String> bindings = new TreeMap<>();
        if (data.keybindings != null) {
            // Never trust a file (SET-11): an entry with no command or no chord is dropped.
            data.keybindings.forEach((id, chord) -> {
                if (id != null && !id.isBlank() && chord != null) bindings.put(id, chord);
            });
        }
        return bindings;
    }

    /** Writes {@code bindings} at this version. */
    public static void write(Path file, Map<String, String> bindings) throws IOException {
        SyncData data = new SyncData();
        data.keybindings = new TreeMap<>(bindings);
        ConfigFiles.writeString(file, JSON.writeValueAsString(data) + "\n");
    }

    /**
     * The sync engine's own base (the bindings both sides last agreed on), which it keeps beside the clone.
     * Never trusted (SET-10, SET-11): a base a newer build wrote is kept as {@code base.json.v<n>.bak}, one
     * that cannot be read as {@code base.json.broken.bak}, and either way the sync goes on from an empty
     * base, as on a first sync, instead of failing every time.
     */
    public static Map<String, String> readBase(Path file) {
        try {
            return read(file);
        } catch (NewerException e) {
            copyAside(file, file.getFileName() + ".v" + e.version() + ".bak");
        } catch (IOException | RuntimeException e) {
            copyAside(file, file.getFileName() + ".broken.bak");
        }
        return new TreeMap<>();
    }

    private static void copyAside(Path file, String name) {
        try {
            Files.copy(file, file.resolveSibling(name), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not back up " + file, e);
        }
    }

    /** The version a tree says it is; a tree with none is the bare map of version 1. */
    static int versionOf(ObjectNode tree) {
        JsonNode version = tree.get("schemaVersion");
        return version != null && version.canConvertToInt() ? version.asInt() : UNVERSIONED;
    }

    /** Runs every step from the tree's version up to {@link #SCHEMA_VERSION}. */
    static ObjectNode migrate(ObjectNode tree) {
        ObjectNode current = tree;
        for (int v = versionOf(tree); v < SCHEMA_VERSION; v++) {
            UnaryOperator<ObjectNode> step = STEPS.get(v);
            if (step == null) throw new IllegalStateException("no migration from sync schema " + v);
            current = step.apply(current);
            current.put("schemaVersion", v + 1);
        }
        return current;
    }

    /** Version 1 was the bindings themselves; version 2 holds them under {@code keybindings}. */
    static ObjectNode v1ToV2(ObjectNode tree) {
        ObjectNode wrapped = JSON.createObjectNode();
        wrapped.set("keybindings", tree);
        return wrapped;
    }
}
