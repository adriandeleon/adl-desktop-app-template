package com.example.app.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.app.AppInfo;

/**
 * "Create on GitHub" for Settings Sync: makes a private repository with the GitHub CLI ({@code gh}), so
 * connecting does not start with a trip to the browser. The app never sees a token: gh signs in as its
 * own active account. Blocking; callers run it off the FX thread.
 */
public final class GitHubCli {

    /** What a create did: the git remote to use, or what went wrong. */
    public record Created(String url, Problem problem, String detail) {
        public boolean ok() {
            return problem == Problem.NONE;
        }
    }

    /** Why a create failed, for a message in the user's language; {@code detail} carries gh's own words. */
    public enum Problem {
        NONE,
        BAD_NAME,
        NOT_INSTALLED,
        NOT_SIGNED_IN,
        FAILED
    }

    static final Duration NETWORK = Duration.ofMinutes(1);
    static final Duration QUICK = Duration.ofSeconds(15);

    /** The web address {@code gh repo create} prints, last thing on a line. */
    private static final Pattern WEB_URL =
            Pattern.compile("https://([A-Za-z0-9.-]+)/([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+?)(?:\\.git)?/?\\s*$");

    private record Result(int exit, String output) {
        boolean ok() {
            return exit == 0;
        }
    }

    private final java.util.function.Supplier<String> finder;
    private volatile @org.jspecify.annotations.Nullable String gh;
    /** Arguments that go before gh's own: none, except for a test's fake run by the JDK's launcher. */
    private final List<String> leading;

    /** Overrides which gh runs; the test suite points it at a command that does not exist. */
    public static final String PROPERTY = AppInfo.ID + ".gh";

    /** The user's gh, looked for on its first run, off the FX thread (PERF-01). */
    public GitHubCli() {
        this.finder = () -> {
            String forced = System.getProperty(PROPERTY);
            return forced != null ? forced : Executables.find("gh");
        };
        this.leading = List.of();
    }

    public GitHubCli(String gh) {
        this(gh, List.of());
    }

    /** gh is {@code command} with {@code leading} first: a test's fake that runs the same on every OS. */
    GitHubCli(String command, List<String> leading) {
        this.finder = () -> command;
        this.gh = command;
        this.leading = List.copyOf(leading);
    }

    private String gh() {
        String found = gh;
        if (found == null) {
            found = finder.get();
            gh = found;
        }
        return found;
    }

    /** Whether GitHub accepts {@code name} for a repository, and gh cannot read it as an option. */
    public static boolean isName(String name) {
        return name.matches("[A-Za-z0-9._][A-Za-z0-9._-]{0,99}") && !name.equals(".") && !name.equals("..");
    }

    /**
     * Creates a private repository called {@code name} under gh's active account and returns the remote
     * git should use: SSH when the user's gh is set to the ssh protocol, HTTPS otherwise.
     */
    public Created createPrivate(String name, String description) {
        if (!isName(name)) return new Created("", Problem.BAD_NAME, name);
        Result status = run(QUICK, "auth", "status");
        if (status.exit() == -1) return new Created("", Problem.NOT_INSTALLED, status.output());
        if (!status.ok()) return new Created("", Problem.NOT_SIGNED_IN, status.output());
        Result created = run(NETWORK, "repo", "create", name, "--private", "--description", description);
        if (!created.ok()) return new Created("", Problem.FAILED, created.output());
        Result protocol = run(QUICK, "config", "get", "git_protocol");
        String url = remoteUrl(created.output(), protocol.ok() ? protocol.output() : "");
        return url.isEmpty() ? new Created("", Problem.FAILED, created.output()) : new Created(url, Problem.NONE, "");
    }

    /**
     * The git remote for the repository whose web address gh printed: {@code git@host:owner/name.git} for
     * the ssh protocol, else {@code https://host/owner/name.git}; blank when there is no address. Pure.
     */
    public static String remoteUrl(String output, String protocol) {
        for (String line : output.strip().split("\\R")) {
            Matcher m = WEB_URL.matcher(line.strip());
            if (m.find()) {
                String path = m.group(2) + "/" + m.group(3) + ".git";
                return protocol.strip().equalsIgnoreCase("ssh")
                        ? "git@" + m.group(1) + ":" + path
                        : "https://" + m.group(1) + "/" + path;
            }
        }
        return "";
    }

    /** gh's version line, or null when gh can't be run (the Doctor's row, DIAG-07). */
    public @org.jspecify.annotations.Nullable String version() {
        Result version = run(QUICK, "--version");
        return version.ok() && !version.output().isBlank()
                ? version.output().lines().findFirst().orElse("")
                : null;
    }

    /** {@code Logged in to github.com account NAME} (gh 2.40 on) or {@code ... as NAME} (before). */
    private static final Pattern ACCOUNT = Pattern.compile("Logged in to (\\S+) (?:account|as) ([\\w.-]+)");

    /** The github.com accounts gh is signed in to, the active one first as gh lists them. */
    public List<String> accounts() {
        Result status = run(QUICK, "auth", "status", "--hostname", "github.com");
        return accounts(status.output());
    }

    /** The accounts in {@code gh auth status}'s output. Pure. */
    static List<String> accounts(String output) {
        List<String> accounts = new ArrayList<>();
        Matcher m = ACCOUNT.matcher(output);
        while (m.find()) if (!accounts.contains(m.group(2))) accounts.add(m.group(2));
        return accounts;
    }

    /** {@code account}'s token, or null when gh has none for it. Never logged, never shown. */
    public @org.jspecify.annotations.Nullable String token(String account) {
        Result token = run(QUICK, "auth", "token", "--hostname", "github.com", "--user", account);
        return token.ok() && !token.output().isBlank() ? token.output().strip() : null;
    }

    /** Whether the account behind {@code token} can read {@code ownerRepo} ({@code owner/name}). */
    public boolean canRead(String token, String ownerRepo) {
        return run(java.util.Map.of("GH_TOKEN", token), QUICK, "api", "repos/" + ownerRepo, "--silent")
                .ok();
    }

    private Result run(Duration timeout, String... args) {
        return run(java.util.Map.of(), timeout, args);
    }

    /** Runs gh with prompts off and nothing on its terminal; exit -1 when it could not start or hung. */
    private Result run(java.util.Map<String, String> env, Duration timeout, String... args) {
        List<String> argv = new ArrayList<>();
        argv.add(gh());
        argv.addAll(leading);
        argv.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
        builder.environment().put("PATH", ShellPath.path()); // the login shell's (CLI-10)
        builder.environment().putAll(env);
        builder.environment().put("GH_PROMPT_DISABLED", "1");
        builder.environment().put("GH_NO_UPDATE_NOTIFIER", "1");
        builder.environment().put("NO_COLOR", "1");
        try {
            Process process = builder.start();
            process.getOutputStream().close();
            byte[] output = process.getInputStream().readNBytes(1 << 20);
            if (!process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                process.destroyForcibly();
                return new Result(-1, "gh did not finish in " + timeout.toSeconds() + " s");
            }
            return new Result(process.exitValue(), new String(output, StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            return new Result(-1, String.valueOf(e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "interrupted");
        }
    }
}
