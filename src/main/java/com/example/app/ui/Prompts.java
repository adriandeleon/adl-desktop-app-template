package com.example.app.ui;

import java.util.function.Consumer;
import java.util.function.Function;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import org.jspecify.annotations.Nullable;

/**
 * A palette command that prompts for a setting's value (SET-06): a text dialog that says why a value
 * is invalid and keeps OK off until it is not, so an invalid value is never committed. Not
 * modal-blocking: the value is committed when the dialog closes on OK.
 */
final class Prompts {

    private Prompts() {}

    /**
     * Asks for a value. {@code problem} answers null for a valid text, else the reason shown under the
     * field; {@code commit} gets the trimmed text on OK.
     */
    static TextInputDialog text(
            @Nullable Window owner,
            String title,
            String label,
            String initial,
            Function<String, @Nullable String> problem,
            Consumer<String> commit) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(label);
        dialog.getEditor().setAccessibleText(label); // the field is named by its label (ACC-01)
        Label why = new Label();
        why.getStyleClass().add("prompt-problem");
        why.setWrapText(true);
        dialog.getDialogPane().setExpandableContent(null);
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        Runnable check = () -> {
            String reason = problem.apply(dialog.getEditor().getText().trim());
            ok.setDisable(reason != null);
            why.setText(reason == null ? "" : reason);
            dialog.getEditor().getStyleClass().remove("invalid");
            if (reason != null) dialog.getEditor().getStyleClass().add("invalid");
        };
        // Validation waits for typing to pause (PERF-06); OK re-validates before it commits anyway.
        Debounce checking = new Debounce(check);
        dialog.getEditor().textProperty().addListener((o, was, now) -> checking.request());
        check.run();
        if (dialog.getDialogPane().getContent() instanceof javafx.scene.layout.GridPane grid) grid.add(why, 1, 1);
        Dialogs.styled(dialog, owner);
        dialog.setOnHidden(e -> {
            String result = dialog.getResult();
            if (result != null && problem.apply(result.trim()) == null) commit.accept(result.trim());
        });
        dialog.show();
        return dialog;
    }
}
