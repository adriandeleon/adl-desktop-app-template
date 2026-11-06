package com.example.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;

/**
 * Loads, migrates and saves {@code settings.json}, and tells every listener when it changes, so a
 * change made in one window reaches all of them (live apply: there is no Save button).
 *
 * <p>Users' data outlives versions: a file from an older build is migrated forward, and a file from a
 * <em>newer</em> build is copied to {@code settings.json.v<n>.bak} before defaults load, so an older
 * build never clobbers a newer config. An unreadable file is kept as {@code settings.json.broken.bak}.
 */
public final class ConfigStore {

    public static final String FILE = "settings.json";

    private static final Logger LOG = Logger.getLogger(ConfigStore.class.getName());

    static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** What happened at load, for the status bar. */
    public enum LoadResult {
        DEFAULTS,
        LOADED,
        MIGRATED,
        NEWER_BACKED_UP,
        BROKEN_BACKED_UP
    }

    private final Path dir;
    private final List<Consumer<Settings>> listeners = new CopyOnWriteArrayList<>();
    private Settings settings = new Settings();
    private LoadResult loadResult = LoadResult.DEFAULTS;
    private @Nullable Path backup;

    public ConfigStore(Path dir) {
        this.dir = dir;
    }

    /** Opens the store and loads the file. */
    public static ConfigStore open(Path dir) {
        ConfigStore store = new ConfigStore(dir);
        store.reload();
        return store;
    }

    public Path dir() {
        return dir;
    }

    public Path file() {
        return dir.resolve(FILE);
    }

    /** The live settings. Change them through {@link #update}, so they are saved and announced. */
    public Settings settings() {
        return settings;
    }

    public LoadResult loadResult() {
        return loadResult;
    }

    /** The backup the last load made, or null. */
    public @Nullable Path backup() {
        return backup;
    }

    /** Applies a change, saves, and notifies every listener. */
    public void update(Consumer<Settings> change) {
        change.accept(settings);
        save();
        fire();
    }

    public void addListener(Consumer<Settings> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<Settings> listener) {
        listeners.remove(listener);
    }

    private void fire() {
        for (Consumer<Settings> listener : listeners) listener.accept(settings);
    }

    /** Re-reads the file (after an import or a sync) and notifies the listeners. */
    public void reload() {
        backup = null;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            settings = new Settings();
            loadResult = LoadResult.DEFAULTS;
            fire();
            return;
        }
        try {
            JsonNode tree = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (!(tree instanceof ObjectNode object)) throw new IOException("not a JSON object");
            int version = Migrations.versionOf(object);
            if (version > Settings.SCHEMA_VERSION) {
                backup = copyAside(file, FILE + ".v" + version + ".bak");
                settings = new Settings();
                loadResult = LoadResult.NEWER_BACKED_UP;
            } else {
                settings = JSON.treeToValue(Migrations.migrate(object), Settings.class);
                loadResult = version < Settings.SCHEMA_VERSION ? LoadResult.MIGRATED : LoadResult.LOADED;
                if (loadResult == LoadResult.MIGRATED) save();
            }
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read " + file + "; using defaults", e);
            backup = copyAside(file, FILE + ".broken.bak");
            settings = new Settings();
            loadResult = LoadResult.BROKEN_BACKED_UP;
        }
        fire();
    }

    private @Nullable Path copyAside(Path file, String name) {
        try {
            return Files.copy(file, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not back up " + file, e);
            return null;
        }
    }

    /** Writes the file atomically: a crash mid-write leaves the old file, never half a new one. */
    public void save() {
        try {
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, FILE, ".tmp");
            Files.writeString(temp, JSON.writeValueAsString(settings), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not save " + file(), e);
        }
    }
}
