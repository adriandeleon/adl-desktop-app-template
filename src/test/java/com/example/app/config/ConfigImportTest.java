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
        store.flush();
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

    @Test
    void tooManyEntriesABrokenZipOrBrokenSettingsAreRefused() throws Exception {
        java.util.Map<String, byte[]> many = new java.util.LinkedHashMap<>();
        many.put("settings.json", utf8("{}"));
        for (int i = 0; i < ConfigImport.MAX_ENTRIES; i++) many.put("f" + i, new byte[0]);
        assertEquals(
                ConfigImport.Refused.Reason.TOO_LARGE,
                assertThrows(ConfigImport.Refused.class, () -> ConfigImport.read(zip(many)))
                        .reason());
        Path broken = Files.write(dir.resolve("broken.zip"), new byte[] {'P', 'K', 3, 4, 20, 0, 9, 9, 9, 9, 9, 9});
        assertEquals(
                ConfigImport.Refused.Reason.NOT_A_ZIP,
                assertThrows(ConfigImport.Refused.class, () -> ConfigImport.read(broken))
                        .reason());
        assertEquals(
                ConfigImport.Refused.Reason.NOT_AN_EXPORT,
                assertThrows(
                                ConfigImport.Refused.class,
                                () -> ConfigImport.read(zip(Map.of("settings.json", utf8("{")))))
                        .reason());
    }

    @Test
    void aTotalOverTheLimitIsRefused() throws Exception {
        java.util.Map<String, byte[]> big = new java.util.LinkedHashMap<>();
        big.put("settings.json", utf8("{}"));
        byte[] chunk = new byte[(int) ConfigImport.MAX_ENTRY_BYTES];
        for (long total = 0; total <= ConfigImport.MAX_TOTAL_BYTES; total += chunk.length) big.put("c" + total, chunk);
        assertEquals(
                ConfigImport.Refused.Reason.TOO_LARGE,
                assertThrows(ConfigImport.Refused.class, () -> ConfigImport.read(zip(big)))
                        .reason());
    }

    @Test
    void applyKeepsToTheFolderAndReplacesAnUnreadableSettingsFile() throws Exception {
        Path here = dir.resolve("here");
        Files.createDirectories(here);
        Files.writeString(here.resolve("settings.json"), "{ broken");
        var scan = new ConfigImport.Scan(
                (com.fasterxml.jackson.databind.node.ObjectNode) ConfigStore.JSON.readTree("{\"keymap\":\"emacs\"}"),
                Map.of("../outside.json", utf8("x"), "inside.json", utf8("y")));
        ConfigImport.apply(scan, here, EnumSet.allOf(ConfigImport.Part.class));
        assertFalse(Files.exists(dir.resolve("outside.json")), "a path outside the folder is never written");
        assertTrue(Files.exists(here.resolve("inside.json")));
        assertEquals("emacs", ConfigStore.open(here).settings().keymap);
    }
}
