package com.example.app.sync;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.app.AppInfo;
import org.jspecify.annotations.Nullable;

/**
 * Settings Sync through a Git repository the user owns: no server and no account of ours.
 *
 * <p>The repository is cloned into {@code <config>/sync/repo}. What syncs is the user's own key
 * bindings ({@code keybindings.json}); which keymap is in use, window placement and every other
 * machine-local preference stay on each computer. {@code base.json} remembers the last synced state
 * for the three-way {@link SyncMerge}. Both files are {@link SyncData}, versioned. Blocking: callers run it off the FX thread.
 */
public final class SyncEngine {

    public static final String DATA_FILE = "keybindings.json";

    /** The last synced state, beside the clone, for the three-way merge. */
    public static final String BASE_FILE = "base.json";

    /** What a sync did: the merged bindings to apply locally, or the reason it failed. */
    public record Result(
            @Nullable Map<String, String> merged, @Nullable String error) {
        public boolean ok() {
            return error == null;
        }
    }

    /** A scheme and the user-info before the host: {@code https://user:secret@host/…}. */
    private static final Pattern USER_INFO = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9+.-]*)://([^/@?#]*)@");

    /** A user name that is a token: GitHub's and GitLab's prefixes, or any long run of token characters. */
    private static final Pattern TOKEN =
            Pattern.compile("(ghp|gho|ghu|ghs|ghr)_\\w+|github_pat_\\w+|glpat-[\\w-]+|[\\w-]{30,}");

    private final Path syncDir;
    private final Git git;
    private final @org.jspecify.annotations.Nullable GitHubCli gh;

    public SyncEngine(Path syncDir, Git git) {
        this(syncDir, git, null);
    }

    /** With {@code gh}, an HTTPS github.com remote uses the account pinned to the clone (SET-18). */
    public SyncEngine(Path syncDir, Git git, @org.jspecify.annotations.Nullable GitHubCli gh) {
        this.syncDir = syncDir;
        this.git = git;
        this.gh = gh;
    }

    /** The git this engine runs: the Doctor asks it for its version (DIAG-07). */
    public Git git() {
        return git;
    }

    public Path repoDir() {
        return syncDir.resolve("repo");
    }

    /**
     * Why a repository URL is refused, as a catalog suffix, or null when it is usable. A password or token
     * in it is refused (SET-15, SYNC-02): it would sit in plain text in the settings file, its exports and
     * the clone's {@code .git/config}. The page and the palette prompt check it, and so does every sync.
     */
    public static @Nullable String checkUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return "empty";
        if (hasCredentials(trimmed)) return "credentials";
        return null;
    }

    /**
     * Whether {@code url} carries a secret: {@code user:password@} in any scheme, or a token as the user
     * name of an HTTP(S) URL ({@code https://ghp_…@github.com/…}). An SSH user ({@code git@host:…},
     * {@code ssh://git@host/…}) is never a secret. After Editora's {@code SyncEngine.hasCredentials}.
     */
    public static boolean hasCredentials(String url) {
        Matcher m = USER_INFO.matcher(url);
        if (!m.find()) return false; // no scheme (scp-like git@host:path) or no user-info
        String scheme = m.group(1).toLowerCase(Locale.ROOT);
        String userInfo = m.group(2);
        int colon = userInfo.indexOf(':');
        if (colon >= 0) return colon < userInfo.length() - 1; // "user:secret"
        return (scheme.equals("http") || scheme.equals("https"))
                && TOKEN.matcher(userInfo).matches();
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
        String refused = checkUrl(url); // again here: a URL can reach the file by hand or by an import
        if (refused != null) return new Result(null, "url." + refused);
        try {
            Path repo = repoDir();
            // The account pinned to this clone, for an HTTPS github.com remote (SET-18).
            GitHubCli cli = gh;
            java.util.Map<String, String> env =
                    cli == null ? java.util.Map.of() : AccountPin.env(syncDir, url.trim(), cli);
            Git.Result step = prepare(repo, url.trim(), branch, env);
            if (!step.ok()) return new Result(null, step.output());

            Map<String, String> remote = SyncData.read(repo.resolve(DATA_FILE));
            Map<String, String> base = SyncData.readBase(syncDir.resolve(BASE_FILE));
            Map<String, String> merged = SyncMerge.merge(base, local, remote);
            SyncData.write(repo.resolve(DATA_FILE), merged);

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
                step = git.run(env, repo, "push", "-q", "origin", "HEAD:refs/heads/" + branch);
                if (!step.ok()) return new Result(null, step.output());
            }
            SyncData.write(syncDir.resolve(BASE_FILE), merged);
            return new Result(merged, null);
        } catch (SyncData.NewerException e) {
            // Another computer runs a newer build: nothing here is written over its data.
            return new Result(null, "data.newer");
        } catch (IOException e) {
            return new Result(null, e.getMessage());
        }
    }

    /** Clones on first use, then brings the clone to the remote branch (an empty remote is fine). */
    private Git.Result prepare(Path repo, String url, String branch, java.util.Map<String, String> env)
            throws IOException {
        if (!Files.isDirectory(repo.resolve(".git"))) {
            Files.createDirectories(repo);
            Git.Result init = git.run(repo, "init", "-q");
            if (!init.ok()) return init;
            git.run(repo, "remote", "add", "origin", url);
        } else {
            git.run(repo, "remote", "set-url", "origin", url);
        }
        Git.Result fetch = git.run(env, repo, "fetch", "-q", "origin");
        if (!fetch.ok()) return fetch;
        boolean remoteBranch =
                git.run(repo, "rev-parse", "--verify", "-q", "origin/" + branch).ok();
        return remoteBranch
                ? git.run(repo, "checkout", "-q", "-B", branch, "origin/" + branch)
                : git.run(repo, "checkout", "-q", "-B", branch);
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "another computer";
        }
    }
}
