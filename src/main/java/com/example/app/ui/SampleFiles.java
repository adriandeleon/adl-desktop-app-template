package com.example.app.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.example.app.AppInfo;
import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The placeholder documents the template opens in tabs: a small "project" of Markdown files that exist only
 * in memory. ExampleApp edits nothing; these are here so the tab and tool window rules (Pauta's Tabs and
 * tool panels) have something to act on. An app replaces this class with its own files.
 *
 * <p>Each sample carries the Git status a real repository would report, one of each, so the tab strip and
 * the Documents window show every color of TABS-08.
 */
final class SampleFiles {

    private SampleFiles() {}

    /** A file's Git status, with the style class the Documents window and the tab both wear (TABS-08). */
    enum GitStatus {
        MODIFIED,
        ADDED,
        UNTRACKED,
        RENAMED,
        DELETED,
        CONFLICT;

        String cssClass() {
            return "git-status-" + name().toLowerCase(Locale.ROOT);
        }

        /** The status as Git's one letter, drawn beside the name so color is not its only sign (ACC-05). */
        String letter() {
            return switch (this) {
                case MODIFIED -> "M";
                case ADDED -> "A";
                case UNTRACKED -> "U";
                case RENAMED -> "R";
                case DELETED -> "D";
                case CONFLICT -> "C";
            };
        }

        /** The status in words, so color is not its only sign (TABS-08, ACC-05). */
        String words() {
            return com.example.app.i18n.Messages.tr("git.status." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** One sample: where it would live, whether it is read-only, and its Git status (null for clean). */
    record Sample(Path path, boolean readOnly, @Nullable GitStatus status) {

        String name() {
            return String.valueOf(path.getFileName());
        }

        /** The text it opens with: a heading per section, so the Outline has something to list. */
        String text() {
            if (readOnly) return Messages.tr("sample.readOnly");
            GitStatus git = status;
            String status = git == null ? "clean" : git.name().toLowerCase(Locale.ROOT);
            return "# " + name() + "\n\n"
                    + Messages.tr("sample.intro") + "\n\n## "
                    + Messages.tr("sample.tryHeading") + "\n\n"
                    + Messages.tr("sample.try") + "\n\n## "
                    + Messages.tr("sample.statusHeading") + "\n\n"
                    + Messages.tr("sample.status." + status) + "\n";
        }
    }

    /** The project's name, for the window title (TABS-10). */
    static final String PROJECT = "Samples";

    /** Where the samples would live: a folder in the home directory that is never created. */
    static Path root() {
        return Path.of(System.getProperty("user.home"), AppInfo.NAME, PROJECT);
    }

    /** Every sample, in the Documents window's order. */
    static List<Sample> all() {
        Path root = root();
        return List.of(
                new Sample(root.resolve("README.md"), false, GitStatus.MODIFIED),
                new Sample(root.resolve("notes.md"), false, null),
                new Sample(root.resolve("guide.md"), false, GitStatus.ADDED),
                new Sample(root.resolve("ideas.md"), false, GitStatus.UNTRACKED),
                new Sample(root.resolve("plan.md"), false, GitStatus.RENAMED),
                new Sample(root.resolve("draft.md"), false, GitStatus.DELETED),
                new Sample(root.resolve("merge.md"), false, GitStatus.CONFLICT),
                new Sample(root.resolve("LICENSE"), true, null));
    }

    /** The sample at {@code path}, or null. */
    static @Nullable Sample at(Path path) {
        for (Sample sample : all()) {
            if (sample.path().equals(path)) return sample;
        }
        return null;
    }

    /** The headings of a Markdown text, for the Outline: each line that starts with {@code #}. Pure. */
    /** A heading and where its line starts in the text. */
    record Heading(String title, int offset) {}

    /** Every heading of {@code text} with its line's offset, in order. Pure; off the FX thread (PERF-01). */
    static List<Heading> headingsAt(String text) {
        List<Heading> found = new java.util.ArrayList<>();
        int at = 0;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("#")) {
                String title = line.replaceFirst("^#+\\s*", "");
                if (!title.isBlank()) found.add(new Heading(title, at));
            }
            at += line.length() + 1;
        }
        return List.copyOf(found);
    }

    static List<String> headings(String text) {
        return text.lines()
                .filter(line -> line.startsWith("#"))
                .map(line -> line.replaceFirst("^#+\\s*", ""))
                .filter(line -> !line.isBlank())
                .toList();
    }
}
