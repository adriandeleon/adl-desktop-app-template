package com.example.app.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/**
 * Import Configuration: reads a {@code .zip} made by {@link ConfigExporter}, says which parts it holds,
 * and replaces only the parts the user keeps ticked (SET-12).
 *
 * <p>A zip is refused before anything is written when it is not a zip, has no {@code settings.json} at
 * its root, names a path outside the config folder, holds an entry or a total over the limits below or
 * more entries than an export ever would, or was written by a newer version. Sizes are counted while the
 * entries are inflated, not taken from the zip's own headers (PERF-12). What is this computer's own
 * state (the window's size and place, the last update check) is never imported.
 *
 * <p>Pure apart from the files it is given; no JavaFX, so it runs off the FX thread.
 */
public final class ConfigImport {

    /** Largest single file taken from a zip. */
    public static final long MAX_ENTRY_BYTES = 4L * 1024 * 1024;
    /** Largest total taken from a zip. */
    public static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;
    /** Most entries an import looks at. */
    public static final int MAX_ENTRIES = 1_000;

    /** What can be imported, in the order the question lists it. */
    public enum Part {
        PREFERENCES,
        KEY_BINDINGS,
        SYNC,
        OTHER_FILES
    }

    /** The {@code settings.json} keys of each part kept there; any other key is a preference. */
    static final Set<String> KEY_BINDING_KEYS = Set.of("keymap", "keybindings");

    static final Set<String> SYNC_KEYS = Set.of("sync");
    /** This computer's own state: never imported. */
    static final Set<String> LOCAL_KEYS = Set.of("schemaVersion", "window", "lastUpdateCheck");

    /** Why a zip is not imported; the message names the reason and the entry. */
    public static final class Refused extends IOException {
        public enum Reason {
            NOT_A_ZIP,
            NOT_AN_EXPORT,
            UNSAFE_PATH,
            TOO_LARGE,
            NEWER_VERSION
        }

        private final Reason reason;

        Refused(Reason reason, String detail) {
            super(reason + (detail.isEmpty() ? "" : ": " + detail));
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    /** A zip that passed every check: its settings, already migrated, and its other files. */
    public record Scan(ObjectNode settings, Map<String, byte[]> otherFiles) {

        /** The parts this zip holds. */
        public Set<Part> available() {
            Set<Part> parts = EnumSet.noneOf(Part.class);
            for (Iterator<String> it = settings.fieldNames(); it.hasNext(); ) {
                String key = it.next();
                Part part = partOf(key);
                if (part != null) parts.add(part);
            }
            if (!otherFiles.isEmpty()) parts.add(Part.OTHER_FILES);
            return parts;
        }
    }

    private ConfigImport() {}

    /** The part a {@code settings.json} key belongs to, or null for this computer's own state. */
    static @Nullable Part partOf(String key) {
        if (LOCAL_KEYS.contains(key)) return null;
        if (KEY_BINDING_KEYS.contains(key)) return Part.KEY_BINDINGS;
        if (SYNC_KEYS.contains(key)) return Part.SYNC;
        return Part.PREFERENCES;
    }

    /** Reads and checks {@code zip}; nothing is written. */
    public static Scan read(Path zip) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        int entries = 0;
        boolean sawEntry = false;
        try (InputStream in = Files.newInputStream(zip);
                ZipInputStream zipIn = new ZipInputStream(in)) {
            for (ZipEntry entry; (entry = zipIn.getNextEntry()) != null; ) {
                sawEntry = true;
                if (++entries > MAX_ENTRIES) throw new Refused(Refused.Reason.TOO_LARGE, entries + " entries");
                String name = entry.getName();
                if (!safe(name)) throw new Refused(Refused.Reason.UNSAFE_PATH, name);
                if (entry.isDirectory()) continue;
                byte[] bytes = inflate(zipIn, name);
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) throw new Refused(Refused.Reason.TOO_LARGE, "total");
                files.put(name, bytes);
            }
        } catch (ZipException e) {
            throw new Refused(Refused.Reason.NOT_A_ZIP, String.valueOf(e.getMessage()));
        }
        if (!sawEntry) throw new Refused(Refused.Reason.NOT_A_ZIP, "");
        byte[] settingsBytes = files.remove(ConfigStore.FILE);
        if (settingsBytes == null) throw new Refused(Refused.Reason.NOT_AN_EXPORT, "no " + ConfigStore.FILE);
        JsonNode tree;
        try {
            tree = ConfigStore.JSON.readTree(new String(settingsBytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new Refused(Refused.Reason.NOT_AN_EXPORT, ConfigStore.FILE);
        }
        if (!(tree instanceof ObjectNode settings)) throw new Refused(Refused.Reason.NOT_AN_EXPORT, ConfigStore.FILE);
        int version = Migrations.versionOf(settings);
        if (version > Settings.SCHEMA_VERSION) throw new Refused(Refused.Reason.NEWER_VERSION, "schema " + version);
        return new Scan(Migrations.migrate(settings), files);
    }

    /**
     * Replaces the ticked {@code parts} of the config folder with the zip's: the keys of each ticked
     * part in {@code settings.json} (the others are kept as they are here), and the other files when
     * {@link Part#OTHER_FILES} is ticked. Returns how many parts were replaced.
     */
    public static int apply(Scan scan, Path dir, Set<Part> parts) throws IOException {
        Path root = dir.toAbsolutePath().normalize();
        ObjectNode current = currentSettings(root.resolve(ConfigStore.FILE));
        Set<Part> done = EnumSet.noneOf(Part.class);
        for (Iterator<Map.Entry<String, JsonNode>> it = scan.settings().fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            Part part = partOf(field.getKey());
            if (part == null || !parts.contains(part)) continue;
            current.set(field.getKey(), field.getValue().deepCopy());
            done.add(part);
        }
        current.put("schemaVersion", Settings.SCHEMA_VERSION);
        Files.createDirectories(root);
        Files.writeString(
                root.resolve(ConfigStore.FILE), ConfigStore.JSON.writeValueAsString(current), StandardCharsets.UTF_8);
        if (parts.contains(Part.OTHER_FILES)) {
            for (Map.Entry<String, byte[]> file : scan.otherFiles().entrySet()) {
                Path target = root.resolve(file.getKey()).normalize();
                if (!target.startsWith(root) || target.equals(root)) continue; // checked in read(); kept safe here
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.write(target, file.getValue());
            }
            if (!scan.otherFiles().isEmpty()) done.add(Part.OTHER_FILES);
        }
        return done.size();
    }

    private static ObjectNode currentSettings(Path file) {
        try {
            if (Files.isRegularFile(file)
                    && ConfigStore.JSON.readTree(Files.readString(file, StandardCharsets.UTF_8))
                            instanceof ObjectNode object) {
                return Migrations.migrate(object);
            }
        } catch (IOException | RuntimeException e) {
            // an unreadable file here is replaced by the import
        }
        return ConfigStore.JSON.createObjectNode();
    }

    /** A relative path inside the folder: no {@code ..}, no absolute or drive path, no backslash. */
    static boolean safe(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) return false;
        for (String segment : name.split("/")) {
            if (segment.equals("..")) return false;
        }
        return true;
    }

    private static byte[] inflate(InputStream in, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long size = 0;
        for (int n; (n = in.read(buffer)) > 0; ) {
            size += n;
            if (size > MAX_ENTRY_BYTES) throw new Refused(Refused.Reason.TOO_LARGE, name);
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
