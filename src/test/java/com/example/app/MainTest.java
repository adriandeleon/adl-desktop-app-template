package com.example.app;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.app.instance.SingleInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(
                args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void helpAndVersionPrintAndExitWithoutTheGui() {
        assertEquals(0, run("--help"));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("--config-dir"));
        out.reset();
        assertEquals(0, run("--version"));
        // ABOUT-02: --version prints the text the About environment block opens with.
        assertEquals(AppInfo.versionText(), out.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void aBadOptionExitsWithTwo() {
        assertEquals(2, run("--bogus"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("unknown option --bogus"));
    }

    @Test
    void withNothingRunningTheGuiStarts(@TempDir Path dir) {
        assertEquals(Main.LAUNCH, run("--config-dir", dir.toString()));
        assertEquals(Main.LAUNCH, run("--new-instance", "--config-dir", dir.toString()));
    }

    @Test
    void aSecondLaunchHandsItsFilesOver(@TempDir Path dir) throws Exception {
        List<List<String>> received = new CopyOnWriteArrayList<>();
        try (SingleInstance instance = SingleInstance.listen(dir, received::add)) {
            assertNotNull(instance);
            assertEquals(0, run("--config-dir", dir.toString(), "notes.txt"));
            assertEquals(1, received.size());
            assertTrue(received.getFirst().getFirst().endsWith("notes.txt"));
        }
    }
}
