package com.example.app.ui;

import java.nio.file.Path;
import java.util.logging.Logger;

import javafx.scene.input.KeyCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.press;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Debug Log's find bar (FIND-01/02/06/07). */
@Tag("fx")
class DebugLogFindBarFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        Logger log = Logger.getLogger("findbar.test");
        log.warning("alpha one");
        log.warning("beta");
        log.warning("Alpha two");
    }

    @AfterEach
    void stop() throws Exception {
        Debounce.immediate = true;
        FxTestSupport.stop(app);
    }

    @Test
    void theBarIsInTheWindowSearchesAsYouTypeAndSteps() throws Exception {
        run(() -> app.services().commands().run("debugLog.find"));
        DebugLogWindow window = app.window().debugLogWindow();
        assertNotNull(window);
        DebugLogFindBar bar = window.findBar();
        assertTrue(call(bar::isOpen));
        // FIND-01: a node in the Debug Log's own scene, not a popup or a stage of its own.
        assertTrue(call(() -> bar.getScene() == window.stage().getScene()));

        // FIND-02: searching as you type (debounced) updates the count.
        run(() -> bar.field().setText("alpha"));
        String text = call(() -> window.text().getText());
        int first = text.indexOf("alpha one");
        int second = text.indexOf("Alpha two");
        assertTrue(call(bar::countText).matches("\\d+ of \\d+"), call(bar::countText));
        int total = Integer.parseInt(call(bar::countText).replaceAll(".* of ", ""));
        assertTrue(total >= 2);

        // FIND-06: Enter forward, Shift+Enter back, wrapping at the ends.
        run(() -> window.text().selectRange(first, first + 5));
        run(() -> press(bar.field(), KeyCode.ENTER, false, false, false, false));
        assertTrue(call(() -> window.text().getSelection().getStart()) > first);
        run(() -> window.text().selectRange(first, first + 5));
        run(() -> press(bar.field(), KeyCode.ENTER, true, false, false, false));
        int back = call(() -> window.text().getSelection().getStart());
        assertTrue(back < first || back >= second, "Shift+Enter went back, wrapping: " + back);

        // The steps are commands too (KBD-01): the palette reaches them while the bar is open.
        run(() -> window.text().selectRange(first, first + 5));
        run(() -> app.services().commands().run("debugLog.findNext"));
        assertTrue(call(() -> window.text().getSelection().getStart()) > first);
        assertEquals(
                null, app.services().commands().get("debugLog.findPrevious").unavailableReason());

        run(() -> bar.matchCase().setSelected(true));
        run(() -> bar.field().setText("zzz-not-there"));
        assertEquals("No matches", call(bar::countText));

        // FIND-07: Escape closes it, drops a pending search, clears the match, and the log has focus.
        Debounce.immediate = false;
        run(() -> bar.field().setText("beta"));
        run(() -> press(bar.field(), KeyCode.ESCAPE, false, false, false, false));
        assertFalse(call(bar::isOpen));
        assertEquals(0, call(() -> window.text().getSelection().getLength()));
        Thread.sleep(250); // past the typing pause: a cancelled search must not run
        assertEquals("No matches", call(bar::countText), "a pending search ran after the bar closed");
        assertTrue(call(() -> window.stage().getScene().getFocusOwner() == window.text()));
        assertNotNull(app.services().commands().get("debugLog.findNext").unavailableReason(), "closed bar");
    }
}
