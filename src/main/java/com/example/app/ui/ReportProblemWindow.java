package com.example.app.ui;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import com.example.app.AppInfo;
import com.example.app.i18n.Messages;
import com.example.app.report.ProblemReport;
import com.example.app.report.Redactor;
import org.jspecify.annotations.Nullable;

/**
 * Help → Report a Problem: a redacted report the user can read and edit, then copied to the clipboard
 * and opened as a prefilled GitHub issue. Nothing is uploaded.
 */
public final class ReportProblemWindow {

    private final AppServices services;
    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final TextArea report = new TextArea();
    private final String environment;
    private final ButtonType submit;

    public ReportProblemWindow(AppServices services, @Nullable Window owner) {
        this(services, owner, Redactor.forThisMachine());
    }

    ReportProblemWindow(AppServices services, @Nullable Window owner, Redactor redactor) {
        this.services = services;
        this.environment = redactor.redact(AppInfo.environment(services.store().dir()));
        report.setText(redactor.redact(ProblemReport.build(services.settings(), DebugLog.text())));
        report.setWrapText(true);
        report.setPrefRowCount(18);
        report.getStyleClass().add("report-text");
        report.setAccessibleText(Messages.tr("report.title"));
        Label note = new Label(Messages.tr("report.note"));
        note.setWrapText(true);
        dialog.setTitle(Messages.tr("report.title"));
        dialog.getDialogPane().setContent(new VBox(10, note, report));
        UiZoom.bind(dialog.getDialogPane().prefWidthProperty(), 680);
        submit = new ButtonType(Messages.tr("report.submit"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submit, ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button == submit) submit();
            return button;
        });
        Dialogs.styled(dialog, owner);
    }

    public void show() {
        dialog.show();
        report.requestFocus();
    }

    /** Copies the (edited) report and opens the prefilled issue. */
    void submit() {
        String text = report.getText();
        ClipboardContent content = new ClipboardContent();
        content.putString(environment + "\n\n" + text);
        Clipboard.getSystemClipboard().setContent(content);
        services.host().openUrl(ProblemReport.issueUrl(environment, text));
        services.echo(ProblemReport.fitsInUrl(environment, text) ? "status.reportOpened" : "status.reportOnClipboard");
    }

    TextArea report() {
        return report;
    }

    Dialog<ButtonType> dialog() {
        return dialog;
    }

    ButtonType submitButton() {
        return submit;
    }
}
