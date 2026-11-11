package com.example.app;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Which OS the app runs on (OS-02): the one class that reads {@code os.name}. Every other class asks here,
 * so the places that differ by OS are the callers of these methods, each one a row of Pauta's "Where the
 * OS's way wins" (OS-04). Tests can run as any OS through {@link #override} or the {@code exampleapp.os}
 * system property (OS-03).
 */
public final class Os {

    /** The three OSes the app is built for. */
    public enum Kind {
        WINDOWS,
        MAC,
        LINUX
    }

    /** A system property that runs the app as another OS: {@code windows}, {@code mac} or {@code linux}. */
    public static final String PROPERTY = AppInfo.ID + ".os";

    private static volatile @Nullable Kind override;

    private Os() {}

    /** The OS's own name and version, for About, the environment block and a problem report. */
    public static String description() {
        return System.getProperty("os.name", "") + " " + System.getProperty("os.version", "") + " ("
                + System.getProperty("os.arch", "") + ")";
    }

    /** The OS this run behaves as: the override, else the property, else the one it runs on. */
    public static Kind current() {
        Kind forced = override;
        if (forced != null) return forced;
        String property = System.getProperty(PROPERTY);
        if (property != null && !property.isBlank()) return parse(property);
        return parse(System.getProperty("os.name", ""));
    }

    /** {@code Mac OS X} and {@code mac} are MAC, {@code Windows 11} and {@code windows} WINDOWS, the rest LINUX. */
    static Kind parse(String name) {
        String os = name.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) return Kind.MAC;
        if (os.startsWith("windows")) return Kind.WINDOWS;
        return Kind.LINUX;
    }

    public static boolean isWindows() {
        return current() == Kind.WINDOWS;
    }

    public static boolean isMac() {
        return current() == Kind.MAC;
    }

    public static boolean isLinux() {
        return current() == Kind.LINUX;
    }

    /** For tests: behave as {@code kind} until reset with {@code null} (OS-03). */
    public static void override(@Nullable Kind kind) {
        override = kind;
    }
}
