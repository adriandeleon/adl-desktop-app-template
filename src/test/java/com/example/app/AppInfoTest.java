package com.example.app;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppInfoTest {

    @Test
    void versionComesFromTheFilteredBuildInfo() {
        assertFalse(AppInfo.VERSION.startsWith("${"));
        assertEquals(AppInfo.VERSION.endsWith("-SNAPSHOT"), AppInfo.isSnapshot());
        assertEquals("fallback", AppInfo.load("no.such.key", "fallback"));
    }

    @Test
    void environmentNamesEveryPartAndHidesTheHomeFolder() {
        String home = System.getProperty("user.home");
        String env = AppInfo.environment(Path.of(home, ".exampleapp"));
        assertTrue(env.startsWith(AppInfo.NAME + " " + AppInfo.VERSION));
        for (String part : new String[] {"Commit ", "Java: ", "JavaFX: ", "OS: ", "Config: ~"}) {
            assertTrue(env.contains(part), part);
        }
        assertFalse(env.contains(home));
        assertEquals("/elsewhere", AppInfo.tildeHome("/elsewhere"));
    }

    @Test
    void theEnvironmentNamesThePautaEditionRightAfterTheCommit() {
        String env = AppInfo.environment(Path.of("/tmp/x"));
        assertTrue(env.startsWith(AppInfo.versionText() + "\n"));
        String[] lines = env.split("\n");
        assertTrue(lines[1].startsWith("Commit "));
        assertEquals("Pauta: " + AppInfo.PAUTA_EDITION, lines[2]);
    }

    @Test
    void thePautaLinkIsTheEditionsOwnPageNeverLatest() {
        assertEquals("https://pauta-project.dev/" + AppInfo.PAUTA_EDITION + "/", AppInfo.pautaUrl());
        assertFalse(AppInfo.pautaUrl().contains("latest"));
        assertFalse(AppInfo.PAUTA_EDITION.startsWith("${"));
    }

    @Test
    void macCheckFollowsTheOsName() {
        assertEquals(System.getProperty("os.name").toLowerCase().startsWith("mac"), AppInfo.isMac());
    }
}
