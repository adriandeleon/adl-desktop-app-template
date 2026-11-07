package com.example.app.update;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {

    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = "";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/latest", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private UpdateService service() {
        return new UpdateService("http://127.0.0.1:" + server.getAddress().getPort() + "/latest");
    }

    @Test
    void findsANewerRelease() {
        body = "{\"tag_name\":\"v9.0.0\",\"html_url\":\"https://example.com/r\"}";
        UpdateService.Outcome outcome = service().check("1.0.0");
        assertNotNull(outcome.newer());
        assertEquals("9.0.0", outcome.newer().version());
    }

    @Test
    void anOversizedAnswerIsRefusedUnread() {
        body = "{\"tag_name\":\"v9.0.0\",\"body\":\"" + "x".repeat(UpdateService.MAX_RESPONSE_BYTES) + "\"}";
        assertEquals("response too large", service().check("1.0.0").error());
    }

    @Test
    void sameVersionIsUpToDate() {
        body = "{\"tag_name\":\"v1.0.0\"}";
        assertTrue(service().check("1.0.0").upToDate());
    }

    @Test
    void noReleaseYetIsNotAnError() {
        status = 404;
        assertTrue(service().check("1.0.0").upToDate());
    }

    @Test
    void otherStatusesAndNoServerAreErrors() {
        status = 500;
        assertEquals("HTTP 500", service().check("1.0.0").error());
        stop();
        UpdateService.Outcome down = service().check("1.0.0");
        assertNull(down.newer());
        assertNotNull(down.error());
        assertNotNull(new UpdateService().toString());
    }
}
