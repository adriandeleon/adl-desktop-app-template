package com.example.app.cli;

import java.util.ArrayList;
import java.util.List;

import com.example.app.AppInfo;
import org.jspecify.annotations.Nullable;

/**
 * The parsed command line. Pure: no toolkit, no I/O, so {@code --help} and {@code --version} work
 * without a display and every flag is unit-tested.
 *
 * @param error why the arguments were rejected, or null
 */
public record CommandLine(
        boolean help,
        boolean version,
        @Nullable String configDir,
        boolean dev,
        boolean newInstance,
        boolean noSession,
        boolean report,
        List<String> files,
        @Nullable String error) {

    /** Parses {@code args}; an unknown option or a missing value is reported in {@link #error}. */
    public static CommandLine parse(String[] args) {
        boolean help = false;
        boolean version = false;
        String configDir = null;
        boolean dev = false;
        boolean newInstance = false;
        boolean noSession = false;
        boolean report = false;
        List<String> files = new ArrayList<>();
        boolean onlyFiles = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (onlyFiles || !arg.startsWith("-") || arg.equals("-")) {
                files.add(arg);
                continue;
            }
            switch (arg) {
                case "--" -> onlyFiles = true;
                case "-h", "--help" -> help = true;
                case "-v", "-V", "--version" -> version = true;
                // CLI-12: JVM options a launcher can leave among the program's arguments, skipped with their
                // value: never refused, never taken for a file.
                case "--add-exports", "--add-opens", "-p" -> i++;
                case "--dev" -> dev = true;
                case "--new-instance" -> newInstance = true;
                case "--no-session" -> noSession = true;
                case "--report" -> report = true;
                case "--config-dir" -> {
                    if (i + 1 >= args.length || args[i + 1].isBlank()) {
                        return failed("--config-dir needs a folder");
                    }
                    configDir = args[++i];
                }
                default -> {
                    if (arg.startsWith("--add-exports=") || arg.startsWith("--add-opens=")) continue;
                    if (arg.startsWith("--config-dir=")) {
                        configDir = arg.substring("--config-dir=".length());
                        if (configDir.isBlank()) return failed("--config-dir needs a folder");
                    } else {
                        return failed("unknown option " + arg);
                    }
                }
            }
        }
        return new CommandLine(help, version, configDir, dev, newInstance, noSession, report, List.copyOf(files), null);
    }

    private static CommandLine failed(String error) {
        return new CommandLine(false, false, null, false, false, false, false, List.of(), error);
    }

    /** The {@code --help} text. Plain English, not translated. */
    public static String usage() {
        String app = AppInfo.ID;
        return """
                Usage: %1$s [options] [files...]

                %2$s

                Options:
                  -h, --help            Show this help and exit
                  -v, -V, --version     Show the version and exit
                      --config-dir DIR  Use DIR as the config folder (default ~/.%1$s)
                      --dev             Use ~/.%1$s-dev, so a development build keeps its own config
                      --new-instance    Start a second process instead of handing off to the running one
                      --no-session      Do not restore the last window size and position
                      --report          Write a redacted problem report and open a GitHub issue

                Environment:
                  %3$s_CONFIG_DIR       Config folder, when --config-dir and --dev are not given
                """.formatted(app, AppInfo.TAGLINE, app.toUpperCase(java.util.Locale.ROOT));
    }
}
