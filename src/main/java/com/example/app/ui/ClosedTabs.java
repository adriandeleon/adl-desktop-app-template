package com.example.app.ui;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The files whose tabs the user closed in one window, newest first, for Reopen Closed Tab (TABS-06). Bounded
 * to twenty; closing a file again moves it to the top rather than listing it twice; each entry keeps where
 * the caret was. Kept in memory only: a window that closes forgets them. Pure, as in Editora.
 */
final class ClosedTabs {

    /** A closed file and where its caret was. */
    record Entry(Path path, int caret) {}

    static final int DEFAULT_CAPACITY = 20;

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final int capacity;

    ClosedTabs() {
        this(DEFAULT_CAPACITY);
    }

    ClosedTabs(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Records that {@code path}'s tab was closed with the caret at {@code caret}; a tab with no file is not. */
    void closed(@Nullable Path path, int caret) {
        if (path == null) return;
        forget(path);
        entries.addFirst(new Entry(path, Math.max(0, caret)));
        while (entries.size() > capacity) entries.removeLast();
    }

    /** The most recently closed file, removed from the list; null when there is none. */
    @Nullable
    Entry pop() {
        return entries.pollFirst();
    }

    /** Drops {@code path} from the list: it was opened again some other way. */
    void forget(Path path) {
        entries.removeIf(e -> e.path().equals(path));
    }

    int size() {
        return entries.size();
    }

    /** The entries, newest first. */
    List<Entry> entries() {
        return List.copyOf(entries);
    }
}
