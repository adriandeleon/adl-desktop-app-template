package com.example.app.report;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.app.AppInfo;
import com.example.app.config.Settings;
import com.example.app.ui.DebugLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTest {

    private final Redactor redactor = new Redactor("/home/ada", "ada", "lovelace-pc");

    @Test
    void redactsPrivateDetails() {
        String text = redactor.redact("""
                path /home/ada/.exampleapp/settings.json by ada on LOVELACE-PC
                token ghp_abcdefghijklmnopqrstuvwxyz0123 and github_pat_ABCDEFGHIJ_klmnopqrstuvwxyz
                Authorization: Bearer abc.def.ghijklmnop
                password=hunter2 api_key: xyz
                https://ada:secret@example.com/repo.git
                mail ada.l@example.org from 192.168.1.20 and 127.0.0.1
                adamant stays
                """);
        assertTrue(text.contains("path ~/.exampleapp/settings.json by <user> on <host>"), text);
        assertFalse(text.contains("ghp_"));
        assertFalse(text.contains("github_pat_"));
        assertFalse(text.contains("abc.def"));
        assertTrue(text.contains("password=<redacted>"));
        assertTrue(text.contains("api_key: <redacted>"));
        assertTrue(text.contains("https://<credentials>@example.com"));
        assertTrue(text.contains("<email>"));
        assertTrue(text.contains("<ip> and 127.0.0.1"));
        assertTrue(text.contains("adamant stays"));
        assertEquals("x", new Redactor("", "", "").redact("x"));
        assertNotNull(Redactor.forThisMachine());
    }

    @Test
    void buildsAReportWithTheLogTail() {
        Settings settings = new Settings();
        String report = ProblemReport.build(settings, "x".repeat(ProblemReport.LOG_TAIL + 10));
        assertTrue(report.startsWith("Settings: theme=SYSTEM, language=system, keymap=cua"));
        assertTrue(report.contains("sync=off"));
        assertTrue(report.contains("…" + "x".repeat(ProblemReport.LOG_TAIL)));
        settings.language = "de";
        settings.sync.repoUrl = "git@x:y.git";
        String other = ProblemReport.build(settings, " ");
        assertTrue(other.contains("language=de") && other.contains("sync=on") && other.contains("(empty)"));
    }

    @Test
    void issueLinkFillsTheFormOrFallsBackToTheClipboard() {
        String url = ProblemReport.issueUrl("env one", "short report");
        assertTrue(url.startsWith(AppInfo.NEW_ISSUE_URL + "?template=bug_report.yml&environment=env%20one&report="));
        assertTrue(ProblemReport.fitsInUrl("env", "short"));
        String longReport = "y".repeat(ProblemReport.MAX_URL);
        assertFalse(ProblemReport.fitsInUrl("env", longReport));
        String fallback = ProblemReport.issueUrl("env", longReport);
        assertTrue(fallback.length() <= ProblemReport.MAX_URL);
        assertTrue(fallback.contains("clipboard"));
    }

    @Test
    void browserCommandPerPlatform() {
        assertEquals(List.of("open", "u"), Browser.command("Mac OS X", "u"));
        assertEquals(List.of("rundll32", "url.dll,FileProtocolHandler", "u"), Browser.command("Windows 11", "u"));
        assertEquals(List.of("xdg-open", "u"), Browser.command("Linux", "u"));
    }

    @Test
    void reportCommandWritesTheFileAndOpensTheIssue(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(DebugLog.PREVIOUS_FILE), "crash in previous run\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> opened = new ArrayList<>();
        int code = ReportCommand.run(dir, new PrintStream(out, true, StandardCharsets.UTF_8), url -> {
            opened.add(url);
            return false;
        });
        assertEquals(0, code);
        String written = Files.readString(dir.resolve(ReportCommand.FILE));
        assertTrue(written.contains("crash in previous run"));
        assertEquals(1, opened.size());
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Open this link"));
    }

    @Test
    void theReportCommandWritesAReportWithNothingPrivateLeftInIt(@TempDir Path dir) throws Exception {
        // CLI-05, SET-15: what the last runs logged goes through the redactor before it is written or linked.
        Files.writeString(dir.resolve(DebugLog.PREVIOUS_FILE), """
                WARNING could not read /home/ada/.exampleapp/sync/repo on lovelace-pc for ada
                git push https://ada:s3cret-pass@github.com/ada/sync.git failed
                token ghp_abcdefghijklmnopqrstuvwxyz0123 from ada.lovelace@example.com at 192.168.1.20
                """);
        Files.writeString(
                dir.resolve(DebugLog.FILE), "INFO api_key=sk-live-123456 Authorization: Bearer abcdefghijklmnop\n");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<String> opened = new ArrayList<>();
        int code = ReportCommand.run(
                dir,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                url -> {
                    opened.add(url);
                    return true;
                },
                redactor);
        assertEquals(0, code);
        String written = Files.readString(dir.resolve(ReportCommand.FILE));
        String link = java.net.URLDecoder.decode(opened.getFirst(), StandardCharsets.UTF_8);
        for (String secret : List.of(
                "/home/ada",
                "lovelace-pc",
                "s3cret-pass",
                "ghp_abcdefghijklmnopqrstuvwxyz0123",
                "ada.lovelace@example.com",
                "192.168.1.20",
                "sk-live-123456",
                "abcdefghijklmnop",
                " ada ")) {
            assertFalse(written.contains(secret), "left in the report: " + secret);
            assertFalse(link.contains(secret), "left in the issue link: " + secret);
        }
        for (String mark :
                List.of("~/.exampleapp", "<host>", "<user>", "://<credentials>@", "<token>", "<email>", "<ip>")) {
            assertTrue(written.contains(mark), "no " + mark + " in:\n" + written);
        }
        assertTrue(written.contains("could not read"), "the log itself is kept");
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Read it before you submit"));
    }

    @Test
    void aLongReportSaysToPaste(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(DebugLog.FILE), "z ".repeat(5000));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(0, ReportCommand.run(dir, new PrintStream(out, true, StandardCharsets.UTF_8), url -> true));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("too long for a link"));
    }

    @Test
    void anUnwritableFolderFails(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("file");
        Files.writeString(file, "x");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(
                1,
                ReportCommand.run(file.resolve("sub"), new PrintStream(out, true, StandardCharsets.UTF_8), u -> true));
    }
}
