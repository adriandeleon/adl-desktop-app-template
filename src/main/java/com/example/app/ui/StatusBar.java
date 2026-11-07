package com.example.app.ui;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import com.example.app.i18n.Messages;
import com.example.app.update.ReleaseInfo;
import org.jspecify.annotations.Nullable;

/**
 * The status bar. Its left side is the echo area, one line of feedback for the last action, errors in
 * red and warnings amber; a click on it (or on the unread-error count) opens the Message Log.
 */
public final class StatusBar extends HBox {

    private final AppServices services;
    private final Label echo = new Label();
    private final Label unread = new Label();
    private final Hyperlink update = new Hyperlink();
    private final Label keymap = new Label();

    public StatusBar(AppServices services) {
        this.services = services;
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);

        echo.getStyleClass().add("echo");
        // A click runs the Message Log command, like its menu row and its keys (WIN-01).
        echo.setOnMouseClicked(e -> services.commands().run("view.messageLog"));
        unread.getStyleClass().add("unread-errors");
        unread.setOnMouseClicked(e -> services.commands().run("view.messageLog"));
        update.getStyleClass().add("update-notice");
        update.setFocusTraversable(false);
        update.setOnAction(e -> services.commands().run("help.openNewRelease"));
        keymap.getStyleClass().add("status-item");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(echo, unread, spacer, update, keymap);

        // A burst of messages redraws the bar once (PERF-06), with the newest.
        services.messages().addListener(entry -> redraw.request());
        services.updates().addListener(this::refreshUpdate);
        services.onKeybindingsChanged(this::refreshKeymap);
        refreshUnread();
        refreshUpdate();
        refreshKeymap();
    }

    private final Redraw redraw = new Redraw(this::showNewest);

    private void showNewest() {
        List<MessageLog.Entry> entries = services.messages().entries();
        MessageLog.@Nullable Entry entry = entries.isEmpty() ? null : entries.getFirst();
        echo.setText(entry == null ? "" : entry.text());
        echo.getStyleClass().removeAll("error", "warning");
        if (entry != null && entry.severity() == MessageLog.Severity.ERROR)
            echo.getStyleClass().add("error");
        if (entry != null && entry.severity() == MessageLog.Severity.WARNING)
            echo.getStyleClass().add("warning");
        refreshUnread();
    }

    /** Called when the log has been opened or cleared. */
    public void refreshUnread() {
        int count = services.messages().unreadErrors();
        unread.setText(String.valueOf(count));
        unread.setVisible(count > 0);
        unread.setManaged(count > 0);
    }

    private void refreshUpdate() {
        ReleaseInfo latest = services.updates().latest();
        boolean show = latest != null && services.updates().state() == UpdateCoordinator.State.AVAILABLE;
        update.setText(latest == null ? "" : Messages.tr("status.updateNotice", latest.version()));
        update.setVisible(show);
        update.setManaged(show);
    }

    private void refreshKeymap() {
        keymap.setText(services.keybindings().keymap().name());
    }

    Label echo() {
        return echo;
    }

    Label unread() {
        return unread;
    }

    Hyperlink update() {
        return update;
    }
}
