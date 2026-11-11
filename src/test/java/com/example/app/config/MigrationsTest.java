package com.example.app.config;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationsTest {

    @Test
    void everyVersionHasAStep() {
        assertTrue(Migrations.complete());
    }

    @Test
    void v1DarkModeBecomesATheme() {
        ObjectNode v1 = JsonNodeFactory.instance.objectNode().put("darkMode", true);
        ObjectNode v2 = Migrations.migrate(v1);
        assertEquals(Settings.SCHEMA_VERSION, Migrations.versionOf(v2));
        assertEquals("DARK", v2.get("theme").asText());
        assertFalse(v2.has("darkMode"));
        assertEquals(
                "LIGHT",
                Migrations.migrate(JsonNodeFactory.instance.objectNode().put("darkMode", false))
                        .get("theme")
                        .asText());
        assertFalse(Migrations.migrate(JsonNodeFactory.instance.objectNode()).has("theme"));
    }

    @Test
    void anAdditiveVersionMigratesByIdentity() {
        ObjectNode v2 =
                JsonNodeFactory.instance.objectNode().put("schemaVersion", 2).put("keymap", "emacs");
        // 2 to 3 (titleBarMenus), 3 to 4 (showToolbar), 4 to 5 (showTabBar, window.toolWindows) and 5 to 6
        // (simpleMode) only add fields: the tree comes through as it was.
        ObjectNode current = Migrations.migrate(v2);
        assertEquals(Settings.SCHEMA_VERSION, Migrations.versionOf(current));
        assertEquals(6, Settings.SCHEMA_VERSION);
        assertEquals("emacs", current.get("keymap").asText());
    }

    @Test
    void anUnknownOldVersionHasNoPath() {
        ObjectNode v0 = JsonNodeFactory.instance.objectNode().put("schemaVersion", 0);
        assertThrows(IllegalStateException.class, () -> Migrations.migrate(v0));
    }
}
