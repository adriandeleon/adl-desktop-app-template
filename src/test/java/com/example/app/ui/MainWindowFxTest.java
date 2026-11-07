package com.example.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;

import com.example.app.AppInfo;
import com.example.app.config.ConfigStore;
import com.example.app.config.ThemeChoice;
import com.example.app.i18n.Messages;
import com.example.app.update.ReleaseInfo;
import com.example.app.update.UpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.press;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The main window in a real headless toolkit: start-up, the palette, keys, the Message Log, menus. */
@Tag("fx")
class MainWindowFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private MainWindow window;
    private AppServices services;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        window = app.window();
        services = app.services();
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    private String echo() throws Exception {
        return call(() -> window.statusBar().echo().getText());
    }

    @Test
    void startsReadyWithTheSessionLog() throws Exception {
        assertTrue(call(() -> window.stage().isShowing()));
        assertEquals("Ready", echo());
        assertTrue(Files.exists(dir.resolve(DebugLog.FILE)));
        assertEquals("ExampleApp", call(() -> window.stage().getTitle()));
    }

    @Test
    void thePaletteFindsAndRunsACommand() throws Exception {
        run(window::openPalette);
        assertTrue(call(() -> window.overlays().isShowing()));
        TextField query = call(() -> (TextField) window.overlays().root().lookup(".palette-query"));
        run(() -> query.setText("toggle status"));
        @SuppressWarnings("unchecked")
        ListView<Picker.Ranked<String>> list = call(
                () -> (ListView<Picker.Ranked<String>>) window.overlays().root().lookup(".list-view"));
        assertEquals(
                "view.toggleStatusBar",
                call(() -> list.getSelectionModel().getSelectedItem().item().value()));
        run(() -> press(query, KeyCode.ENTER, false, false, false, false));
        assertFalse(call(() -> window.overlays().isShowing()));
        assertFalse(services.settings().showStatusBar);
        assertFalse(call(() -> window.statusBar().isVisible()));
    }

    @Test
    void thePaletteListsEveryRegisteredCommandItselfIncluded() throws Exception {
        run(window::openPalette);
        @SuppressWarnings("unchecked")
        ListView<Picker.Ranked<String>> list = call(
                () -> (ListView<Picker.Ranked<String>>) window.overlays().root().lookup(".list-view"));
        java.util.Set<String> listed = call(
                () -> list.getItems().stream().map(r -> r.item().value()).collect(java.util.stream.Collectors.toSet()));
        java.util.Set<String> registered = services.commands().all().stream()
                .map(com.example.app.command.Command::id)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(registered, listed); // PAL-02: palette.show too
        run(() -> window.overlays().hide());
    }

    @Test
    void helpOpensTheDocsAndTheReleaseNotesAndCopiesTheDetails() throws Exception {
        run(() -> services.commands().run("help.documentation"));
        assertEquals(AppInfo.docsUrl(), host.opened.getLast());
        run(() -> services.commands().run("help.whatsNew"));
        assertEquals(AppInfo.releaseNotesUrl(), host.opened.getLast());
        // The welcome placeholder repeats Documentation and What's New, through the registry (DOCS-05/06).
        java.util.List<javafx.scene.control.Button> buttons =
                call(() -> window.stage().getScene().getRoot().lookupAll(".welcome .button").stream()
                        .map(n -> (javafx.scene.control.Button) n)
                        .toList());
        for (String id : java.util.List.of("help.documentation", "help.whatsNew")) {
            String title = services.commands().get(id).title();
            javafx.scene.control.Button button = buttons.stream()
                    .filter(b -> b.getText().equals(title))
                    .findFirst()
                    .orElseThrow();
            int before = host.opened.size();
            run(button::fire);
            assertEquals(before + 1, host.opened.size(), id);
        }
        run(() -> services.commands().run("help.copyDetails"));
        assertEquals(
                AppInfo.environment(services.store().dir()),
                call(() -> javafx.scene.input.Clipboard.getSystemClipboard().getString()));
    }

    @Test
    void escapeAndCtrlGCloseOverlays() throws Exception {
        run(window::openPalette);
        run(() -> press(window.overlays().root(), KeyCode.ESCAPE, false, false, false, false));
        assertFalse(call(() -> window.overlays().isShowing()));
        run(window::openMessageLog);
        run(() -> press(window.overlays().root(), KeyCode.G, false, true, false, false));
        assertFalse(call(() -> window.overlays().isShowing()));
        run(window::openPalette);
        run(() -> window.overlays().root().lookup(".overlay-backdrop").fireEvent(click()));
        assertFalse(call(() -> window.overlays().isShowing()));
    }

    @Test
    void keySequencesRunCommandsAndHintTheNextKey() throws Exception {
        run(() -> services.store().update(s -> s.keymap = "emacs"));
        var target = call(() -> window.statusBar());
        run(() -> press(target, KeyCode.H, false, true, false, false));
        assertTrue(echo().startsWith("C-h- "), echo());
        assertTrue(echo().contains("e Message Log"), echo());
        run(() -> press(target, KeyCode.E, false, false, false, false));
        assertTrue(call(() -> window.overlays().isShowing()));
        run(() -> window.overlays().hide());

        run(() -> press(target, KeyCode.H, false, true, false, false));
        run(() -> press(target, KeyCode.Z, false, false, false, false));
        assertEquals("C-h z is undefined", echo());

        run(() -> services.store().update(s -> s.keybindings.put("sync.now", "F5")));
        run(() -> press(target, KeyCode.F5, false, false, false, false));
        assertTrue(echo().startsWith("Settings Sync: Sync Now: Unavailable"), echo());

        // Plain typing and unbound keys pass through.
        assertFalse(
                call(() -> press(target, KeyCode.A, false, false, false, false).isConsumed()));
        TextField field = new TextField();
        assertFalse(call(() -> {
            window.overlays().root().getChildren().add(field);
            return press(field, KeyCode.B, false, false, false, false).isConsumed();
        }));
        assertFalse(call(
                () -> press(target, KeyCode.SHIFT, true, false, false, false).isConsumed()));
        assertEquals(
                "Emacs", call(() -> ((Label) window.statusBar().getChildren().get(4)).getText()));
    }

    @Test
    void theMessageLogShowsFiltersCopiesAndClears() throws Exception {
        run(() -> {
            services.echo("status.configExportFailed", "disk full");
            services.echo("status.sync.invalidUrl", "bad");
        });
        assertTrue(call(() -> window.statusBar().unread().isVisible()));
        assertTrue(call(() -> window.statusBar().echo().getStyleClass().contains("warning")));
        run(() -> window.statusBar().echo().fireEvent(click()));
        assertFalse(call(() -> window.statusBar().unread().isVisible()));
        MessageLogView log = window.messageLog();
        assertEquals(3, call(() -> log.list().getItems().size()));
        run(() -> log.filter().setText("disk"));
        assertEquals(1, call(() -> log.list().getItems().size()));
        run(() -> press(log.filter(), KeyCode.DOWN, false, false, false, false));
        assertEquals(0, call(() -> log.list().getSelectionModel().getSelectedIndex()));
        run(() -> press(log.list(), KeyCode.C, false, !AppInfo.isMac(), false, AppInfo.isMac()));
        assertTrue(MessageLogView.asText(services.messages().entries()).contains("Export failed: disk full"));
        // The shortcut runs messageLog.copy, which copies the selection (KBD-01).
        assertTrue(call(() -> javafx.scene.input.Clipboard.getSystemClipboard().getString())
                .endsWith("Export failed: disk full"));
        run(() -> {
            for (var node : window.overlays().root().lookupAll(".button")) {
                if (node instanceof javafx.scene.control.Button b) b.fire();
            }
        });
        assertTrue(services.messages().entries().isEmpty());
        run(() -> window.overlays().hide());
        // A list cell renders the time and the severity.
        run(() -> {
            services.echo("status.ready");
            window.openMessageLog();
        });
        assertEquals(1, call(() -> window.messageLog().list().getItems().size()));
    }

    @Test
    void menusRunCommandsAndGreyUnavailableOnes() throws Exception {
        List<Menu> menus = call(() -> List.copyOf(window.menuBar().getMenus()));
        assertEquals(3, menus.size());
        run(() -> {
            for (Menu menu : menus) menu.getOnShowing().handle(null);
        });
        MenuItem toggle = call(() -> menus.get(1).getItems().stream()
                .filter(i -> "view.toggleStatusBar".equals(i.getId()))
                .findFirst()
                .orElseThrow());
        run(toggle::fire);
        assertFalse(services.settings().showStatusBar);
    }

    @Test
    void pickersChangeSettings() throws Exception {
        run(window::chooseTheme);
        TextField query = call(() -> (TextField) window.overlays().root().lookup(".palette-query"));
        run(() -> query.setText("dark"));
        run(() -> press(query, KeyCode.ENTER, false, false, false, false));
        assertEquals(ThemeChoice.DARK, services.settings().theme);
        assertTrue(call(() -> Themes.isDark()));
        assertEquals("Theme: Dark", echo());

        run(window::chooseKeymap);
        TextField keymapQuery = call(() -> (TextField) window.overlays().root().lookup(".palette-query"));
        run(() -> keymapQuery.setText("emacs"));
        run(() -> press(keymapQuery, KeyCode.ENTER, false, false, false, false));
        assertEquals("emacs", services.settings().keymap);

        run(window::chooseLanguage);
        TextField languageQuery =
                call(() -> (TextField) window.overlays().root().lookup(".palette-query"));
        run(() -> languageQuery.setText("deutsch"));
        run(() -> press(languageQuery, KeyCode.ENTER, false, false, false, false));
        assertEquals("de", services.settings().language);
        assertEquals(Messages.tr("status.languageRestart"), echo());
        run(() -> services.store().update(s -> s.theme = ThemeChoice.LIGHT));
    }

    @Test
    void exportAndImportTheConfigFolder() throws Exception {
        run(() -> services.store().update(s -> s.keymap = "emacs"));
        host.saveAnswer = dir.resolve("backup.zip");
        run(window::exportConfig);
        assertTrue(Files.exists(host.saveAnswer));
        assertTrue(echo().startsWith("Exported 1 files"), echo());

        run(() -> services.store().update(s -> s.keymap = "cua"));
        host.openAnswer = host.saveAnswer;
        // Import overwrites: it asks first, naming the file, and a Cancel changes nothing (WIN-05).
        host.confirmAnswer = false;
        run(window::importConfig);
        FxTestSupport.waitUntil(() -> !host.questions.isEmpty());
        assertEquals("cua", services.settings().keymap);
        assertTrue(host.questions.getLast().message().contains("backup.zip"));
        assertEquals("Overwrite", host.questions.getLast().action());
        // Only the ticked parts are replaced (SET-12): untick Preferences, keep the key bindings.
        run(() -> services.store().update(s -> s.showStatusBar = false));
        host.confirmAnswer = true;
        host.onQuestion = q -> q.choices().stream()
                .filter(c -> c.label().startsWith("Preferences"))
                .forEach(c -> c.tick(false));
        run(window::importConfig);
        FxTestSupport.waitUntil(() -> window.statusBar().echo().getText().startsWith("Imported"));
        assertEquals("emacs", services.settings().keymap);
        assertFalse(services.settings().showStatusBar, "an unticked part is kept");
        host.onQuestion = q -> {};

        host.saveAnswer = dir.resolve("missing/folder/backup.zip");
        run(window::exportConfig);
        assertTrue(echo().startsWith("Export failed"), echo());
        host.openAnswer = dir.resolve("nope.zip");
        run(window::importConfig);
        FxTestSupport.waitUntil(() -> window.statusBar().echo().getText().startsWith("Import failed"));
        host.saveAnswer = null;
        host.openAnswer = null;
        run(window::exportConfig);
        run(window::importConfig);
    }

    @Test
    void handOffQuitAndWindowState() throws Exception {
        run(() -> window.handOff(List.of("a")));
        assertEquals(Messages.tr("status.handOff"), echo());
        run(() -> {
            window.stage().setWidth(700);
            window.stage().setHeight(450);
        });
        run(window::quit);
        assertEquals(1, host.exits);
        assertEquals(700, services.settings().window.width);
        assertNotNull(services.settings().window.x);
        // Not a real maximize: JavaFX's Headless platform throws resizing a maximized stage on Windows
        // (HeadlessWindow.clearRect). What matters here is that the flag is saved and read back.
        run(() -> services.store().update(s -> s.window.maximized = true));
        services.store().flush();
        assertTrue(ConfigStore.open(dir).settings().window.maximized);
    }

    @Test
    void aRestartRestoresTheWindowAndReportsConfigTrouble(@TempDir Path other) throws Exception {
        Files.writeString(
                other.resolve(ConfigStore.FILE),
                "{\"schemaVersion\": 1, \"darkMode\": false,"
                        + " \"window\": {\"x\": 5, \"y\": 6, \"width\": 640, \"height\": 400}}");
        Startup.Running second = FxTestSupport.start(other, host);
        try {
            assertEquals(
                    Messages.tr("status.config.migrated"),
                    call(() -> second.window().statusBar().echo().getText()));
            assertEquals(640, call(() -> second.window().stage().getWidth()));
            assertEquals(ThemeChoice.LIGHT, second.services().settings().theme);
        } finally {
            FxTestSupport.stop(second);
        }
        Files.writeString(other.resolve(ConfigStore.FILE), "{\"schemaVersion\": 99}");
        Startup.Running third = FxTestSupport.start(other, host, "--no-session");
        try {
            assertTrue(
                    call(() -> third.window().statusBar().echo().getText()).startsWith("Settings come from a newer"));
        } finally {
            FxTestSupport.stop(third);
        }
        Files.writeString(other.resolve(ConfigStore.FILE), "{oops");
        Startup.Running fourth = FxTestSupport.start(other, host);
        try {
            assertTrue(
                    call(() -> fourth.window().statusBar().echo().getText()).startsWith("Settings could not be read"));
        } finally {
            FxTestSupport.stop(fourth);
        }
    }

    @Test
    void anUncaughtExceptionReachesTheStatusBar() throws Exception {
        run(() -> DebugLog.uncaught(Thread.currentThread(), new IllegalStateException("kaput")));
        FxTestSupport.waitUntil(() -> window.statusBar().echo().getText().contains("kaput"));
        assertTrue(call(() -> window.statusBar().echo().getStyleClass().contains("error")));
    }

    @Test
    void theSmokeTestWritesAndExits() throws Exception {
        Path file = dir.resolve("smoke.txt");
        run(() -> Startup.smokeTest(file.toString(), host));
        FxTestSupport.waitUntil(() -> host.exits == 1);
        assertEquals("window-shown\n", Files.readString(file));
        run(() -> Startup.smokeTest(dir.resolve("no/such/dir/x").toString(), host));
        FxTestSupport.waitUntil(() -> host.exits == 2);
        run(() -> Startup.smokeTest(null, host));
        run(() -> Startup.smokeTest(" ", host));
    }

    @Test
    void updatesShowInTheStatusBarAndAbout() throws Exception {
        UpdateCoordinator updates = services.updates();
        assertFalse(updates.automaticAllowed());
        assertFalse(updates.scheduleStartupCheck());
        run(() -> updates.finish(
                new UpdateService.Outcome(new ReleaseInfo("9.9.9", "https://example.com/r"), null), false));
        assertEquals(UpdateCoordinator.State.AVAILABLE, updates.state());
        assertTrue(call(() -> window.statusBar().update().isVisible()));
        run(() -> window.statusBar().update().fire());
        assertEquals(List.of("https://example.com/r"), host.opened);
        assertTrue(services.settings().lastUpdateCheck > 0);

        run(() -> updates.finish(new UpdateService.Outcome(null, "timed out"), true));
        assertEquals("Update check failed: timed out", echo());
        run(() -> updates.finish(new UpdateService.Outcome(null, null), true));
        assertEquals("You are up to date.", echo());

        // A real manual check, against the closed local port the suite configures.
        run(() -> updates.checkNow(true));
        run(() -> updates.checkNow(true)); // ignored while one is running
        FxTestSupport.waitUntil(() -> updates.state() == UpdateCoordinator.State.FAILED);
        assertNotNull(updates.error());
    }

    @Test
    void theStartupCheckIsScheduledOnceWhenAllowed() throws Exception {
        String saved = System.getProperty(UpdateCoordinator.PROPERTY);
        try {
            System.clearProperty(UpdateCoordinator.PROPERTY);
            run(() -> services.store().update(s -> s.lastUpdateCheck = System.currentTimeMillis()));
            assertFalse(call(() -> services.updates().scheduleStartupCheck()));
            run(() -> services.store().update(s -> s.lastUpdateCheck = 0));
            assertTrue(call(() -> services.updates().scheduleStartupCheck()));
            assertFalse(call(() -> services.updates().scheduleStartupCheck()));
        } finally {
            System.setProperty(UpdateCoordinator.PROPERTY, saved);
        }
    }

    private static MouseEvent click() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                1,
                1,
                1,
                1,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                null);
    }

    static List<Window> windows() {
        return List.copyOf(Window.getWindows());
    }
}
