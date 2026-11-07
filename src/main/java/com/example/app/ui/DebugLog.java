package com.example.app.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.example.app.AppInfo;
import org.jspecify.annotations.Nullable;

/**
 * Every {@code java.util.logging} record and uncaught exception, kept where a user can reach it: a
 * packaged app has no console.
 *
 * <p>A bounded in-memory buffer backs the Debug Log window ("it is misbehaving now"); a session file
 * in the config folder covers "it died". The previous session's file is kept as
 * {@code <app>-session.prev.log}, so a crash is still readable on the next launch. The file is locked
 * for the life of the process: a second instance on the same folder writes to a pid-named sibling
 * rather than truncating the first one's log.
 */
public final class DebugLog {

    public static final String FILE = AppInfo.ID + "-session.log";
    public static final String PREVIOUS_FILE = AppInfo.ID + "-session.prev.log";

    static final int MAX_ENTRIES = 2000;

    /**
     * The session file stops growing here, with a note saying so (PERF-11): a runaway logger must not fill
     * the disk. The Debug Log window keeps the newest lines either way.
     */
    static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    /**
     * The lock is one byte far past the end of the file, not the whole file: another instance still
     * sees it, but on Windows a whole-file lock also stops anyone (an editor, a second launch's
     * {@code --report}) from reading the log while the app runs.
     */
    static final long LOCK_POSITION = Long.MAX_VALUE - 1;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Deque<String> ENTRIES = new ArrayDeque<>();

    private static @Nullable FileChannel channel;
    private static @Nullable FileLock lock;
    private static @Nullable Path file;
    private static @Nullable Handler handler;
    private static int dropped;
    private static long fileBytes;
    private static boolean fileFull;
    private static volatile @Nullable Consumer<Throwable> onUncaught;

    private DebugLog() {}

    /**
     * Captures every logging record the loggers emit and installs the uncaught exception handler. The first thing
     * {@code main} does after the AWT guard, so a failure during start-up is captured too.
     */
    public static synchronized void install() {
        Logger root = Logger.getLogger("");
        if (handler == null) {
            Handler h = new Handler() {
                @Override
                public void publish(LogRecord record) {
                    // Everything the (already level-gated) loggers emit, FINE included (DIAG-02).
                    append(format(record));
                }

                @Override
                public void flush() {}

                @Override
                public void close() {}
            };
            h.setLevel(Level.ALL);
            root.addHandler(h);
            handler = h;
        }
        Thread.setDefaultUncaughtExceptionHandler(DebugLog::uncaught);
    }

    /** Routes an uncaught exception to the log and, once the window is up, the status bar. Never a dialog. */
    static void uncaught(Thread thread, Throwable error) {
        append(stamp("UNCAUGHT on " + thread.getName() + ": " + stackTrace(error)));
        Consumer<Throwable> listener = onUncaught;
        if (listener != null) listener.accept(error);
    }

    /** Who hears about uncaught exceptions (the status bar), or null. */
    public static void onUncaught(@Nullable Consumer<Throwable> listener) {
        onUncaught = listener;
    }

    /** Mirrors the log to the session file in {@code configDir}, keeping the last one as the previous file. */
    public static synchronized void attachFile(Path configDir) {
        detachFile();
        try {
            Files.createDirectories(configDir);
            if (!claim(configDir.resolve(FILE), true)) {
                claim(
                        configDir.resolve(AppInfo.ID + "-session-"
                                + ProcessHandle.current().pid() + ".log"),
                        false);
            }
        } catch (IOException e) {
            detachFile(); // a log that cannot be written is no reason to fail a launch
        }
        for (String line : ENTRIES) write(line);
    }

    private static boolean claim(Path candidate, boolean rotate) throws IOException {
        FileChannel opened = FileChannel.open(
                candidate, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
        FileLock held;
        try {
            held = opened.tryLock(LOCK_POSITION, 1, false);
        } catch (OverlappingFileLockException e) {
            held = null;
        }
        if (held == null) {
            opened.close();
            return false;
        }
        if (rotate && opened.size() > 0) {
            Path parent = candidate.getParent();
            if (parent != null) {
                // Read through the channel that holds the lock: Windows refuses any other reader of a
                // locked file, so copying the path failed there and the session log never attached.
                // The stream is not closed: that would close the channel and release the lock.
                opened.position(0);
                Files.copy(
                        java.nio.channels.Channels.newInputStream(opened),
                        parent.resolve(PREVIOUS_FILE),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        opened.truncate(0);
        fileBytes = 0;
        fileFull = false;
        channel = opened;
        lock = held;
        file = candidate;
        return true;
    }

    /** Releases the session file. */
    public static synchronized void detachFile() {
        try {
            if (lock != null) lock.release();
            if (channel != null) channel.close();
        } catch (IOException e) {
            // nothing left to do with a channel that will not close
        }
        lock = null;
        channel = null;
        file = null;
    }

    /** The session file, or null when there is none. */
    public static synchronized @Nullable Path file() {
        return file;
    }

    static synchronized void append(String line) {
        ENTRIES.addLast(line);
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeFirst();
            dropped++;
        }
        write(line);
    }

    private static void write(String line) {
        FileChannel out = channel;
        if (out == null || fileFull) return;
        byte[] encoded = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (fileBytes + encoded.length > MAX_FILE_BYTES) {
            fileFull = true;
            encoded = (stamp("The session log reached " + MAX_FILE_BYTES / (1024 * 1024)
                                    + " MB; later lines are only in the Debug Log window.")
                            + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8);
        }
        fileBytes += encoded.length;
        try {
            ByteBuffer bytes = ByteBuffer.wrap(encoded);
            out.position(out.size());
            while (bytes.hasRemaining()) out.write(bytes);
        } catch (IOException e) {
            // the in-memory copy is still there
        }
    }

    /** Everything kept this session, oldest first, after a note of how many older lines were cut. */
    public static synchronized String text() {
        String kept = String.join(System.lineSeparator(), ENTRIES);
        if (dropped == 0) return kept;
        return "… " + dropped + " earlier lines are not kept here; the session file has them." + System.lineSeparator()
                + kept;
    }

    /** How many of the oldest lines were cut to keep the log within {@link #MAX_ENTRIES}. */
    static synchronized int dropped() {
        return dropped;
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        dropped = 0;
    }

    static String format(LogRecord record) {
        String logger = record.getLoggerName() == null ? "?" : record.getLoggerName();
        String line = stamp(record.getLevel().getName() + " " + logger.substring(logger.lastIndexOf('.') + 1) + " — "
                + record.getMessage());
        Throwable thrown = record.getThrown();
        return thrown == null ? line : line + System.lineSeparator() + stackTrace(thrown);
    }

    private static String stamp(String message) {
        return LocalTime.now().format(TIME) + "  " + message;
    }

    private static String stackTrace(Throwable error) {
        StringWriter out = new StringWriter();
        error.printStackTrace(new PrintWriter(out));
        return out.toString().stripTrailing();
    }
}
