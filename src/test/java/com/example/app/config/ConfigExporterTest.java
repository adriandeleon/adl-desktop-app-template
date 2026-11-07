package com.example.app.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigExporterTest {

    @Test
    void exportsConfigButNotTransientFiles(@TempDir Path dir, @TempDir Path out) throws Exception {
        Files.writeString(dir.resolve("settings.json"), "{}");
        Files.createDirectories(dir.resolve("themes"));
        Files.writeString(dir.resolve("themes/mine.json"), "{}");
        Files.createDirectories(dir.resolve("sync/repo"));
        Files.writeString(dir.resolve("sync/repo/x"), "x");
        for (String name : new String[] {"exampleapp-session.log", "settings.json.v9.bak", "instance.sock", "a.tmp"}) {
            Files.writeString(dir.resolve(name), "x");
        }
        Path zip = out.resolve("config.zip");
        assertEquals(2, ConfigExporter.export(dir, zip));

        Path restored = out.resolve("restored");
        ConfigImport.Scan scan = ConfigImport.read(zip);
        assertEquals(java.util.Set.of(ConfigImport.Part.OTHER_FILES), scan.available());
        ConfigImport.apply(scan, restored, java.util.EnumSet.allOf(ConfigImport.Part.class));
        assertEquals("{}", Files.readString(restored.resolve("themes/mine.json")));
        assertFalse(Files.exists(restored.resolve("sync")));
    }

    @Test
    void anArchiveThatEscapesTheFolderIsRefusedWhole(@TempDir Path dir, @TempDir Path out) throws Exception {
        Path zip = out.resolve("evil.zip");
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zip))) {
            zipOut.putNextEntry(new ZipEntry("dir/"));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("ok.json"));
            zipOut.write(1);
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("../escape.json"));
            zipOut.write(1);
            zipOut.closeEntry();
        }
        assertThrows(IOException.class, () -> ConfigImport.read(zip));
        assertFalse(Files.exists(dir.resolve("ok.json")));
    }

    @Test
    void directoryEntriesAreSkipped(@TempDir Path dir, @TempDir Path out) throws Exception {
        Path zip = out.resolve("dirs.zip");
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zip))) {
            zipOut.putNextEntry(new ZipEntry("folder/"));
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("folder/a.json"));
            zipOut.write('a');
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("settings.json"));
            zipOut.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zipOut.closeEntry();
        }
        ConfigImport.apply(ConfigImport.read(zip), dir, java.util.EnumSet.of(ConfigImport.Part.OTHER_FILES));
        assertTrue(Files.exists(dir.resolve("folder/a.json")));
    }

    @Test
    void theDefaultNameIsTimestamped() {
        assertEquals(
                "exampleapp-config-20261106-093005.zip",
                ConfigExporter.defaultName(LocalDateTime.of(2026, 11, 6, 9, 30, 5)));
    }
}
