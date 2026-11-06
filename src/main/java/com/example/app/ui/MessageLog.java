package com.example.app.ui;

import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The session's status-bar messages, newest first, bounded. In memory only: the Debug Log is the
 * persistent record. No JavaFX here; {@link MessageLogView} is the view.
 */
public final class MessageLog {

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public record Entry(LocalTime time, String text, Severity severity) {}

    static final int MAX_ENTRIES = 200;
    static final long REPEAT_MILLIS = 2000;

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final List<Consumer<Entry>> listeners = new ArrayList<>();
    private int unreadErrors;

    /**
     * Severity from the message key: one ending in {@code Failed} or {@code Error} is an error, one with
     * {@code cannot} or {@code invalid} a warning. Name new keys accordingly.
     */
    public static Severity severityOf(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith("failed") || lower.endsWith("error")) return Severity.ERROR;
        if (lower.contains("cannot") || lower.contains("invalid")) return Severity.WARNING;
        return Severity.INFO;
    }

    /**
     * Records a message. A blank one is not recorded. The same text again within {@link #REPEAT_MILLIS}
     * moves to the top as one entry and counts as unread once.
     */
    public void add(String text, Severity severity, LocalTime now) {
        if (text.isBlank()) return;
        Entry newest = entries.peekFirst();
        boolean repeat = newest != null
                && newest.text().equals(text)
                && java.time.Duration.between(newest.time(), now).toMillis() < REPEAT_MILLIS;
        if (repeat) entries.removeFirst();
        Entry entry = new Entry(now, text, severity);
        entries.addFirst(entry);
        while (entries.size() > MAX_ENTRIES) entries.removeLast();
        if (severity == Severity.ERROR && !repeat) unreadErrors++;
        for (Consumer<Entry> listener : List.copyOf(listeners)) listener.accept(entry);
    }

    /** Newest first. */
    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    /** Errors since the log was last opened. Never lost when the entry itself falls off the end. */
    public int unreadErrors() {
        return unreadErrors;
    }

    public void markRead() {
        unreadErrors = 0;
    }

    public void clear() {
        entries.clear();
        unreadErrors = 0;
    }

    public void addListener(Consumer<Entry> listener) {
        listeners.add(listener);
    }
}
