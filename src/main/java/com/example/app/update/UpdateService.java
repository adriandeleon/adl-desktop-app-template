package com.example.app.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import com.example.app.AppInfo;
import org.jspecify.annotations.Nullable;

/**
 * Asks GitHub's {@code releases/latest} whether there is a newer release. Blocking: callers run it off
 * the FX thread. Sends nothing about the user beyond what any HTTP request carries, and never
 * downloads or installs anything.
 */
public final class UpdateService {

    /** What came back: a newer release, nothing newer, or a failure with a short reason. */
    public record Outcome(
            @Nullable ReleaseInfo newer, @Nullable String error) {
        public boolean upToDate() {
            return newer == null && error == null;
        }
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    /** A latest-release answer is a few kilobytes; a body past this is refused unread (PERF-12). */
    public static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private final String endpoint;

    /** Overrides the endpoint; the test suite points it at a closed local port so no test reaches GitHub. */
    public static final String PROPERTY = AppInfo.ID + ".updateUrl";

    public UpdateService() {
        this(System.getProperty(PROPERTY, AppInfo.LATEST_RELEASE_API));
    }

    public UpdateService(String endpoint) {
        this.endpoint = endpoint;
    }

    /** One check against {@code currentVersion}. */
    public Outcome check(String currentVersion) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", AppInfo.NAME + "/" + AppInfo.VERSION)
                .GET()
                .build();
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            HttpResponse<java.io.InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body;
            try (java.io.InputStream in = response.body()) {
                byte[] bytes = in.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) return new Outcome(null, "response too large");
                body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
            // 404: no release published yet. Nothing newer, not an error.
            if (response.statusCode() == 404) return new Outcome(null, null);
            if (response.statusCode() != 200) return new Outcome(null, "HTTP " + response.statusCode());
            ReleaseInfo latest = UpdateCheck.parseLatest(body);
            boolean newer = latest != null && UpdateCheck.isNewer(currentVersion, latest.version());
            return new Outcome(newer ? latest : null, null);
        } catch (HttpTimeoutException e) {
            return new Outcome(null, "timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome(null, "interrupted");
        } catch (IOException | IllegalArgumentException e) {
            return new Outcome(null, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
