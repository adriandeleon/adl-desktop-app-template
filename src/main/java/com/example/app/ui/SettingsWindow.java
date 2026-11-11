package com.example.app.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import com.example.app.AppInfo;
import com.example.app.command.Command;
import com.example.app.command.KeyStroke;
import com.example.app.command.Keymap;
import com.example.app.config.Settings;
import com.example.app.config.ThemeChoice;
import com.example.app.i18n.Messages;
import com.example.app.sync.SyncEngine;
import org.jspecify.annotations.Nullable;

/**
 * Settings: a sidebar of pages, the page on the right, and a search field on top that hides every
 * non-matching row and card and opens the page with the best match. There is no Save button: a change
 * applies as it is made and reaches every window through the config store. Text fields commit on Enter
 * or focus loss; an invalid value is marked and never committed.
 */
public final class SettingsWindow {

    /** The pages, in sidebar order. */
    public enum Page {
        GENERAL("app.settings"),
        APPEARANCE("settings.theme"),
        KEYMAP("settings.keymap"),
        SYNC("sync.setup"),
        ADVANCED("config.openFolder");

        /** The command whose glyph the page wears in the sidebar ({@link Icons#forCommand}). */
        final String command;

        Page(String command) {
            this.command = command;
        }

        String title() {
            return Messages.tr("settings.page." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Each page's commands, for its sidebar row's right-click menu; {@code null} is a separator. */
    static final Map<Page, List<@Nullable String>> PAGE_COMMANDS = Map.of(
            Page.GENERAL,
                    java.util.Arrays.asList(
                            "settings.language",
                            "settings.toggleUpdateCheck",
                            "view.toggleToolbar",
                            "view.toggleTabBar",
                            "view.toggleStatusBar"),
            Page.APPEARANCE, java.util.Arrays.asList("settings.theme", "view.toggleTitleBarMenus"),
            Page.KEYMAP, java.util.Arrays.asList("settings.keymap", "settings.bindKey"),
            Page.SYNC,
                    java.util.Arrays.asList(
                            "sync.now",
                            "sync.toggleAuto",
                            null,
                            "sync.setRepository",
                            "sync.setBranch",
                            "sync.setInterval"),
            Page.ADVANCED, java.util.Arrays.asList("config.export", "config.import", null, "config.openFolder"));

    private final Map<Page, CommandMenu> pageMenus = new EnumMap<>(Page.class);

    private CommandMenu pageMenu(Page page) {
        return pageMenus.computeIfAbsent(
                page, p -> new CommandMenu(services, PAGE_COMMANDS.getOrDefault(p, List.of())));
    }

    /** A searchable row and the card it sits in. */
    record Row(Page page, Node node, VBox card, SettingsSearch.Entry entry) {}

    private final AppServices services;
    private final Stage stage = new Stage();
    private final TextField search = new TextField();
    private final ListView<Page> sidebar = new ListView<>();
    private final ScrollPane scroll = new ScrollPane();
    private final Map<Page, VBox> pages = new EnumMap<>(Page.class);
    private final List<Row> rows = new ArrayList<>();
    private final List<Runnable> loaders = new ArrayList<>();
    private final Map<Page, Boolean> pageHits = new EnumMap<>(Page.class);
    private final ListView<Command> bindingList = new ListView<>();
    private final javafx.collections.ObservableList<Command> bindingSource =
            javafx.collections.FXCollections.observableArrayList();
    private final javafx.collections.transformation.FilteredList<Command> bindingFilter =
            new javafx.collections.transformation.FilteredList<>(bindingSource);
    private final Map<Command, SettingsSearch.Entry> bindingEntries = new java.util.LinkedHashMap<>();

    private final ComboBox<String> language = new ComboBox<>();
    private final SettingSwitch updates = new SettingSwitch(Messages.tr("settings.updates.title"));
    private final SettingSwitch toolbar = new SettingSwitch(Messages.tr("settings.toolbar.title"));
    private final SettingSwitch tabBar = new SettingSwitch(Messages.tr("settings.tabBar.title"));
    private final SettingSwitch statusBar = new SettingSwitch(Messages.tr("settings.statusBar.title"));
    private final ComboBox<ThemeChoice> theme = new ComboBox<>();
    private final SettingSwitch titleBar = new SettingSwitch(Messages.tr("settings.titleBar.title"));
    private final ComboBox<String> keymap = new ComboBox<>();
    private final TextField syncUrl = new TextField();
    private final TextField syncBranch = new TextField();
    private final SettingSwitch syncAuto = new SettingSwitch(Messages.tr("settings.sync.auto.title"));
    private final TextField syncInterval = new TextField();
    private final Label syncPill = new Label();
    private final Consumer<Settings> storeListener = s -> load();
    private final Runnable syncListener = this::loadSyncStatus;

    public SettingsWindow(AppServices services, @Nullable Window owner) {
        this.services = services;
        for (Page page : Page.values()) {
            VBox content = new VBox(14);
            content.getStyleClass().add("settings-page");
            content.setPadding(new Insets(18, 22, 18, 22));
            Label title = new Label(page.title());
            title.getStyleClass().add("settings-page-title");
            content.getChildren().add(title);
            pages.put(page, content);
        }
        buildGeneral();
        buildAppearance();
        buildKeymap();
        buildSync();
        buildAdvanced();

        search.setPromptText(Messages.tr("settings.search"));
        search.getStyleClass().add("settings-search");
        Debounce searching = new Debounce(() -> applySearch(search.getText()));
        search.textProperty().addListener((obs, old, text) -> searching.request());
        sidebar.getItems().setAll(Page.values());
        // A page row's right-click menu holds that page's commands (MENU-05).
        sidebar.setCellFactory(v -> {
            PageCell cell = new PageCell();
            return CommandMenu.onRow(cell, () -> pageMenu(java.util.Objects.requireNonNull(cell.getItem())));
        });
        RowContextMenu.install(sidebar);
        sidebar.getStyleClass().add("settings-sidebar");
        sidebar.getSelectionModel().selectedItemProperty().addListener((obs, old, page) -> {
            if (page != null) scroll.setContent(pages.get(page));
        });
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("settings-scroll");

        VBox side = new VBox(8, search, sidebar);
        side.setPadding(new Insets(10));
        side.setPrefWidth(220);
        VBox.setVgrow(sidebar, Priority.ALWAYS);

        Label note = new Label(Messages.tr("settings.footer"));
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        Button close = new Button(Messages.tr("button.close"));
        close.setDefaultButton(true);
        close.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(10, note, spacer, close);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(8, 12, 10, 12));
        bottom.getStyleClass().add("settings-bottom");

        BorderPane root = new BorderPane(scroll, null, null, bottom, side);
        root.getStyleClass().add("settings-window");
        Scene scene = Themes.style(new Scene(root, 860, 600));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setScene(scene);
        stage.setTitle(Messages.tr("settings.title"));
        if (owner != null) stage.initOwner(owner);

        // One Settings window per app, kept for the session, so these listeners live as long as it does.
        services.store().addListener(storeListener);
        services.sync().addListener(syncListener);
        load();
        sidebar.getSelectionModel().select(Page.GENERAL);
    }

    /** Opens the window on a page. */
    public void show(Page page) {
        sidebar.getSelectionModel().select(page);
        stage.show();
        stage.toFront();
        search.requestFocus();
    }

    // ---- pages ----

    private void buildGeneral() {
        language.getItems().add("");
        language.getItems().addAll(Messages.available().keySet());
        language.setConverter(converter(code -> code.isEmpty()
                ? Messages.tr("settings.language.system")
                : Messages.available().getOrDefault(code, code)));
        language.valueProperty()
                .addListener((obs, old, picked) -> commitIfChanged(picked, s -> s.language, v -> {
                    services.store().update(s -> s.language = v);
                    services.echo("status.languageRestart");
                }));
        VBox card = card(Page.GENERAL, null);
        row(card, Page.GENERAL, "settings.language", language, "locale translation");
        updates.setOnAction(e -> services.store().update(s -> s.checkForUpdates = updates.isSelected()));
        row(card, Page.GENERAL, "settings.updates", updates, "release version network");
        toolbar.setOnAction(e -> services.store().update(s -> s.showToolbar = toolbar.isSelected()));
        row(card, Page.GENERAL, "settings.toolbar", toolbar, "buttons icons");
        tabBar.setOnAction(e -> services.store().update(s -> s.showTabBar = tabBar.isSelected()));
        row(card, Page.GENERAL, "settings.tabBar", tabBar, "tabs documents strip");
        statusBar.setOnAction(e -> services.store().update(s -> s.showStatusBar = statusBar.isSelected()));
        row(card, Page.GENERAL, "settings.statusBar", statusBar, "echo messages");
        loaders.add(() -> {
            Settings s = services.settings();
            language.setValue(s.language);
            updates.setSelected(s.checkForUpdates);
            toolbar.setSelected(s.showToolbar);
            tabBar.setSelected(s.showTabBar);
            statusBar.setSelected(s.showStatusBar);
        });
    }

    private void buildAppearance() {
        theme.getItems().setAll(ThemeChoice.values());
        theme.setConverter(converter(t -> Messages.tr("theme." + t.name().toLowerCase(java.util.Locale.ROOT))));
        theme.valueProperty()
                .addListener((obs, old, picked) -> commitIfChanged(
                        picked, s -> s.theme, v -> services.store().update(s -> s.theme = v)));
        VBox card = card(Page.APPEARANCE, null);
        row(card, Page.APPEARANCE, "settings.theme", theme, "dark light mode colour color");
        titleBar.setOnAction(e -> {
            services.store().update(s -> s.titleBarMenus = titleBar.isSelected());
            services.echo("status.titleBarRestart");
        });
        row(card, Page.APPEARANCE, "settings.titleBar", titleBar, "header bar window title menus");
        loaders.add(() -> {
            theme.setValue(services.settings().theme);
            titleBar.setSelected(services.settings().titleBarMenus);
        });
    }

    private void buildKeymap() {
        keymap.getItems().setAll(Keymap.PRESETS);
        keymap.setConverter(converter(id -> Keymap.load(id).name()));
        keymap.valueProperty()
                .addListener((obs, old, picked) -> commitIfChanged(
                        picked, s -> s.keymap, v -> services.store().update(s -> s.keymap = v)));
        VBox presets = card(Page.KEYMAP, null);
        row(presets, Page.KEYMAP, "settings.keymap", keymap, "emacs cua shortcuts keys");
        loaders.add(() -> keymap.setValue(services.settings().keymap));

        // One row per registered command: a list that grows with the registry, so a virtualized
        // ListView with a fixed cell size, filtered by the search (PERF-08).
        VBox bindings = card(Page.KEYMAP, Messages.tr("settings.bindings.heading"));
        bindingList.getStyleClass().add("binding-list");
        bindingList.setFixedCellSize(BINDING_ROW_HEIGHT);
        CommandMenu rowMenu =
                new CommandMenu(services, java.util.Arrays.asList("keymap.changeBinding", null, "keymap.resetBinding"));
        bindingList.setCellFactory(v -> CommandMenu.onRow(new BindingCell(), () -> rowMenu));
        RowContextMenu.install(bindingList);
        for (Command command : services.commands().all()) {
            bindingEntries.put(
                    command,
                    new SettingsSearch.Entry(
                            command.title(),
                            command.description(),
                            Messages.tr("settings.bindings.reset"),
                            Page.KEYMAP.title() + " " + Messages.tr("settings.bindings.heading"),
                            command.id()));
        }
        bindingSource.setAll(bindingEntries.keySet());
        bindingFilter.setPredicate(c -> true);
        bindingList.setItems(bindingFilter);
        bindingList
                .prefHeightProperty()
                .bind(javafx.beans.binding.Bindings.min(
                                javafx.beans.binding.Bindings.size(bindingFilter), BINDING_ROWS_SHOWN)
                        .multiply(BINDING_ROW_HEIGHT)
                        .add(2));
        bindings.getChildren().add(bindingList);
        loaders.add(bindingList::refresh);
    }

    static final double BINDING_ROW_HEIGHT = 58;
    static final int BINDING_ROWS_SHOWN = 8;

    /** A key-binding row: the command's title and description, its keys, and Reset. */
    private final class BindingCell extends ListCell<Command> {
        private final Label title = new Label();
        private final Label description = new Label();
        private final TextField field = new TextField();
        private final Button reset = new Button(Messages.tr("settings.bindings.reset"));
        private final HBox row;

        BindingCell() {
            title.getStyleClass().add("setting-title");
            description.getStyleClass().add("setting-desc");
            field.setPrefColumnCount(14);
            reset.getStyleClass().add("flat");
            reset.setOnAction(e -> {
                Command command = getItem();
                if (command != null) resetBinding(command);
            });
            field.setOnAction(e -> commit());
            field.focusedProperty().addListener((obs, was, focused) -> {
                if (!focused) commit();
            });
            VBox text = new VBox(2, title, description);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox control = new HBox(6, field, reset);
            control.setAlignment(Pos.CENTER_RIGHT);
            row = new HBox(16, text, control);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("setting-row");
        }

        private void commit() {
            Command command = getItem();
            if (command != null && !isEmpty()) commitBinding(command.id(), field);
        }

        @Override
        protected void updateItem(@Nullable Command command, boolean empty) {
            super.updateItem(command, empty);
            setText(null);
            if (empty || command == null) {
                setGraphic(null);
                return;
            }
            title.setText(command.title());
            description.setText(command.description());
            field.setAccessibleText(command.title());
            String user = services.settings().keybindings.get(command.id());
            field.setText(
                    user != null
                            ? user
                            : services.keybindings().keymap().bindings().getOrDefault(command.id(), ""));
            field.getStyleClass().remove("invalid");
            String other = services.keybindings().conflicts().get(command.id());
            field.setTooltip(
                    other == null
                            ? null
                            : new javafx.scene.control.Tooltip(
                                    Messages.tr("settings.bindings.conflict", Messages.tr("command." + other))));
            if (other != null) field.getStyleClass().add("invalid");
            setGraphic(row);
        }
    }

    /** Removes the user's own binding for a command, after asking: it is something the user made. */
    void resetBinding(Command command) {
        String user = services.settings().keybindings.get(command.id());
        if (user == null) return;
        String keymapChord = services.keybindings().keymap().bindings().getOrDefault(command.id(), "");
        if (!services.host().confirm(stage, Confirmations.resetBinding(command.title(), user, keymapChord))) return;
        services.store().update(s -> s.keybindings.remove(command.id()));
    }

    /** Commits a binding: blank unbinds, an unparsable chord is marked and not saved. */
    void commitBinding(String id, TextField field) {
        String text = field.getText().trim();
        if (!text.isEmpty() && KeyStroke.parseSequence(text) == null) {
            if (!field.getStyleClass().contains("invalid"))
                field.getStyleClass().add("invalid");
            return;
        }
        field.getStyleClass().remove("invalid");
        String base = services.keybindings().keymap().bindings().getOrDefault(id, "");
        String current = services.settings().keybindings.getOrDefault(id, base);
        if (text.equals(current)) return;
        services.store().update(s -> {
            if (text.equals(base)) s.keybindings.remove(id);
            else s.keybindings.put(id, text);
        });
    }

    private void buildSync() {
        Label subtitle = new Label(Messages.tr("settings.sync.subtitle"));
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("muted");
        page(Page.SYNC).getChildren().add(subtitle);

        VBox repo = card(Page.SYNC, null);
        syncUrl.setPromptText("git@github.com:you/" + AppInfo.ID + "-sync.git");
        syncUrl.setOnAction(e -> commitUrl());
        syncUrl.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitUrl();
        });
        row(repo, Page.SYNC, "settings.sync.repo", syncUrl, "git github repository url");
        syncBranch.setPrefColumnCount(8);
        syncBranch.setOnAction(e -> commitBranch());
        syncBranch.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitBranch();
        });
        Button connect = new Button(Messages.tr("settings.sync.connect"));
        connect.getStyleClass().add("accent");
        connect.setOnAction(e -> {
            commitUrl();
            if (!syncUrl.getStyleClass().contains("invalid")) services.sync().syncNow(true);
        });
        Button create = new Button(Messages.tr("settings.sync.createGithub"));
        create.setOnAction(e -> services.commands().run("sync.createGitHubRepo"));
        HBox branchControl = new HBox(6, syncBranch, connect, create);
        branchControl.setAlignment(Pos.CENTER_RIGHT);
        row(repo, Page.SYNC, "settings.sync.branch", branchControl, "branch connect");

        Button now = new Button(Messages.tr("settings.sync.now"));
        now.setOnAction(e -> services.commands().run("sync.now"));
        Button folder = new Button(Messages.tr("settings.sync.openFolder"));
        folder.setOnAction(e -> services.commands().run("sync.openFolder"));
        syncPill.getStyleClass().add("pill");
        HBox status = new HBox(8, syncPill, now, folder);
        status.setAlignment(Pos.CENTER_RIGHT);
        row(repo, Page.SYNC, "settings.sync.status", status, "status");

        VBox automatic = card(Page.SYNC, null);
        syncAuto.setOnAction(e -> services.store().update(s -> s.sync.auto = syncAuto.isSelected()));
        syncInterval.setPrefColumnCount(3);
        syncInterval.setOnAction(e -> commitInterval());
        syncInterval.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitInterval();
        });
        HBox autoControl = new HBox(6, syncInterval, new Label(Messages.tr("settings.sync.minutes")), syncAuto);
        autoControl.setAlignment(Pos.CENTER_RIGHT);
        row(automatic, Page.SYNC, "settings.sync.auto", autoControl, "interval automatic");
        Label notes = new Label(Messages.tr("settings.sync.notes"));
        notes.setWrapText(true);
        notes.getStyleClass().add("note");
        addRow(automatic, Page.SYNC, "", Messages.tr("settings.sync.notes"), notes, "private conflict");

        loaders.add(() -> {
            Settings.Sync s = services.settings().sync;
            syncUrl.setText(s.repoUrl);
            syncUrl.getStyleClass().remove("invalid");
            syncBranch.setText(s.branch);
            syncBranch.getStyleClass().remove("invalid");
            syncAuto.setSelected(s.auto);
            syncInterval.setText(String.valueOf(s.intervalMinutes));
            syncInterval.getStyleClass().remove("invalid");
            loadSyncStatus();
        });
    }

    private void commitUrl() {
        String url = syncUrl.getText().trim();
        String refused = url.isEmpty() ? null : SyncEngine.checkUrl(url);
        if (refused != null) {
            if (!syncUrl.getStyleClass().contains("invalid"))
                syncUrl.getStyleClass().add("invalid");
            services.echo("status.sync.invalidUrl", Messages.tr("settings.sync.url." + refused));
            return;
        }
        syncUrl.getStyleClass().remove("invalid");
        if (!url.equals(services.settings().sync.repoUrl)) services.store().update(s -> s.sync.repoUrl = url);
    }

    /**
     * Asks for a repository name (suggesting {@code <app>-sync}), then creates it on GitHub through the
     * user's gh and connects to it. Not modal-blocking: the answer arrives when the dialog closes.
     */
    TextInputDialog promptCreateOnGitHub() {
        TextInputDialog dialog = new TextInputDialog(SyncController.suggestedRepoName());
        dialog.setTitle(Messages.tr("settings.sync.createGithub.title"));
        dialog.setHeaderText(null);
        dialog.setContentText(Messages.tr("settings.sync.createGithub.prompt"));
        Dialogs.styled(dialog, stage);
        dialog.setOnHidden(e -> {
            String name = dialog.getResult();
            if (name != null && !name.isBlank()) services.sync().createOnGitHub(name);
        });
        dialog.show();
        return dialog;
    }

    /** Commits the branch: a blank or invalid name is marked, said why, and never committed (SET-04). */
    void commitBranch() {
        String branch = syncBranch.getText().trim();
        String refused = SyncEngine.checkBranch(branch);
        if (refused != null) {
            if (!syncBranch.getStyleClass().contains("invalid"))
                syncBranch.getStyleClass().add("invalid");
            services.echo("status.sync.invalidBranch", Messages.tr("settings.sync.branch." + refused));
            return;
        }
        syncBranch.getStyleClass().remove("invalid");
        if (!branch.equals(services.settings().sync.branch)) services.store().update(s -> s.sync.branch = branch);
    }

    void commitInterval() {
        Integer minutes = Settings.Sync.parseInterval(syncInterval.getText());
        if (minutes == null) {
            if (!syncInterval.getStyleClass().contains("invalid"))
                syncInterval.getStyleClass().add("invalid");
            return;
        }
        syncInterval.getStyleClass().remove("invalid");
        if (minutes != services.settings().sync.intervalMinutes) {
            services.store().update(s -> s.sync.intervalMinutes = minutes);
        }
    }

    private void loadSyncStatus() {
        syncPill.setText(services.sync().pillText(System.currentTimeMillis()));
        syncPill.setAccessibleText(Messages.tr("settings.sync.pill.accessible", syncPill.getText()));
        syncPill.getStyleClass().removeAll("green", "amber", "red");
        syncPill.getStyleClass()
                .add(
                        switch (services.sync().pill()) {
                            // Green only once a sync is verified; one in flight is not durable yet (ICON-18).
                            case CONNECTED -> "green";
                            case SYNCING, NOT_CONNECTED -> "amber";
                            case FAILING -> "red";
                        });
    }

    private void buildAdvanced() {
        VBox card = card(Page.ADVANCED, null);
        Label folder = new Label(AppInfo.tildeHome(services.store().dir().toString()));
        folder.getStyleClass().add("muted");
        Button open = new Button(Messages.tr("settings.config.open"));
        open.setOnAction(e -> services.commands().run("config.openFolder"));
        HBox folderControl = new HBox(8, folder, open);
        folderControl.setAlignment(Pos.CENTER_RIGHT);
        row(card, Page.ADVANCED, "settings.config", folderControl, "folder directory path");
        Button export = new Button(Messages.tr("command.config.export"));
        export.setOnAction(e -> services.commands().run("config.export"));
        Button importButton = new Button(Messages.tr("command.config.import"));
        importButton.setOnAction(e -> services.commands().run("config.import"));
        HBox backup = new HBox(8, export, importButton);
        backup.setAlignment(Pos.CENTER_RIGHT);
        row(card, Page.ADVANCED, "settings.backup", backup, "export import zip backup");
    }

    // ---- rows, cards, search ----

    private VBox page(Page page) {
        return java.util.Objects.requireNonNull(pages.get(page));
    }

    private VBox card(Page page, @Nullable String heading) {
        VBox card = new VBox();
        card.getStyleClass().add("settings-card");
        if (heading != null) {
            Label label = new Label(heading);
            label.getStyleClass().add("settings-card-heading");
            card.getChildren().add(label);
        }
        page(page).getChildren().add(card);
        return card;
    }

    private void row(VBox card, Page page, String key, Node control, String keywords) {
        addRow(card, page, Messages.tr(key + ".title"), Messages.tr(key + ".desc"), control, keywords);
    }

    private void addRow(VBox card, Page page, String title, String description, Node control, String keywords) {
        VBox text = new VBox(2);
        if (!title.isEmpty()) {
            Label label = new Label(title);
            label.getStyleClass().add("setting-title");
            label.setWrapText(true);
            text.getChildren().add(label);
        }
        if (!description.isEmpty() && !(control instanceof Label)) {
            Label desc = new Label(description);
            desc.getStyleClass().add("setting-desc");
            desc.setWrapText(true);
            text.getChildren().add(desc);
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(16, text, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        if (control instanceof Label) {
            row.getChildren().setAll(control);
            HBox.setHgrow(control, Priority.ALWAYS);
        }
        card.getChildren().add(row);
        String heading = card.getChildren().isEmpty()
                        || !(card.getChildren().getFirst() instanceof Label first)
                        || !first.getStyleClass().contains("settings-card-heading")
                ? ""
                : first.getText();
        rows.add(new Row(
                page,
                row,
                card,
                new SettingsSearch.Entry(
                        title, description, visibleText(control), page.title() + " " + heading, keywords)));
    }

    /** The text a row's controls show (button and label text), so a search finds a row by it. */
    static String visibleText(Node control) {
        StringBuilder text = new StringBuilder();
        if (control instanceof javafx.scene.control.Labeled labeled && labeled.getText() != null) {
            text.append(labeled.getText()).append(' ');
        }
        if (control instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) text.append(visibleText(child));
        }
        return text.toString();
    }

    /** Filters every page by {@code query} and opens the page with the best match. */
    void applySearch(String query) {
        Page best = null;
        int bestScore = 0;
        for (Page page : Page.values()) pageHits.put(page, false);
        for (Row row : rows) {
            int score = SettingsSearch.score(query, row.entry());
            boolean show = score > 0;
            row.node().setVisible(show);
            row.node().setManaged(show);
            if (show) pageHits.put(row.page(), true);
            if (score > bestScore) {
                bestScore = score;
                best = row.page();
            }
        }
        // The key bindings are a filtered list rather than rows: the search narrows the list.
        java.util.Set<Command> matched = new java.util.HashSet<>();
        for (Map.Entry<Command, SettingsSearch.Entry> binding : bindingEntries.entrySet()) {
            int score = SettingsSearch.score(query, binding.getValue());
            if (score > 0) {
                matched.add(binding.getKey());
                pageHits.put(Page.KEYMAP, true);
            }
            if (score > bestScore) {
                bestScore = score;
                best = Page.KEYMAP;
            }
        }
        bindingFilter.setPredicate(matched::contains);
        for (VBox page : pages.values()) {
            for (Node child : page.getChildren()) {
                if (child instanceof VBox card && card.getStyleClass().contains("settings-card")) {
                    boolean any = card.getChildren().stream()
                            .anyMatch(n -> (n.getStyleClass().contains("setting-row") && n.isVisible())
                                    || (n == bindingList && !bindingFilter.isEmpty()));
                    card.setVisible(any);
                    card.setManaged(any);
                }
            }
        }
        sidebar.refresh();
        if (best != null && !query.isBlank()) sidebar.getSelectionModel().select(best);
    }

    /** Sets every control from the settings. Safe to run at any time. */
    void load() {
        loaders.forEach(Runnable::run);
    }

    /** Commits a picked value; load() setting the value already stored must not write the file again. */
    private <T> void commitIfChanged(
            @Nullable T value, java.util.function.Function<Settings, T> current, Consumer<T> commit) {
        if (value != null && !value.equals(current.apply(services.settings()))) commit.accept(value);
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
        return new StringConverter<>() {
            @Override
            public String toString(@Nullable T value) {
                return value == null ? "" : text.apply(value);
            }

            @Override
            public @Nullable T fromString(String string) {
                return null;
            }
        };
    }

    // ---- accessors for tests ----

    Stage stage() {
        return stage;
    }

    TextField search() {
        return search;
    }

    ListView<Page> sidebar() {
        return sidebar;
    }

    ListView<Command> bindingList() {
        return bindingList;
    }

    List<Row> rows() {
        return rows;
    }

    Map<Page, Boolean> pageHits() {
        return pageHits;
    }

    ComboBox<ThemeChoice> theme() {
        return theme;
    }

    ComboBox<String> language() {
        return language;
    }

    ComboBox<String> keymap() {
        return keymap;
    }

    SettingSwitch updatesSwitch() {
        return updates;
    }

    SettingSwitch titleBarSwitch() {
        return titleBar;
    }

    SettingSwitch toolbarSwitch() {
        return toolbar;
    }

    SettingSwitch tabBarSwitch() {
        return tabBar;
    }

    SettingSwitch statusBarSwitch() {
        return statusBar;
    }

    TextField syncUrl() {
        return syncUrl;
    }

    TextField syncBranch() {
        return syncBranch;
    }

    TextField syncInterval() {
        return syncInterval;
    }

    SettingSwitch syncAuto() {
        return syncAuto;
    }

    Label syncPill() {
        return syncPill;
    }

    /** A sidebar row: the page's icon and name, greyed when a search finds nothing on it. */
    private final class PageCell extends ListCell<Page> {
        @Override
        protected void updateItem(@Nullable Page page, boolean empty) {
            super.updateItem(page, empty);
            getStyleClass().remove("no-match");
            if (empty || page == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(page.title());
            setGraphic(Icons.commandGlyph(page.command));
            if (!search.getText().isBlank() && !pageHits.getOrDefault(page, true))
                getStyleClass().add("no-match");
        }
    }
}
