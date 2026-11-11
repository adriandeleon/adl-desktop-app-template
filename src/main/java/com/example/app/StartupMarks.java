package com.example.app;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase marks of a start (PERF-19): how long after the process began each phase ended, in milliseconds: {@code
 * main}, {@code prepared} (the settings read), {@code window} (shown) and {@code first-paint}. The Debug Log gets
 * them on every start; {@code scripts/measure-startup.sh} reads them from a file ({@code -D<app>.startupMarks})
 * over several cold launches and takes the median.
 */
public final class StartupMarks {

    /** {@code -D<app>.startupMarks=<file>}: write the marks there after the first paint, then exit. */
    public static final String PROPERTY = AppInfo.ID + ".startupMarks";

    /** A phase and how long after the process began it ended. */
    public record Mark(String phase, long millis) {}

    private static final Instant START =
            ProcessHandle.current().info().startInstant().orElseGet(Instant::now);

    private static final List<Mark> MARKS = new ArrayList<>();

    private StartupMarks() {}

    /** Records the end of {@code phase} now. */
    public static synchronized void mark(String phase) {
        MARKS.add(new Mark(phase, Math.max(0, Instant.now().toEpochMilli() - START.toEpochMilli())));
    }

    public static synchronized List<Mark> marks() {
        return List.copyOf(MARKS);
    }

    /** {@code main 120 ms · prepared 340 ms · …}: the Debug Log's line. Pure. */
    public static String line(List<Mark> marks) {
        return marks.stream().map(m -> m.phase() + " " + m.millis() + " ms").collect(Collectors.joining(" · "));
    }

    /** {@code phase millis}, a line each: the file the measuring script reads. Pure. */
    public static String file(List<Mark> marks) {
        return marks.stream().map(m -> m.phase() + " " + m.millis() + "\n").collect(Collectors.joining());
    }
}
