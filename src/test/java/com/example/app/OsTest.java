package com.example.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OS-02 and OS-03: one class answers which OS the app runs on, and a test can make it answer any. */
class OsTest {

    @AfterEach
    void reset() {
        Os.override(null);
    }

    @Test
    void theOsNameGivesTheKind() {
        assertEquals(Os.Kind.MAC, Os.parse("Mac OS X"));
        assertEquals(Os.Kind.WINDOWS, Os.parse("Windows 11"));
        assertEquals(Os.Kind.LINUX, Os.parse("Linux"));
        assertEquals(Os.Kind.LINUX, Os.parse("FreeBSD"));
        assertEquals(Os.parse(System.getProperty("os.name")), Os.current());
    }

    @Test
    void anOverrideRunsTheAppAsAnotherOs() {
        for (Os.Kind kind : Os.Kind.values()) {
            Os.override(kind);
            assertEquals(kind, Os.current());
            assertEquals(kind == Os.Kind.MAC, Os.isMac());
            assertEquals(kind == Os.Kind.WINDOWS, Os.isWindows());
            assertEquals(kind == Os.Kind.LINUX, Os.isLinux());
        }
    }

    @Test
    void theDescriptionIsTheOsOwnName() {
        assertTrue(Os.description().startsWith(System.getProperty("os.name")));
        assertFalse(Os.description().isBlank());
    }
}
