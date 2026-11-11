package com.example.app.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The main toolbar in a real headless toolkit, one test per rule of Pauta's Toolbars and tooltips. */
@Tag("fx")
class AppToolbarFxTest {

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
        run(() -> {
            window.stage().getScene().getRoot().applyCss();
            window.stage().getScene().getRoot().layout();
        });
    }

    @AfterEach
    void stop() throws Exception {
        run(() -> openMenus().forEach(ContextMenu::hide));
        FxTestSupport.stop(app);
    }

    private Button button(String id) {
        return Objects.requireNonNull(toolbar.button(id), id);
    }

    private static boolean open(Node node) {
        return node.getPseudoClassStates().contains(OverlayToggles.OPEN);
    }

    private static List<ContextMenu> openMenus() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof ContextMenu m && m.isShowing())
                .map(w -> (ContextMenu) w)
                .toList();
    }

    /** The bar reads as its lists say: groups in the cluster, Simple mode then Settings in the tail at the end. */
    @Test
    void theBarHoldsItsCommandsInGroupsWithSettingsAtTheEnd() throws Exception {
        List<String> cluster = call(() -> {
            List<String> shape = new ArrayList<>();
            for (Node item : toolbar.bar().getItems()) {
                shape.add(item instanceof Separator ? "|" : item.getId());
            }
            return shape;
        });
        assertEquals(
                List.of(
                        "toolbar-palette.show",
                        "|",
                        "toolbar-settings.theme",
                        "toolbar-settings.keymap",
                        "|",
                        "toolbar-sync.now",
                        "toolbar-view.messageLog"),
                cluster);
        assertEquals(
                List.of(button("view.toggleSimpleMode"), button("app.settings")),
                call(() -> toolbar.tail().getChildren().stream()
                        .filter(n -> n instanceof Button)
                        .toList()));
        assertSame(
                toolbar.tail(), call(() -> toolbar.node().getChildren().getLast()), "TBAR-16: Settings ends the bar");
    }

    /** TBAR-01, TBAR-03: tooltip and accessible text are one string, the title and the keymap's chord. */
    @Test
    void everyButtonIsNamedByItsTitleAndItsChord() throws Exception {
        for (Button b : toolbar.buttons()) {
            assertNotNull(b.getTooltip(), b.getId());
            assertEquals(b.getTooltip().getText(), b.getAccessibleText(), b.getId());
            assertEquals(Icons.TOOLTIP_DELAY, b.getTooltip().getShowDelay(), b.getId());
        }
        String chord = services.keybindings().label("palette.show");
        assertFalse(chord.isEmpty());
        assertEquals("Command Palette (" + chord + ")", button("palette.show").getAccessibleText());
        assertTrue(button("app.settings").getAccessibleText().startsWith("Settings ("), "no ellipsis in a name");
        run(() -> services.store().update(s -> s.keybindings.put("view.messageLog", ""))); // unbound
        assertEquals("Message Log", button("view.messageLog").getAccessibleText(), "an unbound command: its title");

        // The name follows a keymap switch.
        run(() -> services.store().update(s -> s.keymap = "emacs"));
        assertEquals("Command Palette (M-x)", button("palette.show").getAccessibleText());
        assertEquals(
                "Command Palette (M-x)", button("palette.show").getTooltip().getText());
    }

    /** TBAR-02: the buttons stay out of the Tab order. */
    @Test
    void theButtonsStayOutOfTheTabOrder() {
        for (Button b : toolbar.buttons()) assertFalse(b.isFocusTraversable(), b.getId());
    }

    /** TBAR-08, MENU-14: every button is also a menu row and the palette's, and wears its command's glyph. */
    @Test
    void everyButtonIsAlsoAMenuRow() {
        List<String> menuIds = window.menus().groups().stream()
                .flatMap(List::stream)
                .map(AppMenus.Row::commandId)
                .toList();
        List<String> barIds = new ArrayList<>(AppToolbar.TAIL);
        AppToolbar.CLUSTER.stream().filter(Objects::nonNull).forEach(barIds::add);
        for (String id : barIds) {
            assertNotNull(services.commands().get(id), id + " is not a command, so the palette lacks it");
            assertTrue(menuIds.contains(id), id + " is on the toolbar but in no menu");
            assertEquals(
                    Icons.forCommand(id),
                    IconsTest.path(button(id).getGraphic()).getContent(),
                    id);
        }
    }

    /** TBAR-07: a button looks pressed while its overlay is up, however the overlay was opened. */
    @Test
    void aButtonLooksPressedWhileItsOverlayIsUp() throws Exception {
        Button palette = button("palette.show");
        Button theme = button("settings.theme");
        Node welcomePalette = call(() -> window.stage().getScene().getRoot().lookupAll(".welcome .button").stream()
                .filter(n -> n instanceof Button b && "Command Palette".equals(b.getText()))
                .findFirst()
                .orElseThrow());
        assertFalse(open(palette));

        run(palette::fire); // the button
        assertTrue(window.overlays().isShowing());
        assertTrue(open(palette));
        assertTrue(open(welcomePalette), "the start page's palette button too");
        assertFalse(open(theme));

        run(() -> window.overlays().hide());
        assertFalse(open(palette));
        assertFalse(open(welcomePalette));

        run(() -> services.commands().run("palette.show")); // its key or menu row
        assertTrue(open(palette));
        run(() -> services.commands().run("settings.theme")); // one overlay replaces another
        assertFalse(open(palette));
        assertTrue(open(theme));
        run(() -> window.overlays().hide());
        assertFalse(open(theme));
    }

    /** TBAR-17 (Pauta 5.0.0): a button is disabled while its command can't run, and follows the change. */
    @Test
    void syncNowWaitsForARepository() throws Exception {
        Button sync = button("sync.now");
        assertTrue(sync.isDisabled(), "no repository yet");
        run(() -> services.store().update(s -> s.sync.repoUrl = "git@example.com:me/settings.git"));
        assertFalse(sync.isDisabled());
        run(() -> services.store().update(s -> s.sync.repoUrl = ""));
        assertTrue(sync.isDisabled());
    }

    /** TBAR-05: a hidden group takes its gap with it, and every gap comes back with it. */
    @Test
    void aHiddenGroupLeavesNoGapStandingAlone() throws Exception {
        List<Separator> gaps = call(() -> toolbar.bar().getItems().stream()
                .filter(n -> n instanceof Separator)
                .map(n -> (Separator) n)
                .toList());
        run(() -> toolbar.setGroupVisible("settings.theme", false));
        assertFalse(button("settings.keymap").isVisible(), "the whole group goes");
        assertEquals(1, gaps.stream().filter(Node::isVisible).count(), "two gaps side by side become one");
        run(() -> toolbar.setGroupVisible("palette.show", false));
        assertEquals(0, gaps.stream().filter(Node::isVisible).count(), "no gap leads the bar");
        run(() -> {
            toolbar.setGroupVisible("palette.show", true);
            toolbar.setGroupVisible("settings.theme", true);
        });
        assertTrue(gaps.stream().allMatch(Node::isVisible));
    }

    /** TBAR-06: one band, the bars on one ground with no rule of their own; the band draws the one rule. */
    @Test
    void theMenusAndTheToolbarAreOneBand() throws Exception {
        assertSame(toolbar.node(), call(() -> window.band().getChildren().getLast()));
        for (javafx.scene.layout.Region bar : List.of(window.menuBar(), toolbar.bar())) {
            assertEquals(1, call(() -> bar.getBackground().getFills().size()), bar + " draws a rule under itself");
        }
    }

    /** TBAR-18 (Pauta 5.0.0): a command and a setting hide the toolbar, and the choice is kept. */
    @Test
    void theCommandAndTheSettingHideTheToolbar() throws Exception {
        run(() -> services.commands().run("view.toggleToolbar"));
        assertFalse(services.settings().showToolbar);
        assertFalse(toolbar.node().isVisible());
        assertFalse(toolbar.node().isManaged());
        assertEquals("Toolbar: off", call(() -> window.statusBar().echo().getText()));
        services.store().flush(); // saves are written off the FX thread
        assertFalse(ConfigStore.open(dir).settings().showToolbar);

        run(() -> window.openSettings(SettingsWindow.Page.GENERAL));
        SettingsWindow settings = Objects.requireNonNull(window.settingsWindow());
        assertFalse(call(() -> settings.toolbarSwitch().isSelected()));
        run(() -> settings.toolbarSwitch().fire());
        assertTrue(services.settings().showToolbar);
        assertTrue(toolbar.node().isVisible());
    }

    /** TBAR-10: the bar's right-click menu is one menu, and a press on the bar or anywhere else closes it. */
    @Test
    void theBarsMenuIsOneMenuThatAnyPressCloses() throws Exception {
        Node row = toolbar.node();
        run(() -> rightClick(row));
        run(() -> {});
        run(() -> rightClick(button("palette.show")));
        run(() -> {});
        List<ContextMenu> menus = call(AppToolbarFxTest::openMenus);
        assertEquals(1, menus.size(), "one menu, moved");
        assertSame(toolbar.menu().menu(), menus.getFirst());
        assertEquals(
                List.of("view.toggleToolbar", "view.toggleSimpleMode"),
                call(() -> menus.getFirst().getItems().stream()
                        .map(MenuItem::getId)
                        .filter(Objects::nonNull)
                        .toList()));

        run(() -> Event.fireEvent(row, press(row, MouseButton.PRIMARY)));
        assertEquals(0, call(() -> openMenus().size()), "a press on the bar closes it");

        run(() -> rightClick(row));
        run(() -> {});
        Node status = window.statusBar();
        run(() -> Event.fireEvent(status, press(status, MouseButton.PRIMARY)));
        assertEquals(0, call(() -> openMenus().size()), "a press elsewhere closes it");
    }

    /** TBAR-15 (Pauta 5.0.0): a narrow window overflows the cluster's icons, never the tail. */
    @Test
    void aNarrowWindowOverflowsTheClusterNotTheTail() throws Exception {
        Button settings = button("app.settings");
        // The bar at a narrow window's width, read in the same pulse (the next one lays the window out again).
        List<Object> seen = call(() -> {
            HBox row = toolbar.node();
            row.resize(150, row.prefHeight(150));
            row.layout();
            toolbar.bar().layout();
            Bounds end = row.sceneToLocal(settings.localToScene(settings.getLayoutBounds()));
            Node overflow = Objects.requireNonNull(toolbar.bar().lookup(".tool-bar-overflow-button"));
            return List.of(settings.isVisible(), end.getMaxX() <= row.getWidth() + 0.5, overflow.isVisible());
        });
        assertEquals(List.of(true, true, true), seen, "Settings shown, inside the bar; the cluster's chevron shown");
    }

    private static void rightClick(Node node) {
        Bounds b = node.getBoundsInLocal();
        double x = b.getMinX() + 4;
        double y = b.getMinY() + b.getHeight() / 2;
        Event.fireEvent(node, press(node, MouseButton.SECONDARY));
        Point2D scene = node.localToScene(x, y);
        Point2D screen = node.localToScreen(x, y);
        Event.fireEvent(
                node,
                new ContextMenuEvent(
                        ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                        scene.getX(),
                        scene.getY(),
                        screen.getX(),
                        screen.getY(),
                        false,
                        null));
    }

    private static MouseEvent press(Node node, MouseButton button) {
        Bounds b = node.getBoundsInLocal();
        Point2D scene = node.localToScene(b.getMinX() + 4, b.getMinY() + 4);
        Point2D screen = node.localToScreen(b.getMinX() + 4, b.getMinY() + 4);
        return new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                scene.getX(),
                scene.getY(),
                screen.getX(),
                screen.getY(),
                button,
                1,
                false,
                false,
                false,
                false,
                button == MouseButton.PRIMARY,
                false,
                button == MouseButton.SECONDARY,
                false,
                button == MouseButton.SECONDARY,
                false,
                null);
    }
}
