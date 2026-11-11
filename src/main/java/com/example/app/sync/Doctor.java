package com.example.app.sync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The Doctor (DIAG-07): what the app depends on outside itself, each with a status and, for a failure, what to
 * do about it and where to read more. git (Settings Sync), gh (Create on GitHub and the account a clone uses),
 * the Java and JavaFX the app runs on, and a config folder it can write. Blocking: run off the FX thread.
 */
public final class Doctor {

    /** A row's verdict: works, works without something, or does not work. */
    public enum Status {
        OK,
        WARNING,
        FAILED
    }

    /** One row: what was checked, how it went, what was found, and the fix and its link for a failure. */
    public record Check(
            String name,
            Status status,
            String detail,
            @Nullable String fix,
            @Nullable String link) {}

    /** The oldest Java the app runs on. */
    static final int JAVA = 25;

    private Doctor() {}

    /** Every check, in order. */
    public static List<Check> run(Path configDir, Git git, GitHubCli gh) {
        List<Check> checks = new ArrayList<>();
        Path here = Path.of(System.getProperty("java.io.tmpdir"));
        Git.Result gitVersion = git.run(here, "--version");
        checks.add(
                gitVersion.ok()
                        ? new Check("git", Status.OK, gitVersion.output(), null, null)
                        : new Check(
                                "git",
                                Status.FAILED,
                                Messages.tr("doctor.notFound"),
                                Messages.tr("doctor.git.fix"),
                                "https://git-scm.com/downloads"));
        String ghVersion = gh.version();
        checks.add(
                ghVersion != null
                        ? new Check("gh", Status.OK, ghVersion, null, null)
                        : new Check(
                                "gh",
                                Status.WARNING,
                                Messages.tr("doctor.notFound"),
                                Messages.tr("doctor.gh.fix"),
                                "https://cli.github.com/"));
        int feature = Runtime.version().feature();
        checks.add(new Check(
                "Java",
                feature >= JAVA ? Status.OK : Status.FAILED,
                System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
                feature >= JAVA ? null : Messages.tr("doctor.java.fix", JAVA),
                feature >= JAVA ? null : "https://adoptium.net/"));
        String javafx = System.getProperty("javafx.runtime.version");
        checks.add(new Check(
                "JavaFX",
                javafx == null ? Status.WARNING : Status.OK,
                javafx == null ? Messages.tr("doctor.notStarted") : javafx,
                null,
                null));
        boolean writable = Files.isDirectory(configDir) && Files.isWritable(configDir);
        checks.add(new Check(
                Messages.tr("doctor.config"),
                writable ? Status.OK : Status.FAILED,
                configDir.toString(),
                writable ? null : Messages.tr("doctor.config.fix"),
                null));
        return List.copyOf(checks);
    }
}
