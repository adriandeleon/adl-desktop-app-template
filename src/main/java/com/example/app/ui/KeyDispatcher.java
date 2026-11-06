package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.scene.Scene;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyEvent;

import com.example.app.AppInfo;
import com.example.app.command.KeyStroke;
import com.example.app.i18n.Messages;

/**
 * Turns key presses into commands through the active key bindings, including multi-key sequences:
 * after a prefix ({@code C-x}) the status bar lists the possible next keys, which-key style. Plain
 * typing in a text field is left alone; overlays handle their own keys.
 */
public final class KeyDispatcher {

    private final AppServices services;
    private final OverlayHost overlays;
    private final List<KeyStroke> pending = new ArrayList<>();

    public KeyDispatcher(AppServices services, OverlayHost overlays) {
        this.services = services;
        this.overlays = overlays;
    }

    public void install(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handle);
    }

    void handle(KeyEvent event) {
        if (overlays.isShowing()) return;
        KeyStroke stroke = KeyStroke.of(event);
        if (stroke == null) return;
        boolean modified = stroke.ctrl() || stroke.alt() || stroke.meta();
        if (pending.isEmpty() && !modified && event.getTarget() instanceof TextInputControl) return;
        pending.add(stroke);
        List<KeyStroke> sequence = List.copyOf(pending);
        String id = services.keybindings().commandFor(sequence);
        boolean emacs = services.keybindings().keymap().emacs();
        if (services.keybindings().isPrefix(sequence)) {
            Map<String, String> next = services.keybindings().continuations(sequence);
            services.echo(
                    "status.prefix",
                    KeyStroke.label(sequence, emacs, AppInfo.isMac()),
                    next.entrySet().stream()
                            .map(e -> e.getValue() + " " + Messages.tr("command." + e.getKey()))
                            .collect(Collectors.joining(" · ")));
            event.consume();
            return;
        }
        pending.clear();
        if (id != null) {
            event.consume();
            if (!services.commands().run(id)) {
                var command = services.commands().get(id);
                String reason = command == null ? null : command.unavailableReason();
                if (command != null && reason != null) services.echo("status.unavailable", command.title(), reason);
            }
        } else if (sequence.size() > 1) {
            event.consume();
            services.echo("status.undefinedKey", KeyStroke.label(sequence, emacs, AppInfo.isMac()));
        }
    }
}
