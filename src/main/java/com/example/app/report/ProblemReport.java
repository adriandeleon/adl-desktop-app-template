package com.example.app.report;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.example.app.AppInfo;
import com.example.app.config.Settings;

/**
 * Builds a problem report and the prefilled GitHub issue link. Nothing is uploaded: the report is
 * shown, copied to the clipboard and opened as a new-issue form the user submits themselves.
 */
public final class ProblemReport {

    /** Past this length a URL is unreliable; the report then goes by clipboard instead. */
    static final int MAX_URL = 7500;

    /** How much of the Debug Log to include, from its end. */
    static final int LOG_TAIL = 4000;

    private ProblemReport() {}

    /** The report text (before redaction): settings summary and the tail of the Debug Log. */
    public static String build(Settings settings, String debugLog) {
        String log = debugLog.length() > LOG_TAIL ? "…" + debugLog.substring(debugLog.length() - LOG_TAIL) : debugLog;
        return "Settings: theme=" + settings.theme
                + ", language=" + (settings.language.isBlank() ? "system" : settings.language)
                + ", keymap=" + settings.keymap
                + ", updateCheck=" + settings.checkForUpdates
                + ", statusBar=" + settings.showStatusBar
                + ", sync=" + (settings.sync.repoUrl.isBlank() ? "off" : "on")
                + "\n\nDebug Log:\n" + (log.isBlank() ? "(empty)" : log.strip());
    }

    /**
     * The new-issue link with the {@code bug_report.yml} form's {@code environment} and {@code report}
     * fields filled. When that is too long, the report field asks for a paste from the clipboard.
     */
    public static String issueUrl(String environment, String report) {
        String full = url(environment, report);
        if (full.length() <= MAX_URL) return full;
        return url(environment, "(The report is on your clipboard: paste it here.)");
    }

    /** Whether the whole report fits in the link. */
    public static boolean fitsInUrl(String environment, String report) {
        return url(environment, report).length() <= MAX_URL;
    }

    private static String url(String environment, String report) {
        return AppInfo.NEW_ISSUE_URL + "?template=bug_report.yml&environment=" + encode(environment) + "&report="
                + encode(report);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
