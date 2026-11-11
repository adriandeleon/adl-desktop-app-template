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
import org.jspecify.annotations.Nullable;

/**
 * {@code <app> --report}: the problem report from the command line, for when the app cannot start.
 * Reads the settings and the last two session logs, redacts them, writes {@code problem-report.txt} in
 * the current folder (the temp folder when that one can't be written), prints its path and opens the
 * prefilled issue (CLI-05).
 */
public final class ReportCommand {

    public static final String FILE = "problem-report.txt";

    private ReportCommand() {}

    /** Runs the command; returns the process exit code. */
    public static int run(Path configDir, Path here, PrintStream out, Predicate<String> browser) {
        return run(configDir, here, out, browser, Redactor.forThisMachine());
    }

    /** Runs the command with {@code redactor}: the tests give one for a made-up home, user and host. */
    static int run(Path configDir, Path here, PrintStream out, Predicate<String> browser, Redactor redactor) {
        ConfigStore store = new ConfigStore(configDir);
        store.reload();
        String log = read(configDir.resolve(DebugLog.PREVIOUS_FILE)) + read(configDir.resolve(DebugLog.FILE));
        String environment = redactor.redact(AppInfo.environment(configDir));
        String report = redactor.redact(ProblemReport.build(store.settings(), log));
        String text = environment + "\n\n" + report + "\n";
        Path file = write(here.resolve(FILE), text);
        if (file == null)
            file = write(Path.of(System.getProperty("java.io.tmpdir")).resolve(FILE), text);
        if (file == null) {
            out.println("Could not write " + FILE + " in " + here + " or in the temp folder.");
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

    /** Writes the report; the file's path, or null when its folder can't be written. */
    private static @Nullable Path write(Path file, String text) {
        try {
            return Files.writeString(file, text, StandardCharsets.UTF_8).toAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    private static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }
}
