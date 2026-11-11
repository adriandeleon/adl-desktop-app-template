package com.example.app.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the {@code git} command line. Never prompts: automatic runs must not hang on a password, so
 * credentials come from the user's own credential helper or SSH key.
 */
public final class Git {

    /** One run: its exit code (-1 when git could not start or timed out) and its combined output. */
    public record Result(int exit, String output) {
        public boolean ok() {
            return exit == 0;
        }
    }

    private final java.util.function.Supplier<String> finder;
    private volatile @org.jspecify.annotations.Nullable String executable;

    /**
     * The user's git, found even from a Finder-launched app (see {@link Executables}): on its first run,
     * on the thread that runs it, never while the window is built (PERF-01).
     */
    public Git() {
        this.finder = () -> Executables.find("git");
    }

    public Git(String executable) {
        this.finder = () -> executable;
        this.executable = executable;
    }

    private String executable() {
        String found = executable;
        if (found == null) {
            found = finder.get();
            executable = found;
        }
        return found;
    }

    public Result run(Path dir, String... args) {
        return run(java.util.Map.of(), dir, args);
    }

    /**
     * As {@link #run(Path, String...)}, with {@code env} added to git's environment: the token of the account
     * the clone is pinned to ({@code GH_TOKEN}, SET-18), which the credential helper hands to git.
     */
    public Result run(java.util.Map<String, String> env, Path dir, String... args) {
        List<String> command = new ArrayList<>();
        command.add(executable());
        command.add("-c");
        command.add("core.quotepath=off");
        if (env.containsKey("GH_TOKEN")) {
            // The pinned account's token for github.com, whatever helper the user's own config names.
            command.add("-c");
            command.add("credential.https://github.com.helper=");
            command.add("-c");
            command.add("credential.https://github.com.helper="
                    + "!f() { echo username=x-access-token; echo \"password=$GH_TOKEN\"; }; f");
        }
        command.addAll(List.of(args));
        ProcessBuilder builder =
                new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
        builder.environment().putAll(env);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GCM_INTERACTIVE", "never");
        try {
            Process process = builder.start();
            process.getOutputStream().close();
            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(-1, "git timed out");
            }
            return new Result(process.exitValue(), new String(output, StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            return new Result(-1, "git is not available: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "interrupted");
        }
    }
}
