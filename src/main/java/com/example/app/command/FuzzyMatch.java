package com.example.app.command;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Fuzzy matching for the palette: the query's characters in order, case-insensitive. Consecutive
 * characters and word starts score higher. The positions it returns are the ones it scored, so the
 * highlight and the ranking never disagree.
 */
public final class FuzzyMatch {

    /** A hit: higher {@code score} is better; {@code positions} are the matched indices in the text. */
    public record Match(int score, int[] positions) {}

    private FuzzyMatch() {}

    /** Scores {@code text} against {@code query}; null when the characters are not all there in order. */
    public static @Nullable Match match(String query, String text) {
        String q = query.toLowerCase(Locale.ROOT).replace(" ", "");
        if (q.isEmpty()) return new Match(0, new int[0]);
        String t = text.toLowerCase(Locale.ROOT);
        int[] positions = new int[q.length()];
        int score = 0;
        int from = 0;
        int previous = -2;
        for (int i = 0; i < q.length(); i++) {
            int found = t.indexOf(q.charAt(i), from);
            if (found < 0) return null;
            // Prefer a word start a little further on to a mid-word hit right here.
            int wordStart = nextWordStart(t, q.charAt(i), from);
            if (wordStart >= 0 && wordStart != found && previous != found - 1) found = wordStart;
            positions[i] = found;
            score += 1;
            if (found == previous + 1) score += 5;
            if (found == 0 || !Character.isLetterOrDigit(t.charAt(found - 1))) score += 8;
            previous = found;
            from = found + 1;
        }
        score -= positions[0];
        return new Match(score, positions);
    }

    private static int nextWordStart(String text, char c, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) == c && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))) return i;
        }
        return -1;
    }

    /**
     * A bonus when the query names the text: the whole query appears at a word start. It makes typing a
     * command's title outrank a scattered fuzzy hit.
     */
    public static int phraseBonus(String query, String text) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return 0;
        String t = text.toLowerCase(Locale.ROOT);
        int at = t.indexOf(q);
        while (at >= 0) {
            if (at == 0 || !Character.isLetterOrDigit(t.charAt(at - 1))) return 100;
            at = t.indexOf(q, at + 1);
        }
        return 0;
    }
}
