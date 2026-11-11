package com.example.app.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.app.config.ConfigFiles;
import org.jspecify.annotations.Nullable;

/**
 * Which GitHub account a sync clone uses (SET-18): on a computer signed in to two accounts, the first that can
 * read the repository is found once, recorded beside the clone ({@code account.txt}: the URL, then the account)
 * and used for that clone from then on, so a sync never asks which one again. Only an HTTPS remote on github.com
 * has an account to pin; SSH uses the user's keys.
 */
final class AccountPin {

    private static final Pattern HTTPS = Pattern.compile("^https://github\\.com/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$");

    static final String FILE = "account.txt";

    private AccountPin() {}

    /** {@code owner/name} of an HTTPS github.com remote, or null for any other. Pure. */
    static @Nullable String ownerRepo(String url) {
        Matcher m = HTTPS.matcher(url.strip());
        return m.matches() ? m.group(1) + "/" + m.group(2) : null;
    }

    /**
     * The environment git runs the clone with: the pinned account's token, found and recorded on first use;
     * empty for a remote with no account to pin or when no signed-in account can read it.
     */
    static Map<String, String> env(Path syncDir, String url, GitHubCli gh) {
        String ownerRepo = ownerRepo(url);
        if (ownerRepo == null) return Map.of();
        String account = pinned(syncDir, url);
        if (account == null) {
            for (String candidate : gh.accounts()) {
                String token = gh.token(candidate);
                if (token != null && gh.canRead(token, ownerRepo)) {
                    record(syncDir, url, candidate);
                    return Map.of("GH_TOKEN", token);
                }
            }
            return Map.of();
        }
        String token = gh.token(account);
        return token == null ? Map.of() : Map.of("GH_TOKEN", token);
    }

    /** The account recorded for {@code url}, or null when none is, or one was for another URL. */
    static @Nullable String pinned(Path syncDir, String url) {
        try {
            Path file = syncDir.resolve(FILE);
            if (!Files.isRegularFile(file)) return null;
            String[] lines = Files.readString(file, StandardCharsets.UTF_8).split("\\R");
            return lines.length >= 2 && lines[0].strip().equals(url.strip()) && !lines[1].isBlank()
                    ? lines[1].strip()
                    : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void record(Path syncDir, String url, String account) {
        try {
            ConfigFiles.writeString(syncDir.resolve(FILE), url.strip() + "\n" + account + "\n");
        } catch (IOException e) {
            // Not recorded: the next sync looks again, which costs a call and nothing else.
        }
    }
}
