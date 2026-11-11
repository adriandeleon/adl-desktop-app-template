package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.press;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The picker behind the palette: ranking, unavailable rows, keys. */
@Tag("fx")
class PickerFxTest {

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    private static List<Picker.Item<String>> items() {
        return List.of(
                new Picker.Item<>("a", "Alpha", "first letter", "C-a", null, "greek start"),
                new Picker.Item<>("b", "Beta", "second", "", "Unavailable — not now."),
                new Picker.Item<>("c", "Gamma", "third", "", null),
                new Picker.Item<>("d", "Delta", "fourth", "", null));
    }

    @Test
    void ranksTitlesFirstAndSkipsUnavailableRows() throws Exception {
        assertEquals(4, Picker.rank(items(), "").size());
        assertEquals("d", Picker.rank(items(), "delta").getFirst().item().value());
        // KBD-06: the description is drawn, never matched; the id's words or keywords are.
        assertTrue(Picker.rank(items(), "first").isEmpty(), "the description is not matched");
        assertEquals("a", Picker.rank(items(), "greek").getFirst().item().value());
        assertEquals("view toggle status bar", Picker.idWords("view.toggleStatusBar"));
        assertTrue(Picker.rank(items(), "zzz").isEmpty());

        List<String> chosen = new ArrayList<>();
        OverlayHost host = call(() -> new OverlayHost(new Pane()));
        // The keymap's line chords move it (PAL-06): C-n and C-p in Emacs.
        com.example.app.command.Keybindings emacs = new com.example.app.command.Keybindings(
                com.example.app.command.Keymap.load("emacs", false), java.util.Map.of(), false);
        Picker<String> picker =
                call(() -> new Picker<>(host, "Run…", "M-x", items(), chosen::add).keymap(() -> emacs, false));
        run(picker::show);
        assertEquals(0, call(() -> picker.list().getSelectionModel().getSelectedIndex()));
        run(() -> press(picker.query(), KeyCode.DOWN, false, false, false, false));
        assertEquals(
                "c",
                call(() -> picker.list()
                        .getSelectionModel()
                        .getSelectedItem()
                        .item()
                        .value()));
        run(() -> press(picker.query(), KeyCode.P, false, true, false, false));
        assertEquals(
                "a",
                call(() -> picker.list()
                        .getSelectionModel()
                        .getSelectedItem()
                        .item()
                        .value()));
        run(() -> press(picker.query(), KeyCode.PAGE_DOWN, false, false, false, false));
        assertEquals(
                "d",
                call(() -> picker.list()
                        .getSelectionModel()
                        .getSelectedItem()
                        .item()
                        .value()));
        run(() -> press(picker.query(), KeyCode.PAGE_UP, false, false, false, false));
        run(() -> press(picker.query(), KeyCode.N, false, true, false, false));
        run(() -> press(picker.query(), KeyCode.UP, false, false, false, false));
        run(() -> press(picker.query(), KeyCode.X, false, false, false, false));

        run(() -> picker.query().setText("beta"));
        assertEquals(-1, call(() -> picker.list().getSelectionModel().getSelectedIndex()));
        run(picker::choose);
        assertTrue(chosen.isEmpty());
        run(() -> picker.query().setText("zzz"));
        run(() -> press(picker.query(), KeyCode.DOWN, false, false, false, false));
        run(() -> picker.query().setText("gam"));
        run(() -> press(picker.query(), KeyCode.ENTER, false, false, false, false));
        assertEquals(List.of("c"), chosen);
        assertTrue(!call(host::isShowing));
    }

    /**
     * PAL-08: the help chord is the first of Ctrl+H, F1 and Shift+F1 the keymap leaves free; QOPEN-02 and
     * QOPEN-04: a picker has a title and opens on the value in force; QOPEN-06: Ctrl+Home and Ctrl+End.
     */
    @Test
    void helpTitleCurrentValueAndTheEnds() throws Exception {
        com.example.app.command.Keybindings cua = new com.example.app.command.Keybindings(
                com.example.app.command.Keymap.load("cua", false), java.util.Map.of(), false);
        com.example.app.command.Keybindings emacs = new com.example.app.command.Keybindings(
                com.example.app.command.Keymap.load("emacs", false), java.util.Map.of(), false);
        assertEquals("Ctrl+H", PickerKeys.helpChord(cua, false).label(false, false));
        assertEquals("F1", PickerKeys.helpChord(emacs, false).label(false, false), "C-h is Emacs's help prefix");

        List<String> helped = new ArrayList<>();
        OverlayHost host = call(() -> new OverlayHost(new Pane()));
        Picker<String> picker = call(() -> new Picker<>(host, "Pick…", "", items(), v -> {})
                .keymap(() -> cua, false)
                .help(helped::add)
                .titled("Choose a Greek letter")
                .current("d"));
        run(picker::show);
        assertEquals(
                "d",
                call(() -> picker.list()
                        .getSelectionModel()
                        .getSelectedItem()
                        .item()
                        .value()));
        assertTrue(call(() -> picker.title().isVisible()));
        assertEquals(
                List.of("a", "b", "c", "d"),
                call(() -> picker.list().getItems().stream()
                        .map(r -> r.item().value())
                        .toList()),
                "nothing typed: the supplier's own order");
        run(() -> press(picker.query(), KeyCode.HOME, false, true, false, false));
        assertEquals(
                "a",
                call(() -> picker.list()
                        .getSelectionModel()
                        .getSelectedItem()
                        .item()
                        .value()));
        run(() -> press(picker.query(), KeyCode.END, false, true, false, false));
        assertEquals(
                "d",
                call(() -> picker.list()
                        .getSelectionModel()
                        .getSelectedItem()
                        .item()
                        .value()));
        run(() -> press(picker.query(), KeyCode.H, false, true, false, false));
        assertEquals(List.of("d"), helped);
    }
}
