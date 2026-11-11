package com.example.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a config file whole or not at all (SET-22): the bytes go to a temporary file in the same folder,
 * which then moves onto the target, so a crash mid-write leaves the old file, never half a new one. Every
 * file the app writes as a whole and reads back as its state goes through here: the settings, an import,
 * the sync engine's base.
 */
public final class ConfigFiles {

    private ConfigFiles() {}

    public static void write(Path target, byte[] bytes) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        if (dir == null) throw new IOException("No folder for " + target);
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void writeString(Path target, String text) throws IOException {
        write(target, text.getBytes(StandardCharsets.UTF_8));
    }
}
