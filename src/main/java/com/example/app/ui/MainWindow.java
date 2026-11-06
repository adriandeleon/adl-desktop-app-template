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
import com.example.app.command.Keymap;
import com.example.app.config.ConfigExporter;
import com.example.app.config.Settings;
import com.example.app.config.ThemeChoice;
import com.example.app.i18n.Messages;
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
    private final MessageLogView messageLog;
    private @Nullable SettingsWindow settings;
    private @Nullable DebugLogWindow debugLog;

    public MainWindow(Stage stage, AppServices services, boolean restoreSession) {
        this(stage, services, restoreSession, Platform.isSupported(ConditionalFeature.EXTENDED_WINDOW));
    }

    /** With the platform's support for extended windows given, so both title bars are testable. */
    MainWindow(Stage stage, AppServices services, boolean restoreSession, boolean extendedSupported) {
        this.stage = stage;
        this.services = services;
        this.overlays = new OverlayHost(layout);
        this.statusBar = new StatusBar(services, this::openMessageLog);
        this.messageLog = new MessageLogView(services, overlays, statusBar::refreshUnread);
        Commands.register(this, services);

        boolean systemMenu = AppInfo.isMac();
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
        stage.setOnCloseRequest(e -> saveWindowState());

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
        for (String id : List.of("palette.show", "app.settings", "help.about")) {
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

    /**
     * Whether this window draws its own title bar with the menus in it: JavaFX 27's extended stage
     * style, where the platform keeps its window buttons and a {@link HeaderBar} replaces the title bar.
     * Never under a screen menu bar (macOS: there are no menus in the window to put there), and only
     * where the platform supports the style: JavaFX quietly downgrades an unsupported {@code EXTENDED}
     * to {@code DECORATED}, which would draw the header under a native title bar as a second row.
     */
    static boolean usesHeaderBar(boolean systemMenu, boolean setting, boolean extendedSupported) {
        return !systemMenu && setting && extendedSupported;
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
        List<Picker.Item<String>> items = services.commands().all().stream()
                .filter(c -> !c.id().equals("palette.show"))
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

    void openMessageLog() {
        messageLog.show();
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
            window = new DebugLogWindow(services, stage, this::openReportProblem);
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
        try {
            int count = ConfigExporter.export(services.store().dir(), target);
            services.echo("status.configExported", count, String.valueOf(target.getFileName()));
        } catch (IOException e) {
            services.echo("status.configExportFailed", String.valueOf(e.getMessage()));
        }
    }

    void importConfig() {
        Path source = services.host().chooseOpenFile(stage, Messages.tr("command.config.import"));
        if (source == null) return;
        try {
            int count = ConfigExporter.importFrom(source, services.store().dir());
            services.store().reload();
            services.echo("status.configImported", count);
        } catch (IOException e) {
            services.echo("status.configImportFailed", String.valueOf(e.getMessage()));
        }
    }

    /** Another launch handed its arguments over: come to the front and say so. */
    public void handOff(List<String> args) {
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        services.echo("status.handOff");
    }

    void quit() {
        saveWindowState();
        services.host().exit();
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
