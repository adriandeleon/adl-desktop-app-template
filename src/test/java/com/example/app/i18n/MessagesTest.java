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

    /** INTL-14: the four cases of the formatting locale. */
    @Test
    void theFormattingLocaleKeepsTheSystemsRegion() {
        java.util.Locale mexico = java.util.Locale.of("es", "MX");
        java.util.Locale japan = java.util.Locale.of("ja", "JP");
        // None chosen, a catalog for the system's language: the system's locale whole.
        assertEquals(mexico, Messages.locale("", mexico));
        // None chosen, no catalog: English menus, the system's locale whole.
        assertEquals(japan, Messages.locale("", japan));
        assertEquals("en", Messages.resolve("", "ja"));
        // The system's own language chosen: its region kept.
        assertEquals(mexico, Messages.locale("es", mexico));
        // Another language chosen: the bare language.
        assertEquals(java.util.Locale.of("de"), Messages.locale("de", mexico));
    }

    /** INTL-12: no "(s)" beside a count, and every choice pattern parses, in every catalog. */
    @Test
    void countsAreWordedForTheCountInEveryLanguage() {
        for (String language : Messages.available().keySet()) {
            Messages.bundle(language).forEach((key, value) -> {
                if (!Messages.isPattern(value)) return;
                assertFalse(value.contains("(s)"), language + ": " + key + " writes (s)");
                if (value.contains(",choice,")) {
                    new java.text.MessageFormat(value).format(new Object[] {1, 1, 1, 1});
                    new java.text.MessageFormat(value).format(new Object[] {3, 3, 3, 3});
                }
            });
        }
        org.junit.jupiter.api.Assertions.assertTrue(
                Messages.tr("status.configExported", 1, "a.zip").startsWith("Exported 1 file to"));
    }

    /** INTL-13: a chord's modifiers are never translated. */
    @Test
    void noCatalogTranslatesAModifier() {
        java.util.regex.Pattern translated = java.util.regex.Pattern.compile("\\b(Strg|Umschalt|Maj|Mayús|Maiusc)\\+");
        for (String language : Messages.available().keySet()) {
            Messages.bundle(language)
                    .forEach((key, value) -> assertFalse(
                            translated.matcher(value).find(),
                            language + ": " + key + " translates a modifier: " + value));
        }
    }
}
