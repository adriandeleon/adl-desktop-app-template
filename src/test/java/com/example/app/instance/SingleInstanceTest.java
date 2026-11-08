package com.example.app.instance;

import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleInstanceTest {

    @Test
    void aSecondLaunchHandsItsArgumentsToTheFirst(@TempDir Path dir) {
        List<List<String>> received = new CopyOnWriteArrayList<>();
        try (SingleInstance first = SingleInstance.listen(dir, received::add)) {
            assertNotNull(first);
            assertTrue(SingleInstance.handOff(dir, List.of("a.txt", "b c.txt")));
            assertTrue(SingleInstance.handOff(dir, List.of()));
            assertEquals(List.of(List.of("a.txt", "b c.txt"), List.of()), received);
        }
        assertFalse(Files.exists(SingleInstance.socketPath(dir)));
        assertFalse(SingleInstance.handOff(dir, List.of("x")));
    }

    /**
     * A launch that dies mid-message, or sends nonsense, is dropped and the next one is still served. These
     * paths ran only by chance before, so the package's coverage floor held on some runs and not others.
     */
    @Test
    void aBrokenOrBogusHandOffIsDroppedAndTheNextIsServed(@TempDir Path dir) throws Exception {
        List<List<String>> received = new CopyOnWriteArrayList<>();
        try (SingleInstance first = SingleInstance.listen(dir, received::add)) {
            assertNotNull(first);
            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(SingleInstance.socketPath(dir));
            try (SocketChannel dropped = SocketChannel.open(address)) {
                dropped.write(ByteBuffer.wrap(new byte[] {0, 0})); // half a length, then gone
            }
            try (SocketChannel bogus = SocketChannel.open(address)) {
                bogus.write(ByteBuffer.allocate(4).putInt(-1).flip()); // a length no launch sends
            }
            assertTrue(SingleInstance.handOff(dir, List.of("after.txt")));
            assertEquals(List.of(List.of("after.txt")), received);
        }
    }

    @Test
    void aStaleSocketIsNotARunningApp(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(SingleInstance.SOCKET), "stale");
        assertFalse(SingleInstance.handOff(dir, List.of()));
    }

    @Test
    void aLongFolderGetsASocketInTheTempFolder(@TempDir Path dir) {
        Path deep = dir.resolve("a".repeat(60)).resolve("b".repeat(60));
        Path socket = SingleInstance.socketPath(deep);
        assertTrue(socket.startsWith(Path.of(System.getProperty("java.io.tmpdir"))));
        assertEquals(dir.resolve(SingleInstance.SOCKET).toAbsolutePath(), SingleInstance.socketPath(dir));
        List<List<String>> received = new CopyOnWriteArrayList<>();
        try (SingleInstance first = SingleInstance.listen(deep, received::add)) {
            assertNotNull(first);
            assertTrue(SingleInstance.handOff(deep, List.of("z")));
        }
    }

    @Test
    void anUnusableFolderMeansNoHandOff(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("file");
        Files.writeString(file, "x");
        assertNull(SingleInstance.listen(file.resolve("sub"), args -> {}));
    }
}
