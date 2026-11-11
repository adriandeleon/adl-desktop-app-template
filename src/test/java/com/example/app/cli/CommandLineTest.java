package com.example.app.cli;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineTest {

    @Test
    void parsesEveryFlag() {
        CommandLine cli = CommandLine.parse(new String[] {
            "-h", "-v", "--dev", "--new-instance", "--no-session", "--report", "--config-dir", "/tmp/c", "a.txt"
        });
        assertTrue(cli.help() && cli.version() && cli.dev() && cli.newInstance() && cli.noSession() && cli.report());
        assertEquals("/tmp/c", cli.configDir());
        assertEquals(List.of("a.txt"), cli.files());
        assertNull(cli.error());
    }

    @Test
    void acceptsTheEqualsFormAndDoubleDash() {
        CommandLine cli = CommandLine.parse(new String[] {"--config-dir=/x", "--", "--not-a-flag", "-"});
        assertEquals("/x", cli.configDir());
        assertEquals(List.of("--not-a-flag", "-"), cli.files());
    }

    @Test
    void reportsMistakes() {
        assertEquals(
                "unknown option --what",
                CommandLine.parse(new String[] {"--what"}).error());
        assertEquals(
                "--config-dir needs a folder",
                CommandLine.parse(new String[] {"--config-dir"}).error());
        assertEquals(
                "--config-dir needs a folder",
                CommandLine.parse(new String[] {"--config-dir", " "}).error());
        assertEquals(
                "--config-dir needs a folder",
                CommandLine.parse(new String[] {"--config-dir="}).error());
    }

    @Test
    void defaultsAreOff() {
        CommandLine cli = CommandLine.parse(new String[0]);
        assertFalse(cli.help() || cli.version() || cli.dev() || cli.report());
        assertNull(cli.configDir());
    }

    @Test
    void usageListsEveryFlag() {
        String usage = CommandLine.usage();
        for (String flag : new String[] {
            "--help",
            "--version",
            "--config-dir",
            "--dev",
            "--new-instance",
            "--no-session",
            "--report",
            "EXAMPLEAPP_CONFIG_DIR"
        }) {
            assertTrue(usage.contains(flag), flag);
        }
    }

    /** CLI-12: the JVM options a launcher can leave are skipped with their values; CLI-13: -V is --version. */
    @Test
    void skipsTheLaunchersJvmOptionsAndTakesCapitalVForVersion() {
        CommandLine cli = CommandLine.parse(new String[] {
            "--add-exports",
            "javafx.graphics/com.sun.glass.ui=com.example.app",
            "--add-opens=javafx.base/x=y",
            "-p",
            "/app/mods",
            "--add-exports=a/b=c",
            "notes.txt",
            "-V"
        });
        assertNull(cli.error());
        assertEquals(List.of("notes.txt"), cli.files());
        assertTrue(cli.version());
        assertEquals("unknown option -x", CommandLine.parse(new String[] {"-x"}).error());
    }
}
