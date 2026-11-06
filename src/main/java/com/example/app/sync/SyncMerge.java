package com.example.app.sync;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/**
 * A three-way, per-entry merge. {@code base} is what both sides had at the last sync. An entry this
 * machine changed keeps this machine's value (this machine wins); otherwise the remote's value,
 * including its deletions, is taken. The other side's version stays in the repository history.
 */
public final class SyncMerge {

    private SyncMerge() {}

    public static Map<String, String> merge(
            Map<String, String> base, Map<String, String> local, Map<String, String> remote) {
        TreeSet<String> keys = new TreeSet<>(base.keySet());
        keys.addAll(local.keySet());
        keys.addAll(remote.keySet());
        Map<String, String> merged = new TreeMap<>();
        for (String key : keys) {
            @Nullable String mine = local.get(key);
            @Nullable String chosen = Objects.equals(mine, base.get(key)) ? remote.get(key) : mine;
            if (chosen != null) merged.put(key, chosen);
        }
        return merged;
    }
}
