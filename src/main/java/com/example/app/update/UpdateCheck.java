package com.example.app.update;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

/** The decisions behind an update check, apart from the network so they are unit-tested. */
public final class UpdateCheck {

    /** At most once a day: something the user did not ask for should not ask more often. */
    public static final long INTERVAL_MS = 24L * 60 * 60 * 1000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private UpdateCheck() {}

    /** Reads a GitHub {@code releases/latest} payload; null for a draft, a pre-release or anything unusable. */
    public static @Nullable ReleaseInfo parseLatest(String json) {
        try {
            JsonNode tree = JSON.readTree(json);
            if (tree == null || !tree.isObject()) return null;
            if (tree.path("draft").asBoolean(false) || tree.path("prerelease").asBoolean(false)) return null;
            String tag = tree.path("tag_name").asText("");
            if (tag.isBlank()) return null;
            return new ReleaseInfo(normalize(tag), tree.path("html_url").asText(""));
        } catch (Exception e) {
            return null;
        }
    }

    /** Strips a leading {@code v}. */
    public static String normalize(String tag) {
        String t = tag.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }

    /**
     * Whether {@code latest} is newer than {@code current}. A {@code -SNAPSHOT} counts as the release
     * it leads to, so a development build is not told to "update" to the release it is ahead of.
     */
    public static boolean isNewer(String current, String latest) {
        String c = normalize(current);
        int snapshot = c.toUpperCase(Locale.ROOT).indexOf("-SNAPSHOT");
        if (snapshot >= 0) c = c.substring(0, snapshot);
        return compare(normalize(latest), c) > 0;
    }

    /** Compares dotted versions numerically (0.10.0 is newer than 0.9.0); a suffix sorts below the release. */
    public static int compare(String a, String b) {
        String[] left = core(a).split("\\.");
        String[] right = core(b).split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int cmp =
                    Integer.compare(number(i < left.length ? left[i] : "0"), number(i < right.length ? right[i] : "0"));
            if (cmp != 0) return cmp;
        }
        return Boolean.compare(a.indexOf('-') < 0, b.indexOf('-') < 0);
    }

    private static String core(String version) {
        int dash = version.indexOf('-');
        return dash < 0 ? version : version.substring(0, dash);
    }

    /** A segment that is not a number sorts lowest, so a malformed tag is never offered as an update. */
    private static int number(String segment) {
        try {
            return Integer.parseInt(segment.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Whether a day has passed since the last check. A future timestamp (the clock moved back) counts as due. */
    public static boolean isDue(long lastCheckMs, long nowMs) {
        return lastCheckMs <= 0 || lastCheckMs > nowMs || nowMs - lastCheckMs >= INTERVAL_MS;
    }
}
