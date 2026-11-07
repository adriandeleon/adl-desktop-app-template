package com.example.app.ui;

import com.example.app.AppInfo;
import com.example.app.command.CommandRegistry;
import com.example.app.i18n.Messages;

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
        r.register("view.toggleTitleBarMenus", () -> {
            services.store().update(s -> s.titleBarMenus = !s.titleBarMenus);
            services.echo("status.titleBarRestart");
        });
        r.register("view.toggleStatusBar", () -> {
            services.store().update(s -> s.showStatusBar = !s.showStatusBar);
            services.echo(services.settings().showStatusBar ? "status.statusBarOn" : "status.statusBarOff");
        });

        r.register("settings.theme", window::chooseTheme);
        r.register("settings.keymap", window::chooseKeymap);
        r.register("settings.language", window::chooseLanguage);
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
        r.register("sync.createGitHubRepo", window::createSyncRepo);
        r.register(
                "sync.now",
                () -> services.sync().syncNow(true),
                () -> services.sync().isConfigured() ? null : Messages.tr("palette.disabled.syncNotSetUp"));
        r.register("sync.toggleAuto", () -> {
            services.store().update(s -> s.sync.auto = !s.sync.auto);
            services.echo(services.settings().sync.auto ? "status.syncAutoOn" : "status.syncAutoOff");
        });

        r.register("help.documentation", () -> services.host().openUrl(AppInfo.docsUrl()));
        r.register("help.whatsNew", () -> services.host().openUrl(AppInfo.releaseNotesUrl()));
        r.register("help.about", window::openAbout);
        r.register("help.copyDetails", window::copyDetails);
        r.register(
                "help.checkForUpdates",
                () -> services.updates().checkNow(true),
                () -> services.updates().state() == UpdateCoordinator.State.CHECKING
                        ? Messages.tr("palette.disabled.checking")
                        : null);
        r.register("help.debugLog", window::openDebugLog);
        r.register(
                "debugLog.refresh",
                window::refreshDebugLog,
                () -> window.isDebugLogOpen() ? null : Messages.tr("palette.disabled.debugLogClosed"));
        r.register("debugLog.copy", () -> Clipboards.copy(DebugLog.text()));
        r.register("debugLog.clear", window::clearDebugLog);
        r.register("debugLog.export", window::exportDebugLog);
        r.register("help.reportProblem", window::openReportProblem);
    }
}
