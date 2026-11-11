package com.example.app.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Cell;
import javafx.scene.control.Control;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ACC-01: every control a person can reach with Tab or the mouse has a name a screen reader says: its own
 * text, a {@code Label} whose {@code labelFor} is the control, or its accessible text. The test opens every
 * window and dialog the app has, walks each one's scene, and fails on a control, a cell or a menu row that
 * reads as nothing.
 */
@Tag("fx")
class NamedControlsFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;

    /** What holds controls but is no control a person uses: scrolling, separators, layout. */
    private static final Set<Class<?>> NOT_A_CONTROL = Set.of(
            Label.class,
            ScrollBar.class,
            ScrollPane.class,
            Separator.class,
            javafx.scene.control.ToolBar.class,
            javafx.scene.control.SplitPane.class,
            javafx.scene.control.ButtonBar.class,
            javafx.scene.control.TabPane.class, // each tab reads as its own title
            javafx.scene.control.MenuBar.class); // each menu reads as its own title

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
    }

    @AfterEach
    void stop() throws Exception {
        run(() -> {
            for (Window w : List.copyOf(Window.getWindows())) {
                if (w != app.window().stage()) w.hide();
            }
        });
        FxTestSupport.stop(app);
    }

    @Test
    void everyControlInEveryWindowHasAName() throws Exception {
        MainWindow window = app.window();
        run(() -> {
            window.tabs().tabPane().getSelectionModel().select(1);
            app.services().commands().run("tool.documents");
            app.services().commands().run("tool.outline");
            app.services().commands().run("output.find");
            app.services().commands().run("find.show");
            window.openMessageLog();
            window.openSettings(SettingsWindow.Page.GENERAL);
            window.openAbout();
            window.openPauta();
            window.openDebugLog();
            window.openReportProblem();
        });
        List<String> unnamed = new ArrayList<>();
        int[] windows = {0};
        run(() -> {
            for (Window w : List.copyOf(Window.getWindows())) {
                Scene scene = w.getScene();
                if (scene == null || !w.isShowing()) continue;
                windows[0]++;
                scene.getRoot().applyCss();
                if (scene.getRoot() instanceof Parent root) root.layout();
                walk(
                        scene.getRoot(),
                        labelled(scene.getRoot()),
                        unnamed,
                        w.getClass().getSimpleName());
            }
        });
        assertTrue(windows[0] >= 6, "the main window, Settings, About, Pauta, Debug Log and Report a Problem");
        // Every Settings page, each shown in turn, and the Command Palette over the main window.
        for (SettingsWindow.Page page : SettingsWindow.Page.values()) {
            run(() -> {
                window.openSettings(page);
                Scene scene = window.settingsWindow().stage().getScene();
                scene.getRoot().applyCss();
                walk(scene.getRoot(), labelled(scene.getRoot()), unnamed, "Settings " + page);
            });
        }
        run(() -> {
            app.services().commands().run("palette.show");
            Scene scene = window.stage().getScene();
            scene.getRoot().applyCss();
            walk(scene.getRoot(), labelled(scene.getRoot()), unnamed, "palette");
            app.services().commands().run("switcher.show");
            scene.getRoot().applyCss();
            walk(scene.getRoot(), labelled(scene.getRoot()), unnamed, "switcher");
            window.overlays().hide();
        });
        // A question before something is lost, and a text prompt: dialogs of their own.
        run(() -> {
            javafx.scene.control.Alert question =
                    Confirm.dialog(Confirmations.quitUnsaved(List.of("notes.md")), window.stage());
            question.show();
            Scene scene = question.getDialogPane().getScene();
            scene.getRoot().applyCss();
            walk(scene.getRoot(), labelled(scene.getRoot()), unnamed, "confirm");
            question.close();
            javafx.scene.control.TextInputDialog prompt =
                    Prompts.text(window.stage(), "Title", "Value", "x", v -> null, v -> {});
            Scene promptScene = prompt.getDialogPane().getScene();
            promptScene.getRoot().applyCss();
            walk(promptScene.getRoot(), labelled(promptScene.getRoot()), unnamed, "prompt");
            prompt.close();
        });
        assertEquals(List.of(), unnamed, "controls with no name");
    }

    /**
     * Every row of every menu the app draws reads as its title (and chord), ACC-01, and wears its command's
     * glyph in the icon column, MENU-14.
     */
    @Test
    void everyMenuRowReadsAsItsTitleAndWearsAGlyph() throws Exception {
        MainWindow window = app.window();
        List<String> unnamed = new ArrayList<>();
        List<javafx.scene.control.Menu> bar =
                call(() -> List.copyOf(window.menuBar().getMenus()));
        for (javafx.scene.control.Menu menu : bar) {
            run(() -> menu.getOnShowing().handle(null));
            for (MenuItem item : call(menu::getItems)) check(item, unnamed, menu.getText());
        }
        run(window::openDebugLog);
        List<CommandMenu> menus = List.of(
                window.tabs().menu(),
                window.toolbar().menu(),
                window.messageLog().rowMenu(),
                call(() -> window.debugLogWindow().menu()));
        for (CommandMenu menu : menus) {
            run(() -> {
                menu.menu();
                menu.refresh(); // what a showing does
            });
            for (MenuItem item : call(menu::items)) check(item, unnamed, "right-click");
        }
        assertEquals(List.of(), unnamed, "menu rows that read as nothing or wear no glyph");
    }

    private static void check(MenuItem item, List<String> unnamed, String where) throws Exception {
        if (item instanceof SeparatorMenuItem) return;
        String name = call(() ->
                item instanceof CustomMenuItem custom ? custom.getContent().getAccessibleText() : item.getText());
        if (name == null || name.isBlank()) unnamed.add(where + ": " + item.getId());
        // MENU-14: the icon column holds a glyph, never an empty slot.
        boolean glyph = call(() -> item instanceof CustomMenuItem custom
                ? custom.getContent() instanceof Parent row
                        && !row.getChildrenUnmodifiable().isEmpty()
                        && row.getChildrenUnmodifiable().getFirst().lookup(".icon-box") != null
                : item.getGraphic() != null);
        if (!glyph) unnamed.add(where + ": " + item.getId() + " has no glyph");
    }

    /** The controls some label in this scene names (its {@code labelFor}). */
    private static Set<Node> labelled(Parent root) {
        Set<Node> named = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Node node : root.lookupAll(".label")) {
            if (node instanceof Label label && label.getLabelFor() != null) named.add(label.getLabelFor());
        }
        return named;
    }

    private static void walk(Node node, Set<Node> labelled, List<String> unnamed, String where) {
        if (!node.isVisible()) return;
        if (node instanceof Control control
                && NOT_A_CONTROL.stream().noneMatch(c -> c.isInstance(control))
                && !control.isDisabled()) {
            if (!named(control, labelled)) unnamed.add(where + ": " + describe(control));
        }
        // A combo box's own parts (its popup's list) are its skin's: the box carries the name.
        if (node instanceof Parent parent && !(node instanceof javafx.scene.control.ComboBoxBase<?>)) {
            for (Node child : parent.getChildrenUnmodifiable()) walk(child, labelled, unnamed, where);
        }
    }

    private static boolean named(Control control, Set<Node> labelled) {
        if (has(control.getAccessibleText()) || labelled.contains(control)) return true;
        if (control instanceof Labeled labeled && has(labeled.getText())) return true;
        if (control instanceof Cell<?> cell) {
            // An empty cell is no row; a filled one reads its text or its graphic's.
            if (cell.isEmpty()) return true;
            Node graphic = cell.getGraphic();
            return graphic != null && has(graphic.getAccessibleText());
        }
        return false;
    }

    private static boolean has(String text) {
        return text != null && !text.isBlank();
    }

    private static String describe(Control control) {
        String id = control.getId();
        StringBuilder path = new StringBuilder();
        for (Parent p = control.getParent(); p != null && path.length() < 200; p = p.getParent()) {
            path.append(" < ").append(p.getStyleClass());
        }
        return control.getClass().getSimpleName() + (id == null ? "" : "#" + id) + " " + control.getStyleClass() + path;
    }
}
