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
        // The window's regions, in turn (ACC-06): the documents and each open tool window.
        r.register("window.focusNextRegion", () -> window.toolWindows().focusRegion(1));
        r.register("window.focusPreviousRegion", () -> window.toolWindows().focusRegion(-1));
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
        r.register("view.toggleToolbar", () -> {
            services.store().update(s -> s.showToolbar = !s.showToolbar);
            services.echo(services.settings().showToolbar ? "status.toolbarOn" : "status.toolbarOff");
        });
        // Simple mode: a setting with its own command, the toolbar's toggle and a View menu row in both menus.
        r.register("view.toggleSimpleMode", () -> {
            services.store().update(s -> s.simpleMode = !s.simpleMode);
            services.echo(services.settings().simpleMode ? "status.simpleModeOn" : "status.simpleModeOff");
        });
        r.register("view.toggleStatusBar", () -> {
            services.store().update(s -> s.showStatusBar = !s.showStatusBar);
            services.echo(services.settings().showStatusBar ? "status.statusBarOn" : "status.statusBarOff");
        });

        r.register("view.toggleTabBar", () -> {
            services.store().update(s -> s.showTabBar = !s.showTabBar);
            services.echo(services.settings().showTabBar ? "status.tabBarOn" : "status.tabBarOff");
        });

        registerTabs(window, r);
        registerToolWindows(window, r);

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

    /**
     * Every tab operation is a command (TABS-01): bound to a chord, in the palette, a row in the Window menu,
     * and the per-tab ones on the tab's right-click menu ({@link TabContextMenu}). Each acts on the selected
     * tab. The ids are Editora's.
     */
    private static void zoomUi(AppServices services, int percent) {
        services.store().update(s -> s.uiZoom = percent);
        services.echo("status.uiZoom", percent);
    }

    private static void registerTabs(MainWindow window, CommandRegistry r) {
        r.register(
                "buffer.close",
                () -> window.onSelectedTab(t -> window.tabs().closeTab(t)),
                () -> window.tabs().closeUnavailable());
        for (String id : java.util.List.of(
                "buffer.closeOthers",
                "buffer.closeAll",
                "buffer.closeUnmodified",
                "buffer.closeLeft",
                "buffer.closeRight")) {
            r.register(
                    id,
                    () -> window.onSelectedTab(t -> {
                        switch (id) {
                            case "buffer.closeOthers" -> window.tabs().closeOtherTabs(t);
                            case "buffer.closeAll" -> window.tabs().closeAllTabs();
                            case "buffer.closeUnmodified" -> window.tabs().closeUnmodifiedTabs();
                            case "buffer.closeLeft" -> window.tabs().closeTabsToLeft(t);
                            default -> window.tabs().closeTabsToRight(t);
                        }
                    }),
                    () -> window.tabs().nothingToClose(id));
        }
        r.register(
                "buffer.togglePin",
                () -> window.onSelectedTab(t -> window.tabs().togglePin(t)),
                () -> window.tabs().closeUnavailable());
        r.register(
                "buffer.reopenClosed",
                () -> window.tabs().reopenClosed(),
                () -> window.tabs().reopenUnavailable());
        r.register(
                "buffer.next",
                () -> window.tabs().selectNext(1),
                () -> window.tabs().cycleUnavailable());
        r.register(
                "buffer.previous",
                () -> window.tabs().selectNext(-1),
                () -> window.tabs().cycleUnavailable());
        r.register("switcher.show", window::showSwitcher, () -> window.tabs().closeUnavailable());
        r.register("help.welcome", () -> window.tabs().showWelcome());
    }

    /**
     * Each tool window's {@code tool.<id>} command (TABS-13), which its stripe button runs, and the commands
     * that float, maximize, resize, close and re-show them from the keyboard (TABS-18). Those act on the
     * window whose stripe menu or header was used, else the one holding focus, else the last one opened.
     */
    private static void registerToolWindows(MainWindow window, CommandRegistry r) {
        for (String id : java.util.List.of("documents", "outline", "output")) {
            r.register(
                    "tool." + id,
                    () -> window.toolWindows().toggle(toolWindow(window, id)),
                    () -> window.toolWindows().unavailableReason(toolWindow(window, id)));
        }
        r.register(
                "toolWindow.openInSplit",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().openInSplit(tw)),
                () -> Chrome.splitUnavailable(splitTarget(window)));
        r.register(
                "toolWindow.hide",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().setVisible(tw, false)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().target() != null));
        r.register(
                "view.restoreStripeButtons",
                () -> window.toolWindows()
                        .all()
                        .forEach(tw -> window.toolWindows().setVisible(tw, true)),
                () -> Chrome.restoreStripeButtonsUnavailable(window.toolWindows().all().stream()
                        .anyMatch(tw -> !window.toolWindows().isVisible(tw))));
        r.register(
                "view.floatToolWindow",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().toggleFloating(tw)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().target() != null));
        r.register(
                "view.maximizeToolWindow",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().toggleMaximized(tw)),
                () -> Chrome.toolWindowUnavailable(
                        docked(window, window.toolWindows().target())));
        r.register(
                "view.closeFocusedToolWindow",
                () -> window.onToolWindow(
                        window.toolWindows().target(),
                        tw -> window.toolWindows().close(tw)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().target() != null));
        r.register(
                "view.resizeHorizontalToolWindowGreater",
                () -> window.onToolWindow(
                        window.toolWindows().horizontalTarget(),
                        tw -> window.toolWindows().resize(tw, ToolWindowManager.RESIZE_STEP)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().horizontalTarget() != null));
        r.register(
                "view.resizeHorizontalToolWindowLess",
                () -> window.onToolWindow(
                        window.toolWindows().horizontalTarget(),
                        tw -> window.toolWindows().resize(tw, -ToolWindowManager.RESIZE_STEP)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().horizontalTarget() != null));
        r.register(
                "view.resizeBottomToolWindow",
                () -> window.onToolWindow(
                        window.toolWindows().bottomTarget(),
                        tw -> window.toolWindows().resize(tw, ToolWindowManager.RESIZE_STEP)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().bottomTarget() != null));
        r.register(
                "view.resizeBottomToolWindowLess",
                () -> window.onToolWindow(
                        window.toolWindows().bottomTarget(),
                        tw -> window.toolWindows().resize(tw, -ToolWindowManager.RESIZE_STEP)),
                () -> Chrome.toolWindowUnavailable(window.toolWindows().bottomTarget() != null));
        // Find (FIND-04): find.show over the documents, output.find over the console; each, run while its
        // bar is open, finds the next match. The bars hand Enter, Shift+Enter and their buttons to these.
        r.register(
                "find.show",
                () -> {
                    FindBar bar = window.documentFindBar();
                    if (bar != null) bar.openOrNext();
                },
                () -> Chrome.findUnavailable(window.documentFindBar() != null));
        r.register(
                "find.next",
                () -> stepFind(window.documentFindBar(), true),
                () -> Chrome.findStepUnavailable(isOpen(window.documentFindBar())));
        r.register(
                "find.previous",
                () -> stepFind(window.documentFindBar(), false),
                () -> Chrome.findStepUnavailable(isOpen(window.documentFindBar())));
        r.register("output.find", window::findInOutput);
        r.register(
                "output.findNext",
                () -> stepFind(window.outputFindBar(), true),
                () -> Chrome.findStepUnavailable(isOpen(window.outputFindBar())));
        r.register(
                "output.findPrevious",
                () -> stepFind(window.outputFindBar(), false),
                () -> Chrome.findStepUnavailable(isOpen(window.outputFindBar())));
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
        r.register(
                "documents.open",
                () -> {
                    SamplePanels.Documents list = window.documents();
                    if (list != null) list.openSelected();
                },
                () -> Chrome.documentsOpenUnavailable(
                        window.documents() != null && window.documents().hasSelection()));
        r.register(
                "output.clear",
                () -> {
                    SamplePanels.Output output = window.output();
                    if (output != null) output.clear();
                },
                () -> Chrome.outputClearUnavailable(window.output() != null
                        && !window.output().console().getText().isEmpty()));
        r.register(
                "output.runSample",
                window::runSampleTask,
                () -> Chrome.sampleTaskUnavailable(window.isSampleTaskRunning()));
    }

    private static void onText(
            MainWindow window, java.util.function.Consumer<javafx.scene.control.TextInputControl> action) {
        javafx.scene.control.TextInputControl text = window.focusedText();
        if (text != null) action.accept(text);
    }

    private static boolean isOpen(@org.jspecify.annotations.Nullable FindBar bar) {
        return bar != null && bar.isOpen();
    }

    private static void stepFind(@org.jspecify.annotations.Nullable FindBar bar, boolean forward) {
        if (bar != null && bar.isOpen()) bar.step(forward);
    }

    private static ToolWindow toolWindow(MainWindow window, String id) {
        return java.util.Objects.requireNonNull(window.toolWindows().get(id), id);
    }

    /** The window Open in Split would open: the stripe button's whose menu is open, if it can. */
    private static boolean splitTarget(MainWindow window) {
        ToolWindow tw = window.toolWindows().target();
        return tw != null && window.toolWindows().canSplitWith(tw);
    }

    private static boolean docked(MainWindow window, @org.jspecify.annotations.Nullable ToolWindow tw) {
        return tw != null
                && window.toolWindows().isOpen(tw)
                && !window.toolWindows().isFloating(tw);
    }
}
