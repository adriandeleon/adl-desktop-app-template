package com.example.app.command;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeybindingsTest {

    @Test
    void cuaHasAMacVariantWhereCmdReplacesCtrl() {
        Keymap mac = Keymap.load("cua", true);
        Keymap other = Keymap.load("cua", false);
        assertEquals(other.bindings().keySet(), mac.bindings().keySet(), "the same commands in both");
        assertEquals("Cmd+Shift+P", mac.bindings().get("palette.show"));
        assertEquals("Shortcut+Shift+P", other.bindings().get("palette.show"));
        mac.bindings().values().forEach(chord -> {
            assertNotNull(KeyStroke.parseSequence(chord), chord);
            org.junit.jupiter.api.Assertions.assertFalse(chord.contains("Ctrl"), chord);
        });
        // Emacs has no macOS variant: the plain file serves both.
        assertEquals(
                Keymap.load("emacs", false).bindings(),
                Keymap.load("emacs", true).bindings());
    }

    @Test
    void bundledPresetsLoad() {
        for (String id : Keymap.PRESETS) {
            Keymap keymap = Keymap.load(id);
            assertEquals(id, keymap.id());
            assertTrue(keymap.bindings().containsKey("palette.show"), id);
            for (String text : keymap.bindings().values()) assertTrue(KeyStroke.parseSequence(text) != null, text);
        }
        assertEquals("cua", Keymap.load("nope").id());
        assertTrue(Keymap.load("emacs").emacs());
        assertFalse(Keymap.load("cua").emacs());
    }

    @Test
    void userBindingsWinAndBlankUnbinds() {
        Keybindings bindings = new Keybindings(
                Keymap.load("emacs"), Map.of("app.quit", "C-q", "help.about", "", "bad", "Hyper+X"), false);
        assertEquals("app.quit", bindings.commandFor(KeyStroke.parseSequence("C-q")));
        assertNull(bindings.commandFor(KeyStroke.parseSequence("C-x C-c")));
        assertEquals("", bindings.label("help.about"));
        assertEquals("M-x", bindings.label("palette.show"));
        assertEquals("emacs", bindings.keymap().id());
    }

    @Test
    void prefixesAndWhichKeyHints() {
        Keybindings bindings = new Keybindings(Keymap.load("emacs"), Map.of(), false);
        List<KeyStroke> ch = KeyStroke.parseSequence("C-h");
        assertTrue(bindings.isPrefix(ch));
        assertFalse(bindings.isPrefix(KeyStroke.parseSequence("M-x")));
        Map<String, String> next = bindings.continuations(ch);
        assertEquals("e", next.get("view.messageLog"));
        assertEquals("C-a", next.get("help.about"));
    }

    @Test
    void aSecondCommandOnTheSameKeysIsAConflict() {
        Keybindings bindings = new Keybindings(Keymap.load("cua", false), Map.of("help.debugLog", "Shortcut+Q"), false);
        assertEquals(Map.of("app.quit", "help.debugLog"), bindings.conflicts());
        assertEquals("", bindings.label("app.quit"));
        assertEquals("help.debugLog", bindings.commandFor(KeyStroke.parseSequence("Ctrl+Q")));
        // The same every run: no hash order decides it.
        for (int i = 0; i < 20; i++) {
            Keybindings again = new Keybindings(
                    Keymap.load("cua", false),
                    Map.of("help.debugLog", "Shortcut+Q", "help.about", "Shortcut+Q"),
                    false);
            assertEquals(Map.of("help.debugLog", "help.about", "app.quit", "help.about"), again.conflicts());
        }
    }

    @Test
    void shortcutResolvesPerPlatform() {
        Keybindings mac = new Keybindings(Keymap.load("cua"), Map.of(), true);
        assertEquals("palette.show", mac.commandFor(List.of(KeyStroke.parse("Meta+Shift+P"))));
        assertEquals("⇧⌘P", mac.label("palette.show"));
    }

    @Test
    void registryRunsByIdAndRefusesUnavailable() {
        CommandRegistry registry = new CommandRegistry();
        int[] ran = new int[1];
        registry.register("a.run", () -> ran[0]++);
        registry.register("a.off", () -> ran[0] += 10, () -> "no");
        registry.register("a.throws", () -> {
            throw new IllegalStateException("boom");
        });
        assertTrue(registry.run("a.run"));
        assertFalse(registry.run("a.off"));
        assertFalse(registry.run("missing"));
        assertTrue(registry.run("a.throws"));
        assertEquals(1, ran[0]);
        assertEquals(List.of("a.run", "a.off", "a.throws"), registry.ids());
        assertEquals(3, registry.all().size());
        assertEquals("no", registry.get("a.off").unavailableReason());
        assertEquals("command.a.run", registry.get("a.run").title());
        assertEquals("command.a.run.desc", registry.get("a.run").description());
        assertThrows(IllegalArgumentException.class, () -> registry.register("a.run", () -> {}));
    }

    /**
     * KBD-10: IntelliJ, VS Code and Sublime keymaps beside CUA and Emacs, each with its macOS form; every bundled
     * keymap parses whole, has no two commands on the same keys, and binds find.show (FIND-04) and the region
     * commands (ACC-06).
     */
    @Test
    void everyBundledKeymapParsesWholeAndBindsWhatEveryKeymapMust() {
        assertEquals(java.util.List.of("cua", "emacs", "intellij", "vscode", "sublime"), Keymap.PRESETS);
        for (String id : Keymap.PRESETS) {
            for (boolean mac : new boolean[] {false, true}) {
                Keymap keymap = Keymap.load(id, mac);
                String where = id + (mac ? " (mac)" : "");
                keymap.bindings()
                        .forEach((command, chord) ->
                                assertNotNull(KeyStroke.parseSequence(chord), where + ": " + command + " = " + chord));
                Keybindings bindings = new Keybindings(keymap, java.util.Map.of(), mac);
                assertTrue(bindings.conflicts().isEmpty(), where + ": " + bindings.conflicts());
                for (String command : java.util.List.of(
                        "palette.show", "find.show", "window.focusNextRegion", "window.focusPreviousRegion")) {
                    assertFalse(bindings.label(command).isEmpty(), where + " binds " + command);
                }
            }
        }
        for (String id : java.util.List.of("intellij", "vscode", "sublime")) {
            assertNotNull(Keymap.class.getResource("/com/example/app/keymaps/" + id + ".mac.json"), id + ".mac.json");
        }
    }
}
