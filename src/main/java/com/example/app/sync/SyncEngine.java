package com.example.app.sync;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.example.app.AppInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jspecify.annotations.Nullable;

/**
 * Settings Sync through a Git repository the user owns: no server and no account of ours.
 *
 * <p>The repository is cloned into {@code <config>/sync/repo}. What syncs is the user's own key
 * bindings ({@code keybindings.json}); which keymap is in use, window placement and every other
 * machine-local preference stay on each computer. {@code base.json} remembers the last synced state
 * for the three-way {@link SyncMerge}. Blocking: callers run it off the FX thread.
 */
public final class SyncEngine {

    public static final String DATA_FILE = "keybindings.json";

    /** What a sync did: the merged bindings to apply locally, or the reason it failed. */
    public record Result(
            @Nullable Map<String, String> merged, @Nullable String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private static final Pattern CREDENTIALS = Pattern.compile("^[a-z][a-z0-9+.-]*://[^/@]*:[^/@]*@.*");
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final TypeReference<TreeMap<String, String>> MAP = new TypeReference<>() {};

    private final Path syncDir;
    private final Git git;

    public SyncEngine(Path syncDir, Git git) {
        this.syncDir = syncDir;
        this.git = git;
    }

    public Path repoDir() {
        return syncDir.resolve("repo");
    }

    /** Why a repository URL is refused, or null when it is usable. A password or token in it is refused. */
    public static @Nullable String checkUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return "empty";
        if (CREDENTIALS.matcher(trimmed).matches()) return "credentials";
        return null;
    }

    /**
     * Why {@code branch} cannot be a sync branch, as a catalog suffix ({@code empty}, {@code invalid}), or
     * null when it can: Git's own rules for a branch name ({@code git check-ref-format --branch}).
     */
    public static @Nullable String checkBranch(String branch) {
        String b = branch.trim();
        if (b.isEmpty()) return "empty";
        boolean bad = b.startsWith("-")
                || b.startsWith("/")
                || b.endsWith("/")
                || b.endsWith(".")
                || b.endsWith(".lock")
                || b.contains("..")
                || b.contains("//")
                || b.contains("@{")
                || b.equals("@")
                || b.contains("/.")
                || b.startsWith(".")
                || b.chars().anyMatch(c -> c < 0x20 || c == 0x7f || " ~^:?*[\\".indexOf(c) >= 0);
        return bad ? "invalid" : null;
    }

    /** One sync of {@code local} against the repository's {@code branch}. */
    public Result sync(String url, String branch, Map<String, String> local) {
        String refused = checkUrl(url);
        if (refused != null) return new Result(null, "url." + refused);
        try {
            Path repo = repoDir();
            Git.Result step = prepare(repo, url.trim(), branch);
            if (!step.ok()) return new Result(null, step.output());

            Map<String, String> remote = read(repo.resolve(DATA_FILE));
            Map<String, String> base = read(syncDir.resolve("base.json"));
            Map<String, String> merged = SyncMerge.merge(base, local, remote);
            write(repo.resolve(DATA_FILE), merged);

            git.run(repo, "add", DATA_FILE);
            if (!git.run(repo, "diff", "--cached", "--quiet").ok()) {
                step = git.run(
                        repo,
                        "-c",
                        "user.name=" + AppInfo.NAME,
                        "-c",
                        "user.email=" + AppInfo.ID + "@localhost",
                        "commit",
                        "-q",
                        "-m",
                        "Sync from " + hostName());
                if (!step.ok()) return new Result(null, step.output());
                step = git.run(repo, "push", "-q", "origin", "HEAD:refs/heads/" + branch);
                if (!step.ok()) return new Result(null, step.output());
            }
            write(syncDir.resolve("base.json"), merged);
            return new Result(merged, null);
        } catch (IOException e) {
            return new Result(null, e.getMessage());
        }
    }

    /** Clones on first use, then brings the clone to the remote branch (an empty remote is fine). */
    private Git.Result prepare(Path repo, String url, String branch) throws IOException {
        if (!Files.isDirectory(repo.resolve(".git"))) {
            Files.createDirectories(repo);
            Git.Result init = git.run(repo, "init", "-q");
            if (!init.ok()) return init;
            git.run(repo, "remote", "add", "origin", url);
        } else {
            git.run(repo, "remote", "set-url", "origin", url);
        }
        Git.Result fetch = git.run(repo, "fetch", "-q", "origin");
        if (!fetch.ok()) return fetch;
        boolean remoteBranch =
                git.run(repo, "rev-parse", "--verify", "-q", "origin/" + branch).ok();
        return remoteBranch
                ? git.run(repo, "checkout", "-q", "-B", branch, "origin/" + branch)
                : git.run(repo, "checkout", "-q", "-B", branch);
    }

    /** A synced data file larger than this is refused before it is read (PERF-12): bindings are tiny. */
    public static final long MAX_DATA_BYTES = 1024 * 1024;

    static Map<String, String> read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return new TreeMap<>();
        if (Files.size(file) > MAX_DATA_BYTES) throw new IOException(file.getFileName() + " is larger than 1 MB");
        return JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), MAP);
    }

    private static void write(Path file, Map<String, String> map) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(map) + "\n");
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "another computer";
        }
    }
}
