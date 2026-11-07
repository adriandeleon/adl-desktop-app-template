package com.example.app.ui;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The Message Log: this session's echo messages, newest first, as an in-scene card just above the
 * status bar. The filter is focused on open; Esc closes; Copy copies the visible entries; Clear empties
 * the log and its unread count.
 */
public final class MessageLogView {

    static final double WIDTH = 773;
    static final double LIST_HEIGHT = 336;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AppServices services;
    private final OverlayHost host;
    private final Runnable onRead;
    private final TextField filter = new TextField();
    private final ListView<MessageLog.Entry> list = new ListView<>();
    private final VBox card;

    public MessageLogView(AppServices services, OverlayHost host, Runnable onRead) {
        this.services = services;
        this.host = host;
        this.onRead = onRead;

        Label title = new Label(Messages.tr("messagelog.title"));
        title.getStyleClass().add("message-log-title");
        filter.setPromptText(Messages.tr("messagelog.filter"));
        Debounce filtering = new Debounce(this::refresh);
        filter.textProperty().addListener((obs, old, text) -> filtering.request());
        filter.setOnKeyPressed(e -> {
            filtering.flush();
            if ((e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.ENTER)
                    && !list.getItems().isEmpty()) {
                list.requestFocus();
                list.getSelectionModel().selectFirst();
                e.consume();
            }
        });

        list.getStyleClass().add("message-log-list");
        list.setPrefHeight(LIST_HEIGHT);
        list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        list.setPlaceholder(new Label(Messages.tr("messagelog.empty")));
        // Each row's right-click menu holds its commands; the Menu key and Shift+F10 reach it too.
        CommandMenu rowMenu =
                new CommandMenu(services, java.util.Arrays.asList("messageLog.copy", null, "messageLog.clear"));
        list.setCellFactory(v -> CommandMenu.onRow(new EntryCell(), () -> rowMenu));
        RowContextMenu.install(list);
        list.setOnKeyPressed(e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.C) {
                services.commands().run("messageLog.copy");
                e.consume();
            }
        });

        Button copy = new Button(Messages.tr("messagelog.copy"));
        copy.getStyleClass().add("flat");
        copy.setOnAction(e -> services.commands().run("messageLog.copy"));
        Button clear = new Button(Messages.tr("messagelog.clear"));
        clear.getStyleClass().add("flat");
        clear.setOnAction(e -> services.commands().run("messageLog.clear"));
        Label hint = new Label(Messages.tr("messagelog.hint"));
        hint.getStyleClass().add("hint");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(6, copy, clear, spacer, hint);
        footer.setAlignment(Pos.CENTER_LEFT);

        card = new VBox(8, title, filter, list, footer);
        card.getStyleClass().add("message-log");
        card.setPrefWidth(WIDTH);
        card.setMaxWidth(WIDTH);
        card.setMaxHeight(Region.USE_PREF_SIZE);
    }

    /** Opens the log above the status bar and marks its errors read. */
    public void show() {
        services.messages().markRead();
        onRead.run();
        filter.clear();
        refresh();
        host.show(card, Pos.BOTTOM_LEFT, new Insets(0, 0, 34, 8), null);
        filter.requestFocus();
    }

    /**
     * What Copy copies: while the log is open, the selected entries or else the filtered list; when it
     * is closed, every entry.
     */
    List<MessageLog.Entry> listed() {
        if (card.getParent() == null) return services.messages().entries();
        List<MessageLog.Entry> selected = list.getSelectionModel().getSelectedItems();
        return List.copyOf(selected.isEmpty() ? list.getItems() : selected);
    }

    void refresh() {
        String query = filter.getText().toLowerCase(Locale.ROOT).strip();
        list.getItems()
                .setAll(services.messages().entries().stream()
                        .filter(e -> query.isEmpty()
                                || e.text().toLowerCase(Locale.ROOT).contains(query))
                        .toList());
    }

    static String asText(List<MessageLog.Entry> entries) {
        return entries.stream()
                .map(e -> TIME.format(e.time()) + "  " + e.text())
                .collect(Collectors.joining("\n"));
    }

    ListView<MessageLog.Entry> list() {
        return list;
    }

    TextField filter() {
        return filter;
    }

    private static final class EntryCell extends ListCell<MessageLog.Entry> {
        @Override
        protected void updateItem(MessageLog.@Nullable Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label time = new Label(TIME.format(entry.time()));
            time.getStyleClass().add("time");
            Label text = new Label(entry.text());
            text.setWrapText(true);
            text.getStyleClass().add(entry.severity().name().toLowerCase(Locale.ROOT));
            HBox row = new HBox(10, time, text);
            row.setAlignment(Pos.TOP_LEFT);
            setText(null);
            setGraphic(row);
        }
    }
}
