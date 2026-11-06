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
                new Picker.Item<>("a", "Alpha", "first letter", "C-a", null),
                new Picker.Item<>("b", "Beta", "second", "", "Unavailable — not now."),
                new Picker.Item<>("c", "Gamma", "third", "", null),
                new Picker.Item<>("d", "Delta", "fourth", "", null));
    }

    @Test
    void ranksTitlesFirstAndSkipsUnavailableRows() throws Exception {
        assertEquals(4, Picker.rank(items(), "").size());
        assertEquals("d", Picker.rank(items(), "delta").getFirst().item().value());
        assertEquals("a", Picker.rank(items(), "first").getFirst().item().value());
        assertTrue(Picker.rank(items(), "zzz").isEmpty());

        List<String> chosen = new ArrayList<>();
        OverlayHost host = call(() -> new OverlayHost(new Pane()));
        Picker<String> picker = call(() -> new Picker<>(host, "Run…", "M-x", items(), chosen::add));
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
}
