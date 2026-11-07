package com.example.app.ui;

import com.example.app.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The window's visibility and enablement rules, without a toolkit (QUAL-05). */
class ChromeTest {

    @BeforeEach
    void english() {
        Messages.init("en");
    }

    @Test
    void theHeaderBarIsUsedOnlyWithMenusInTheWindowTheSettingOnAndPlatformSupport() {
        assertTrue(Chrome.usesHeaderBar(false, true, true));
        assertFalse(Chrome.usesHeaderBar(true, true, true), "macOS screen menu bar");
        assertFalse(Chrome.usesHeaderBar(false, false, true), "setting off");
        assertFalse(Chrome.usesHeaderBar(false, true, false), "EXTENDED unsupported");
    }

    @Test
    void eachGateSaysWhyACommandCannotRun() {
        assertNull(Chrome.syncNowUnavailable(true));
        assertTrue(Chrome.syncNowUnavailable(false).contains("not set up"));
        assertNull(Chrome.checkForUpdatesUnavailable(false));
        assertTrue(Chrome.checkForUpdatesUnavailable(true).contains("already running"));
        assertNull(Chrome.debugLogRefreshUnavailable(true));
        assertEquals(Messages.tr("palette.disabled.debugLogClosed"), Chrome.debugLogRefreshUnavailable(false));
    }
}
