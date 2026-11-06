package com.example.app.config;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigDirTest {

    private static final String HOME = Path.of("/home/u").toString();

    @Test
    void flagThenDevThenEnvironmentThenPropertyThenHome() {
        Map<String, String> env = Map.of(ConfigDir.ENV, "/from/env");
        assertEquals(Path.of("/flag").toAbsolutePath(), ConfigDir.resolve("/flag", true, env, "/prop", HOME));
        assertEquals(Path.of(HOME, ".exampleapp-dev"), ConfigDir.resolve(null, true, env, "/prop", HOME));
        assertEquals(Path.of("/from/env").toAbsolutePath(), ConfigDir.resolve(" ", false, env, "/prop", HOME));
        assertEquals(Path.of("/prop").toAbsolutePath(), ConfigDir.resolve(null, false, Map.of(), "/prop", HOME));
        assertEquals(Path.of(HOME, ".exampleapp"), ConfigDir.resolve(null, false, Map.of(ConfigDir.ENV, ""), "", HOME));
    }

    @Test
    void theProcessVersionReadsTheTestProperty() {
        assertEquals(Path.of(System.getProperty(ConfigDir.PROPERTY)).toAbsolutePath(), ConfigDir.resolve(null, false));
    }
}
