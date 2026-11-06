package com.example.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugLogTest {

    @AfterEach
    void reset() {
        DebugLog.detachFile();
        DebugLog.onUncaught(null);
        DebugLog.clear();
    }

    @Test
    void capturesLoggingAndUncaughtExceptions() {
        DebugLog.install();
        DebugLog.install(); // idempotent
        Logger.getLogger("com.example.app.Thing").warning("careful");
        AtomicReference<Throwable> heard = new AtomicReference<>();
        DebugLog.onUncaught(heard::set);
        RuntimeException boom = new RuntimeException("boom");
        DebugLog.uncaught(Thread.currentThread(), boom);
        String text = DebugLog.text();
        assertTrue(text.contains("WARNING Thing — careful"), text);
        assertTrue(text.contains("UNCAUGHT on " + Thread.currentThread().getName()));
        assertSame(boom, heard.get());
        DebugLog.clear();
        assertEquals("", DebugLog.text());
    }

    @Test
    void formatsRecordsWithTheirStackTrace() {
        LogRecord record = new LogRecord(Level.SEVERE, "bad");
        record.setThrown(new IllegalStateException("why"));
        String line = DebugLog.format(record);
        assertTrue(line.contains("SEVERE ? — bad"));
        assertTrue(line.contains("IllegalStateException: why"));
    }

    @Test
    void isBounded() {
        for (int i = 0; i < DebugLog.MAX_ENTRIES + 5; i++) DebugLog.append("line " + i);
        assertFalse(DebugLog.text().contains("line 4\n"));
        assertTrue(DebugLog.text().endsWith("line " + (DebugLog.MAX_ENTRIES + 4)));
    }

    @Test
    void mirrorsToTheSessionFileAndKeepsThePreviousOne(@TempDir Path dir) throws Exception {
        DebugLog.attachFile(dir);
        DebugLog.append("first session");
        assertEquals(dir.resolve(DebugLog.FILE), DebugLog.file());
        DebugLog.detachFile();
        assertNull(DebugLog.file());
        DebugLog.clear();

        DebugLog.attachFile(dir);
        DebugLog.append("second session");
        assertTrue(Files.readString(dir.resolve(DebugLog.PREVIOUS_FILE)).contains("first session"));
        assertTrue(Files.readString(dir.resolve(DebugLog.FILE)).contains("second session"));
    }

    @Test
    void aFolderThatCannotBeCreatedLeavesNoFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("file");
        Files.writeString(file, "x");
        DebugLog.attachFile(file.resolve("sub"));
        assertNull(DebugLog.file());
        DebugLog.append("still in memory");
        assertTrue(DebugLog.text().contains("still in memory"));
    }
}
