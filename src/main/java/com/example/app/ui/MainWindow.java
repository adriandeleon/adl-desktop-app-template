package com.example.app.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderDragType;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import com.example.app.AppInfo;
import com.example.app.command.Command;
import com.example.app.command.KeyStroke;
import com.example.app.command.Keymap;
import com.example.app.config.ConfigExporter;
import com.example.app.config.ConfigImport;
import com.example.app.config.ConfigStore;
import com.example.app.config.Settings;
import com.example.app.config.ThemeChoice;
import com.example.app.i18n.Messages;
import com.example.app.sync.SyncEngine;
import org.jspecify.annotations.Nullable;

/**
 * The main window: menus, a placeholder for the app's own content, the status bar, and the root
 * {@code StackPane} that carries the in-scene overlays. Every menu item, button and key binding runs a
 * command by id ({@link Commands}).
 */
public final class MainWindow {

    private final Stage stage;
    private final AppServices services;
    private final OverlayHost overlays;
    private final BorderPane layout = new BorderPane();
    private final AppMenus menus;
    private final @Nullable HeaderBar headerBar;
    private final StatusBar statusBar;
    /** Built on first open, not before the first frame (PERF-14). */
    private @Nullable MessageLogView messageLog;

    private @Nullable SettingsWindow settings;
    private @Nullable DebugLogWindow debugLog;

    public MainWindow(Stage stage, AppServices services, boolean restoreSession) {
        this(
                stage,
                services,
                restoreSession,
                AppInfo.isMac(),
                Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW));
    }

    /**
     * With the platform given (a screen menu bar, support for extended windows), so every combination
     * is testable on any OS.
     */
    MainWindow(
            Stage stage, AppServices services, boolean restoreSession, boolean systemMenu, boolean extendedSupported) {
        this.stage = stage;
        this.services = services;
        this.overlays = new OverlayHost(layout);
        this.statusBar = new StatusBar(services);
        Commands.register(this, services);

        this.menus = new AppMenus(services, systemMenu);
        // The stage style is fixed before the window first shows, so this is decided once per window.
        this.headerBar = usesHeaderBar(systemMenu, services.settings().titleBarMenus, extendedSupported)
                ? buildHeaderBar()
                : null;
        if (headerBar != null && !stage.isShowing()) stage.initStyle(StageStyle.EXTENDED);
        layout.setTop(headerBar != null ? headerBar : menus.bar());
        if (headerBar != null) stage.fullScreenProperty().addListener((o, was, full) -> placeHeaderBar(full));
        layout.setCenter(placeholder());
        layout.setBottom(statusBar);

        Scene scene = Themes.style(new Scene(overlays.root()));
        new KeyDispatcher(services, overlays).install(scene);
        stage.setScene(scene);
        Themes.titleButtons(stage);
        stage.setTitle(AppInfo.NAME);
        for (int size : new int[] {16, 32, 64, 128, 256}) {
            var icon = MainWindow.class.getResource("/com/example/app/icons/app-" + size + ".png");
            if (icon != null) stage.getIcons().add(new Image(icon.toExternalForm()));
        }
        Settings.Window window = services.settings().window;
        stage.setWidth(window.width);
        stage.setHeight(window.height);
        if (restoreSession) {
            if (window.x != null && window.y != null) {
                stage.setX(window.x);
                stage.setY(window.y);
            }
            stage.setMaximized(window.maximized);
        }
        // The window's close button is a Quit like any other: one path (TRAY-04).
        stage.setOnCloseRequest(e -> {
            e.consume();
            quit();
        });

        services.store().addListener(this::apply);
        services.onKeybindingsChanged(() -> layout.setCenter(placeholder()));
        apply(services.settings());
    }

    public void show() {
        stage.show();
    }

    /** Applies the settings that live in this window: the theme and the status bar. */
    private void apply(Settings settings) {
        if (settings.theme != Themes.choice()) Themes.apply(settings.theme);
        statusBar.setVisible(settings.showStatusBar);
        statusBar.setManaged(settings.showStatusBar);
    }

    /** The app's own content goes here. */
    private VBox placeholder() {
        Label title = new Label(AppInfo.NAME);
        title.getStyleClass().add("welcome-title");
        Label tagline = new Label(AppInfo.TAGLINE);
        tagline.getStyleClass().add("muted");
        String chord = services.keybindings().label("palette.show");
        Label hint = new Label(Messages.tr("welcome.hint", chord.isEmpty() ? "—" : chord));
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);
        for (String id : List.of("palette.show", "app.settings", "help.documentation", "help.whatsNew")) {
            Command command = services.commands().get(id);
            if (command == null) continue;
            Button button = new Button(command.title());
            button.setOnAction(e -> services.commands().run(id));
            actions.getChildren().add(button);
        }
        VBox box = new VBox(12, title, tagline, hint, actions);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("welcome");
        return box;
    }

    /** Whether this window draws its own title bar: the pure rule in {@link Chrome#usesHeaderBar}. */
    static boolean usesHeaderBar(boolean systemMenu, boolean setting, boolean extendedSupported) {
        return Chrome.usesHeaderBar(systemMenu, setting, extendedSupported);
    }

    /**
     * The title bar: the menus on the left, the window title in the middle. The bar's background drags
     * the window and its controls do not; the title is marked draggable too, since a title bar you
     * cannot drag by its title is a trap.
     */
    HeaderBar buildHeaderBar() {
        HeaderBar bar = new HeaderBar();
        bar.getStyleClass().add("header-bar");
        bar.setLeft(menus.bar());
        Label title = new Label();
        title.getStyleClass().add("header-title");
        title.textProperty().bind(stage.titleProperty());
        HeaderBar.setDragType(title, HeaderDragType.DRAGGABLE);
        bar.setCenter(title);
        return bar;
    }

    /** Full screen has no title bar: the menus become an ordinary row, and come back on the way out. */
    void placeHeaderBar(boolean fullScreen) {
        HeaderBar header = headerBar;
        if (header == null) return;
        if (fullScreen) {
            header.setLeft(null);
            layout.setTop(menus.bar());
        } else {
            layout.setTop(header);
            header.setLeft(menus.bar());
        }
        HeaderBar.setSystemButtonHeight(stage, fullScreen ? 0 : HeaderBar.USE_DEFAULT_SIZE);
    }

    // ---- what the commands do ----

    void openPalette() {
        // Every registered command (PAL-02): one that cannot run now stays listed, grayed with the reason.
        List<Picker.Item<String>> items = services.commands().all().stream()
                .sorted(java.util.Comparator.comparing(Command::title))
                .map(c -> new Picker.Item<>(
                        c.id(),
                        c.title(),
                        c.description(),
                        services.keybindings().label(c.id()),
                        c.unavailableReason()))
                .toList();
        new Picker<>(
                        overlays,
                        Messages.tr("palette.prompt"),
                        services.keybindings().label("palette.show"),
                        items,
                        services.commands()::run)
                .show();
    }

    void chooseTheme() {
        List<Picker.Item<ThemeChoice>> items = java.util.Arrays.stream(ThemeChoice.values())
                .map(t -> new Picker.Item<>(
                        t, Messages.tr("theme." + t.name().toLowerCase(java.util.Locale.ROOT)), "", "", null))
                .toList();
        new Picker<>(overlays, Messages.tr("picker.theme"), "", items, t -> {
                    services.store().update(s -> s.theme = t);
                    services.echo(
                            "status.theme", Messages.tr("theme." + t.name().toLowerCase(java.util.Locale.ROOT)));
                })
                .show();
    }

    void chooseKeymap() {
        List<Picker.Item<String>> items = Keymap.PRESETS.stream()
                .map(id -> new Picker.Item<>(id, Keymap.load(id).name(), "", "", null))
                .toList();
        new Picker<>(overlays, Messages.tr("picker.keymap"), "", items, id -> {
                    services.store().update(s -> s.keymap = id);
                    services.echo("status.keymap", Keymap.load(id).name());
                })
                .show();
    }

    void chooseLanguage() {
        List<Picker.Item<String>> items = new java.util.ArrayList<>();
        items.add(new Picker.Item<>("", Messages.tr("settings.language.system"), "", "", null));
        for (Map.Entry<String, String> language : Messages.available().entrySet()) {
            items.add(new Picker.Item<>(language.getKey(), language.getValue(), language.getKey(), "", null));
        }
        new Picker<>(overlays, Messages.tr("picker.language"), "", items, code -> {
                    services.store().update(s -> s.language = code);
                    services.echo("status.languageRestart");
                })
                .show();
    }

    // ---- a palette command per setting that a picker or a toggle does not cover (SET-06) ----

    TextInputDialog promptSyncRepository() {
        return Prompts.text(
                stage,
                Messages.tr("command.sync.setRepository"),
                Messages.tr("settings.sync.repo.title"),
                services.settings().sync.repoUrl,
                url -> url.isEmpty() ? null : reason(SyncEngine.checkUrl(url), "settings.sync.url."),
                url -> services.store().update(s -> s.sync.repoUrl = url));
    }

    TextInputDialog promptSyncBranch() {
        return Prompts.text(
                stage,
                Messages.tr("command.sync.setBranch"),
                Messages.tr("settings.sync.branch.title"),
                services.settings().sync.branch,
                branch -> reason(SyncEngine.checkBranch(branch), "settings.sync.branch."),
                branch -> services.store().update(s -> s.sync.branch = branch));
    }

    TextInputDialog promptSyncInterval() {
        return Prompts.text(
                stage,
                Messages.tr("command.sync.setInterval"),
                Messages.tr("settings.sync.interval.prompt"),
                String.valueOf(services.settings().sync.intervalMinutes),
                text -> Settings.Sync.parseInterval(text) == null
                        ? Messages.tr("settings.sync.interval.invalid")
                        : null,
                text -> {
                    Integer minutes = Settings.Sync.parseInterval(text);
                    if (minutes != null) services.store().update(s -> s.sync.intervalMinutes = minutes);
                });
    }

    private static @Nullable String reason(@Nullable String suffix, String prefix) {
        return suffix == null ? null : Messages.tr(prefix + suffix);
    }

    /** Settings → Keymap from the palette: pick a command, then type its keys (blank unbinds). */
    void chooseBinding() {
        List<Picker.Item<Command>> items = services.commands().all().stream()
                .sorted(java.util.Comparator.comparing(Command::title))
                .map(c -> new Picker.Item<>(
                        c, c.title(), c.description(), services.keybindings().label(c.id()), null))
                .toList();
        new Picker<>(overlays, Messages.tr("picker.bindKey"), "", items, this::promptBinding).show();
    }

    TextInputDialog promptBinding(Command command) {
        String base = services.keybindings().keymap().bindings().getOrDefault(command.id(), "");
        String current = services.settings().keybindings.getOrDefault(command.id(), base);
        return Prompts.text(
                stage,
                Messages.tr("command.settings.bindKey"),
                command.title(),
                current,
                text -> text.isEmpty() || KeyStroke.parseSequence(text) != null
                        ? null
                        : Messages.tr("settings.bindings.invalid"),
                text -> services.store().update(s -> {
                    if (text.equals(base)) s.keybindings.remove(command.id());
                    else s.keybindings.put(command.id(), text);
                }));
    }

    void openMessageLog() {
        messageLog().show();
    }

    /** Copies what the Message Log lists: the filtered entries while it is open, else every entry. */
    void copyMessageLog() {
        MessageLogView view = messageLog;
        Clipboards.copy(MessageLogView.asText(
                view != null ? view.listed() : services.messages().entries()));
        services.echo("status.messageLogCopied");
    }

    /** Empties the Message Log and its unread count. */
    void clearMessageLog() {
        services.messages().clear();
        MessageLogView view = messageLog;
        if (view != null) view.refresh();
        statusBar.refreshUnread();
    }

    /** About's Copy details: the environment block, for a bug report. */
    void copyDetails() {
        Clipboards.copy(AppInfo.environment(services.store().dir()));
        services.echo("status.detailsCopied");
    }

    boolean isDebugLogOpen() {
        DebugLogWindow window = debugLog;
        return window != null && window.stage().isShowing();
    }

    void refreshDebugLog() {
        DebugLogWindow window = debugLog;
        if (window != null) window.refresh();
    }

    /** Debug Log: Find: opens the Debug Log and its find bar. */
    void findInDebugLog() {
        openDebugLog();
        DebugLogWindow window = debugLog;
        if (window != null) window.find();
    }

    /** The command whose key-binding row is selected in an open Settings → Keymap, or null. */
    @Nullable
    Command selectedBinding() {
        SettingsWindow window = settings;
        if (window == null || !window.stage().isShowing()) return null;
        return window.bindingList().getSelectionModel().getSelectedItem();
    }

    void resetSelectedBinding() {
        SettingsWindow window = settings;
        Command selected = selectedBinding();
        if (window != null && selected != null) window.resetBinding(selected);
    }

    void clearDebugLog() {
        DebugLog.clear();
        refreshDebugLog();
    }

    /** Saves the Debug Log as a text file the user chooses; the write runs off the FX thread. */
    void exportDebugLog() {
        DebugLogWindow window = debugLog;
        Stage owner = window != null && window.stage().isShowing() ? window.stage() : stage;
        Path target = services.host().chooseSaveFile(owner, Messages.tr("debuglog.export"), AppInfo.ID + "-debug.log");
        if (target == null) return;
        String text = DebugLog.text();
        Background.run(() -> {
            try {
                java.nio.file.Files.writeString(target, text, java.nio.charset.StandardCharsets.UTF_8);
                services.echo("status.logExported", String.valueOf(target.getFileName()));
            } catch (IOException e) {
                services.echo("status.logExportFailed", String.valueOf(e.getMessage()));
            }
        });
    }

    void openSettings(SettingsWindow.Page page) {
        SettingsWindow window = settings;
        if (window == null) {
            window = new SettingsWindow(services, stage);
            settings = window;
        }
        window.show(page);
    }

    /** Settings Sync's "Create on GitHub…", from the palette: the Sync page with the name prompt open. */
    void createSyncRepo() {
        openSettings(SettingsWindow.Page.SYNC);
        SettingsWindow window = settings;
        if (window != null) window.promptCreateOnGitHub();
    }

    void openAbout() {
        new AboutDialog(services, stage).show();
    }

    void openDebugLog() {
        DebugLogWindow window = debugLog;
        if (window == null) {
            window = new DebugLogWindow(services, stage);
            debugLog = window;
        }
        window.show();
    }

    void openReportProblem() {
        new ReportProblemWindow(services, stage).show();
    }

    void exportConfig() {
        Path target = services.host()
                .chooseSaveFile(
                        stage, Messages.tr("command.config.export"), ConfigExporter.defaultName(LocalDateTime.now()));
        if (target == null) return;
        Path dir = services.store().dir();
        Background.run(
                () -> { // the walk and the zip write stay off the FX thread (PERF-01)
                    services.store().flush();
                    try {
                        int count = ConfigExporter.export(dir, target);
                        services.echo("status.configExported", count, String.valueOf(target.getFileName()));
                    } catch (IOException e) {
                        services.echo("status.configExportFailed", String.valueOf(e.getMessage()));
                    }
                });
    }

    /**
     * Import Configuration: the zip is read and checked off the FX thread, then the question lists the
     * parts it holds, and only the ticked ones replace this computer's (SET-12, WIN-05).
     */
    void importConfig() {
        Path source = services.host().chooseOpenFile(stage, Messages.tr("command.config.import"));
        if (source == null) return;
        Path dir = services.store().dir();
        Background.run(() -> {
            ConfigImport.Scan scan;
            try {
                scan = ConfigImport.read(source);
            } catch (IOException e) {
                services.echo("status.configImportFailed", String.valueOf(e.getMessage()));
                return;
            }
            Platform.runLater(() -> askAndImport(source, scan, dir));
        });
    }

    private void askAndImport(Path source, ConfigImport.Scan scan, Path dir) {
        List<ConfigImport.Part> parts = List.copyOf(scan.available());
        Confirmations.Question question = Confirmations.importConfig(String.valueOf(source.getFileName()), parts);
        if (!services.host().confirm(stage, question)) return;
        java.util.Set<ConfigImport.Part> ticked = java.util.EnumSet.noneOf(ConfigImport.Part.class);
        for (int i = 0; i < parts.size(); i++) {
            if (question.choices().get(i).ticked()) ticked.add(parts.get(i));
        }
        if (ticked.isEmpty()) return;
        Background.run(() -> {
            try {
                int count = ConfigImport.apply(scan, dir, ticked);
                ConfigStore.Loaded loaded = services.store().read(); // the file work, here
                Platform.runLater(() -> {
                    services.store().install(loaded);
                    services.echo("status.configImported", count);
                });
            } catch (IOException e) {
                services.echo("status.configImportFailed", String.valueOf(e.getMessage()));
            }
        });
    }

    /** Another launch handed its arguments over: come to the front and say so. */
    public void handOff(List<String> args) {
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        services.echo("status.handOff");
    }

    /**
     * The one quit path: File → Quit, the window's close button and the system's Quit all come here. It
     * closes the app's other windows and dialogs first, so no nested event loop is left running, then
     * saves the window geometry and the settings, then exits (TRAY-04).
     */
    void quit() {
        for (javafx.stage.Window other : List.copyOf(javafx.stage.Window.getWindows())) {
            if (other != stage) other.hide();
        }
        saveWindowState();
        // The last save is waited for off the FX thread (PERF-01); the exit comes back to it.
        Background.run(() -> {
            services.store().flush();
            Platform.runLater(services.host()::exit);
        });
    }

    /** Remembers size, position and maximized state for the next launch. */
    void saveWindowState() {
        boolean maximized = stage.isMaximized();
        double x = stage.getX();
        double y = stage.getY();
        double width = stage.getWidth();
        double height = stage.getHeight();
        services.store().update(s -> {
            s.window.maximized = maximized;
            if (!maximized && width > 0 && height > 0 && !Double.isNaN(x)) {
                s.window.x = x;
                s.window.y = y;
                s.window.width = width;
                s.window.height = height;
            }
        });
    }

    // ---- accessors ----

    public Stage stage() {
        return stage;
    }

    OverlayHost overlays() {
        return overlays;
    }

    StatusBar statusBar() {
        return statusBar;
    }

    MenuBar menuBar() {
        return menus.bar();
    }

    AppMenus menus() {
        return menus;
    }

    @Nullable
    HeaderBar headerBar() {
        return headerBar;
    }

    MessageLogView messageLog() {
        MessageLogView view = messageLog;
        if (view == null) {
            view = new MessageLogView(services, overlays, statusBar::refreshUnread);
            messageLog = view;
        }
        return view;
    }

    /** The Message Log if it has been built, without building it. */
    @Nullable
    MessageLogView builtMessageLog() {
        return messageLog;
    }

    @Nullable
    SettingsWindow settingsWindow() {
        return settings;
    }

    @Nullable
    DebugLogWindow debugLogWindow() {
        return debugLog;
    }
}
