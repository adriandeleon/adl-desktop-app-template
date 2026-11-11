package com.example.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.example.app.AppInfo;
import com.example.app.PautaConformance;
import com.example.app.PautaConformance.Conformance;
import com.example.app.PautaConformance.Entry;
import com.example.app.PautaConformance.Kind;
import com.example.app.report.Redactor;
import com.example.app.update.ReleaseInfo;
import com.example.app.update.UpdateService;
import org.jspecify.annotations.Nullable;
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

/** About, the Pauta window, Report a Problem and the Debug Log window. */
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
    void theConfirmDialogNamesTheActionInRedAndEveryOtherWayOutIsCancel() throws Exception {
        Confirmations.Question question = Confirmations.importConfig(
                "backup.zip",
                java.util.List.of(
                        com.example.app.config.ConfigImport.Part.PREFERENCES,
                        com.example.app.config.ConfigImport.Part.KEY_BINDINGS));
        javafx.scene.control.Alert alert =
                call(() -> Confirm.dialog(question, app.window().stage()));
        var buttons = alert.getButtonTypes();
        assertEquals(2, buttons.size());
        var action = buttons.get(0);
        var cancel = buttons.get(1);
        assertEquals("Overwrite", action.getText());
        assertEquals(javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE, cancel.getButtonData());
        assertEquals("Cancel", cancel.getText());
        assertTrue(call(
                () -> alert.getDialogPane().lookupButton(action).getStyleClass().contains("danger")));
        assertFalse(call(
                () -> alert.getDialogPane().lookupButton(cancel).getStyleClass().contains("danger")));
        // One check box per part, all ticked; with none ticked the action waits.
        java.util.List<javafx.scene.control.CheckBox> boxes =
                call(() -> alert.getDialogPane().lookupAll(".check-box").stream()
                        .map(n -> (javafx.scene.control.CheckBox) n)
                        .toList());
        assertEquals(2, boxes.size());
        run(() -> boxes.forEach(b -> b.setSelected(false)));
        assertTrue(call(() -> alert.getDialogPane().lookupButton(action).isDisabled()));
        assertFalse(question.choices().get(0).ticked());
        run(() -> boxes.get(1).setSelected(true));
        assertFalse(call(() -> alert.getDialogPane().lookupButton(action).isDisabled()));
        assertTrue(question.choices().get(1).ticked());
        assertTrue(Confirm.confirmed(alert, java.util.Optional.of(action)));
        assertFalse(Confirm.confirmed(alert, java.util.Optional.of(cancel)));
        assertFalse(Confirm.confirmed(alert, java.util.Optional.empty()), "Escape or the close button");
    }

    /** CONF-13: Cancel is the one default button, so Enter in a question that just opened keeps everything. */
    @Test
    void theConfirmDialogsDefaultIsCancelNeverTheRedAction() throws Exception {
        Confirmations.Question question = Confirmations.importConfig(
                "backup.zip", java.util.List.of(com.example.app.config.ConfigImport.Part.PREFERENCES));
        javafx.scene.control.Alert alert =
                call(() -> Confirm.dialog(question, app.window().stage()));
        var action = alert.getButtonTypes().get(0);
        var cancel = alert.getButtonTypes().get(1);
        assertFalse(call(
                () -> ((javafx.scene.control.Button) alert.getDialogPane().lookupButton(action)).isDefaultButton()));
        assertTrue(call(
                () -> ((javafx.scene.control.Button) alert.getDialogPane().lookupButton(cancel)).isDefaultButton()));
    }

    /**
     * WIN-11 and CONF-14: every dialog gets one button order, Cancel before every action, and JavaFX's stock
     * buttons are worded from the catalog, not in the JVM's locale.
     */
    @Test
    void everyDialogHasOneButtonOrderAndCatalogWords() throws Exception {
        java.util.Locale was = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMAN); // what JavaFX would word OK and Cancel in
            javafx.scene.control.TextInputDialog prompt = call(() -> Dialogs.styled(
                    new javafx.scene.control.TextInputDialog("x"), app.window().stage()));
            javafx.scene.control.ButtonBar bar = call(() ->
                    (javafx.scene.control.ButtonBar) prompt.getDialogPane().lookup(".button-bar"));
            String order = call(bar::getButtonOrder);
            assertEquals(Dialogs.BUTTON_ORDER, order);
            for (char action : "UYNOA".toCharArray()) {
                assertTrue(order.indexOf('C') < order.indexOf(action), "Cancel before " + action);
            }
            assertEquals(
                    "OK",
                    call(() -> ((javafx.scene.control.Button)
                                    prompt.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK))
                            .getText()));
            assertEquals(
                    "Cancel",
                    call(() -> ((javafx.scene.control.Button)
                                    prompt.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL))
                            .getText()));
            // A stock button added after the dialog was styled is worded too.
            run(() -> prompt.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.APPLY));
            assertEquals(
                    "Apply",
                    call(() -> ((javafx.scene.control.Button)
                                    prompt.getDialogPane().lookupButton(javafx.scene.control.ButtonType.APPLY))
                            .getText()));
        } finally {
            java.util.Locale.setDefault(was);
        }
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
            String link = ((Hyperlink) n).getText();
            if (!link.equals("Check now") && !link.startsWith("Pauta conformance")) ((Hyperlink) n).fire();
        }));
        assertTrue(host.opened.stream().anyMatch(u -> u.endsWith("settings.json")));
        // ABOUT-05/06: the edition's link ("Built to", or at Partial "Working toward") opens its own page.
        assertTrue(host.opened.contains("https://pauta-project.dev/" + AppInfo.PAUTA_EDITION + "/"));
        run(() -> about.dialog().close());
    }

    private static Entry entry(String rule, String level, Kind kind, @Nullable String link) {
        return new Entry(
                rule, level, "https://pauta-project.dev/6.0.0/x/#" + rule.toLowerCase(), kind, rule + " reason.", link);
    }

    private static @Nullable Stage pautaStage() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && "Pauta Conformance".equals(stage.getTitle())) return stage;
        }
        return null;
    }

    @Test
    void aboutNamesTheConformanceAndItsLinkOpensThePautaWindow() throws Exception {
        assertEquals("about.pautaWorkingToward", AboutDialog.editionKey(Conformance.PARTIAL));
        assertEquals("about.pauta", AboutDialog.editionKey(Conformance.CORE));
        assertEquals("about.pauta", AboutDialog.editionKey(Conformance.COMPLETE));

        AboutDialog about = call(() -> new AboutDialog(services, app.window().stage()));
        run(about::show);
        Conformance conformance = PautaConformance.conformance();
        String all = call(() -> text(about.dialog().getDialogPane()));
        String edition = (conformance == Conformance.PARTIAL ? "Working toward Pauta " : "Built to Pauta ")
                + AppInfo.PAUTA_EDITION;
        assertTrue(all.contains(edition + "|"), all);
        assertTrue(all.contains("Pauta conformance: " + conformance.english() + "|"), all);
        assertTrue(all.contains(PautaConformance.environmentLine(PautaConformance.exceptions())), "environment line");

        run(() -> about.dialog().getDialogPane().lookupAll(".hyperlink").forEach(n -> {
            if (((Hyperlink) n).getText().startsWith("Pauta conformance")) ((Hyperlink) n).fire();
        }));
        Stage pauta = call(DialogsFxTest::pautaStage);
        assertNotNull(pauta, "the conformance link opens the Pauta window");
        run(pauta::close);
        run(() -> about.dialog().close());
    }

    @Test
    void thePautaWindowListsEveryExceptionPendingFirst() throws Exception {
        List<Entry> list = List.of(
                entry("TEST-01", "MUST", Kind.NOT_APPLICABLE, null),
                entry("TEST-02", "SHOULD", Kind.PENDING, "https://github.com/a/b/issues/5"),
                entry(
                        "TEST-03",
                        "SHOULD",
                        Kind.DEVIATION,
                        "https://github.com/a/b/blob/master/docs/decisions/0003-x.md"),
                entry("TEST-04", "MUST", Kind.PENDING, "https://github.com/a/b/issues/7"));
        PautaWindow window = call(() -> new PautaWindow(services, app.window().stage(), list));
        run(window::show);
        String all = call(() -> text(window.dialog().getDialogPane()));
        assertTrue(all.contains("Pauta " + AppInfo.PAUTA_EDITION + "|Partial|"), all);
        assertTrue(all.contains("ExampleApp does not meet every MUST yet: 1 is pending.|"), all);
        assertTrue(all.contains("2 pending · 1 deviation · 1 not applicable|"), all);

        String rows = call(() -> text(window.list()));
        assertTrue(rows.startsWith("Pending|MUST|SHOULD|Deviation|SHOULD|Not applicable|MUST|"), rows);
        assertTrue(rows.contains("TEST-04|#7|TEST-02|#5|TEST-03|Decision 0003|TEST-01|"), rows);

        run(() -> window.list().lookupAll(".hyperlink").forEach(n -> ((Hyperlink) n).fire()));
        assertTrue(host.opened.contains("https://pauta-project.dev/6.0.0/x/#test-04"));
        assertTrue(host.opened.contains("https://github.com/a/b/issues/7"));
        run(() -> window.dialog().getDialogPane().lookupAll(".hyperlink").forEach(n -> {
            String link = ((Hyperlink) n).getText();
            if (link.equals("Pauta " + AppInfo.PAUTA_EDITION + " rules") || link.equals("Conformance in the docs"))
                ((Hyperlink) n).fire();
        }));
        assertTrue(host.opened.contains(AppInfo.pautaUrl()), "PWIN-04: the edition's own page");
        assertTrue(host.opened.contains(AppInfo.pautaDocsUrl()), "PWIN-07: the docs page");

        run(() -> window.copyButton().fire());
        assertEquals("Copied", call(() -> window.copyButton().getText()));
        assertTrue(window.dialog().isShowing(), "Copy as text leaves the window open");
        run(() -> window.dialog().close());

        String markdown = PautaWindow.markdown(list);
        assertTrue(markdown.startsWith("# Pauta " + AppInfo.PAUTA_EDITION + ": Partial\n"), markdown);
        assertTrue(
                markdown.contains("\n## Pending\n\n- [TEST-04](https://pauta-project.dev/6.0.0/x/#test-04) MUST: "
                        + "TEST-04 reason. [#7](https://github.com/a/b/issues/7)\n"),
                markdown);
        assertTrue(
                markdown.contains("- [TEST-03](https://pauta-project.dev/6.0.0/x/#test-03) SHOULD: TEST-03 reason. "
                        + "[Decision 0003](https://github.com/a/b/blob/master/docs/decisions/0003-x.md)\n"),
                markdown);
        assertTrue(markdown.contains("\n## Not applicable\n\n- [TEST-01]"), markdown);
    }

    @Test
    void withNoExceptionsThePautaWindowSaysSoInOneLine() throws Exception {
        PautaWindow window = call(() -> new PautaWindow(services, null, List.of()));
        run(window::show);
        String none = "No exceptions: ExampleApp meets every MUST and SHOULD of Pauta " + AppInfo.PAUTA_EDITION + ".";
        assertEquals(none + "|", call(() -> text(window.list())));
        assertTrue(call(() -> text(window.dialog().getDialogPane())).contains("|Complete|"));
        run(() -> window.copyButton().fire());
        assertEquals("Copied", call(() -> window.copyButton().getText()));
        run(() -> window.dialog().close());
        assertTrue(PautaWindow.markdown(List.of()).contains(none));
        assertEquals("", PautaWindow.counts(List.of()));
        assertEquals(
                "ExampleApp meets every MUST of this edition; 2 SHOULDs are pending.",
                PautaWindow.meaning(
                        Conformance.CORE,
                        List.of(
                                entry("TEST-05", "SHOULD", Kind.PENDING, "l"),
                                entry("TEST-06", "SHOULD", Kind.PENDING, "l"))));
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

        // Export runs a command and writes off the FX thread (PERF-01): wait for its echo.
        host.saveAnswer = dir.resolve("debug.log");
        run(() -> services.commands().run("debugLog.export"));
        FxTestSupport.waitUntil(() -> app.window().statusBar().echo().getText().startsWith("Saved the Debug Log"));
        assertTrue(Files.readString(host.saveAnswer).contains("something to see"));
        host.saveAnswer = dir.resolve("no/such/folder/debug.log");
        run(() -> services.commands().run("debugLog.export"));
        FxTestSupport.waitUntil(
                () -> app.window().statusBar().echo().getText().startsWith("Saving the Debug Log failed"));
        host.saveAnswer = null;
        run(() -> services.commands().run("debugLog.export"));
        assertTrue(call(() -> app.window().isDebugLogOpen()));
        assertEquals(null, services.commands().get("debugLog.refresh").unavailableReason());

        run(() -> window.stage().getScene().getRoot().lookupAll(".button").forEach(n -> {
            String label = ((Button) n).getText();
            if (!label.startsWith("Export") && !label.startsWith("Report")) ((Button) n).fire();
        }));
        assertFalse(call(() -> window.text().getText()).contains("something to see"));
        run(() -> FxTestSupport.press(window.stage().getScene(), KeyCode.ESCAPE, false, false, false, false));
        assertFalse(call(() -> window.stage().isShowing()));
        assertNotNull(services.commands().get("debugLog.refresh").unavailableReason());
        run(app.window()::openDebugLog);
        assertTrue(call(() -> window.stage().isShowing()));
    }
}
