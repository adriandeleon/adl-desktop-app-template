package com.example.app.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.example.app.AppInfo;
import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The Debug Log window: modeless, with Refresh, Copy, Clear, Export and Report a Problem. The only way
 * to see logs in a packaged build.
 */
public final class DebugLogWindow {

    private final AppServices services;
    private final Stage stage = new Stage();
    private final TextArea text = new TextArea();

    public DebugLogWindow(AppServices services, @Nullable Window owner, Runnable reportProblem) {
        this.services = services;
        text.setEditable(false);
        text.getStyleClass().add("debug-log-text");

        Button refresh = button("debuglog.refresh", this::refresh);
        Button copy = button("debuglog.copy", () -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(DebugLog.text());
            Clipboard.getSystemClipboard().setContent(content);
        });
        Button clear = button("debuglog.clear", () -> {
            DebugLog.clear();
            refresh();
        });
        Button export = button("debuglog.export", this::export);
        Button report = button("debuglog.report", reportProblem);
        Path file = DebugLog.file();
        Label where = new Label(file == null ? "" : Messages.tr("debuglog.file", AppInfo.tildeHome(file.toString())));
        where.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(6, refresh, copy, clear, export, report, spacer, where);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(8));

        BorderPane root = new BorderPane(text, null, null, buttons, null);
        Scene scene = Themes.style(new Scene(root, 820, 480));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setScene(scene);
        stage.setTitle(Messages.tr("debuglog.title"));
        if (owner != null) stage.initOwner(owner);
        refresh();
    }

    private static Button button(String key, Runnable action) {
        Button button = new Button(Messages.tr(key));
        button.setOnAction(e -> action.run());
        return button;
    }

    public void show() {
        refresh();
        stage.show();
        stage.toFront();
    }

    void refresh() {
        text.setText(DebugLog.text());
        text.positionCaret(text.getLength());
    }

    /** Saves the log as a text file the user chooses. */
    void export() {
        Path target = services.host().chooseSaveFile(stage, Messages.tr("debuglog.export"), AppInfo.ID + "-debug.log");
        if (target == null) return;
        try {
            Files.writeString(target, DebugLog.text(), StandardCharsets.UTF_8);
            services.echo("status.logExported", String.valueOf(target.getFileName()));
        } catch (IOException e) {
            services.echo("status.logExportFailed", String.valueOf(e.getMessage()));
        }
    }

    Stage stage() {
        return stage;
    }

    TextArea text() {
        return text;
    }
}
