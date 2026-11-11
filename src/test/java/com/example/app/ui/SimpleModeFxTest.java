package com.example.app.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.Separator;
import javafx.scene.layout.Region;

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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple mode in a real headless toolkit: one setting that shortens the menus and empties the toolbar, one
 * test per rule it shows (MENU-04, TBAR-05, TBAR-07, and Pauta 5.0.0's TBAR-16 and TBAR-19).
 */
@Tag("fx")
class SimpleModeFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private MainWindow window;
    private AppServices services;
    private AppToolbar toolbar;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        window = app.window();
        services = app.services();
        toolbar = window.toolbar();
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

    private Button button(String id) {
        return Objects.requireNonNull(toolbar.button(id), id);
    }

    private static boolean shown(Node node) {
        return node.isVisible() && node.isManaged();
    }

    private static boolean lit(Node node) {
        return node.getPseudoClassStates().contains(OverlayToggles.OPEN);
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

    /** TBAR-05: the groups Simple mode hides take their gaps with them, and all come back. */
    @Test
    void simpleModeHidesGroupsAndTheGapsTheyLeave() throws Exception {
        List<Node> items = call(() -> List.copyOf(toolbar.bar().getItems()));
        assertTrue(items.stream().allMatch(SimpleModeFxTest::shown));

        simple(true);
        for (String id : List.of("settings.theme", "settings.keymap", "sync.now", "view.messageLog")) {
            assertFalse(shown(button(id)), id);
        }
        assertTrue(shown(button("palette.show")), "the palette stays: every command is still in it");
        assertTrue(
                items.stream().filter(n -> n instanceof Separator).noneMatch(SimpleModeFxTest::shown),
                "no gap trails the palette");

        simple(false);
        assertTrue(items.stream().allMatch(SimpleModeFxTest::shown), "everything back, gaps included");
    }

    /** TBAR-07: the toggle looks on while the mode is on, and an overlay coming and going leaves it so. */
    @Test
    void theToggleLooksOnWhileTheModeIsOn() throws Exception {
        Button toggle = button("view.toggleSimpleMode");
        assertFalse(lit(toggle));
        run(toggle::fire); // the button itself
        assertTrue(services.settings().simpleMode);
        assertTrue(lit(toggle));

        run(() -> services.commands().run("palette.show"));
        assertTrue(lit(button("palette.show")));
        assertTrue(lit(toggle), "an overlay opening leaves the mode's light on");
        run(() -> window.overlays().hide());
        assertTrue(lit(toggle), "and so does one closing");

        run(toggle::fire);
        assertFalse(services.settings().simpleMode);
        assertFalse(lit(toggle));
    }

    /** TBAR-16 (Pauta 5.0.0): Settings ends the bar with the toggle just before it, after the tail's gap. */
    @Test
    void theToggleSitsJustBeforeSettingsEitherWay() throws Exception {
        for (boolean on : List.of(false, true)) {
            simple(on);
            List<Node> tail = call(() -> List.copyOf(toolbar.tail().getChildren()));
            assertEquals(3, tail.size());
            Region gap = assertInstanceOf(Region.class, tail.get(0));
            assertFalse(gap instanceof Separator, "a blank, not a separator");
            assertEquals(AppToolbar.TAIL_GAP, call(gap::getWidth), 0.5);
            assertSame(button("view.toggleSimpleMode"), tail.get(1));
            assertSame(button("app.settings"), tail.get(2));
            assertTrue(shown(tail.get(1)) && shown(tail.get(2)), "both shown, mode " + on);
            assertSame(toolbar.tail(), call(() -> toolbar.node().getChildren().getLast()));
        }
    }

    /** TBAR-19 (Pauta 5.0.0): the bar keeps its height, so the tab strip's top edge stays where it was. */
    @Test
    void theBarKeepsItsHeight() throws Exception {
        double height = call(() -> toolbar.node().getHeight());
        double tabsTop = call(() -> window.tabs().tabPane().localToScene(0, 0).getY());
        assertTrue(height > 0);
        assertEquals(toolbar.steadyHeight(), height, 0.5, "the bar's height is its steady height");

        simple(true);
        assertEquals(height, call(() -> toolbar.node().getHeight()), 0.01);
        assertEquals(
                tabsTop, call(() -> window.tabs().tabPane().localToScene(0, 0).getY()), 0.01);

        // Even with every group hidden, as an app whose tools are all missing would have it.
        run(() -> {
            toolbar.setGroupVisible("palette.show", false);
            toolbar.setGroupVisible("settings.theme", false);
            toolbar.setGroupVisible("sync.now", false);
        });
        layout();
        assertEquals(height, call(() -> toolbar.node().getHeight()), 0.01);
        assertEquals(
                tabsTop, call(() -> window.tabs().tabPane().localToScene(0, 0).getY()), 0.01);

        simple(false);
        run(() -> toolbar.setGroupVisible("palette.show", true));
        layout();
        assertEquals(height, call(() -> toolbar.node().getHeight()), 0.01);
    }
}
