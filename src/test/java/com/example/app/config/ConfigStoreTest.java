package com.example.app.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {

    @TempDir
    Path dir;

    @Test
    void noFileMeansDefaults() {
        ConfigStore store = ConfigStore.open(dir);
        assertEquals(ConfigStore.LoadResult.DEFAULTS, store.loadResult());
        assertEquals(ThemeChoice.SYSTEM, store.settings().theme);
        assertEquals(dir, store.dir());
        assertNull(store.backup());
    }

    /** The settings round trip: every switch, number and choice survives a restart. */
    @Test
    void everySettingSurvivesARestart() {
        ConfigStore store = ConfigStore.open(dir);
        List<Settings> heard = new ArrayList<>();
        Consumer<Settings> listener = heard::add;
        store.addListener(listener);
        store.update(s -> {
            s.theme = ThemeChoice.DARK;
            s.language = "de";
            s.keymap = "emacs";
            s.keybindings.put("app.quit", "C-q");
            s.checkForUpdates = false;
            s.lastUpdateCheck = 42;
            s.showStatusBar = false;
            s.titleBarMenus = false;
            s.window.x = 10.0;
            s.window.y = 20.0;
            s.window.width = 800;
            s.window.height = 500;
            s.window.maximized = true;
            s.sync.repoUrl = "git@example.com:me/sync.git";
            s.sync.branch = "trunk";
            s.sync.auto = false;
            s.sync.intervalMinutes = 5;
            s.sync.lastSync = 7;
        });
        assertEquals(1, heard.size());
        store.removeListener(listener);
        store.flush(); // saves are written off the caller's thread

        ConfigStore again = ConfigStore.open(dir);
        assertEquals(ConfigStore.LoadResult.LOADED, again.loadResult());
        Settings s = again.settings();
        assertEquals(ThemeChoice.DARK, s.theme);
        assertEquals("de", s.language);
        assertEquals("emacs", s.keymap);
        assertEquals("C-q", s.keybindings.get("app.quit"));
        assertFalse(s.checkForUpdates);
        assertEquals(42, s.lastUpdateCheck);
        assertFalse(s.showStatusBar);
        assertFalse(s.titleBarMenus);
        assertEquals(10.0, s.window.x);
        assertEquals(20.0, s.window.y);
        assertEquals(800, s.window.width);
        assertEquals(500, s.window.height);
        assertTrue(s.window.maximized);
        assertEquals("git@example.com:me/sync.git", s.sync.repoUrl);
        assertEquals("trunk", s.sync.branch);
        assertFalse(s.sync.auto);
        assertEquals(5, s.sync.intervalMinutes);
        assertEquals(7, s.sync.lastSync);
    }

    @Test
    void anOldFileIsMigratedAndRewritten() throws Exception {
        Files.writeString(dir.resolve(ConfigStore.FILE), "{\"darkMode\": true, \"unknown\": 1}");
        ConfigStore store = ConfigStore.open(dir);
        assertEquals(ConfigStore.LoadResult.MIGRATED, store.loadResult());
        assertEquals(ThemeChoice.DARK, store.settings().theme);
        store.flush();
        assertTrue(Files.readString(store.file()).contains("\"schemaVersion\" : " + Settings.SCHEMA_VERSION));
    }

    @Test
    void aNewerFileIsBackedUpNotClobbered() throws Exception {
        String newer = "{\"schemaVersion\": 99, \"theme\": \"DARK\"}";
        Files.writeString(dir.resolve(ConfigStore.FILE), newer);
        ConfigStore store = ConfigStore.open(dir);
        assertEquals(ConfigStore.LoadResult.NEWER_BACKED_UP, store.loadResult());
        assertEquals(ThemeChoice.SYSTEM, store.settings().theme);
        assertNotNull(store.backup());
        assertEquals(newer, Files.readString(dir.resolve("settings.json.v99.bak")));
    }

    @Test
    void anUnreadableFileIsBackedUp() throws Exception {
        Files.writeString(dir.resolve(ConfigStore.FILE), "[not an object]");
        assertEquals(
                ConfigStore.LoadResult.BROKEN_BACKED_UP, ConfigStore.open(dir).loadResult());
        Files.writeString(dir.resolve(ConfigStore.FILE), "{broken");
        ConfigStore store = ConfigStore.open(dir);
        assertEquals(ConfigStore.LoadResult.BROKEN_BACKED_UP, store.loadResult());
        assertTrue(Files.exists(dir.resolve("settings.json.broken.bak")));
    }

    @Test
    void savingIntoAnUnwritablePlaceIsLoggedNotThrown() throws Exception {
        Path file = dir.resolve("a-file");
        Files.writeString(file, "x");
        ConfigStore store = new ConfigStore(file.resolve("sub"));
        store.save(); // the parent is a file: cannot create the folder
        assertFalse(Files.exists(file.resolve("sub")));
    }

    @Test
    void anOversizedFileIsSetAsideUnread() throws Exception {
        java.nio.file.Files.writeString(
                dir.resolve(ConfigStore.FILE), "{\"pad\":\"" + "x".repeat((int) ConfigStore.MAX_FILE_BYTES) + "\"}");
        ConfigStore store = ConfigStore.open(dir);
        assertEquals(ConfigStore.LoadResult.BROKEN_BACKED_UP, store.loadResult());
    }

    @Test
    void aBurstOfChangesIsWrittenOffTheCallersThreadAndEndsWithTheNewest() throws Exception {
        ConfigStore store = ConfigStore.open(dir);
        for (int i = 1; i <= 50; i++) {
            int minutes = i;
            store.update(s -> s.sync.intervalMinutes = minutes);
        }
        store.flush();
        assertEquals(50, ConfigStore.open(dir).settings().sync.intervalMinutes);
    }
}
