package com.example.app.report;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import com.example.app.AppInfo;
import com.example.app.config.ConfigStore;
import com.example.app.ui.DebugLog;

/**
 * {@code <app> --report}: the problem report from the command line, for when the app cannot start.
 * Reads the settings and the last two session logs, redacts them, writes {@code problem-report.txt} in
 * the config folder and opens the prefilled issue.
 */
public final class ReportCommand {

    public static final String FILE = "problem-report.txt";

    private ReportCommand() {}

    /** Runs the command; returns the process exit code. */
    public static int run(Path configDir, PrintStream out, Predicate<String> browser) {
        return run(configDir, out, browser, Redactor.forThisMachine());
    }

    /** Runs the command with {@code redactor}: the tests give one for a made-up home, user and host. */
    static int run(Path configDir, PrintStream out, Predicate<String> browser, Redactor redactor) {
        ConfigStore store = new ConfigStore(configDir);
        store.reload();
        String log = read(configDir.resolve(DebugLog.PREVIOUS_FILE)) + read(configDir.resolve(DebugLog.FILE));
        String environment = redactor.redact(AppInfo.environment(configDir));
        String report = redactor.redact(ProblemReport.build(store.settings(), log));
        Path file = configDir.resolve(FILE);
        try {
            Files.createDirectories(configDir);
            Files.writeString(file, environment + "\n\n" + report + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            out.println("Could not write " + file + ": " + e.getMessage());
            return 1;
        }
        out.println("Wrote the redacted report to " + file);
        out.println("Read it before you submit: anything left in it becomes public.");
        String url = ProblemReport.issueUrl(environment, report);
        if (!ProblemReport.fitsInUrl(environment, report)) {
            out.println("The report is too long for a link: paste it from " + file + " into the issue.");
        }
        if (!browser.test(url)) out.println("Open this link to file the issue:\n" + url);
        return 0;
    }

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
