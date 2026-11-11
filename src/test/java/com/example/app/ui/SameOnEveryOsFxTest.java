package com.example.app.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.Node;

import com.example.app.Os;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OS-03, OS-01: the app runs as each OS in turn, through {@link Os#override}, and has the same commands, the
 * same menu rows and the same Settings pages and rows on Windows, macOS and Linux. Where macOS's own way
 * wins (the system menu bar draws the rows, the application menu holds About and Settings) the rows are the
 * same commands all the same.
 */
@Tag("fx")
class SameOnEveryOsFxTest {

    @TempDir
    Path dir;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @AfterEach
    void reset() {
        Os.override(null);
    }

    @Test
    void theCommandsTheMenusAndSettingsAreTheSameOnEveryOs() throws Exception {
        Map<Os.Kind, List<String>> commands = new LinkedHashMap<>();
        Map<Os.Kind, List<String>> menus = new LinkedHashMap<>();
        Map<Os.Kind, List<String>> settings = new LinkedHashMap<>();
        for (Os.Kind kind : Os.Kind.values()) {
            Os.override(kind);
            Startup.Running app = FxTestSupport.start(dir.resolve(kind.name()), new FakeHost());
            try {
                commands.put(
                        kind,
                        call(() -> app.services().commands().ids().stream()
                                .sorted()
                                .toList()));
                menus.put(
                        kind,
                        call(() -> app.window().menus().groups().stream()
                                .flatMap(List::stream)
                                .map(AppMenus.Row::commandId)
                                .toList()));
                List<String> rows = new ArrayList<>();
                for (SettingsWindow.Page page : SettingsWindow.Page.values()) {
                    run(() -> app.window().openSettings(page));
                    rows.add(page.name());
                    rows.addAll(call(() ->
                            app
                                    .window()
                                    .settingsWindow()
                                    .stage()
                                    .getScene()
                                    .getRoot()
                                    .lookupAll(".setting-title")
                                    .stream()
                                    .filter(Node::isVisible)
                                    .filter(n -> !inList(n)) // a list's rows are its data, built as it lays out
                                    .map(n -> ((javafx.scene.control.Label) n).getText())
                                    .distinct()
                                    .toList()));
                }
                settings.put(kind, rows);
            } finally {
                FxTestSupport.stop(app);
            }
        }
        assertTrue(commands.get(Os.Kind.LINUX).contains("app.settings"));
        assertEquals(commands.get(Os.Kind.LINUX), commands.get(Os.Kind.WINDOWS), "commands");
        assertEquals(commands.get(Os.Kind.LINUX), commands.get(Os.Kind.MAC), "commands");
        assertEquals(menus.get(Os.Kind.LINUX), menus.get(Os.Kind.WINDOWS), "menu rows");
        assertEquals(menus.get(Os.Kind.LINUX), menus.get(Os.Kind.MAC), "menu rows");
        assertEquals(settings.get(Os.Kind.LINUX), settings.get(Os.Kind.WINDOWS), "Settings");
        assertEquals(settings.get(Os.Kind.LINUX), settings.get(Os.Kind.MAC), "Settings");
    }

    private static boolean inList(Node node) {
        for (javafx.scene.Parent p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof javafx.scene.control.ListView<?>) return true;
        }
        return false;
    }
}
