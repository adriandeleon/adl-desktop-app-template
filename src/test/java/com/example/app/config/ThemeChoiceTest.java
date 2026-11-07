package com.example.app.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeChoiceTest {

    @TempDir
    Path dir;

    @Test
    void aSavedNameIsLookedUpAndAnUnknownOneIsTheDefault() {
        assertEquals(ThemeChoice.SYSTEM, ThemeChoice.DEFAULT);
        assertEquals(ThemeChoice.DARK, ThemeChoice.of("DARK"));
        assertEquals(ThemeChoice.LIGHT, ThemeChoice.of(" light "));
        assertEquals(ThemeChoice.DEFAULT, ThemeChoice.of("solarized"));
        assertEquals(ThemeChoice.DEFAULT, ThemeChoice.of(""));
        assertEquals(ThemeChoice.DEFAULT, ThemeChoice.of(null));
    }

    /** One theme this version does not know costs the theme, not the whole settings file. */
    @Test
    void anUnknownThemeFallsBackAndKeepsTheOtherSettings() throws Exception {
        Files.writeString(
                dir.resolve(ConfigStore.FILE),
                "{\"schemaVersion\": " + Settings.SCHEMA_VERSION
                        + ", \"theme\": \"solarized\", \"language\": \"de\", \"keymap\": \"emacs\","
                        + " \"showStatusBar\": false}");
        ConfigStore store = ConfigStore.open(dir);
        assertEquals(ConfigStore.LoadResult.LOADED, store.loadResult());
        assertNull(store.backup());
        assertEquals(ThemeChoice.DEFAULT, store.settings().theme);
        assertEquals("de", store.settings().language);
        assertEquals("emacs", store.settings().keymap);
        assertEquals(false, store.settings().showStatusBar);
        store.save();
        assertTrue(Files.readString(store.file()).contains("\"theme\" : \"SYSTEM\""));
    }
}
