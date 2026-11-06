package com.example.app.instance;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.app.AppInfo;
import org.jspecify.annotations.Nullable;

/**
 * One running app per config folder. The first launch listens on a Unix-domain socket in the config
 * folder (Windows 10+ supports them too); a second launch hands its arguments over and exits.
 */
public final class SingleInstance implements AutoCloseable {

    public static final String SOCKET = "instance.sock";

    /** Below every platform's limit for a Unix-domain socket path (104 on macOS, 108 on Linux). */
    static final int MAX_PATH = 100;

    private static final Logger LOG = Logger.getLogger(SingleInstance.class.getName());

    private final Path socket;
    private @Nullable ServerSocketChannel server;

    private SingleInstance(Path socket) {
        this.socket = socket;
    }

    /**
     * Hands {@code args} to a running instance. Returns true when one took them (this process should
     * exit), false when none is running.
     */
    public static boolean handOff(Path configDir, List<String> args) {
        Path socket = socketPath(configDir);
        if (!Files.exists(socket)) return false;
        try (SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
            byte[] payload = String.join("\0", args).getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.allocate(4).putInt(payload.length).flip());
            channel.write(ByteBuffer.wrap(payload));
            return channel.read(ByteBuffer.allocate(1)) == 1; // the acknowledgement
        } catch (IOException e) {
            return false; // a stale socket from a crashed run: nobody is listening
        }
    }

    /**
     * The socket for a config folder: inside it, or, when that path is longer than a Unix socket path
     * may be (about 100 bytes), in the temp folder under a name derived from the folder's path.
     */
    static Path socketPath(Path configDir) {
        Path inside = configDir.toAbsolutePath().resolve(SOCKET);
        if (inside.toString().getBytes(StandardCharsets.UTF_8).length <= MAX_PATH) return inside;
        String hash = Integer.toHexString(
                configDir.toAbsolutePath().normalize().toString().hashCode());
        return Path.of(System.getProperty("java.io.tmpdir"), AppInfo.ID + "-" + hash + ".sock");
    }

    /**
     * Starts listening; {@code onArgs} receives each later launch's arguments on a background thread.
     * Returns null when the socket cannot be bound (the app then simply runs without hand-off).
     */
    public static @Nullable SingleInstance listen(Path configDir, Consumer<List<String>> onArgs) {
        SingleInstance instance = new SingleInstance(socketPath(configDir));
        try {
            Files.createDirectories(configDir);
            Files.deleteIfExists(instance.socket);
            ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(instance.socket));
            instance.server = server;
        } catch (IOException | UnsupportedOperationException e) {
            LOG.log(Level.INFO, "Single-instance hand-off unavailable: " + e.getMessage());
            return null;
        }
        Thread thread = new Thread(() -> instance.accept(onArgs), "single-instance");
        thread.setDaemon(true);
        thread.start();
        return instance;
    }

    private void accept(Consumer<List<String>> onArgs) {
        ServerSocketChannel listening = server;
        while (listening != null && listening.isOpen()) {
            try (SocketChannel client = listening.accept()) {
                var in = Channels.newInputStream(client);
                int length = ByteBuffer.wrap(in.readNBytes(4)).getInt();
                if (length < 0 || length > 1 << 20) continue;
                String text = new String(in.readNBytes(length), StandardCharsets.UTF_8);
                // Delivered before the acknowledgement: once handOff returns, the running app has them.
                onArgs.accept(text.isEmpty() ? List.of() : List.of(text.split("\0", -1)));
                client.write(ByteBuffer.wrap(new byte[] {1}));
            } catch (IOException | RuntimeException e) {
                if (listening.isOpen()) LOG.log(Level.FINE, "Hand-off failed", e);
            }
        }
    }

    @Override
    public void close() {
        try {
            if (server != null) server.close();
            Files.deleteIfExists(socket);
        } catch (IOException e) {
            // best effort: a leftover socket is detected as stale next time
        }
    }
}
