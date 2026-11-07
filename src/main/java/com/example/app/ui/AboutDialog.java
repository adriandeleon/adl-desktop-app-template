package com.example.app.ui;

import java.io.InputStream;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import com.example.app.AppInfo;
import com.example.app.i18n.Messages;
import com.example.app.update.ReleaseInfo;
import org.jspecify.annotations.Nullable;

/**
 * About, as in the family's About template: identity, update status, copyright, links, the
 * environment block with Copy details, and one Close button that Enter and Esc both press.
 */
public final class AboutDialog {

    private final AppServices services;
    private final Dialog<Void> dialog = new Dialog<>();
    private final VBox updateBlock = new VBox(2);
    private final Button copy = new Button(Messages.tr("about.copyDetails"));
    private final Runnable listener = this::refreshUpdate;

    public AboutDialog(AppServices services, @Nullable Window owner) {
        this.services = services;
        dialog.setTitle(Messages.tr("dialog.about.title", AppInfo.NAME));

        Label name = new Label(AppInfo.NAME);
        name.getStyleClass().add("about-name");
        Label version = new Label(Messages.tr("about.version", AppInfo.VERSION));
        version.getStyleClass().add("about-version");
        Label tagline = new Label(AppInfo.TAGLINE);
        tagline.getStyleClass().add("about-tagline");
        tagline.setWrapText(true);
        HBox head = new HBox(16, icon(), new VBox(2, name, version, tagline));
        head.setAlignment(Pos.CENTER_LEFT);

        Label copyright = new Label(AppInfo.COPYRIGHT);
        copyright.getStyleClass().add("muted");

        String settingsFile = AppInfo.tildeHome(services.store().file().toString());
        VBox links = new VBox(
                2,
                link(Messages.tr("about.license"), AppInfo.LICENSE_URL),
                link(Messages.tr("about.homepage"), AppInfo.HOMEPAGE),
                link(Messages.tr("about.releases"), AppInfo.RELEASES_PAGE),
                link(Messages.tr("about.pauta", AppInfo.PAUTA_EDITION), AppInfo.pautaUrl()),
                link(
                        Messages.tr("about.settingsFile", settingsFile),
                        services.store().file().toUri().toString()));

        Label environment = new Label(AppInfo.environment(services.store().dir()));
        environment.getStyleClass().add("about-env");
        environment.setWrapText(true);
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(AppInfo.environment(services.store().dir()));
            Clipboard.getSystemClipboard().setContent(content);
            copy.setText(Messages.tr("about.copied"));
        });
        VBox content = new VBox(14, head, updateBlock, copyright, links, environment, copy);
        content.getStyleClass().add("about-content");
        content.setPadding(new Insets(6, 8, 2, 8));
        content.setPrefWidth(460);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (dialog.getDialogPane().lookupButton(ButtonType.CLOSE) instanceof Button close) {
            close.setDefaultButton(true);
        }
        Dialogs.styled(dialog, owner);
        services.updates().addListener(listener);
        refreshUpdate();
    }

    private Node icon() {
        try (InputStream in = AboutDialog.class.getResourceAsStream("/com/example/app/icons/app-128.png")) {
            if (in != null) {
                ImageView view = new ImageView(new Image(in, 72, 72, true, true));
                view.setFitWidth(72);
                view.setFitHeight(72);
                return view;
            }
        } catch (java.io.IOException e) {
            // no icon: the name stands alone
        }
        return new Label();
    }

    private Hyperlink link(String text, String url) {
        Hyperlink link = new Hyperlink(text);
        link.setOnAction(e -> services.host().openUrl(url));
        return link;
    }

    /** Shows the update status. An update check that never answered never reads as "up to date". */
    void refreshUpdate() {
        UpdateCoordinator updates = services.updates();
        updateBlock.getChildren().clear();
        Hyperlink checkNow = new Hyperlink(Messages.tr("about.checkNow"));
        checkNow.setOnAction(e -> updates.checkNow(true));
        String last = updates.lastChecked() <= 0
                ? ""
                : Messages.tr("about.lastChecked", Ago.format(updates.lastChecked(), System.currentTimeMillis()));
        ReleaseInfo latest = updates.latest();
        Node status;
        switch (updates.state()) {
            case AVAILABLE -> {
                Hyperlink available = new Hyperlink(
                        Messages.tr("about.updateAvailable") + " " + (latest == null ? "" : latest.version()));
                available.getStyleClass().add("about-update-available");
                available.setOnAction(e -> {
                    if (latest != null) services.host().openUrl(latest.url());
                });
                status = available;
            }
            case UP_TO_DATE -> status = styled(Messages.tr("about.upToDate"), "about-update-current");
            case FAILED ->
                status = styled(
                        Messages.tr("about.updateFailed", String.valueOf(updates.error())), "about-update-failed");
            case CHECKING -> status = styled(Messages.tr("about.checking"), "muted");
            default -> status = styled(last.isEmpty() ? Messages.tr("about.notChecked") : last, "muted");
        }
        updateBlock.getChildren().add(status);
        HBox second = new HBox(4);
        second.setAlignment(Pos.CENTER_LEFT);
        boolean statusIsLast = updates.state() == UpdateCoordinator.State.NEVER;
        if (!last.isEmpty() && !statusIsLast) second.getChildren().add(styled(last, "muted"));
        if (updates.state() != UpdateCoordinator.State.CHECKING)
            second.getChildren().add(checkNow);
        updateBlock.getChildren().add(second);
    }

    private static Label styled(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }

    public Dialog<Void> dialog() {
        return dialog;
    }

    VBox updateBlock() {
        return updateBlock;
    }

    Button copyButton() {
        return copy;
    }

    /** Shows the dialog (modal to its window, without blocking the caller). */
    public void show() {
        dialog.setOnHidden(e -> services.updates().removeListener(listener));
        dialog.show();
    }
}
