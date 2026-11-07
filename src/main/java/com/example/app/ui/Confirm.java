package com.example.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The one confirm helper: a question before a destructive action. The action button names the
 * action from the catalog in the {@code danger} style; Cancel has the {@code CANCEL_CLOSE} role, so
 * Escape and the window's close button both answer Cancel, and anything but the action button is a
 * Cancel. Callers reach it through {@link Host#confirm}, which the headless tests answer.
 */
public final class Confirm {

    private Confirm() {}

    /** The dialog for a question, not yet shown. */
    static Alert dialog(Confirmations.Question question, @Nullable Window owner) {
        ButtonType action = new ButtonType(question.action(), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Messages.tr("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, question.message(), action, cancel);
        alert.setTitle(question.title());
        alert.setHeaderText(null);
        if (!question.choices().isEmpty()) {
            Label message = new Label(question.message());
            message.setWrapText(true);
            VBox content = new VBox(8, message);
            List<CheckBox> boxes = new ArrayList<>();
            for (Confirmations.Choice choice : question.choices()) {
                CheckBox box = new CheckBox(choice.label());
                box.setSelected(choice.ticked());
                box.selectedProperty().addListener((o, was, now) -> choice.tick(now));
                boxes.add(box);
            }
            content.getChildren().addAll(boxes);
            alert.getDialogPane().setContent(content);
            // Nothing ticked, nothing to do: the action waits for a tick.
            alert.getDialogPane()
                    .lookupButton(action)
                    .disableProperty()
                    .bind(Bindings.createBooleanBinding(
                            () -> boxes.stream().noneMatch(CheckBox::isSelected),
                            boxes.stream().map(CheckBox::selectedProperty).toArray(Observable[]::new)));
        }
        alert.getDialogPane().lookupButton(action).getStyleClass().add("danger");
        return Dialogs.styled(alert, owner);
    }

    /** Whether the dialog's answer is its action: a close by any other route is Cancel. */
    static boolean confirmed(Alert alert, Optional<ButtonType> answer) {
        ButtonType cancel = alert.getButtonTypes().stream()
                .filter(b -> b.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE)
                .findFirst()
                .orElse(ButtonType.CANCEL);
        return answer.orElse(cancel).getButtonData() == ButtonBar.ButtonData.OK_DONE;
    }

    /** Asks and waits; true only when the action button was pressed. */
    public static boolean ask(Confirmations.Question question, @Nullable Window owner) {
        Alert alert = dialog(question, owner);
        return confirmed(alert, alert.showAndWait());
    }
}
