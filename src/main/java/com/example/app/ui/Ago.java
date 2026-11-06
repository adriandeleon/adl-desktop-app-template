package com.example.app.ui;

import com.example.app.i18n.Messages;

/** "just now", "5 min ago", "2 h ago", "3 d ago". */
final class Ago {

    private Ago() {}

    static String format(long thenMs, long nowMs) {
        long minutes = Math.max(0, (nowMs - thenMs) / 60_000);
        if (minutes < 1) return Messages.tr("time.justNow");
        if (minutes < 60) return Messages.tr("time.minutesAgo", minutes);
        if (minutes < 48 * 60) return Messages.tr("time.hoursAgo", minutes / 60);
        return Messages.tr("time.daysAgo", minutes / (24 * 60));
    }
}
