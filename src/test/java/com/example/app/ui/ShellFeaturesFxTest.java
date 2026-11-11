package com.example.app.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import com.example.app.config.ConfigStore;
import com.example.app.i18n.Messages;
import com.example.app.sync.Git;
import com.example.app.sync.GitHubCli;
import com.example.app.sync.SyncEngine;
import com.example.app.update.UpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.example.app.ui.FxTestSupport.call;
import static com.example.app.ui.FxTestSupport.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The window shell: the title bar, key bindings in the menus, the AOT exit and Create on GitHub. */
@Tag("fx")
class ShellFeaturesFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private AppServices services;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        app = FxTestSupport.start(dir, host);
        services = app.services();
    }

    @AfterEach
    void stop() throws Exception {
        FxTestSupport.stop(app);
    }

    private String echo() throws Exception {
        return call(() -> app.window().statusBar().echo().getText());
    }

    @Test
    void menusShowEachCommandsBindingAndFollowTheKeymap() throws Exception {
        // The in-window rendering, on every OS (macOS's own window uses the screen menu bar).
        AppMenus menus = call(() -> new AppMenus(services, false));
        AppMenus.Row palette = menus.groups().getLast().getFirst(); // Help opens with the palette (DOCS-07)
        assertInstanceOf(CustomMenuItem.class, palette.item());
        String cua = call(() -> AppMenus.displayText(palette));
        assertTrue(cua.startsWith("Command Palette   ") && cua.endsWith("P"), cua);
        run(() -> services.store().update(s -> s.keymap = "emacs"));
        assertEquals("Command Palette   M-x", call(() -> AppMenus.displayText(palette)));
        AppMenus.Row quit = row(menus, "app.quit");
        assertEquals("Quit   C-x C-c", call(() -> AppMenus.displayText(quit)));
        AppMenus.Row about = row(menus, "help.about");
        run(() -> services.store().update(s -> s.keybindings.put("help.about", "")));
        assertEquals("About", call(() -> AppMenus.displayText(about)));

        // Opening a menu greys what cannot run and lines the chords up.
        List<Menu> all = call(() -> List.copyOf(menus.bar().getMenus()));
        run(() -> all.forEach(m -> {
            m.getOnShowing().handle(null);
            m.getOnShown().handle(null);
        }));
        double width = call(() -> palette.title().getMinWidth());
        assertTrue(menus.groups().getLast().stream().allMatch(r -> r.title().getMinWidth() == width));
        run(() -> palette.item().fire());
        assertTrue(call(() -> app.window().overlays().isShowing()));

        // MENU-02 / ICON-20: an in-window row leads with its command's glyph, the same one Settings draws.
        javafx.scene.layout.HBox box = (javafx.scene.layout.HBox) ((CustomMenuItem) about.item()).getContent();
        javafx.scene.shape.SVGPath glyph =
                (javafx.scene.shape.SVGPath) box.getChildren().getFirst().lookup("SVGPath");
        assertEquals(Icons.forCommand("help.about"), glyph.getContent());
        assertTrue(glyph.getStyleClass().contains(Icons.LINE));
        assertEquals(null, box.getChildren().getFirst().lookup(".toolbar-icon"));
    }

    private static AppMenus.Row row(AppMenus menus, String id) {
        return menus.groups().stream()
                .flatMap(List::stream)
                .filter(r -> r.commandId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void onTheScreenMenuBarTheChordIsInTheText(@TempDir Path other) throws Exception {
        AppServices second = new AppServices(ConfigStore.open(other), host);
        AppMenus mac = call(() -> {
            second.commands().register("palette.show", () -> {});
            second.commands().register("app.quit", () -> {});
            return new AppMenus(second, true);
        });
        AppMenus.Row quit = mac.groups().getFirst().getFirst();
        assertFalse(quit.item() instanceof CustomMenuItem);
        assertTrue(quit.item().getText().startsWith("Quit"), quit.item().getText());
        assertEquals(quit.item().getText(), AppMenus.displayText(quit));
    }

    @Test
    void theTitleBarHoldsTheMenusAndStepsAsideInFullScreen(@TempDir Path other) throws Exception {
        assertTrue(MainWindow.usesHeaderBar(false, true, true));
        assertFalse(MainWindow.usesHeaderBar(true, true, true));
        assertFalse(MainWindow.usesHeaderBar(false, false, true));
        assertFalse(MainWindow.usesHeaderBar(false, true, false));

        AppServices second = new AppServices(ConfigStore.open(other), host);
        MainWindow window = call(() -> new MainWindow(new Stage(), second, false, false, true));
        assertNotNull(window.headerBar());
        assertEquals(StageStyle.EXTENDED, window.stage().getStyle());
        assertSame(window.menuBar(), window.headerBar().getLeft());
        run(() -> window.placeHeaderBar(true));
        assertTrue(window.headerBar().getLeft() == null);
        run(() -> window.placeHeaderBar(false));
        assertSame(window.menuBar(), window.headerBar().getLeft());
        run(() -> Themes.titleButtons(window.stage()));
        // Without a header bar there is nothing to place.
        MainWindow plain = call(() -> new MainWindow(
                new Stage(), new AppServices(ConfigStore.open(other.resolve("p")), host), false, false, false));
        assertTrue(plain.headerBar() == null);
        run(() -> plain.placeHeaderBar(true));
    }

    /** OS-01: where the title bar can't hold the menus, the command and the switch stay, disabled, and say why. */
    @Test
    void theTitleBarSettingAndCommandSayWhyWhereTheSystemCantDrawIt() throws Exception {
        String reason = call(() -> app.window().titleBarMenusUnavailable());
        // On macOS the menus are in the screen's menu bar; elsewhere the headless toolkit has no extended stage.
        boolean screenMenu = call(() -> app.window().menuBar().isUseSystemMenuBar());
        assertEquals(
                Messages.tr(
                        screenMenu ? "palette.disabled.titleBarScreenMenu" : "palette.disabled.titleBarUnsupported"),
                reason,
                "the reason this system gives");
        assertEquals(reason, services.commands().get("view.toggleTitleBarMenus").unavailableReason());
        assertFalse(call(() -> services.commands().run("view.toggleTitleBarMenus")));
        run(() -> app.window().openSettings(SettingsWindow.Page.APPEARANCE));
        SettingsWindow settings = app.window().settingsWindow();
        assertTrue(call(() -> settings.titleBarSwitch().isDisabled()));
        assertTrue(call(() -> settings.titleBarSwitch().getParent().getParent().lookupAll(".setting-desc").stream()
                .anyMatch(n -> ((javafx.scene.control.Label) n).getText().contains(reason))));
    }

    @Test
    void theTitleBarSettingAndCommandApplyOnTheNextStart() throws Exception {
        // Where the system can draw it (ChromeTest has the gate), the command flips the setting for the next start.
        run(() -> services.store().update(s -> s.titleBarMenus = !s.titleBarMenus));
        run(() -> services.echo("status.titleBarRestart"));
        assertFalse(services.settings().titleBarMenus);
        assertEquals("The title bar changes the next time you start the app.", echo());
        SettingsWindow settings = call(() -> new SettingsWindow(services, null, null));
        run(() -> settings.show(SettingsWindow.Page.APPEARANCE));
        assertFalse(call(() -> settings.titleBarSwitch().isDisabled()));
        assertFalse(call(() -> settings.titleBarSwitch().isSelected()));
        run(() -> settings.titleBarSwitch().fire());
        assertTrue(services.settings().titleBarMenus);
        services.store().flush(); // saves are written off the FX thread
        assertTrue(ConfigStore.open(dir).settings().titleBarMenus);
    }

    @Test
    void anAotTrainingRunExitsAfterTheWindowSettles() throws Exception {
        run(() -> Startup.aotTrain(null, host));
        run(() -> Startup.aotTrain(" ", host));
        run(() -> Startup.aotTrain("1", host));
        FxTestSupport.waitUntil(() -> host.exits == 1);
    }

    @Test
    void createOnGitHubPromptsWithTheSuggestedName() throws Exception {
        run(() -> services.commands().run("sync.createGitHubRepo"));
        SettingsWindow settings = app.window().settingsWindow();
        assertNotNull(settings);
        TextInputDialog dialog = call(settings::promptCreateOnGitHub);
        assertEquals("exampleapp-sync", call(() -> dialog.getEditor().getText()));
        run(dialog::close); // cancelled: nothing is created
        assertEquals("", services.settings().sync.repoUrl);
    }

    @Test
    void createOutcomesAreSaid() throws Exception {
        SyncController sync = services.sync();
        run(() -> sync.created(new GitHubCli.Created("", GitHubCli.Problem.BAD_NAME, "a b")));
        assertEquals("a b is not a repository name GitHub accepts.", echo());
        run(() -> sync.created(new GitHubCli.Created("", GitHubCli.Problem.NOT_INSTALLED, "")));
        assertTrue(echo().startsWith("Cannot find the GitHub CLI"), echo());
        run(() -> sync.created(new GitHubCli.Created("", GitHubCli.Problem.NOT_SIGNED_IN, "")));
        assertTrue(echo().startsWith("Cannot use the GitHub CLI"), echo());
        run(() -> sync.created(new GitHubCli.Created("", GitHubCli.Problem.FAILED, "exists")));
        assertEquals("Creating the repository failed: exists", echo());
        // Through the suite's gh, which does not exist: the real one is never reached.
        run(() -> sync.createOnGitHub("exampleapp-sync"));
        run(() -> sync.createOnGitHub("again")); // ignored while one runs
        FxTestSupport.waitUntil(() -> {
            try {
                return echo().startsWith("Cannot find the GitHub CLI");
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Test
    void aCreatedRepositoryIsConnectedAndSynced(@TempDir Path other) throws Exception {
        // An address on this machine with nothing listening: the first sync fails fast, offline. The fake
        // gh is a shell script, or on Windows a batch file.
        Path gh;
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
            gh = Files.writeString(
                    other.resolve("gh.cmd"),
                    String.join(
                            "\r\n",
                            "@echo off",
                            "if \"%~1 %~2\"==\"repo create\" echo.https://127.0.0.1/me/exampleapp-sync",
                            "exit /b 0",
                            ""));
        } else {
            gh = Files.writeString(other.resolve("gh"), """
                    #!/bin/sh
                    case "$1 $2" in
                      "repo create") echo "https://127.0.0.1/me/exampleapp-sync" ;;
                    esac
                    exit 0
                    """);
            Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        AppServices second = new AppServices(
                ConfigStore.open(other.resolve("config")),
                host,
                new UpdateService("http://127.0.0.1:9/latest"),
                new SyncEngine(other.resolve("config/sync"), new Git()),
                new GitHubCli(gh.toString()));
        run(() -> second.sync().createOnGitHub("exampleapp-sync"));
        FxTestSupport.waitUntil(() -> !second.settings().sync.repoUrl.isEmpty());
        assertEquals("https://127.0.0.1/me/exampleapp-sync.git", second.settings().sync.repoUrl);
        FxTestSupport.waitUntil(() -> second.sync().pill() == SyncController.Pill.FAILING);
    }

    /** DIAG-07: the Doctor opens from its command and draws a row per check, a pill each. */
    @Test
    void theDoctorDrawsARowPerCheck() throws Exception {
        assertTrue(call(() -> services.commands().run("help.doctor")));
        DoctorWindow doctor = call(() -> new DoctorWindow(services, app.window().stage()));
        run(() -> doctor.fill(java.util.List.of(
                new com.example.app.sync.Doctor.Check(
                        "git", com.example.app.sync.Doctor.Status.FAILED, "Not found.", "Install git.", "https://x"),
                new com.example.app.sync.Doctor.Check(
                        "Java", com.example.app.sync.Doctor.Status.OK, "25", null, null))));
        assertEquals(
                2,
                call(() -> doctor.rows().getChildren().stream()
                                .filter(n -> n.getStyleClass().contains("pill"))
                                .count())
                        .intValue());
        assertTrue(call(() -> doctor.rows().lookupAll(".hyperlink").size() == 1), "the failure's link");
        run(() -> doctor.dialog().close());
    }
}
