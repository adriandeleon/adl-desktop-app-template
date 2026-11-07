package com.example.app.config;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.example.app.AppInfo;

/**
 * Export of the config folder as a {@code .zip}; {@link ConfigImport} reads one back. Transient files (the instance socket,
 * session logs, the sync clone, backups) stay out of it.
 */
public final class ConfigExporter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigExporter() {}

    /** The suggested file name: {@code <app>-config-<timestamp>.zip}. */
    public static String defaultName(LocalDateTime now) {
        return AppInfo.ID + "-config-" + STAMP.format(now) + ".zip";
    }

    /** Whether a path relative to the config folder belongs in an export. */
    static boolean exported(Path relative) {
        String first = relative.getName(0).toString();
        String name = relative.getFileName().toString();
        return !first.equals("sync")
                && !name.endsWith(".sock")
                && !name.endsWith(".log")
                && !name.endsWith(".bak")
                && !name.endsWith(".tmp");
    }

    /** Writes the config folder to {@code zip}; returns how many files went in. */
    public static int export(Path dir, Path zip) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> exported(dir.relativize(p)))
                    .sorted()
                    .toList();
        }
        try (OutputStream out = Files.newOutputStream(zip);
                ZipOutputStream zipOut = new ZipOutputStream(out)) {
            for (Path file : files) {
                zipOut.putNextEntry(new ZipEntry(dir.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zipOut);
                zipOut.closeEntry();
            }
        }
        return files.size();
    }
}
