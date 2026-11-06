package com.example.app.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckTest {

    @Test
    void parsesTheLatestRelease() {
        ReleaseInfo info = UpdateCheck.parseLatest(
                "{\"tag_name\":\"v1.2.0\",\"html_url\":\"https://x/r\",\"assets\":[{\"name\":\"n\"}]}");
        assertEquals(new ReleaseInfo("1.2.0", "https://x/r"), info);
    }

    @Test
    void ignoresDraftsPrereleasesAndJunk() {
        assertNull(UpdateCheck.parseLatest("{\"tag_name\":\"v2\",\"draft\":true}"));
        assertNull(UpdateCheck.parseLatest("{\"tag_name\":\"v2\",\"prerelease\":true}"));
        assertNull(UpdateCheck.parseLatest("{\"tag_name\":\"  \"}"));
        assertNull(UpdateCheck.parseLatest("[1,2]"));
        assertNull(UpdateCheck.parseLatest("not json"));
        assertNull(UpdateCheck.parseLatest(""));
    }

    @Test
    void comparesVersionsNumerically() {
        assertTrue(UpdateCheck.isNewer("0.9.0", "v0.10.0"));
        assertFalse(UpdateCheck.isNewer("0.10.0", "0.9.0"));
        assertFalse(UpdateCheck.isNewer("1.0.0-SNAPSHOT", "1.0.0"));
        assertTrue(UpdateCheck.isNewer("1.0.0-SNAPSHOT", "1.0.1"));
        assertTrue(UpdateCheck.isNewer("1.0.0-rc1", "1.0.0"));
        assertFalse(UpdateCheck.isNewer("1.0.0", "1.0.x"));
        assertEquals(0, UpdateCheck.compare("1.0", "1.0.0"));
        assertTrue(UpdateCheck.compare("1.0.0-rc1", "1.0.0") < 0);
        assertEquals("1.0", UpdateCheck.normalize(" V1.0 "));
    }

    @Test
    void dueOncePerDay() {
        long day = UpdateCheck.INTERVAL_MS;
        assertTrue(UpdateCheck.isDue(0, 5));
        assertFalse(UpdateCheck.isDue(10, 10 + day - 1));
        assertTrue(UpdateCheck.isDue(10, 10 + day));
        assertTrue(UpdateCheck.isDue(100, 50)); // the clock went back
    }
}
