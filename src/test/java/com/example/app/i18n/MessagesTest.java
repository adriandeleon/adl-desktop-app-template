package com.example.app.i18n;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Message parity: every catalog has exactly the English keys, every value real, every pattern valid. */
class MessagesTest {

    private static final Pattern SINGLE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");

    @AfterEach
    void english() {
        Messages.init("en");
    }

    @Test
    void sixLanguagesAtLeast() {
        assertTrue(Messages.available().size() >= 6);
        assertEquals(
                Set.of("en", "es", "pt", "fr", "it", "de"), Messages.available().keySet());
    }

    @Test
    void everyCatalogHasEveryKey() {
        Set<String> english = Messages.bundle("en").keySet();
        assertTrue(english.size() > 100);
        for (String lang : Messages.available().keySet()) {
            Set<String> keys = Messages.bundle(lang).keySet();
            Set<String> missing = new TreeSet<>(english);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(english);
            assertTrue(missing.isEmpty(), lang + " is missing " + missing);
            assertTrue(extra.isEmpty(), lang + " has keys English does not: " + extra);
        }
    }

    @Test
    void everyCommandHasADescription() {
        for (String key : Messages.bundle("en").keySet()) {
            if (key.startsWith("command.") && !key.endsWith(".desc")) {
                assertTrue(Messages.has(key + ".desc"), key + " has no .desc");
            }
        }
    }

    @Test
    void apostrophesFollowTheValueRule() {
        for (String lang : Messages.available().keySet()) {
            for (Map.Entry<String, String> e : Messages.bundle(lang).entrySet()) {
                String v = e.getValue();
                assertFalse(v.isBlank(), lang + " " + e.getKey() + " is blank");
                if (Messages.isPattern(v)) {
                    assertFalse(
                            SINGLE_APOSTROPHE.matcher(v).find(), lang + " " + e.getKey() + ": double the apostrophe");
                } else {
                    assertFalse(v.contains("''"), lang + " " + e.getKey() + ": single apostrophes in plain text");
                }
            }
        }
    }

    @Test
    void translationsAreNotEnglishCopies() {
        Map<String, String> en = Messages.bundle("en");
        for (String lang : Set.of("es", "pt", "fr", "it", "de")) {
            Map<String, String> other = Messages.bundle(lang);
            assertNotEquals(en.get("command.app.settings.desc"), other.get("command.app.settings.desc"), lang);
            assertNotEquals(en.get("settings.sync.notes"), other.get("settings.sync.notes"), lang);
        }
    }

    @Test
    void trFallsBackAndFormats() {
        Messages.init("fr");
        assertEquals("fr", Messages.current());
        assertEquals("Réglages…", Messages.tr("command.app.settings"));
        assertEquals("Version 1.2", Messages.tr("about.version", "1.2"));
        assertEquals("no.such.key", Messages.tr("no.such.key"));
        Messages.init("xx");
        assertEquals("en", Messages.current());
        assertEquals("Exported 1200 files to x", Messages.tr("status.configExported", 1200, "x"));
        assertEquals("{0 broken", Messages.render("{0 broken", new Object[] {1}));
        assertEquals("{0}", Messages.render("{0}", null));
    }

    @Test
    void resolvePrefersTheChoiceThenTheSystem() {
        assertEquals("de", Messages.resolve("de", "es"));
        assertEquals("es", Messages.resolve("", "es"));
        assertEquals("en", Messages.resolve("", "ja"));
    }
}
