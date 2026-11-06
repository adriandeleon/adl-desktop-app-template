package com.example.app.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.example.app.AppInfo;

/**
 * Export and import of the config folder as a {@code .zip}. Transient files (the instance socket,
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

    /**
     * Restores a config folder from {@code zip}; returns how many files were written. An entry that
     * would land outside {@code dir} ("zip slip") rejects the whole archive before anything is written.
     */
    public static int importFrom(Path zip, Path dir) throws IOException {
        Path root = dir.toAbsolutePath().normalize();
        try (InputStream in = Files.newInputStream(zip);
                ZipInputStream check = new ZipInputStream(in)) {
            for (ZipEntry entry; (entry = check.getNextEntry()) != null; ) target(root, entry);
        }
        int count = 0;
        try (InputStream in = Files.newInputStream(zip);
                ZipInputStream zipIn = new ZipInputStream(in)) {
            for (ZipEntry entry; (entry = zipIn.getNextEntry()) != null; ) {
                if (entry.isDirectory()) continue;
                Path target = target(root, entry);
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.copy(zipIn, target, StandardCopyOption.REPLACE_EXISTING);
                count++;
            }
        }
        return count;
    }

    private static Path target(Path root, ZipEntry entry) throws IOException {
        Path target = root.resolve(entry.getName()).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("unsafe entry in archive: " + entry.getName());
        }
        return target;
    }
}
