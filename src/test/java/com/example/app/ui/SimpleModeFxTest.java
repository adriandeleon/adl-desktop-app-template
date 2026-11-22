package com.example.app.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import javafx.scene.Parent;
import javafx.scene.control.Menu;

import com.example.app.config.ConfigStore;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple mode in a real headless toolkit: one setting that shortens the menus (MENU-04).
 */
@Tag("fx")
class SimpleModeFxTest {

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
        layout();
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    private void layout() throws Exception {
        run(() -> {
            Parent root = window.stage().getScene().getRoot();
            root.applyCss();
            root.layout();
        });
    }

    private void simple(boolean on) throws Exception {
        run(() -> services.store().update(s -> s.simpleMode = on));
        layout();
    }

    private List<String> menuRows() {
        return window.menus().groups().stream()
                .flatMap(List::stream)
                .map(AppMenus.Row::commandId)
                .toList();
    }

    /** The five places of a setting: the command, its echo, the saved file, the Settings switch and back. */
    @Test
    void theCommandTheSettingAndTheSwitchAgree() throws Exception {
        assertFalse(services.settings().simpleMode, "off by default");
        run(() -> services.commands().run("view.toggleSimpleMode"));
        assertTrue(services.settings().simpleMode);
        assertEquals("Simple mode: on", call(() -> window.statusBar().echo().getText()));
        services.store().flush(); // saves are written off the FX thread
        assertTrue(ConfigStore.open(dir).settings().simpleMode, "kept for the next launch");

        run(() -> window.openSettings(SettingsWindow.Page.GENERAL));
        SettingsWindow settings = Objects.requireNonNull(window.settingsWindow());
        assertTrue(call(() -> settings.simpleModeSwitch().isSelected()));
        run(() -> settings.simpleModeSwitch().fire());
        assertFalse(services.settings().simpleMode);
        assertTrue(SettingsWindow.PAGE_COMMANDS.get(SettingsWindow.Page.GENERAL).contains("view.toggleSimpleMode"));
        run(() -> settings.stage().hide());
    }

    /** MENU-04: Simple mode shows a shorter menu, and the bar is rebuilt only when the mode changes. */
    @Test
    void simpleModeShowsAShorterMenuRebuiltOnlyOnAChange() throws Exception {
        List<Menu> full = call(() -> List.copyOf(window.menuBar().getMenus()));
        List<String> fullRows = call(this::menuRows);
        assertTrue(fullRows.contains("view.toggleSimpleMode"), "the way in");

        run(() -> services.store().update(s -> s.showStatusBar = false)); // a save that leaves the mode
        assertEquals(full, call(() -> List.copyOf(window.menuBar().getMenus())), "not rebuilt");
        for (int i = 0; i < full.size(); i++) {
            assertSame(full.get(i), call(() -> window.menuBar().getMenus()).get(i));
        }

        simple(true);
        List<Menu> shorter = call(() -> List.copyOf(window.menuBar().getMenus()));
        List<String> simpleRows = call(this::menuRows);
        assertNotSame(full.getFirst(), shorter.getFirst(), "rebuilt on the change");
        assertTrue(simpleRows.size() < fullRows.size(), "shorter");
        assertTrue(fullRows.containsAll(simpleRows), "nothing the full menu lacks");
        assertTrue(simpleRows.contains("view.toggleSimpleMode"), "the way back out");
        assertEquals(
                AppMenus.menus(true).stream()
                        .flatMap(spec -> spec.ids().stream())
                        .filter(Objects::nonNull)
                        .toList(),
                simpleRows);
        assertEquals(
                full.stream().map(Menu::getText).toList(),
                shorter.stream().map(Menu::getText).toList(),
                "the same menus, fewer rows");

        run(() -> services.store().update(s -> s.showStatusBar = true));
        assertSame(shorter.getFirst(), call(() -> window.menuBar().getMenus().getFirst()), "not rebuilt");

        simple(false);
        assertEquals(fullRows, call(this::menuRows));
        // Every row of either table is a registered command (MENU-01).
        for (String id : AppMenus.allCommandIds()) {
            assertNotNull(services.commands().get(id), id);
        }
    }
}
