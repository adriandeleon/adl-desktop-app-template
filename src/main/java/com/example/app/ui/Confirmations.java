package com.example.app.ui;

import com.example.app.i18n.Messages;

/**
 * The words of every question the app asks before it removes or overwrites something the user made,
 * as pure code, so a unit test reads them without a screen (Pauta's Confirm dialog template). The
 * question names the thing and what happens to it; the action button carries the action's own verb,
 * never OK or Yes. {@link Confirm} turns one into a dialog.
 */
public final class Confirmations {

    /** One question: the window title, the question itself, and the action button's verb. */
    public record Question(String title, String message, String action) {}

    private Confirmations() {}

    /** Before Import replaces the config folder with a {@code .zip}'s contents. */
    public static Question importConfig(String fileName) {
        return new Question(
                Messages.tr("confirm.import.title"),
                Messages.tr("confirm.import.question", fileName),
                Messages.tr("confirm.import.action"));
    }

    /** Before a binding the user set is removed and the keymap's own applies again. */
    public static Question resetBinding(String commandTitle, String userChord, String keymapChord) {
        return new Question(
                Messages.tr("confirm.resetBinding.title"),
                keymapChord.isEmpty()
                        ? Messages.tr("confirm.resetBinding.questionUnbound", commandTitle, userChord)
                        : Messages.tr("confirm.resetBinding.question", commandTitle, userChord, keymapChord),
                Messages.tr("confirm.resetBinding.action"));
    }
}
