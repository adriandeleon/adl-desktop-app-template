import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a macOS {@code .icns} from PNG files, with no tool beyond the JDK (iconutil is macOS-only).
 *
 * <p>An icns is a 4-byte {@code icns} magic and a big-endian total length, then one entry per image: a
 * 4-byte type, a length that includes its own 8-byte header, and the data. Since macOS 10.7 the types
 * below hold a PNG as it is, so nothing is re-encoded:
 *
 * <pre>
 *   ic07 128x128   ic08 256x256   ic09 512x512   ic10 1024x1024 (512x512@2x)
 * </pre>
 *
 * Usage: {@code java scripts/Icns.java <out.icns> ic07=<png> ic08=<png> ...}
 */
public final class Icns {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: java scripts/Icns.java <out.icns> <type>=<png>...");
            System.exit(2);
        }
        var entries = new ByteArrayOutputStream();
        var out = new DataOutputStream(entries);
        for (int i = 1; i < args.length; i++) {
            String[] pair = args[i].split("=", 2);
            if (pair.length != 2 || pair[0].length() != 4) {
                throw new IllegalArgumentException("expected <type>=<png>, got " + args[i]);
            }
            byte[] png = Files.readAllBytes(Path.of(pair[1]));
            for (int b = 0; b < PNG_MAGIC.length; b++) {
                if (png.length <= b || png[b] != PNG_MAGIC[b]) {
                    throw new IllegalArgumentException(pair[1] + " is not a PNG");
                }
            }
            out.writeBytes(pair[0]);
            out.writeInt(png.length + 8);
            out.write(png);
        }
        out.flush();
        var file = new ByteArrayOutputStream();
        var header = new DataOutputStream(file);
        header.writeBytes("icns");
        header.writeInt(entries.size() + 8);
        entries.writeTo(header);
        header.flush();
        Files.write(Path.of(args[0]), file.toByteArray());
    }
}
