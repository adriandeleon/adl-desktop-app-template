package com.example.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.control.Button;

import com.example.app.command.KeyStroke;
import com.example.app.config.ThemeChoice;
import com.example.app.sync.Git;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settings: search, live apply, and every control writing its field. */
@Tag("fx")
class SettingsWindowFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private AppServices services;
    private SettingsWindow settings;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void open() throws Exception {
        app = FxTestSupport.start(dir, host);
        services = app.services();
        run(() -> app.window().openSettings(SettingsWindow.Page.GENERAL));
        settings = app.window().settingsWindow();
        assertNotNull(settings);
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    @Test
    void searchHidesRowsAndOpensTheBestPage() throws Exception {
        assertTrue(call(() -> settings.stage().isShowing()));
        run(() -> settings.search().setText("sync automatically"));
        assertEquals(
                SettingsWindow.Page.SYNC,
                call(() -> settings.sidebar().getSelectionModel().getSelectedItem()));
        assertFalse(settings.pageHits().get(SettingsWindow.Page.APPEARANCE));
        long visible = call(
                () -> settings.rows().stream().filter(r -> r.node().isVisible()).count());
        assertTrue(visible > 0 && visible < settings.rows().size());
        run(() -> settings.search().setText("dark"));
        assertEquals(
                SettingsWindow.Page.APPEARANCE,
                call(() -> settings.sidebar().getSelectionModel().getSelectedItem()));
        run(() -> settings.search().setText(""));
        assertTrue(call(() -> settings.rows().stream().allMatch(r -> r.node().isVisible())));
        run(() -> settings.sidebar().getSelectionModel().select(SettingsWindow.Page.ADVANCED));
    }

    @Test
    void everyControlAppliesLive() throws Exception {
        run(() -> settings.theme().setValue(ThemeChoice.DARK));
        assertEquals(ThemeChoice.DARK, services.settings().theme);
        run(() -> settings.language().setValue("it"));
        assertEquals("it", services.settings().language);
        run(() -> settings.keymap().setValue("emacs"));
        assertEquals("emacs", services.settings().keymap);
        run(() -> settings.updatesSwitch().fire());
        assertFalse(services.settings().checkForUpdates);
        run(() -> settings.statusBarSwitch().fire());
        assertFalse(services.settings().showStatusBar);
        assertEquals("Off", call(() -> settings.statusBarSwitch().getText()));

        // A change made elsewhere (a palette command) reaches the open window.
        run(() -> services.commands().run("view.toggleStatusBar"));
        assertTrue(call(() -> settings.statusBarSwitch().isSelected()));
        run(() -> services.store().update(s -> s.theme = ThemeChoice.LIGHT));
        assertEquals(ThemeChoice.LIGHT, call(() -> settings.theme().getValue()));
    }

    @Test
    void keyBindingsCommitValidateAndReset() throws Exception {
        var field = new javafx.scene.control.TextField();
        run(() -> {
            field.setText("Ctrl+Alt+J");
            settings.commitBinding("help.about", field);
        });
        assertEquals("Ctrl+Alt+J", services.settings().keybindings.get("help.about"));
        assertEquals("help.about", services.keybindings().commandFor(KeyStroke.parseSequence("Ctrl+Alt+J")));
        run(() -> {
            field.setText("Hyper+J");
            settings.commitBinding("help.about", field);
        });
        assertTrue(field.getStyleClass().contains("invalid"));
        assertEquals("Ctrl+Alt+J", services.settings().keybindings.get("help.about"));
        run(() -> {
            field.setText("");
            settings.commitBinding("help.about", field);
        });
        assertEquals("", services.settings().keybindings.get("help.about"));
        run(() -> {
            field.setText("F12");
            settings.commitBinding("help.about", field);
        });
        assertNull(services.settings().keybindings.get("help.about"));
        run(() -> settings.commitBinding("help.about", field)); // unchanged: nothing written

        // A conflict is marked on the field of the binding that lost.
        run(() -> settings.sidebar().getSelectionModel().select(SettingsWindow.Page.KEYMAP));
        run(() -> services.store().update(s -> s.keybindings.put("help.debugLog", "Shortcut+Q")));
        assertTrue(call(() -> settings.stage().getScene().getRoot().lookupAll(".text-field").stream()
                .anyMatch(n -> n.getStyleClass().contains("invalid"))));
        // Every Reset button removes its override.
        run(() -> settings.stage().getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && "Reset".equals(b.getText()))
                .forEach(n -> ((Button) n).fire()));
        assertTrue(services.settings().keybindings.isEmpty());
    }

    @Test
    void syncFieldsValidateBeforeTheyCommit() throws Exception {
        run(() -> {
            settings.syncUrl().setText("https://me:token@github.com/me/sync.git");
            settings.syncUrl().fireEvent(new javafx.event.ActionEvent());
        });
        assertTrue(call(() -> settings.syncUrl().getStyleClass().contains("invalid")));
        assertEquals("", services.settings().sync.repoUrl);
        assertTrue(call(() -> app.window().statusBar().echo().getText()).startsWith("Repository URL refused"));

        Path remote = dir.resolve("remote.git");
        Files.createDirectories(remote);
        new Git().run(remote, "init", "-q", "--bare");
        run(() -> {
            settings.syncUrl().setText(remote.toUri().toString());
            settings.syncUrl().fireEvent(new javafx.event.ActionEvent());
            settings.syncBranch().setText("trunk");
            settings.syncBranch().fireEvent(new javafx.event.ActionEvent());
            settings.syncInterval().setText("0");
            settings.syncInterval().fireEvent(new javafx.event.ActionEvent());
        });
        assertEquals(remote.toUri().toString(), services.settings().sync.repoUrl);
        assertEquals("trunk", services.settings().sync.branch);
        assertTrue(call(() -> settings.syncInterval().getStyleClass().contains("invalid")));
        run(() -> {
            settings.syncInterval().setText("7");
            settings.syncInterval().fireEvent(new javafx.event.ActionEvent());
            settings.syncAuto().fire();
        });
        assertEquals(7, services.settings().sync.intervalMinutes);
        assertFalse(services.settings().sync.auto);
        assertEquals("Connected", call(() -> settings.syncPill().getText()));
        assertTrue(call(() -> settings.syncPill().getStyleClass().contains("green")));

        // Connect runs a manual sync against the bare repository.
        run(() -> settings.sidebar().getSelectionModel().select(SettingsWindow.Page.SYNC));
        run(() -> settings.stage().getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && "Connect".equals(b.getText()))
                .forEach(n -> ((Button) n).fire()));
        FxTestSupport.waitUntil(() -> services.settings().sync.lastSync > 0);
        assertTrue(call(() -> settings.syncPill().getText()).startsWith("Connected · synced"));
        run(() -> settings.sidebar().getSelectionModel().select(SettingsWindow.Page.ADVANCED));
        run(() -> settings.stage().getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && "Open".equals(b.getText()))
                .forEach(n -> ((Button) n).fire()));
        run(() -> settings.sidebar().getSelectionModel().select(SettingsWindow.Page.SYNC));
        run(() -> settings.stage().getScene().getRoot().lookupAll(".button").stream()
                .filter(n ->
                        n instanceof Button b && ("Open Sync Folder".equals(b.getText()) || "Open".equals(b.getText())))
                .forEach(n -> ((Button) n).fire()));
        assertEquals(2, host.opened.size());
    }
}
