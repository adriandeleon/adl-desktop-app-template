package com.example.app.ui;

import com.example.app.AppInfo;
import com.example.app.command.Command;
import com.example.app.command.CommandRegistry;

/**
 * Every command the starter registers, in one place. Each needs {@code command.<id>} and
 * {@code command.<id>.desc} in every catalog ({@code MessagesTest} checks), and runs in
 * {@code CommandSweepFxTest}. Every setting has one too.
 */
final class Commands {

    private Commands() {}

    static void register(MainWindow window, AppServices services) {
        CommandRegistry r = services.commands();
        r.register("palette.show", window::openPalette);
        r.register("app.settings", () -> window.openSettings(SettingsWindow.Page.GENERAL));
        r.register("app.quit", window::quit);

        r.register("view.messageLog", window::openMessageLog);
        r.register("messageLog.copy", window::copyMessageLog);
        r.register("messageLog.clear", window::clearMessageLog);
        r.register("help.doctor", window::openDoctor); // DIAG-07
        // The UI zoom (WIN-12): three commands, kept across launches through the setting.
        r.register(
                "view.uiZoomIn",
                () -> zoomUi(services, UiZoom.in(services.settings().uiZoom)),
                () -> Chrome.uiZoomInUnavailable(services.settings().uiZoom));
        r.register(
                "view.uiZoomOut",
                () -> zoomUi(services, UiZoom.out(services.settings().uiZoom)),
                () -> Chrome.uiZoomOutUnavailable(services.settings().uiZoom));
        r.register(
                "view.uiZoomReset",
                () -> zoomUi(services, com.example.app.config.Settings.DEFAULT_UI_ZOOM),
                () -> Chrome.uiZoomResetUnavailable(services.settings().uiZoom));
        r.register(
                "view.toggleTitleBarMenus",
                () -> {
                    services.store().update(s -> s.titleBarMenus = !s.titleBarMenus);
                    services.echo("status.titleBarRestart");
                },
                window::titleBarMenusUnavailable);
        // Simple mode: a setting with its own command and a View menu row in both menus.
        r.register("view.toggleSimpleMode", () -> {
            services.store().update(s -> s.simpleMode = !s.simpleMode);
            services.echo(services.settings().simpleMode ? "status.simpleModeOn" : "status.simpleModeOff");
        });
        r.register("view.toggleStatusBar", () -> {
            services.store().update(s -> s.showStatusBar = !s.showStatusBar);
            services.echo(services.settings().showStatusBar ? "status.statusBarOn" : "status.statusBarOff");
        });

        registerEditing(window, r);

        r.register("settings.theme", window::chooseTheme);
        r.register("settings.keymap", window::chooseKeymap);
        r.register("settings.language", window::chooseLanguage);
        r.register("settings.bindKey", window::chooseBinding);
        // The actions of a key-binding row in Settings, on its right-click menu (MENU-05).
        r.register(
                "keymap.changeBinding",
                () -> {
                    Command selected = window.selectedBinding();
                    if (selected != null) window.promptBinding(selected);
                },
                () -> Chrome.bindingRowUnavailable(window.selectedBinding() != null));
        r.register(
                "keymap.resetBinding",
                window::resetSelectedBinding,
                () -> Chrome.bindingRowUnavailable(window.selectedBinding() != null));
        r.register("settings.toggleUpdateCheck", () -> {
            services.store().update(s -> s.checkForUpdates = !s.checkForUpdates);
            services.echo(services.settings().checkForUpdates ? "status.updateCheckOn" : "status.updateCheckOff");
        });

        r.register("config.export", window::exportConfig);
        r.register("config.import", window::importConfig);
        r.register(
                "config.openFolder",
                () -> services.host().openUrl(services.store().dir().toUri().toString()));

        r.register(
                "sync.openFolder",
                () -> services.host()
                        .openUrl(services.store().dir().resolve("sync").toUri().toString()));
        r.register("sync.setup", () -> window.openSettings(SettingsWindow.Page.SYNC));
        // The keyboard reference (DOCS-08): the key binding editor, searchable, from the Help menu.
        r.register("help.keyboardShortcuts", () -> window.openSettings(SettingsWindow.Page.KEYMAP));
        r.register("sync.createGitHubRepo", window::createSyncRepo);
        r.register("sync.setRepository", window::promptSyncRepository);
        r.register("sync.setBranch", window::promptSyncBranch);
        r.register("sync.setInterval", window::promptSyncInterval);
        r.register(
                "sync.now",
                () -> services.sync().syncNow(true),
                () -> Chrome.syncNowUnavailable(services.sync().isConfigured()));
        r.register("sync.toggleAuto", () -> {
            services.store().update(s -> s.sync.auto = !s.sync.auto);
            services.echo(services.settings().sync.auto ? "status.syncAutoOn" : "status.syncAutoOff");
        });

        r.register("help.documentation", () -> services.host().openUrl(AppInfo.docsUrl()));
        r.register("help.whatsNew", () -> services.host().openUrl(AppInfo.releaseNotesUrl()));
        r.register("help.about", window::openAbout);
        r.register(
                "help.openNewRelease",
                () -> {
                    com.example.app.update.ReleaseInfo latest =
                            services.updates().latest();
                    if (latest != null) services.host().openUrl(latest.url());
                },
                () -> Chrome.newReleaseUnavailable(services.updates().latest() != null));
        r.register("help.homepage", () -> services.host().openUrl(AppInfo.HOMEPAGE));
        r.register("help.releases", () -> services.host().openUrl(AppInfo.RELEASES_PAGE));
        r.register("help.license", () -> services.host().openUrl(AppInfo.LICENSE_URL));
        // The Pauta window (PWIN-01) and its links; the edition's own page is About's first Pauta link.
        r.register("help.pauta", window::openPauta);
        r.register("help.pautaRules", () -> services.host().openUrl(AppInfo.pautaUrl()));
        r.register("help.pautaDocs", () -> services.host().openUrl(AppInfo.pautaDocsUrl()));
        r.register("help.copyPauta", window::copyPauta);
        r.register(
                "config.openSettingsFile",
                () -> services.host().openUrl(services.store().file().toUri().toString()));
        r.register("help.copyDetails", window::copyDetails);
        r.register(
                "help.checkForUpdates",
                () -> services.updates().checkNow(true),
                () -> Chrome.checkForUpdatesUnavailable(
                        services.updates().state() == UpdateCoordinator.State.CHECKING));
        r.register("help.debugLog", window::openDebugLog);
        r.register(
                "debugLog.refresh",
                window::refreshDebugLog,
                () -> Chrome.debugLogRefreshUnavailable(window.isDebugLogOpen()));
        r.register("debugLog.find", window::findInDebugLog);
        r.register(
                "debugLog.findNext",
                () -> window.stepDebugLogFind(true),
                () -> Chrome.findBarUnavailable(window.isDebugLogFindOpen()));
        r.register(
                "debugLog.findPrevious",
                () -> window.stepDebugLogFind(false),
                () -> Chrome.findBarUnavailable(window.isDebugLogFindOpen()));
        r.register("debugLog.copy", () -> Clipboards.copy(DebugLog.text()));
        r.register("debugLog.clear", window::clearDebugLog);
        r.register("debugLog.export", window::exportDebugLog);
        r.register("help.reportProblem", window::openReportProblem);
    }

    private static void zoomUi(AppServices services, int percent) {
        services.store().update(s -> s.uiZoom = percent);
        services.echo("status.uiZoom", percent);
    }

    /** Cancel and the text commands, which act on whatever has the keyboard. */
    private static void registerEditing(MainWindow window, CommandRegistry r) {
        // Cancel (PAL-07, QOPEN-07, MLOG-03): the keymap's chord closes the palette, a picker or the Message
        // Log, as Esc does; C-g in the Emacs keymap.
        r.register(
                "edit.cancel",
                () -> window.overlays().hide(),
                () -> Chrome.cancelUnavailable(window.overlays().isShowing()));
        // The text commands (MENU-05): on the text control that has the keyboard, as the stock menu did.
        r.register(
                "edit.cut",
                () -> onText(window, javafx.scene.control.TextInputControl::cut),
                () -> Chrome.editUnavailable(window.focusedText(), true));
        r.register(
                "edit.copy",
                () -> onText(window, javafx.scene.control.TextInputControl::copy),
                () -> Chrome.editUnavailable(window.focusedText(), false));
        r.register(
                "edit.paste",
                () -> onText(window, javafx.scene.control.TextInputControl::paste),
                () -> Chrome.editUnavailable(window.focusedText(), true));
        r.register(
                "edit.selectAll",
                () -> onText(window, javafx.scene.control.TextInputControl::selectAll),
                () -> Chrome.editUnavailable(window.focusedText(), false));
    }

    private static void onText(
            MainWindow window, java.util.function.Consumer<javafx.scene.control.TextInputControl> action) {
        javafx.scene.control.TextInputControl text = window.focusedText();
        if (text != null) action.accept(text);
    }
}
