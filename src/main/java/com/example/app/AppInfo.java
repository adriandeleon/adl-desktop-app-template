package com.example.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Identity: the one place the name, version and links are read from.
 *
 * <p>The version comes from {@code pom.xml} through the Maven-filtered {@code build-info.properties},
 * so a release means editing one number. {@code --version}, About and a problem report all print the
 * same {@link #environment} text.
 */
public final class AppInfo {

    public static final String NAME = "ExampleApp";
    /** The lower-case name: launcher, config folder ({@code ~/.exampleapp}) and file prefixes. */
    public static final String ID = "exampleapp";

    public static final String TAGLINE = "A keyboard-first JavaFX desktop app.";
    public static final String COPYRIGHT = "© 2026 Adrián Arturo De León Saldivar";
    public static final String LICENSE = "MIT License";

    public static final String GITHUB_REPO = "adriandeleon/ExampleApp";
    public static final String HOMEPAGE = "https://github.com/" + GITHUB_REPO;
    public static final String RELEASES_PAGE = HOMEPAGE + "/releases";
    public static final String LICENSE_URL = HOMEPAGE + "/blob/master/LICENSE";
    public static final String NEW_ISSUE_URL = HOMEPAGE + "/issues/new";
    /**
     * The app's website, from its {@code <app>-website} repository: the user documentation lives there
     * under {@code /docs/} (DOCS-01, DOCS-02), not in this repository.
     */
    public static final String WEBSITE = "https://" + ID + "-project.dev";

    public static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";

    public static final String VERSION = load("version", "0.0.0");
    public static final String BUILD_TIME = load("build.time", "");
    public static final String COMMIT = load("build.commit", "unknown");

    /**
     * The Pauta edition this app implements: {@code pauta.version} in {@code pom.xml}, filtered into
     * {@code build-info.properties} like the version. About links to it and the environment block names it.
     */
    public static final String PAUTA_EDITION = load("pauta.version", "unknown");

    private AppInfo() {}

    /** Falls back when the resource was not filtered (classes run straight from an IDE build). */
    static String load(String key, String fallback) {
        try (InputStream in = AppInfo.class.getResourceAsStream("build-info.properties")) {
            if (in == null) return fallback;
            Properties properties = new Properties();
            properties.load(in);
            String value = properties.getProperty(key, fallback);
            return value.isBlank() || value.startsWith("${") ? fallback : value;
        } catch (IOException e) {
            return fallback;
        }
    }

    /**
     * The user documentation for this build, {@code <website>/docs/v-<version>}: versioned, so a build
     * never opens the docs of a different release, and without {@code -SNAPSHOT}, so a development
     * build opens the release it is working toward.
     */
    public static String docsUrl() {
        return WEBSITE + "/docs/v-" + VERSION.replace("-SNAPSHOT", "");
    }

    /** This build's release notes: its GitHub release, or the releases page for a development build. */
    public static String releaseNotesUrl() {
        return releaseNotesUrl(VERSION);
    }

    static String releaseNotesUrl(String version) {
        if (version.isBlank() || version.endsWith("-SNAPSHOT") || version.equals("0.0.0")) return RELEASES_PAGE;
        return RELEASES_PAGE + "/tag/v" + version;
    }

    /** True for a development build; a release drops the suffix. */
    public static boolean isSnapshot() {
        return VERSION.endsWith("-SNAPSHOT");
    }

    /**
     * The edition's own page, which never changes: {@code https://pauta-project.dev/<edition>/}, never
     * {@code latest/}, which moves on to newer editions.
     */
    public static String pautaUrl() {
        return "https://pauta-project.dev/" + PAUTA_EDITION + "/";
    }

    /**
     * What {@code --version} prints and the environment block opens with: version and build, commit
     * and the Pauta edition.
     */
    public static String versionText() {
        String build = BUILD_TIME.isEmpty() ? "" : " (Built " + BUILD_TIME + ")";
        return NAME + " " + VERSION + build + "\n" + "Commit " + COMMIT + "\n" + "Pauta: " + PAUTA_EDITION;
    }

    /**
     * The environment block: {@link #versionText}, then Java, JavaFX, OS and config folder, with the
     * home folder shown as {@code ~}. Plain English on purpose: it goes into bug reports.
     */
    public static String environment(Path configDir) {
        return versionText() + "\n"
                + "Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")\n"
                + "JavaFX: " + System.getProperty("javafx.runtime.version", "not started") + "\n"
                + "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " ("
                + System.getProperty("os.arch") + ")\n"
                + "Config: " + tildeHome(configDir.toString());
    }

    /** Replaces the user's home folder with {@code ~}. */
    public static String tildeHome(String path) {
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty() && path.startsWith(home)) return "~" + path.substring(home.length());
        return path;
    }

    /** Whether this JVM runs on macOS: the one place the check lives. */
    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }
}
