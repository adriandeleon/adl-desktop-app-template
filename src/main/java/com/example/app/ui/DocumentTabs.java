package com.example.app.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ContextMenuEvent;

import com.example.app.AppInfo;
import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The document tabs (Pauta's Tabs and tool panels, Tabs): a strip of sample documents with a pinned group
 * at its front, after Editora's {@code EditorArea} and the tab half of its {@code MainController}. Every
 * operation is a command ({@link Commands}, TABS-01) that acts on the selected tab; the decisions are
 * {@link TabStrip}'s, the headers {@link TabHeaders}', the closed list {@link ClosedTabs}'.
 *
 * <p>A closed document keeps its text for the session: closing never throws away typing (reopening, or
 * opening it again from the Documents window, brings it back as it was), so a close here needs no question.
 * An app whose close discards unsaved work asks first (WIN-05).
 */
final class DocumentTabs {

    private final AppServices services;
    private final TabPane tabPane = new TabPane();
    private final Map<Tab, DocumentView> documents = new IdentityHashMap<>();
    private final Set<Tab> pinned = Collections.newSetFromMap(new IdentityHashMap<>());
    /** What a closed document held when it differed from its file, by path. */
    private final Map<Path, String> edits = new HashMap<>();

    private final CommandMenu editorMenu;

    private final ClosedTabs closedTabs = new ClosedTabs();
    private final TabHeaders headers;
    private final CommandMenu menu;
    private final Tab welcome;
    private final Consumer<String> setTitle;
    private final List<Runnable> activeListeners = new ArrayList<>();
    private final List<Runnable> textListeners = new ArrayList<>();

    DocumentTabs(AppServices services, Node welcomeContent, Consumer<String> setTitle) {
        this.services = services;
        this.setTitle = setTitle;
        this.headers = new TabHeaders(pinned::contains, this::moveBeside);
        this.menu = new CommandMenu(services, TabContextMenu.COMMANDS);
        // The editor's right-click menu (MENU-05), one for every document, in place of the stock one.
        this.editorMenu = new CommandMenu(
                services,
                java.util.Arrays.asList(
                        "edit.cut", "edit.copy", "edit.paste", null, "edit.selectAll", null, "find.show"));
        tabPane.getStyleClass().add("document-tabs");
        tabPane.setMinSize(0, 0); // the strip scrolls its headers; it never holds a tool window's divider back
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.setTabDragPolicy(TabPane.TabDragPolicy.FIXED); // the headers drag, with Pauta's feedback

        welcome = new Tab();
        welcome.setContent(welcomeContent);
        install(welcome);
        tabPane.getTabs().add(welcome);
        pinned.add(welcome); // the start page stays at the front through every bulk close
        render(welcome);

        tabPane.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            updateWindowTitle();
            activeListeners.forEach(Runnable::run);
        });
        // Three of the samples open at start: one modified, one clean, one read-only.
        List<SampleFiles.Sample> samples = SampleFiles.all();
        for (SampleFiles.Sample sample : List.of(samples.get(0), samples.get(1), samples.getLast())) open(sample);
        tabPane.getSelectionModel().select(welcome);
        updateWindowTitle();
    }

    TabPane tabPane() {
        return tabPane;
    }

    List<Tab> tabs() {
        return List.copyOf(tabPane.getTabs());
    }

    Tab welcome() {
        return welcome;
    }

    ClosedTabs closedTabs() {
        return closedTabs;
    }

    CommandMenu menu() {
        return menu;
    }

    TabHeaders headers() {
        return headers;
    }

    /** Runs {@code listener} whenever the active tab changes: what a panel follows (TABS-23). */
    void onActiveChanged(Runnable listener) {
        activeListeners.add(listener);
    }

    /**
     * Runs {@code listener} on every edit of the active tab's text. Per keystroke: a listener that reads the
     * whole document waits for typing to pause (PERF-08).
     */
    void onActiveTextChanged(Runnable listener) {
        textListeners.add(listener);
    }

    @Nullable
    Tab selected() {
        return tabPane.getSelectionModel().getSelectedItem();
    }

    /** The active tab's document, or null on a tab that holds none. */
    @Nullable
    DocumentView activeDocument() {
        Tab tab = selected();
        return tab == null ? null : documents.get(tab);
    }

    @Nullable
    DocumentView document(Tab tab) {
        return documents.get(tab);
    }

    boolean isPinned(Tab tab) {
        return pinned.contains(tab);
    }

    boolean isDirty(Tab tab) {
        DocumentView document = documents.get(tab);
        return document != null && document.isDirty();
    }

    /** The tab showing {@code path}, or null. */
    @Nullable
    Tab tabFor(Path path) {
        for (Map.Entry<Tab, DocumentView> e : documents.entrySet()) {
            if (e.getValue().path().equals(path)) return e.getKey();
        }
        return null;
    }

    /** Opens {@code sample} in a tab and selects it; an open one is selected. */
    Tab open(SampleFiles.Sample sample) {
        return open(sample, 0);
    }

    private Tab open(SampleFiles.Sample sample, int caret) {
        Tab existing = tabFor(sample.path());
        if (existing != null) {
            tabPane.getSelectionModel().select(existing);
            return existing;
        }
        closedTabs.forget(sample.path());
        DocumentView document = new DocumentView(sample, edits.remove(sample.path()), caret);
        Tab tab = new Tab();
        tab.setContent(document.node());
        CommandMenu.onConsole(document.text(), editorMenu);
        documents.put(tab, document);
        install(tab);
        document.dirtyProperty().addListener((o, was, now) -> {
            render(tab);
            if (tab == selected()) updateWindowTitle();
        });
        document.text().textProperty().addListener((o, was, now) -> {
            if (tab == selected()) textListeners.forEach(Runnable::run);
        });
        tabPane.getTabs().add(tab);
        render(tab);
        tabPane.getSelectionModel().select(tab);
        return tab;
    }

    /** Selects the Welcome tab, bringing it back when it was closed. */
    void showWelcome() {
        if (!tabPane.getTabs().contains(welcome)) {
            tabPane.getTabs().add(TabStrip.moveToPinBoundary(tabs(), welcome, pinned::contains), welcome);
        }
        tabPane.getSelectionModel().select(welcome);
    }

    private void install(Tab tab) {
        // The close button is Close Tab like any other: one path, which keeps the closed list.
        tab.setOnCloseRequest(e -> {
            e.consume();
            closeTab(tab);
        });
    }

    /** Re-renders a tab's header from its state. */
    void render(Tab tab) {
        DocumentView document = documents.get(tab);
        if (document != null) headers.documentHeader(tab, document);
        else headers.contentHeader(tab, Messages.tr("tabs.welcome"));
        Node header = tab.getGraphic();
        if (header != null) header.setOnContextMenuRequested(e -> showMenu(tab, e));
    }

    /** The tab's right-click menu: the tab is selected first, so its commands act on it (MENU-10). */
    void showMenu(Tab tab, ContextMenuEvent event) {
        tabPane.getSelectionModel().select(tab);
        Node header = tab.getGraphic();
        if (header != null) menu.show(header, event);
    }

    /**
     * The documents whose typing a quit would lose (WIN-13): the open ones with changes, then those closed
     * this session with changes kept, by name.
     */
    List<String> unsavedNames() {
        List<String> names = new ArrayList<>();
        for (Tab tab : tabPane.getTabs()) {
            DocumentView document = documents.get(tab);
            if (document != null && document.isDirty()) names.add(document.title());
        }
        for (Path path : edits.keySet()) {
            Path name = path.getFileName();
            String title = name == null ? path.toString() : name.toString();
            if (!names.contains(title)) names.add(title);
        }
        return names;
    }

    // ---- the operations (TABS-01), each run by a command on the selected tab ----

    void closeTab(Tab tab) {
        DocumentView document = documents.remove(tab);
        if (document != null) {
            closedTabs.closed(document.path(), document.caret());
            if (document.isDirty()) edits.put(document.path(), document.text().getText());
        }
        pinned.remove(tab);
        tabPane.getTabs().remove(tab);
        if (tab == welcome) pinned.add(welcome); // it comes back pinned
        updateWindowTitle();
    }

    private void closeAll(List<Tab> tabs) {
        tabs.forEach(this::closeTab);
    }

    void closeOtherTabs(Tab keep) {
        closeAll(TabStrip.closeOtherTabs(tabs(), keep, pinned::contains));
    }

    void closeAllTabs() {
        closeAll(TabStrip.closeAllTabs(tabs(), pinned::contains));
    }

    void closeUnmodifiedTabs() {
        closeAll(TabStrip.closeUnmodifiedTabs(tabs(), pinned::contains, this::isDirty));
    }

    void closeTabsToLeft(Tab tab) {
        closeAll(TabStrip.eligibleToLeft(tabs(), tab, pinned::contains));
    }

    void closeTabsToRight(Tab tab) {
        closeAll(TabStrip.eligibleToRight(tabs(), tab, pinned::contains));
    }

    /** Pins or unpins {@code tab}, moving it to the pin boundary (TABS-05). */
    void togglePin(Tab tab) {
        if (!pinned.remove(tab)) pinned.add(tab);
        moveToPinBoundary(tab);
        render(tab);
        services.echo(pinned.contains(tab) ? "status.tabPinned" : "status.tabUnpinned", title(tab));
    }

    /** Moves {@code tab} to the end of the pinned group, or just after it, keeping it selected. */
    void moveToPinBoundary(Tab tab) {
        int at = TabStrip.moveToPinBoundary(tabs(), tab, pinned::contains);
        move(tab, at);
    }

    /** A dragged tab dropped beside {@code target}, held to its own group (TABS-07). */
    void moveBeside(Tab dragged, Tab target, boolean after) {
        int at = TabStrip.dropIndex(tabs(), dragged, target, after, pinned::contains);
        if (at >= 0) move(dragged, at);
    }

    private void move(Tab tab, int at) {
        boolean wasSelected = tab == selected();
        tabPane.getTabs().remove(tab);
        tabPane.getTabs().add(Math.min(at, tabPane.getTabs().size()), tab);
        if (wasSelected) tabPane.getSelectionModel().select(tab);
    }

    /** Brings back the most recently closed file with its caret where it was (TABS-06). */
    void reopenClosed() {
        ClosedTabs.Entry entry = closedTabs.pop();
        if (entry == null) return;
        SampleFiles.Sample sample = SampleFiles.at(entry.path());
        if (sample == null) return;
        Tab tab = open(sample, entry.caret());
        DocumentView document = documents.get(tab);
        if (document != null) document.text().requestFocus();
    }

    /** Selects the tab {@code delta} steps along the strip, wrapping. */
    void selectNext(int delta) {
        int at = TabStrip.cycle(
                tabPane.getSelectionModel().getSelectedIndex(),
                tabPane.getTabs().size(),
                delta);
        if (at >= 0) tabPane.getSelectionModel().select(at);
    }

    /** Shows or hides the tab strip (TABS-12): {@code .tab-pane.no-tab-header} in {@code app.css}. */
    void setTabBarShown(boolean shown) {
        TabHeaders.toggle(tabPane, "no-tab-header", !shown);
        tabPane.requestLayout(); // a hidden header area is unmanaged, which asks for no layout of its own
    }

    /** What a tab is called: its file's name, or Welcome. */
    String title(Tab tab) {
        DocumentView document = documents.get(tab);
        return document == null ? Messages.tr("tabs.welcome") : document.title();
    }

    /** The window title: the file, the project and the app, with {@code ●} for an unsaved file (TABS-10). */
    void updateWindowTitle() {
        Tab tab = selected();
        DocumentView document = tab == null ? null : documents.get(tab);
        setTitle.accept(
                windowTitle(document == null ? null : document.title(), document != null && document.isDirty()));
    }

    /** The window title for a file (or none) of the samples project. Pure. */
    static String windowTitle(@Nullable String file, boolean dirty) {
        String rest = SampleFiles.PROJECT + " — " + AppInfo.NAME;
        if (file == null) return rest;
        return (dirty ? "● " : "") + file + " — " + rest;
    }

    /** The tabs a close command would take now, by its id; for whether it can run. */
    List<Tab> wouldClose(String commandId) {
        Tab tab = selected();
        if (tab == null) return List.of();
        return switch (commandId) {
            case "buffer.closeOthers" -> TabStrip.closeOtherTabs(tabs(), tab, pinned::contains);
            case "buffer.closeAll" -> TabStrip.closeAllTabs(tabs(), pinned::contains);
            case "buffer.closeUnmodified" -> TabStrip.closeUnmodifiedTabs(tabs(), pinned::contains, this::isDirty);
            case "buffer.closeLeft" -> TabStrip.eligibleToLeft(tabs(), tab, pinned::contains);
            case "buffer.closeRight" -> TabStrip.eligibleToRight(tabs(), tab, pinned::contains);
            default -> List.of(tab);
        };
    }

    // ---- why a command cannot run now ----

    @Nullable
    String closeUnavailable() {
        return selected() == null ? Messages.tr("palette.disabled.noTab") : null;
    }

    @Nullable
    String nothingToClose(String commandId) {
        if (selected() == null) return Messages.tr("palette.disabled.noTab");
        return wouldClose(commandId).isEmpty() ? Messages.tr("palette.disabled.nothingToClose") : null;
    }

    @Nullable
    String reopenUnavailable() {
        return closedTabs.size() == 0 ? Messages.tr("palette.disabled.noClosedTab") : null;
    }

    @Nullable
    String cycleUnavailable() {
        return tabPane.getTabs().size() < 2 ? Messages.tr("palette.disabled.oneTab") : null;
    }
}
