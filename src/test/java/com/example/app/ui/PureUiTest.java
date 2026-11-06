package com.example.app.ui;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import javafx.application.ColorScheme;

import com.example.app.config.ThemeChoice;
import com.example.app.i18n.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of the UI that are decisions, without a toolkit. */
class PureUiTest {

    @Test
    void messageLogKeepsNewestFirstMergesRepeatsAndCountsErrors() {
        Messages.init("en");
        MessageLog log = new MessageLog();
        List<MessageLog.Entry> heard = new ArrayList<>();
        log.addListener(heard::add);
        LocalTime t = LocalTime.of(10, 0);
        log.add(" ", MessageLog.Severity.INFO, t);
        log.add("one", MessageLog.Severity.INFO, t);
        log.add("bad", MessageLog.Severity.ERROR, t);
        log.add("bad", MessageLog.Severity.ERROR, t.plusSeconds(1));
        log.add("bad", MessageLog.Severity.ERROR, t.plusSeconds(10));
        assertEquals(
                List.of("bad", "bad", "one"),
                log.entries().stream().map(MessageLog.Entry::text).toList());
        assertEquals(2, log.unreadErrors());
        assertEquals(4, heard.size());
        log.markRead();
        assertEquals(0, log.unreadErrors());
        for (int i = 0; i < MessageLog.MAX_ENTRIES + 3; i++)
            log.add("m" + i, MessageLog.Severity.ERROR, t.plusMinutes(i));
        assertEquals(MessageLog.MAX_ENTRIES, log.entries().size());
        assertEquals(MessageLog.MAX_ENTRIES + 3, log.unreadErrors());
        log.clear();
        assertTrue(log.entries().isEmpty());
        assertEquals(0, log.unreadErrors());
    }

    @Test
    void severityComesFromTheKey() {
        assertEquals(MessageLog.Severity.ERROR, MessageLog.severityOf("status.exportFailed"));
        assertEquals(MessageLog.Severity.ERROR, MessageLog.severityOf("status.uncaughtError"));
        assertEquals(MessageLog.Severity.WARNING, MessageLog.severityOf("status.config.cannotReadNewer"));
        assertEquals(MessageLog.Severity.WARNING, MessageLog.severityOf("status.sync.invalidUrl"));
        assertEquals(MessageLog.Severity.INFO, MessageLog.severityOf("status.ready"));
    }

    @Test
    void settingsSearchRanksTitlesAboveDescriptions() {
        var title = new SettingsSearch.Entry("Sync automatically", "", "Sync", "");
        var desc = new SettingsSearch.Entry("Branch", "The branch your synced files are kept on", "Sync", "");
        var keyword = new SettingsSearch.Entry("Theme", "", "Appearance", "dark light");
        assertTrue(SettingsSearch.score("sync", title) > SettingsSearch.score("sync", desc));
        assertTrue(SettingsSearch.score("dark", keyword) > 0);
        assertEquals(0, SettingsSearch.score("zebra", title));
        assertEquals(0, SettingsSearch.score("sync zebra", title));
        assertEquals(1, SettingsSearch.score("  ", title));
        assertEquals(3, SettingsSearch.field("sync", "sync now"));
        assertEquals(2, SettingsSearch.field("syn", "sync now"));
        assertEquals(1, SettingsSearch.field("ync", "sync now"));
        assertEquals(0, SettingsSearch.field("x", "sync now"));
    }

    @Test
    void agoInWords() {
        Messages.init("en");
        long now = 10_000_000_000L;
        assertEquals("just now", Ago.format(now - 30_000, now));
        assertEquals("5 min ago", Ago.format(now - 5 * 60_000, now));
        assertEquals("3 h ago", Ago.format(now - 3 * 3_600_000, now));
        assertEquals("4 d ago", Ago.format(now - 4 * 86_400_000L, now));
    }

    @Test
    void darkFollowsTheChoiceOrTheSystem() {
        assertTrue(Themes.isDark(ThemeChoice.DARK, ColorScheme.LIGHT));
        assertFalse(Themes.isDark(ThemeChoice.LIGHT, ColorScheme.DARK));
        assertTrue(Themes.isDark(ThemeChoice.SYSTEM, ColorScheme.DARK));
        assertFalse(Themes.isDark(ThemeChoice.SYSTEM, ColorScheme.LIGHT));
    }
}
