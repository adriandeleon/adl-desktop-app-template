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
 * read the repository is found once, recorded beside the clone ({@code account.json}: a schema version, the URL and the account; SET-09)
 * and used for that clone from then on, so a sync never asks which one again. Only an HTTPS remote on github.com
 * has an account to pin; SSH uses the user's keys.
 */
final class AccountPin {

    private static final Pattern HTTPS = Pattern.compile("^https://github\\.com/([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$");

    static final String FILE = "account.json";

    /** The file's schema: a newer one is kept aside and the account found again (SET-10). */
    static final int SCHEMA_VERSION = 1;

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

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

    /**
     * The account recorded for {@code url}, or null when none is, or one was for another URL. A file a newer
     * build wrote is kept aside ({@code account.json.v<n>.bak}, SET-10) and one that can't be read is ignored
     * (SET-11): either way the account is found again.
     */
    static @Nullable String pinned(Path syncDir, String url) {
        Path file = syncDir.resolve(FILE);
        try {
            if (!Files.isRegularFile(file)) return null;
            var tree = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            int version = tree.path("schemaVersion").asInt(0);
            if (version > SCHEMA_VERSION) {
                Files.move(
                        file,
                        file.resolveSibling(FILE + ".v" + version + ".bak"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return null;
            }
            String account = tree.path("account").asText("");
            return tree.path("url").asText("").equals(url.strip()) && !account.isBlank() ? account : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void record(Path syncDir, String url, String account) {
        try {
            var tree = JSON.createObjectNode()
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("url", url.strip())
                    .put("account", account);
            ConfigFiles.writeString(syncDir.resolve(FILE), JSON.writeValueAsString(tree) + "\n");
        } catch (IOException e) {
            // Not recorded: the next sync looks again, which costs a call and nothing else.
        }
    }
}
