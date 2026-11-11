package com.example.app.sync;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.app.Os;
import org.jspecify.annotations.Nullable;

/**
 * Finds an external tool (git, gh). A {@code .app} started from Finder inherits a stripped
 * {@code PATH} with none of Homebrew's folders, so the usual install places are searched after it.
 */
public final class Executables {

    /** Where package managers put tools that a GUI app's {@code PATH} may not include. */
    static final List<String> EXTRA_DIRS =
            List.of("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/home/linuxbrew/.linuxbrew/bin");

    private Executables() {}

    /** The tool's full path, or its bare name (left to the OS to find, or to fail) when not found. */
    public static String find(String name) {
        Path found = find(name, System.getenv().getOrDefault("PATH", ""), Os.isWindows());
        return found == null ? name : found.toString();
    }

    static @Nullable Path find(String name, String pathVariable, boolean windows) {
        List<String> dirs = new ArrayList<>(List.of(pathVariable.split(File.pathSeparator)));
        if (!windows) dirs.addAll(EXTRA_DIRS);
        List<String> names = windows ? List.of(name + ".exe", name + ".cmd", name) : List.of(name);
        for (String dir : dirs) {
            if (dir.isBlank()) continue;
            for (String candidate : names) {
                Path file = Path.of(dir, candidate);
                if (Files.isRegularFile(file) && Files.isExecutable(file)) return file;
            }
        }
        return null;
    }
}
