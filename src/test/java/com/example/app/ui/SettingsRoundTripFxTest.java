package com.example.app.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QUAL-13: every switch, number and choice in Settings survives a restart. The test changes each control of
 * every page from its default, restarts the app on the same config folder, and reads every control back.
 */
@Tag("fx")
class SettingsRoundTripFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @Test
    void everyControlSurvivesARestart() throws Exception {
        Startup.Running first = FxTestSupport.start(dir, host);
        List<String> before = new ArrayList<>();
        List<String> defaults = new ArrayList<>();
        try {
            for (SettingsWindow.Page page : SettingsWindow.Page.values()) {
                run(() -> first.window().openSettings(page));
                Parent root = call(
                        () -> first.window().settingsWindow().stage().getScene().getRoot());
                defaults.addAll(call(() -> values(root)));
                run(() -> changeEvery(root));
                before.addAll(call(() -> values(root)));
            }
            first.services().store().flush();
        } finally {
            FxTestSupport.stop(first);
        }
        assertTrue(before.size() >= 12, "every switch, number and choice: " + before);
        int changed = 0;
        for (int i = 0; i < before.size(); i++) if (!before.get(i).equals(defaults.get(i))) changed++;
        assertTrue(changed >= before.size() - 1, "each control was changed (the title bar's can't be here): " + before);

        Startup.Running second = FxTestSupport.start(dir, host);
        try {
            List<String> after = new ArrayList<>();
            for (SettingsWindow.Page page : SettingsWindow.Page.values()) {
                run(() -> second.window().openSettings(page));
                Parent root = call(() ->
                        second.window().settingsWindow().stage().getScene().getRoot());
                after.addAll(call(() -> values(root)));
            }
            assertEquals(before, after);
            assertNotEquals(defaults, before);
        } finally {
            FxTestSupport.stop(second);
        }
    }

    /**
     * Every setting control on every page, in layout order: combo boxes, switches and fields. The rows of the
     * keybinding list are left out: it is a virtual list whose cell count follows the layout, and
     * SettingsWindowFxTest and KeybindingsTest cover it.
     */
    private static List<Node> controls(Parent root) {
        List<Node> found = new ArrayList<>();
        for (Node row : root.lookupAll(".setting-row")) {
            if (!inList(row)) collect(row, found);
        }
        return found;
    }

    private static boolean inList(Node node) {
        for (Node at = node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof ListView<?>) return true;
        }
        return false;
    }

    private static void collect(Node node, List<Node> found) {
        if (node instanceof ComboBox<?> || node instanceof SettingSwitch || node instanceof TextField) {
            found.add(node);
            return;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) collect(child, found);
        }
    }

    private static List<String> values(Parent root) {
        List<String> values = new ArrayList<>();
        for (Node control : controls(root)) {
            switch (control) {
                case ComboBox<?> box -> values.add(String.valueOf(box.getValue()));
                case SettingSwitch toggle -> values.add(String.valueOf(toggle.isSelected()));
                case TextField field -> values.add(field.getText());
                default -> {}
            }
        }
        return values;
    }

    /** Moves every control off its value: the next choice, the other switch state, a valid new text. */
    @SuppressWarnings("unchecked")
    private static void changeEvery(Parent root) {
        List<String> texts = new ArrayList<>(List.of("https://example.com/me/app-sync.git", "work", "45"));
        for (Node control : controls(root)) {
            switch (control) {
                case ComboBox<?> box -> {
                    ComboBox<Object> any = (ComboBox<Object>) box;
                    // The language stays English, so the second run's rows read the same; it still changes from
                    // following the system to a choice.
                    Object en = any.getItems().contains("en") ? "en" : null;
                    int at = any.getItems().indexOf(any.getValue());
                    any.setValue(
                            en != null
                                    ? en
                                    : any.getItems()
                                            .get((at + 1) % any.getItems().size()));
                }
                case SettingSwitch toggle -> {
                    if (!toggle.isDisabled()) toggle.fire();
                }
                case TextField field -> {
                    if (texts.isEmpty()) break;
                    field.setText(texts.removeFirst());
                    field.fireEvent(new ActionEvent());
                }
                default -> {}
            }
        }
    }
}
