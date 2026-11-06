package com.example.app.ui;

import java.nio.file.Path;

import com.example.app.command.Command;
import com.example.app.i18n.Messages;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The command sweep: every registered command runs in a real headless window without failing. */
@Tag("fx")
class CommandSweepFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @Test
    void everyCommandIsLocalizedAndRuns(@TempDir Path dir) throws Exception {
        FakeHost host = new FakeHost();
        Startup.Running app = FxTestSupport.start(dir, host);
        try {
            for (Command command : app.services().commands().all()) {
                assertTrue(Messages.has("command." + command.id()), command.id() + " has no title");
                assertTrue(Messages.has("command." + command.id() + ".desc"), command.id() + " has no description");
                run(() -> {
                    app.window().overlays().hide();
                    app.services().commands().run(command.id());
                });
                assertFalse(call(DebugLog::text).contains("Command " + command.id() + " failed"), command.id());
            }
        } finally {
            FxTestSupport.stop(app);
        }
    }
}
