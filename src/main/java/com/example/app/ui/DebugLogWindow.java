package com.example.app.ui;

import java.nio.file.Path;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
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
    private final DebugLogFindBar findBar = new DebugLogFindBar(text);
    private final CommandMenu menu;

    public DebugLogWindow(AppServices services, @Nullable Window owner) {
        this.services = services;
        text.setEditable(false);
        findBar.runCommandsThrough(id -> services.commands().run(id));
        text.getStyleClass().add("debug-log-text");
        // A read-only console: its right-click menu is the Debug Log's commands.
        menu = new CommandMenu(
                services,
                java.util.Arrays.asList(
                        "debugLog.find",
                        null,
                        "debugLog.copy",
                        "debugLog.export",
                        null,
                        "debugLog.refresh",
                        "debugLog.clear"));
        CommandMenu.onConsole(text, menu);

        // Every button runs a command (KBD-01), so the palette and a key binding reach the same work.
        Button refresh = button("debuglog.refresh", "debugLog.refresh");
        Button copy = button("debuglog.copy", "debugLog.copy");
        Button clear = button("debuglog.clear", "debugLog.clear");
        Button export = button("debuglog.export", "debugLog.export");
        Button report = button("debuglog.report", "help.reportProblem");
        Path file = DebugLog.file();
        Label where = new Label(file == null ? "" : Messages.tr("debuglog.file", AppInfo.tildeHome(file.toString())));
        where.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(6, refresh, copy, clear, export, report, spacer, where);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(8));

        findBar.setPadding(new Insets(6, 8, 0, 8));
        BorderPane root = new BorderPane(text, null, null, new javafx.scene.layout.VBox(findBar, buttons), null);
        Scene scene = Themes.style(new Scene(root, 820, 480));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
            else if (e.isShortcutDown() && e.getCode() == KeyCode.F) {
                services.commands().run("debugLog.find");
                e.consume();
            }
        });
        stage.setScene(scene);
        stage.setTitle(Messages.tr("debuglog.title"));
        if (owner != null) stage.initOwner(owner);
        refresh();
    }

    private Button button(String key, String commandId) {
        Button button = new Button(Messages.tr(key));
        button.setOnAction(e -> services.commands().run(commandId));
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

    Stage stage() {
        return stage;
    }

    /** Opens the find bar over the log (the {@code debugLog.find} command). */
    void find() {
        findBar.open();
    }

    DebugLogFindBar findBar() {
        return findBar;
    }

    CommandMenu menu() {
        return menu;
    }

    TextArea text() {
        return text;
    }
}
