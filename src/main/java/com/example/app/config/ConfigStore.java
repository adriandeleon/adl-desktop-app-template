package com.example.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
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

    /**
     * A settings file larger than this is not read (PERF-12): it was not written by this app, and is kept
     * aside as broken like any unreadable one.
     */
    public static final long MAX_FILE_BYTES = 1024 * 1024;

    private static final Logger LOG = Logger.getLogger(ConfigStore.class.getName());

    static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setDefaultPrettyPrinter(ConfigFiles.indented());

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
    /**
     * Saves run here, off the caller's thread (PERF-01): one at a time, in order, and a burst of changes
     * writes only the newest text.
     */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("settings-writer").factory());

    private final AtomicReference<@Nullable Settings> pendingWrite = new AtomicReference<>();

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

    /** Session state: the window's place and the open documents (SET-08, WIN-08), apart from the settings. */
    public static final String SESSION_FILE = "session.json";

    /** The session file's own schema, apart from the settings'. */
    static final int SESSION_SCHEMA = 1;

    public Path sessionFile() {
        return dir.resolve(SESSION_FILE);
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

    /** How many listeners are registered: a closed window must leave none of its own behind (PERF-13). */
    public int listenerCount() {
        return listeners.size();
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

    /** What a read of the file found: the settings, how they loaded, and the backup it made. */
    public record Loaded(
            Settings settings, LoadResult result, @Nullable Path backup) {}

    /** Re-reads the file (after an import or a sync) and notifies the listeners. */
    public void reload() {
        install(read());
    }

    /**
     * Reads the file without touching the live settings: file work that may run off the FX thread
     * (PERF-01), then {@link #install} on it. Waits for pending saves first.
     */
    public Loaded read() {
        flush();
        Path file = file();
        if (!Files.isRegularFile(file)) {
            Settings fresh = new Settings();
            fresh.window = readSession(null);
            return new Loaded(fresh.clamped(), LoadResult.DEFAULTS, null);
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("larger than " + MAX_FILE_BYTES + " bytes");
            JsonNode tree = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            if (!(tree instanceof ObjectNode object)) throw new IOException("not a JSON object");
            int version = Migrations.versionOf(object);
            if (version > Settings.SCHEMA_VERSION) {
                Settings defaults = new Settings();
                defaults.window = readSession(null);
                return new Loaded(
                        defaults.clamped(),
                        LoadResult.NEWER_BACKED_UP,
                        copyAside(file, FILE + ".v" + version + ".bak"));
            }
            // A file from before session.json kept the window here: it is carried over once (SET-08).
            JsonNode legacyWindow = object.get("window");
            // Clamped as it is read (SET-11): a hand-edited value out of range never reaches the app.
            Settings loaded = JSON.treeToValue(Migrations.migrate(object), Settings.class);
            loaded.window = readSession(legacyWindow);
            return new Loaded(
                    loaded.clamped(),
                    version < Settings.SCHEMA_VERSION ? LoadResult.MIGRATED : LoadResult.LOADED,
                    null);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read " + file + "; using defaults", e);
            Settings defaults = new Settings();
            defaults.window = readSession(null);
            return new Loaded(defaults.clamped(), LoadResult.BROKEN_BACKED_UP, copyAside(file, FILE + ".broken.bak"));
        }
    }

    /** Makes a read the live settings and notifies the listeners; a migrated file is saved forward. */
    public void install(Loaded loaded) {
        settings = loaded.settings();
        loadResult = loaded.result();
        backup = loaded.backup();
        if (loadResult == LoadResult.MIGRATED) save();
        fire();
    }

    /**
     * The session state from {@code session.json}; from {@code legacy} (an old settings file's window) when
     * there is none yet; else the defaults. A session file that can't be read, or that a newer version wrote, is
     * kept aside and the session starts fresh (SET-10, SET-11): it is never a reason to fail a start.
     */
    private Settings.Window readSession(@Nullable JsonNode legacy) {
        Path file = sessionFile();
        try {
            if (Files.isRegularFile(file)) {
                if (Files.size(file) > MAX_FILE_BYTES)
                    throw new IOException("larger than " + MAX_FILE_BYTES + " bytes");
                JsonNode tree = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
                if (!(tree instanceof ObjectNode object)) throw new IOException("not a JSON object");
                int version = object.path("schemaVersion").asInt(SESSION_SCHEMA);
                if (version > SESSION_SCHEMA) {
                    copyAside(file, SESSION_FILE + ".v" + version + ".bak");
                    return new Settings.Window();
                }
                object.remove("schemaVersion");
                return JSON.treeToValue(object, Settings.Window.class);
            }
            if (legacy instanceof ObjectNode old) return JSON.treeToValue(old, Settings.Window.class);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read " + file + "; starting a fresh session", e);
            if (Files.isRegularFile(file)) copyAside(file, SESSION_FILE + ".broken.bak");
        }
        return new Settings.Window();
    }

    private @Nullable Path copyAside(Path file, String name) {
        try {
            return Files.copy(file, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not back up " + file, e);
            return null;
        }
    }

    /**
     * Saves the settings: a snapshot is taken now, on the caller's thread, and serialized and written
     * off it (PERF-01). The write is atomic: a crash mid-write leaves the old file, never half a new one.
     */
    public void save() {
        if (pendingWrite.getAndSet(settings.copy()) == null) writer.execute(this::writePending);
    }

    /** Waits until every save so far is on disk: before a reload, at exit, and in tests. */
    public void flush() {
        try {
            writer.submit(() -> {}).get();
        } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
            Thread.currentThread().interrupt(); // the no-op task cannot fail: only an interrupt gets here
        }
    }

    private void writePending() {
        Settings snapshot = pendingWrite.getAndSet(null);
        if (snapshot == null) return;
        try {
            ConfigFiles.writeString(file(), JSON.writeValueAsString(snapshot));
            // Session state in a file of its own (SET-08): settings.json holds no window and no open documents.
            ObjectNode session = JSON.valueToTree(snapshot.window);
            session.put("schemaVersion", SESSION_SCHEMA);
            ConfigFiles.writeString(sessionFile(), JSON.writeValueAsString(session));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not save " + file(), e);
        }
    }
}
