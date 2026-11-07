package com.example.app.ui;

import java.nio.file.Path;
import java.util.List;

import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

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

/**
 * Right-click menus (MENU-05..09): a Message Log row's menu holds its commands, one menu at a time, a
 * press anywhere else closes it, and the Menu key or Shift+F10 opens the selected row's.
 */
@Tag("fx")
class RightClickMenusFxTest {

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
        run(() -> {
            services.echo("status.ready");
            services.echo("status.titleBarRestart");
            services.echo("status.languageRestart");
            app.window().openMessageLog();
            app.window().stage().getScene().getRoot().applyCss();
            app.window().stage().getScene().getRoot().layout();
        });
    }

    @AfterEach
    void stop() throws Exception {
        run(() -> openMenus().forEach(ContextMenu::hide));
        FxTestSupport.stop(app);
    }

    private static List<ContextMenu> openMenus() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof ContextMenu m && m.isShowing())
                .map(w -> (ContextMenu) w)
                .toList();
    }

    private ListCell<?> cell(int index) throws Exception {
        return call(() -> app.window().messageLog().list().lookupAll(".list-cell").stream()
                .map(n -> (ListCell<?>) n)
                .filter(c -> c.getIndex() == index && !c.isEmpty())
                .findFirst()
                .orElseThrow());
    }

    @Test
    void aRowsMenuHoldsItsCommandsAndARightClickSelectsTheRow() throws Exception {
        ListCell<?> second = cell(1);
        run(() -> rightClick(second));
        List<ContextMenu> open = call(RightClickMenusFxTest::openMenus);
        assertEquals(1, open.size());
        List<String> ids = call(() -> open.getFirst().getItems().stream()
                .map(MenuItem::getId)
                .filter(java.util.Objects::nonNull)
                .toList());
        assertEquals(List.of("messageLog.copy", "messageLog.clear"), ids);
        assertEquals(
                1,
                call(() -> app.window().messageLog().list().getSelectionModel().getSelectedIndex()));
        // A row runs its command through the registry.
        run(() -> open.getFirst().getItems().stream()
                .filter(i -> "messageLog.clear".equals(i.getId()))
                .findFirst()
                .orElseThrow()
                .fire());
        assertTrue(services.messages().entries().isEmpty());
    }

    @Test
    void aRightClickOnAnotherRowLeavesOneMenu() throws Exception {
        ListCell<?> first = cell(0);
        ListCell<?> second = cell(1);
        run(() -> rightClick(first));
        FxTestSupport.run(() -> {});
        run(() -> rightClick(second));
        FxTestSupport.run(() -> {});
        assertEquals(1, call(() -> openMenus().size()), "MENU-06: one right-click menu at a time");
        assertEquals(
                1,
                call(() -> app.window().messageLog().list().getSelectionModel().getSelectedIndex()));
    }

    @Test
    void aPressInTheOwnerControlOrAnotherWindowClosesIt() throws Exception {
        ListCell<?> first = cell(0);
        run(() -> rightClick(first));
        FxTestSupport.run(() -> {});
        assertEquals(1, call(() -> openMenus().size()));
        Node list = app.window().messageLog().list();
        run(() -> Event.fireEvent(list, press(list, 30, 5, MouseButton.PRIMARY)));
        assertEquals(0, call(() -> openMenus().size()), "MENU-07: a click in the owner list closes it");

        Stage other = call(() -> {
            Stage s = new Stage();
            s.setScene(new Scene(new StackPane(), 200, 200));
            s.show();
            return s;
        });
        try {
            run(() -> rightClick(first));
            FxTestSupport.run(() -> {});
            assertEquals(1, call(() -> openMenus().size()));
            run(() -> {
                Node root = other.getScene().getRoot();
                Event.fireEvent(root, press(root, 20, 20, MouseButton.PRIMARY));
            });
            assertEquals(0, call(() -> openMenus().size()), "MENU-07: a click in another window closes it");
        } finally {
            run(other::close);
        }
    }

    @Test
    void theMenuKeyOpensTheSelectedRowsMenuAtThatRow() throws Exception {
        var list = app.window().messageLog().list();
        run(() -> {
            list.requestFocus();
            list.getSelectionModel().select(2);
            Event.fireEvent(
                    list, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, true, null));
        });
        List<ContextMenu> open = call(RightClickMenusFxTest::openMenus);
        assertEquals(1, open.size(), "MENU-09: the keyboard request opened the row's menu");
        ListCell<?> third = cell(2);
        Bounds row = call(() -> third.localToScreen(third.getBoundsInLocal()));
        assertEquals(row.getMaxY(), call(() -> open.getFirst().getAnchorY()), 1.0, "anchored to the selected row");
        // Escape closes it (the stock ContextMenu, MENU-08).
        run(() -> open.getFirst().hide());
        assertFalse(call(() -> open.getFirst().isShowing()));
    }

    @Test
    void theDebugLogConsoleMenuIsItsCommands() throws Exception {
        run(app.window()::openDebugLog);
        DebugLogWindow window = app.window().debugLogWindow();
        assertNotNull(window);
        ContextMenu menu = call(() -> window.text().getContextMenu());
        assertNotNull(menu);
        List<String> ids = call(() -> menu.getItems().stream()
                .map(MenuItem::getId)
                .filter(java.util.Objects::nonNull)
                .toList());
        assertEquals(List.of("debugLog.copy", "debugLog.export", "debugLog.refresh", "debugLog.clear"), ids);
        run(window.stage()::close);
    }

    private static void rightClick(Node node) {
        Bounds b = node.getBoundsInLocal();
        double x = b.getMinX() + 10;
        double y = b.getMinY() + b.getHeight() / 2;
        Event.fireEvent(node, press(node, x, y, MouseButton.SECONDARY));
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

    private static MouseEvent press(Node node, double x, double y, MouseButton button) {
        Point2D scene = node.localToScene(x, y);
        Point2D screen = node.localToScreen(x, y);
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
                true,
                button == MouseButton.SECONDARY,
                false,
                null);
    }
}
