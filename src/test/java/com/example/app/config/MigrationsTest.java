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
        ObjectNode v3 = Migrations.migrate(v2);
        assertEquals(3, Migrations.versionOf(v3));
        assertEquals("emacs", v3.get("keymap").asText());
    }

    @Test
    void anUnknownOldVersionHasNoPath() {
        ObjectNode v0 = JsonNodeFactory.instance.objectNode().put("schemaVersion", 0);
        assertThrows(IllegalStateException.class, () -> Migrations.migrate(v0));
    }
}
