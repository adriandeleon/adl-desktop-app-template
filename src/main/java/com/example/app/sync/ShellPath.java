package com.example.app.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.example.app.Os;
import org.jspecify.annotations.Nullable;

/**
 * The {@code PATH} the app's tools run with (CLI-10): on macOS and Linux, the login shell's, which a GUI app
 * does not inherit (a {@code .app} from Finder gets a bare one, without Homebrew's folders or the user's own),
 * before the one the app inherited. Asked once, on first use, off the FX thread. Windows has no login shell to
 * ask, and the inherited {@code PATH} is used there.
 */
public final class ShellPath {

    private static volatile @Nullable String cached;

    private ShellPath() {}

    /** The {@code PATH} for a process the app starts. */
    public static String path() {
        String path = cached;
        if (path == null) {
            String inherited = System.getenv().getOrDefault("PATH", "");
            path = Os.isWindows()
                    ? inherited
                    : merged(read(System.getenv().getOrDefault("SHELL", "/bin/sh")), inherited);
            cached = path;
        }
        return path;
    }

    /** {@code login}'s folders, then the inherited ones it lacks, each once, in order. Pure. */
    static String merged(@Nullable String login, String inherited) {
        LinkedHashSet<String> folders = new LinkedHashSet<>();
        List<String> parts = new ArrayList<>();
        if (login != null) parts.addAll(List.of(login.split(java.io.File.pathSeparator)));
        parts.addAll(List.of(inherited.split(java.io.File.pathSeparator)));
        for (String folder : parts) if (!folder.isBlank()) folders.add(folder.strip());
        return String.join(java.io.File.pathSeparator, folders);
    }

    /** What {@code shell -l} sets {@code PATH} to, or null when it can't say in a few seconds. */
    static @Nullable String read(String shell) {
        try {
            Process process = new ProcessBuilder(shell, "-l", "-c", "printf %s \"$PATH\"")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.getOutputStream().close();
            // Read on its own thread, so a shell that keeps its output open can't outlast the timeout.
            java.util.concurrent.CompletableFuture<byte[]> out =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            return process.getInputStream().readNBytes(64 * 1024);
                        } catch (IOException e) {
                            return new byte[0];
                        }
                    });
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String path;
            try {
                path = new String(out.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8).strip();
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                return null;
            }
            return process.exitValue() == 0 && !path.isEmpty() ? path : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
