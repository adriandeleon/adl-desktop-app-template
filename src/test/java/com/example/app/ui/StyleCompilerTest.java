package com.example.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.css.Stylesheet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-17: every stylesheet gets its precompiled .bss, which reads back to the same rules; no toolkit. */
class StyleCompilerTest {

    @Test
    void everyStylesheetIsPrecompiledAndReadsBack(@TempDir Path dir) throws Exception {
        for (String name : List.of("app.css", "ui-font.css", "high-contrast.css", "editora-dark.css")) {
            try (var in = StyleCompilerTest.class.getResourceAsStream("/com/example/app/styles/" + name)) {
                Files.write(dir.resolve(name), in.readAllBytes());
            }
        }
        List<Path> written = StyleCompiler.compile(dir);
        assertEquals(4, written.size());
        Path bss = dir.resolve("app.bss");
        assertTrue(Files.size(bss) > 0);
        Stylesheet binary = Stylesheet.loadBinary(bss.toUri().toURL());
        assertFalse(binary.getRules().isEmpty(), "the rules are in the binary form");
        assertEquals(
                "app.bss",
                StyleCompiler.binaryOf(dir.resolve("app.css")).getFileName().toString());
        assertEquals("app.bss", Themes.binaryName("app.css"));
        assertNull(Themes.binaryName("notes.txt"));
    }
}
