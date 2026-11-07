package com.example.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Import replaces only the ticked parts, and refuses a zip that is not an export before writing (SET-12, PERF-12). */
class ConfigImportTest {

    @TempDir
    Path dir;

    private Path zip(Map<String, byte[]> entries) throws IOException {
        Path zip = Files.createTempFile(dir, "import", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (var e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return zip;
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void onlyTheTickedPartsAreReplaced() throws Exception {
        Path here = dir.resolve("here");
        ConfigStore store = ConfigStore.open(here);
        store.update(s -> {
            s.theme = ThemeChoice.DARK;
            s.keymap = "cua";
            s.sync.repoUrl = "git@example.com:me/mine.git";
            s.window.width = 1234;
        });
        Path zip = zip(
                Map.of(
                        "settings.json",
                        utf8(
                                "{\"schemaVersion\":3,\"theme\":\"LIGHT\",\"keymap\":\"emacs\",\"keybindings\":{\"help.about\":\"F1\"},"
                                        + "\"sync\":{\"repoUrl\":\"git@example.com:them/theirs.git\"},\"window\":{\"width\":500}}")));
        ConfigImport.Scan scan = ConfigImport.read(zip);
        assertEquals(
                EnumSet.of(ConfigImport.Part.PREFERENCES, ConfigImport.Part.KEY_BINDINGS, ConfigImport.Part.SYNC),
                scan.available());

        assertEquals(1, ConfigImport.apply(scan, here, EnumSet.of(ConfigImport.Part.KEY_BINDINGS)));
        store.reload();
        Settings s = store.settings();
        assertEquals("emacs", s.keymap, "the ticked part is replaced");
        assertEquals("F1", s.keybindings.get("help.about"));
        assertEquals(ThemeChoice.DARK, s.theme, "an unticked part is kept");
        assertEquals("git@example.com:me/mine.git", s.sync.repoUrl);
        assertEquals(1234, s.window.width, "this computer's own state is never imported");

        ConfigImport.apply(scan, here, EnumSet.allOf(ConfigImport.Part.class));
        store.reload();
        assertEquals(ThemeChoice.LIGHT, store.settings().theme);
        assertEquals("git@example.com:them/theirs.git", store.settings().sync.repoUrl);
        assertEquals(1234, store.settings().window.width);
    }

    @Test
    void aZipThatIsNotAnExportIsRefusedBeforeAnythingIsWritten() throws Exception {
        Path notZip = Files.writeString(dir.resolve("x.zip"), "hello");
        assertEquals(
                ConfigImport.Refused.Reason.NOT_A_ZIP,
                assertThrows(ConfigImport.Refused.class, () -> ConfigImport.read(notZip))
                        .reason());
        assertEquals(
                ConfigImport.Refused.Reason.NOT_AN_EXPORT,
                assertThrows(ConfigImport.Refused.class, () -> ConfigImport.read(zip(Map.of("a.json", utf8("{}")))))
                        .reason());
        assertEquals(
                ConfigImport.Refused.Reason.NOT_AN_EXPORT,
                assertThrows(
                                ConfigImport.Refused.class,
                                () -> ConfigImport.read(zip(Map.of("settings.json", utf8("[1]")))))
                        .reason());
        assertEquals(
                ConfigImport.Refused.Reason.UNSAFE_PATH,
                assertThrows(
                                ConfigImport.Refused.class,
                                () -> ConfigImport.read(zip(Map.of("settings.json", utf8("{}"), "../evil", utf8("x")))))
                        .reason());
        assertEquals(
                ConfigImport.Refused.Reason.NEWER_VERSION,
                assertThrows(
                                ConfigImport.Refused.class,
                                () -> ConfigImport.read(zip(Map.of("settings.json", utf8("{\"schemaVersion\":999}")))))
                        .reason());
        assertFalse(Files.exists(dir.resolve("evil")));
    }

    @Test
    void anEntryOverTheSizeLimitIsRefusedByWhatItInflatesTo() throws Exception {
        byte[] big = new byte[(int) ConfigImport.MAX_ENTRY_BYTES + 1];
        assertEquals(
                ConfigImport.Refused.Reason.TOO_LARGE,
                assertThrows(
                                ConfigImport.Refused.class,
                                () -> ConfigImport.read(zip(Map.of("settings.json", utf8("{}"), "big.bin", big))))
                        .reason());
    }

    @Test
    void pathsInsideTheFolderAreSafeAndOthersAreNot() {
        assertTrue(ConfigImport.safe("themes/mine.json"));
        for (String bad : Set.of("", "/etc/passwd", "a/../../b", "..", "c:\\x", "a\\b")) {
            assertFalse(ConfigImport.safe(bad), bad);
        }
    }
}
