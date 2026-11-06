package com.example.app.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import com.example.app.i18n.Messages;
import com.example.app.update.ReleaseInfo;

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

    public StatusBar(AppServices services, Runnable openLog) {
        this.services = services;
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);

        echo.getStyleClass().add("echo");
        echo.setOnMouseClicked(e -> openLog.run());
        unread.getStyleClass().add("unread-errors");
        unread.setOnMouseClicked(e -> openLog.run());
        update.getStyleClass().add("update-notice");
        update.setFocusTraversable(false);
        update.setOnAction(e -> {
            ReleaseInfo latest = services.updates().latest();
            if (latest != null) services.host().openUrl(latest.url());
        });
        keymap.getStyleClass().add("status-item");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        getChildren().addAll(echo, unread, spacer, update, keymap);

        services.messages().addListener(entry -> {
            echo.setText(entry.text());
            echo.getStyleClass().removeAll("error", "warning");
            if (entry.severity() == MessageLog.Severity.ERROR)
                echo.getStyleClass().add("error");
            if (entry.severity() == MessageLog.Severity.WARNING)
                echo.getStyleClass().add("warning");
            refreshUnread();
        });
        services.updates().addListener(this::refreshUpdate);
        services.onKeybindingsChanged(this::refreshKeymap);
        refreshUnread();
        refreshUpdate();
        refreshKeymap();
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
