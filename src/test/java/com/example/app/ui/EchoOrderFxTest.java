package com.example.app.ui;

import java.nio.file.Path;
import java.util.List;

import com.example.app.i18n.Messages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A message from a background thread waits in a batch for the FX thread (PERF-07); one said on the FX
 * thread afterwards must still come after it, or the status bar shows the older one as the newest.
 */
@Tag("fx")
class EchoOrderFxTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @Test
    void aBackgroundMessageSaidFirstStaysFirst() throws Exception {
        var app = FxTestSupport.start(dir, new FakeHost());
        try {
            AppServices services = app.services();
            // A drain just ran, so the next background message waits out the batcher's interval...
            Thread.ofPlatform()
                    .start(() -> services.echoText("zero", MessageLog.Severity.INFO))
                    .join();
            FxTestSupport.waitUntil(() -> !services.messages().entries().isEmpty());
            // ...and is still queued when the FX thread says the second, at once.
            run(() -> {
                try {
                    Thread.ofPlatform()
                            .start(() -> services.echoText("first", MessageLog.Severity.INFO))
                            .join();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                services.echoText("second", MessageLog.Severity.INFO);
            });
            List<String> newestFirst = call(() -> services.messages().entries().stream()
                    .map(MessageLog.Entry::text)
                    .limit(2)
                    .toList());
            assertEquals(List.of("second", "first"), newestFirst);
            assertEquals("second", call(() -> app.window().statusBar().echo().getText()));
        } finally {
            FxTestSupport.stop(app);
            Messages.init("en");
        }
    }
}
