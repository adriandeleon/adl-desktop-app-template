package com.example.app.ui;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;

import com.example.app.AppInfo;
import com.example.app.PautaConformance;
import com.example.app.PautaConformance.Conformance;
import com.example.app.PautaConformance.Entry;
import com.example.app.PautaConformance.Kind;
import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The Pauta window, as in Pauta's template of that name: the edition, the conformance and every
 * exception the app records, built like About. It reads {@code pauta.json} and {@code AppInfo} alone and
 * takes the conformance from {@link PautaConformance} (PWIN-03); it holds no rule IDs or conformance names
 * of its own.
 */
public final class PautaWindow {

    private final AppServices services;
    private final List<Entry> exceptions;
    private final Dialog<Void> dialog = new Dialog<>();
    private final VBox list = new VBox(2);
    private final Button copy = new Button(Messages.tr("pauta.copy"));

    public PautaWindow(AppServices services, @Nullable Window owner) {
        this(services, owner, PautaConformance.exceptions());
    }

    PautaWindow(AppServices services, @Nullable Window owner, List<Entry> exceptions) {
        this.services = services;
        this.exceptions = PautaConformance.sorted(exceptions);
        Conformance conformance = PautaConformance.of(exceptions);
        dialog.setTitle(Messages.tr("dialog.pauta.title"));

        Label edition = new Label(Messages.tr("pauta.edition", AppInfo.PAUTA_EDITION));
        edition.getStyleClass().add("about-name");
        Label pill = new Label(conformanceName(conformance));
        pill.getStyleClass().addAll("pill", "pauta-conformance", conformance.key());
        Label meaning = new Label(meaning(conformance, exceptions));
        meaning.getStyleClass().add("about-tagline");
        meaning.setWrapText(true);
        HBox head = new HBox(16, mark(), new VBox(4, edition, new HBox(pill), meaning));
        head.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(14, head);
        String counts = counts(exceptions);
        if (!counts.isEmpty()) {
            Label countsLabel = new Label(counts);
            countsLabel.getStyleClass().add("muted");
            content.getChildren().add(countsLabel);
        }
        fillList();
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("pauta-list");
        content.getChildren().add(scroll);

        VBox links = new VBox(
                2,
                link(Messages.tr("pauta.rules", AppInfo.PAUTA_EDITION), "help.pautaRules"),
                link(Messages.tr("pauta.docs"), "help.pautaDocs"));
        copy.setOnAction(e -> {
            services.commands().run("help.copyPauta");
            copy.setText(Messages.tr("pauta.copied"));
        });
        content.getChildren().addAll(links, copy);
        content.getStyleClass().add("about-content");
        content.setPadding(new Insets(6, 8, 2, 8));
        UiZoom.bind(content.prefWidthProperty(), 560);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (dialog.getDialogPane().lookupButton(ButtonType.CLOSE) instanceof Button close) {
            close.setDefaultButton(true);
        }
        Dialogs.styled(dialog, owner);
    }

    /** One group header per kind present, then its rows (PWIN-05's order); one line when there are none. */
    private void fillList() {
        if (exceptions.isEmpty()) {
            Label none = new Label(Messages.tr("pauta.none", AppInfo.NAME, AppInfo.PAUTA_EDITION));
            none.setWrapText(true);
            list.getChildren().add(none);
            return;
        }
        Kind group = null;
        for (Entry entry : exceptions) {
            if (entry.kind() != group) {
                group = entry.kind();
                Label header = new Label(Messages.tr("pauta.kind." + group.key()));
                header.getStyleClass().add("pauta-group");
                list.getChildren().add(header);
            }
            list.getChildren().add(row(entry));
        }
    }

    /** The rule's ID linked to its address in this edition, its level, the reason and the entry's link. */
    private Node row(Entry entry) {
        Hyperlink id = new Hyperlink(entry.rule());
        id.getStyleClass().add("pauta-rule");
        id.setOnAction(e -> services.host().openUrl(entry.url()));
        UiZoom.bind(id.minWidthProperty(), 80);
        Label level = new Label(entry.level());
        level.getStyleClass().addAll("muted", "pauta-level");
        UiZoom.bind(level.minWidthProperty(), 56);
        TextFlow reason = new TextFlow(new Text(entry.reason()));
        reason.getStyleClass().add("pauta-reason");
        String target = entry.link();
        if (target != null) {
            Hyperlink link = new Hyperlink(linkText(target));
            link.setOnAction(e -> services.host().openUrl(target));
            reason.getChildren().addAll(new Text(" "), link);
        }
        HBox.setHgrow(reason, Priority.ALWAYS);
        HBox row = new HBox(8, id, level, reason);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("pauta-row");
        return row;
    }

    private static String linkText(String link) {
        String issue = PautaConformance.issueNumber(link);
        if (issue != null) return issue;
        String decision = PautaConformance.decisionNumber(link);
        if (decision != null) return Messages.tr("pauta.decision", decision);
        return Messages.tr("pauta.link");
    }

    private static Node mark() {
        // Loaded in the background: opening the window reads no file on the FX thread (PERF-01).
        java.net.URL url = PautaWindow.class.getResource("/com/example/app/icons/pauta-128.png");
        if (url != null) {
            ImageView view = new ImageView(new Image(url.toExternalForm(), 72, 72, true, true, true));
            view.setFitWidth(72);
            view.setFitHeight(72);
            return view;
        }
        return new Label();
    }

    private Hyperlink link(String text, String commandId) {
        Hyperlink link = new Hyperlink(text);
        link.setOnAction(e -> services.commands().run(commandId));
        return link;
    }

    /** Complete, Core or Partial, in the active language. */
    static String conformanceName(Conformance conformance) {
        return Messages.tr("pauta.conformance." + conformance.key());
    }

    /** The sentence under the pill: what the conformance means for this app. */
    static String meaning(Conformance conformance, List<Entry> exceptions) {
        return switch (conformance) {
            case COMPLETE -> Messages.tr("pauta.conformance.complete.desc", AppInfo.NAME);
            case CORE ->
                Messages.tr(
                        "pauta.conformance.core.desc", AppInfo.NAME, PautaConformance.pending(exceptions, "SHOULD"));
            case PARTIAL ->
                Messages.tr(
                        "pauta.conformance.partial.desc", AppInfo.NAME, PautaConformance.pending(exceptions, "MUST"));
        };
    }

    /** "3 pending · 1 deviation · 1 not applicable"; a kind with none is left out, and no exceptions is empty. */
    static String counts(List<Entry> exceptions) {
        StringJoiner line = new StringJoiner(" · ");
        for (Map.Entry<Kind, Integer> count :
                PautaConformance.counts(exceptions).entrySet()) {
            line.add(Messages.tr("pauta.count." + count.getKey().key(), count.getValue()));
        }
        return line.toString();
    }

    /** Copy as text (PWIN-06): the edition, the conformance and the list as Markdown, one line per exception. */
    static String markdown(List<Entry> exceptions) {
        Conformance conformance = PautaConformance.of(exceptions);
        StringBuilder text = new StringBuilder();
        text.append("# ")
                .append(Messages.tr("pauta.edition", AppInfo.PAUTA_EDITION))
                .append(": ")
                .append(conformanceName(conformance))
                .append("\n\n")
                .append(meaning(conformance, exceptions))
                .append('\n');
        if (exceptions.isEmpty()) {
            text.append('\n')
                    .append(Messages.tr("pauta.none", AppInfo.NAME, AppInfo.PAUTA_EDITION))
                    .append('\n');
        }
        Kind group = null;
        for (Entry entry : PautaConformance.sorted(exceptions)) {
            if (entry.kind() != group) {
                group = entry.kind();
                text.append("\n## ")
                        .append(Messages.tr("pauta.kind." + group.key()))
                        .append("\n\n");
            }
            text.append("- [")
                    .append(entry.rule())
                    .append("](")
                    .append(entry.url())
                    .append(") ")
                    .append(entry.level())
                    .append(": ")
                    .append(entry.reason());
            String link = entry.link();
            if (link != null)
                text.append(" [")
                        .append(linkText(link))
                        .append("](")
                        .append(link)
                        .append(')');
            text.append('\n');
        }
        text.append('\n').append(AppInfo.pautaUrl()).append('\n');
        return text.toString();
    }

    public Dialog<Void> dialog() {
        return dialog;
    }

    VBox list() {
        return list;
    }

    Button copyButton() {
        return copy;
    }

    /** Shows the window (modal to its owner, without blocking the caller). */
    public void show() {
        dialog.show();
    }
}
