package com.example.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;

import com.example.app.AppInfo;
import com.example.app.report.Redactor;
import com.example.app.update.ReleaseInfo;
import com.example.app.update.UpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** About, Report a Problem and the Debug Log window. */
@Tag("fx")
class DialogsFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private AppServices services;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        services = app.services();
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    private static String text(Node block) {
        StringBuilder out = new StringBuilder();
        for (Node node : block.lookupAll(".label")) {
            if (node instanceof Label label) out.append(label.getText()).append('|');
        }
        for (Node node : block.lookupAll(".hyperlink")) {
            if (node instanceof Hyperlink link) out.append(link.getText()).append('|');
        }
        return out.toString();
    }

    @Test
    void aboutShowsEveryUpdateState() throws Exception {
        AboutDialog about = call(() -> new AboutDialog(services, app.window().stage()));
        run(about::show);
        assertTrue(call(() -> about.dialog().isShowing()));
        assertTrue(text(about.updateBlock()).contains("Not checked for updates yet."));
        UpdateCoordinator updates = services.updates();

        run(() -> updates.finish(new UpdateService.Outcome(null, null), false));
        assertTrue(call(() -> text(about.updateBlock())).contains("You are up to date."));
        assertTrue(call(() -> text(about.updateBlock())).contains("Last checked just now."));

        run(() -> updates.finish(
                new UpdateService.Outcome(new ReleaseInfo("2.0.0", "https://example.com/2"), null), false));
        assertTrue(call(() -> text(about.updateBlock())).contains("Update available: 2.0.0"));
        run(() -> about.updateBlock().lookupAll(".hyperlink").forEach(n -> ((Hyperlink) n).fire()));
        FxTestSupport.waitUntil(() -> updates.state() != UpdateCoordinator.State.CHECKING);
        assertTrue(host.opened.contains("https://example.com/2"));

        run(() -> updates.finish(new UpdateService.Outcome(null, "offline"), false));
        assertTrue(call(() -> text(about.updateBlock())).contains("Update check failed: offline"));

        run(() -> about.copyButton().fire());
        assertEquals("Copied", call(() -> about.copyButton().getText()));
        run(() -> about.dialog().getDialogPane().lookupAll(".hyperlink").forEach(n -> {
            if (!((Hyperlink) n).getText().equals("Check now")) ((Hyperlink) n).fire();
        }));
        assertTrue(host.opened.stream().anyMatch(u -> u.endsWith("settings.json")));
        // ABOUT-05/06: "Built to Pauta <edition>" opens the edition's own page.
        assertTrue(host.opened.contains("https://pauta-project.dev/" + AppInfo.PAUTA_EDITION + "/"));
        run(() -> about.dialog().close());
    }

    @Test
    void reportAProblemRedactsCopiesAndOpensTheIssue() throws Exception {
        Logger.getLogger("test").warning("token ghp_abcdefghijklmnopqrstuvwxyz0123 in the log");
        ReportProblemWindow report =
                call(() -> new ReportProblemWindow(services, null, new Redactor("/nowhere", "", "")));
        run(report::show);
        assertFalse(call(() -> report.report().getText()).contains("ghp_"));
        run(() -> report.report().appendText("\nMy note"));
        run(() -> ((Button) report.dialog().getDialogPane().lookupButton(report.submitButton())).fire());
        assertEquals(1, host.opened.size());
        assertTrue(host.opened.getFirst().contains("My%20note"));
        assertNotNull(call(() -> new ReportProblemWindow(services, null)));
    }

    @Test
    void theDebugLogWindowRefreshesExportsAndClears() throws Exception {
        Logger.getLogger("test").warning("something to see");
        run(app.window()::openDebugLog);
        DebugLogWindow window = app.window().debugLogWindow();
        assertNotNull(window);
        assertTrue(call(() -> window.text().getText()).contains("something to see"));

        host.saveAnswer = dir.resolve("debug.log");
        run(window::export);
        assertTrue(Files.readString(host.saveAnswer).contains("something to see"));
        host.saveAnswer = dir.resolve("no/such/folder/debug.log");
        run(window::export);
        assertTrue(call(() -> app.window().statusBar().echo().getText()).startsWith("Saving the Debug Log failed"));
        host.saveAnswer = null;
        run(window::export);

        run(() -> window.stage().getScene().getRoot().lookupAll(".button").forEach(n -> {
            String label = ((Button) n).getText();
            if (!label.startsWith("Export") && !label.startsWith("Report")) ((Button) n).fire();
        }));
        assertFalse(call(() -> window.text().getText()).contains("something to see"));
        run(() -> FxTestSupport.press(window.stage().getScene(), KeyCode.ESCAPE, false, false, false, false));
        assertFalse(call(() -> window.stage().isShowing()));
        run(app.window()::openDebugLog);
        assertTrue(call(() -> window.stage().isShowing()));
    }
}
