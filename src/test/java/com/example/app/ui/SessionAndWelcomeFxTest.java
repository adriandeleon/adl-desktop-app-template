package com.example.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.scene.control.Tab;

import com.example.app.config.ConfigStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session apart from the settings (SET-08), the documents that come back (WIN-08), the Welcome page's
 * place, menu and refresh (WELC-03, WELC-05, WELC-11) and the documents' own text zoom (WIN-09).
 */
@Tag("fx")
class SessionAndWelcomeFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    private static List<String> titles(MainWindow window) throws Exception {
        return call(() ->
                window.tabs().tabs().stream().map(t -> window.tabs().title(t)).toList());
    }

    /** SET-08, WIN-08, WELC-03: what was open comes back, from a file of its own; --no-session starts afresh. */
    @Test
    void theOpenDocumentsComeBackFromTheSessionFile() throws Exception {
        Startup.Running first = FxTestSupport.start(dir, host);
        try {
            // No session yet: the Welcome page, selected (WELC-03).
            assertEquals(
                    "Welcome",
                    call(() -> first.window().tabs().title(first.window().tabs().selected())));
            Tab notes = call(() -> first.window().tabs().tabs().stream()
                    .filter(t -> "notes.md".equals(first.window().tabs().title(t)))
                    .findFirst()
                    .orElseThrow());
            run(() -> first.window().tabs().tabPane().getSelectionModel().select(notes));
            run(() -> first.window().saveWindowState());
        } finally {
            FxTestSupport.stop(first);
        }
        String settings = Files.readString(dir.resolve(ConfigStore.FILE));
        String session = Files.readString(dir.resolve(ConfigStore.SESSION_FILE));
        assertFalse(settings.contains("\"window\""), "settings.json holds no session state");
        assertFalse(settings.contains("notes.md"));
        assertTrue(session.contains("notes.md") && session.contains("\"width\""), session);

        Startup.Running second = FxTestSupport.start(dir, host);
        try {
            assertEquals(List.of("README.md", "notes.md", "LICENSE"), titles(second.window()), "no Welcome page");
            assertEquals(
                    "notes.md",
                    call(() ->
                            second.window().tabs().title(second.window().tabs().selected())));
        } finally {
            FxTestSupport.stop(second);
        }

        Startup.Running third = FxTestSupport.start(dir, host, "--no-session");
        try {
            assertEquals(
                    "Welcome",
                    call(() -> third.window().tabs().title(third.window().tabs().selected())));
            assertEquals(List.of("Welcome"), titles(third.window()), "--no-session opens nothing of its own (CLI-04)");
            run(() -> third.window().tabs().closeAllTabs());
            run(() -> third.window().saveWindowState());
        } finally {
            FxTestSupport.stop(third);
        }
        assertTrue(
                Files.readString(dir.resolve(ConfigStore.SESSION_FILE)).contains("notes.md"),
                "a --no-session run leaves the last session alone");
    }

    /** WELC-03, WELC-05, WELC-11: Help → Welcome Page; a Close-only menu; rebuilt on every show. */
    @Test
    void theWelcomePageIsInHelpClosesAloneAndIsRebuiltWhenShown() throws Exception {
        Startup.Running app = FxTestSupport.start(dir, host);
        try {
            MainWindow window = app.window();
            assertTrue(call(() -> window.menus().groups().stream()
                    .flatMap(List::stream)
                    .anyMatch(r -> r.commandId().equals("view.welcome"))));
            assertTrue(
                    call(() -> window.menuBar().getMenus().getLast().getItems().stream()
                            .anyMatch(i -> "view.welcome".equals(i.getId()))),
                    "in the Help menu");
            CommandMenu menu = call(() -> window.tabs().welcomeMenu());
            run(menu::menu);
            assertEquals(
                    List.of("buffer.close"),
                    call(() -> menu.items().stream()
                            .map(javafx.scene.control.MenuItem::getId)
                            .toList()));
            assertFalse(call(() -> window.tabs().isPinned(window.tabs().welcome())), "not a pinned document");
            int before = call(window::welcomeRefreshes);
            run(() -> window.tabs().tabPane().getSelectionModel().select(1));
            assertTrue(call(() -> app.services().commands().run("view.welcome")));
            assertTrue(call(window::welcomeRefreshes) > before, "rebuilt when shown");
        } finally {
            FxTestSupport.stop(app);
        }
    }

    /** WIN-09: the documents zoom on their own, kept as a setting, and every keymap binds the three commands. */
    @Test
    void theDocumentsTextZoomsOnItsOwn() throws Exception {
        Startup.Running app = FxTestSupport.start(dir, host);
        try {
            MainWindow window = app.window();
            run(() -> window.tabs().tabPane().getSelectionModel().select(1));
            assertTrue(call(() -> app.services().commands().run("view.zoomIn")));
            assertEquals(110, app.services().settings().textZoom);
            assertTrue(
                    call(() -> window.tabs().activeDocument().text().getStyle()).contains("1.1em"));
            assertEquals(100, app.services().settings().uiZoom, "the UI zoom is its own");
            assertTrue(call(() -> app.services().commands().run("view.zoomReset")));
            assertEquals("", call(() -> window.tabs().activeDocument().text().getStyle()));
        } finally {
            FxTestSupport.stop(app);
        }
        for (String id : com.example.app.command.Keymap.PRESETS) {
            for (boolean mac : new boolean[] {false, true}) {
                var bindings = new com.example.app.command.Keybindings(
                        com.example.app.command.Keymap.load(id, mac), java.util.Map.of(), mac);
                for (String command : List.of("view.zoomIn", "view.zoomOut", "view.zoomReset")) {
                    assertFalse(bindings.label(command).isEmpty(), id + (mac ? " mac " : " ") + command);
                }
            }
        }
    }
}
