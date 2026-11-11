package com.example.app.ui;

import java.util.List;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import com.example.app.i18n.Messages;
import com.example.app.sync.Doctor;
import org.jspecify.annotations.Nullable;

/**
 * The Doctor (DIAG-07): a row per thing the app depends on outside itself, its status as a pill, what was found,
 * and for a failure what to do and a link to read more. The checks run off the FX thread; the window says it is
 * checking until they land.
 */
final class DoctorWindow {

    private final AppServices services;
    private final Dialog<Void> dialog = new Dialog<>();
    private final GridPane rows = new GridPane();
    private final Label checking = new Label(Messages.tr("doctor.checking"));

    DoctorWindow(AppServices services, @Nullable Window owner) {
        this.services = services;
        dialog.setTitle(Messages.tr("doctor.title"));
        rows.setHgap(12);
        rows.setVgap(8);
        rows.getStyleClass().add("doctor-rows");
        checking.getStyleClass().add("muted");
        VBox content = new VBox(10, checking, rows);
        content.setPadding(new Insets(6, 8, 2, 8));
        UiZoom.bind(content.prefWidthProperty(), 560);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Dialogs.styled(dialog, owner);
    }

    void show() {
        dialog.show();
        Background.run(() -> {
            List<Doctor.Check> checks = services.doctor();
            Platform.runLater(() -> fill(checks));
        });
    }

    /** One row per check: its name, its status pill, what was found, and the fix with its link. */
    void fill(List<Doctor.Check> checks) {
        checking.setVisible(false);
        checking.setManaged(false);
        rows.getChildren().clear();
        int row = 0;
        for (Doctor.Check check : checks) {
            Label name = new Label(check.name());
            name.getStyleClass().add("setting-title");
            Label status = new Label(
                    Messages.tr("doctor.status." + check.status().name().toLowerCase(java.util.Locale.ROOT)));
            status.getStyleClass()
                    .addAll(
                            "pill",
                            switch (check.status()) {
                                case OK -> "green";
                                case WARNING -> "amber";
                                case FAILED -> "red";
                            });
            Label detail = new Label(check.detail());
            detail.getStyleClass().add("muted");
            detail.setWrapText(true);
            VBox found = new VBox(2, detail);
            String fix = check.fix();
            if (fix != null) {
                Label what = new Label(fix);
                what.setWrapText(true);
                found.getChildren().add(what);
                String link = check.link();
                if (link != null) {
                    Hyperlink more = new Hyperlink(link);
                    more.setOnAction(e -> services.host().openUrl(link));
                    found.getChildren().add(more);
                }
            }
            rows.addRow(row++, name, status, found);
        }
    }

    Dialog<Void> dialog() {
        return dialog;
    }

    GridPane rows() {
        return rows;
    }
}
