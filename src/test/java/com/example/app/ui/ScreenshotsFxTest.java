package com.example.app.ui;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;

import com.example.app.config.ThemeChoice;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Retakes the README's hero screenshot from the real window, at 2×, in the light and the dark theme
 * (DOCS-18, through the headless fixture QUAL-09 asks for). It runs only when asked, with the folder to
 * write to: {@code scripts/screenshots.sh}, or {@code ./mvnw test -Dtest=ScreenshotsFxTest
 * -Dexampleapp.shots=docs/images}.
 */
@Tag("fx")
class ScreenshotsFxTest {

    private static final String SHOTS = "exampleapp.shots";
    private static final double WIDTH = 1200;
    private static final double HEIGHT = 720;
    private static final double SCALE = 2;

    @TempDir
    Path dir;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @Test
    void retakesTheReadmeHeroInLightAndDark() throws Exception {
        String folder = System.getProperty(SHOTS, "");
        assumeTrue(!folder.isBlank(), "set -D" + SHOTS + "=<folder> to retake the screenshots");
        Path out = Path.of(folder).toAbsolutePath();
        Files.createDirectories(out);
        Startup.Running app = FxTestSupport.start(dir, new FakeHost());
        try {
            MainWindow window = app.window();
            run(() -> {
                window.stage().setWidth(WIDTH);
                window.stage().setHeight(HEIGHT);
            });
            shoot(window, ThemeChoice.LIGHT, out.resolve("screenshot-light.png"));
            shoot(window, ThemeChoice.DARK, out.resolve("screenshot-dark.png"));
        } finally {
            run(() -> Themes.apply(ThemeChoice.LIGHT));
            FxTestSupport.stop(app);
        }
    }

    private static void shoot(MainWindow window, ThemeChoice theme, Path file) throws Exception {
        run(() -> Themes.apply(theme));
        run(() -> window.stage().getScene().getRoot().layout());
        run(() -> {}); // a pulse, so the new stylesheet is drawn
        WritableImage image = call(() -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(Transform.scale(SCALE, SCALE));
            return window.stage().getScene().getRoot().snapshot(params, null);
        });
        Files.write(file, png(image));
    }

    /** The image as an RGB PNG, written with the JDK alone (no javafx.swing, no AWT). */
    static byte[] png(WritableImage image) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] argb = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(raw)) {
            byte[] row = new byte[1 + width * 3];
            for (int y = 0; y < height; y++) {
                row[0] = 0; // no filter
                for (int x = 0; x < width; x++) {
                    int p = argb[y * width + x];
                    row[1 + x * 3] = (byte) (p >> 16);
                    row[2 + x * 3] = (byte) (p >> 8);
                    row[3 + x * 3] = (byte) p;
                }
                z.write(row);
            }
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(file);
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(header);
        h.writeInt(width);
        h.writeInt(height);
        h.write(new byte[] {8, 2, 0, 0, 0}); // 8-bit RGB, deflate, no filter method, no interlace
        chunk(out, "IHDR", header.toByteArray());
        chunk(out, "IDAT", raw.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return file.toByteArray();
    }

    private static void chunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(name);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }
}
