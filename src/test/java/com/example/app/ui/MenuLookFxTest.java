package com.example.app.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.stage.Window;

import com.example.app.config.ThemeChoice;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How every menu looks, whatever opened it: titles in Inter 14 px regular (MENU-16), chords in Inter 11 px
 * in the keyboard's periwinkle and following the keymap (MENU-15), and that periwinkle readable on both
 * themes' grounds (ICON-06).
 */
@Tag("fx")
class MenuLookFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private AppServices services;
    private MainWindow window;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        services = app.services();
        window = app.window();
        run(() -> {
            services.echo("status.ready");
            window.openMessageLog();
            window.stage().getScene().getRoot().applyCss();
            window.stage().getScene().getRoot().layout();
        });
    }

    @AfterEach
    void stop() throws Exception {
        run(() -> {
            hideMenus();
            Themes.zoom(com.example.app.config.Settings.DEFAULT_UI_ZOOM);
            Themes.apply(ThemeChoice.LIGHT);
        });
        FxTestSupport.stop(app);
    }

    private static void hideMenus() {
        for (Window w : List.copyOf(Window.getWindows())) {
            if (w instanceof ContextMenu menu && menu.isShowing()) menu.hide();
        }
    }

    /** Shows {@code menu} from {@code anchor}, as a right-click there would. */
    private static void showFrom(CommandMenu menu, Node anchor) {
        hideMenus();
        Bounds b = anchor.getBoundsInLocal();
        Point2D screen = anchor.localToScreen(b.getMinX() + 4, b.getMinY() + 4);
        menu.menu().show(anchor, screen.getX(), screen.getY());
        menu.menu().getScene().getRoot().applyCss();
    }

    private static Label label(MenuItem item, String styleClass) {
        return (Label) ((CustomMenuItem) item).getContent().lookup("." + styleClass);
    }

    private static MenuItem row(CommandMenu menu, String id) {
        return menu.items().stream()
                .filter(i -> id.equals(i.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row " + id));
    }

    /** What {@code -state-key} resolves to in the main window's scene, on its default ground. */
    private Paint stateKey() {
        Label probe = new Label("x");
        probe.setStyle("-fx-text-fill: -state-key;");
        Pane root = (Pane) window.overlays().root();
        root.getChildren().add(probe);
        root.applyCss();
        Paint paint = probe.getTextFill();
        root.getChildren().remove(probe);
        return paint;
    }

    @Test
    void aRightClickMenuShowsEachChordInTheKeyColorAndFollowsTheKeymap() throws Exception {
        CommandMenu tabMenu = call(() -> window.tabs().menu());
        Node tab = call(() -> window.tabs().tabPane().lookup(".tab"));
        // A binding of the user's own, and one from the keymap.
        run(() -> services.store().update(s -> s.keybindings.put("buffer.closeOthers", "Shortcut+Alt+O")));
        run(() -> showFrom(tabMenu, tab));

        String[] cua = new String[2];
        run(() -> {
            Label own = label(row(tabMenu, "buffer.closeOthers"), "menu-item-chord");
            Label base = label(row(tabMenu, "buffer.close"), "menu-item-chord");
            cua[0] = own.getText();
            cua[1] = base.getText();
            assertEquals(services.keybindings().label("buffer.closeOthers"), own.getText());
            assertEquals(services.keybindings().label("buffer.close"), base.getText());
            assertFalse(own.getText().isEmpty());
            assertFalse(base.getText().isEmpty());
            // Inter 11 px in -state-key, as in the menu bar.
            assertEquals(stateKey(), base.getTextFill(), "the chord is not in the key color");
            assertEquals("Inter", base.getFont().getFamily());
            assertEquals(11, base.getFont().getSize(), 0.01);
        });
        // An unbound command leaves the cell empty, never a wrong chord.
        run(() -> services.store().update(s -> s.keybindings.put("buffer.closeOthers", "")));
        run(() -> showFrom(tabMenu, tab));
        assertEquals(
                "",
                call(() -> label(row(tabMenu, "buffer.closeOthers"), "menu-item-chord")
                        .getText()));

        // A keymap switch rewrites every cell.
        run(() -> services.store().update(s -> s.keymap = "emacs"));
        run(() -> showFrom(tabMenu, tab));
        String emacs = call(
                () -> label(row(tabMenu, "buffer.close"), "menu-item-chord").getText());
        assertEquals(call(() -> services.keybindings().label("buffer.close")), emacs);
        assertNotEquals(cua[1], emacs, "the chord did not follow the keymap");
    }

    @Test
    void everyMenuDrawsInInterFourteenWhateverOpenedIt() throws Exception {
        List<String> seen = new ArrayList<>();
        // From a toolbar drawn at a larger size, as Editora's once was.
        run(() -> window.toolbar().bar().setStyle("-fx-font-size: 18px;"));
        check(
                seen,
                "toolbar",
                call(() -> window.toolbar().menu()),
                call(() -> window.toolbar().bar()));
        // From a tab, a list row (the Message Log's) and a monospaced console (the Debug Log's).
        check(
                seen,
                "tab",
                call(() -> window.tabs().menu()),
                call(() -> window.tabs().tabPane().lookup(".tab")));
        Node row = call(() -> window.messageLog().list().lookup(".list-cell"));
        check(seen, "list row", call(() -> window.messageLog().rowMenu()), row);
        run(window::openDebugLog);
        DebugLogWindow log = call(window::debugLogWindow);
        run(() -> log.text().setStyle("-fx-font-family: \"JetBrains Mono\"; -fx-font-size: 20px;"));
        check(seen, "console", call(log::menu), log.text());
        // And the menu bar's own drop-down.
        run(() -> {
            hideMenus();
            Menu file = window.menuBar().getMenus().getFirst();
            file.show();
        });
        run(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof ContextMenu menu && menu.isShowing()) {
                    menu.getScene().getRoot().applyCss();
                    for (Node title : menu.getScene().getRoot().lookupAll(".menu-item-title")) {
                        assertInter14((Label) title, "menu bar");
                        seen.add("menu bar");
                    }
                }
            }
        });
        assertTrue(seen.containsAll(List.of("toolbar", "tab", "list row", "console", "menu bar")), seen.toString());
    }

    private void check(List<String> seen, String from, CommandMenu menu, Node anchor) throws Exception {
        check(seen, from, menu, anchor, 1);
    }

    private void check(List<String> seen, String from, CommandMenu menu, Node anchor, double zoom) throws Exception {
        run(() -> {
            showFrom(menu, anchor);
            for (MenuItem item : menu.items()) {
                if (!(item instanceof CustomMenuItem) || item instanceof javafx.scene.control.SeparatorMenuItem)
                    continue;
                assertInter(label(item, "menu-item-title"), from, 14 * zoom);
                Label chord = label(item, "menu-item-chord");
                assertEquals(11 * zoom, chord.getFont().getSize(), 0.01, from + ": chord size");
            }
            seen.add(from);
        });
    }

    /** MENU-16, WIN-12: the UI zoom scales every menu's 14 and 11 px, whatever opened it, and resets. */
    @Test
    void theUiZoomScalesEveryMenu() throws Exception {
        assertTrue(call(() -> services.commands().run("view.uiZoomIn")));
        assertTrue(call(() -> services.commands().run("view.uiZoomIn")));
        assertEquals(120, services.settings().uiZoom);
        FxTestSupport.waitUntil(() -> Themes.zoom() == 120);
        List<String> seen = new ArrayList<>();
        check(
                seen,
                "toolbar",
                call(() -> window.toolbar().menu()),
                call(() -> window.toolbar().bar()),
                1.2);
        check(
                seen,
                "tab",
                call(() -> window.tabs().menu()),
                call(() -> window.tabs().tabPane().lookup(".tab")),
                1.2);
        assertTrue(call(() -> services.commands().run("view.uiZoomReset")));
        FxTestSupport.waitUntil(() -> Themes.zoom() == 100);
        check(
                seen,
                "toolbar",
                call(() -> window.toolbar().menu()),
                call(() -> window.toolbar().bar()),
                1);
    }

    private static void assertInter14(Label title, String from) {
        assertInter(title, from, 14);
    }

    private static void assertInter(Label title, String from, double size) {
        Font font = title.getFont();
        assertEquals("Inter", font.getFamily(), from + ": " + title.getText());
        assertEquals(size, font.getSize(), 0.01, from + ": " + title.getText());
        assertFalse(font.getStyle().toLowerCase(java.util.Locale.ROOT).contains("bold"), from + ": bold");
    }

    /** ICON-06: the key color reads at 4.5:1 or more on the default, subtle and menu grounds of both themes. */
    @Test
    void theKeyColorReadsOnBothThemesGrounds() throws Exception {
        for (ThemeChoice theme : List.of(ThemeChoice.LIGHT, ThemeChoice.DARK)) {
            run(() -> {
                Themes.apply(theme);
                Pane root = (Pane) window.overlays().root();
                // The menus' own overlay ground too: that is where every chord is drawn.
                for (String ground : List.of("-color-bg-default", "-color-bg-subtle", "-color-bg-overlay")) {
                    Label probe = new Label("x");
                    probe.setStyle("-fx-text-fill: -state-key; -fx-background-color: " + ground + ";");
                    root.getChildren().add(probe);
                    root.applyCss();
                    Color ink = (Color) probe.getTextFill();
                    Background background = probe.getBackground();
                    Color fill = (Color) background.getFills().getFirst().getFill();
                    root.getChildren().remove(probe);
                    double ratio = StyleContrastTest.contrast(ink, fill);
                    assertTrue(ratio >= 4.5, theme + ": -state-key on " + ground + " is " + ratio);
                }
            });
        }
    }
}
