package com.example.app.ui;

import java.nio.file.Path;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

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

/** The UI font on every window's scene, and each page glyph in the Settings sidebar as drawn. */
@Tag("fx")
class UiFontAndGlyphsFxTest {

    @TempDir
    Path dir;

    private Startup.Running app;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, new FakeHost());
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    /** ICON-01: a window nobody styled, a popup, and a window's new scene all get the UI font. */
    @Test
    void everyWindowsSceneGetsTheUiFont() throws Exception {
        String font = Themes.sheet(Themes.UI_FONT);
        assertTrue(call(() -> {
            Stage plain = new Stage();
            plain.setScene(new Scene(new Label("plain")));
            plain.show();
            boolean shown = plain.getScene().getStylesheets().contains(font);
            plain.setScene(new Scene(new Label("swapped")));
            boolean swapped = plain.getScene().getStylesheets().contains(font);
            plain.hide();
            plain.show();
            boolean once = plain.getScene().getStylesheets().stream()
                            .filter(font::equals)
                            .count()
                    == 1;
            plain.close();
            return shown && swapped && once;
        }));
        assertTrue(call(() -> {
            ContextMenu menu = new ContextMenu(new MenuItem("item"));
            menu.show(app.window().stage(), 10, 10);
            boolean styled = menu.getScene().getStylesheets().contains(font);
            menu.hide();
            return styled;
        }));
        run(Themes::installUiFont); // a second call adds nothing
    }

    /** ICON-19, ICON-20: each sidebar page wears its command's line glyph, as Icons draws it anywhere. */
    @Test
    void theSettingsSidebarDrawsEachPageWithItsCommandsGlyph() throws Exception {
        run(() -> app.window().openSettings(SettingsWindow.Page.GENERAL));
        SettingsWindow settings = app.window().settingsWindow();
        assertNotNull(settings);
        int checked = call(() -> {
            settings.sidebar().applyCss();
            settings.sidebar().layout();
            int count = 0;
            for (Node node : settings.sidebar().lookupAll(".list-cell")) {
                if (!(node instanceof ListCell<?> cell) || !(cell.getItem() instanceof SettingsWindow.Page page))
                    continue;
                SVGPath drawn = IconsTest.path(cell.getGraphic());
                SVGPath expected =
                        IconsTest.path(Icons.line(java.util.Objects.requireNonNull(Icons.forCommand(page.command))));
                assertEquals(expected.getContent(), drawn.getContent(), page.name());
                assertEquals(expected.getStyleClass(), drawn.getStyleClass(), page.name());
                assertFalse(drawn.getStyleClass().contains(Icons.FILLED), page.name());
                count++;
            }
            return count;
        });
        assertEquals(SettingsWindow.Page.values().length, checked);
    }
}
