package com.example.app.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.Window;

import com.example.app.command.Command;
import com.example.app.config.Settings;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tabs and the tool windows in a real headless toolkit, a test per rule of Pauta's Tabs and tool panels. */
@Tag("fx")
class TabsAndPanelsFxTest {

    @TempDir
    Path dir;

    private final FakeHost host = new FakeHost();
    private Startup.Running app;
    private MainWindow window;
    private AppServices services;
    private DocumentTabs tabs;
    private ToolWindowManager tools;

    @BeforeAll
    static void boot() throws Exception {
        FxTestSupport.boot();
    }

    @BeforeEach
    void start() throws Exception {
        launch();
    }

    private void launch() throws Exception {
        app = FxTestSupport.start(dir, host);
        window = app.window();
        services = app.services();
        tabs = window.tabs();
        tools = window.toolWindows();
        settle();
    }

    @AfterEach
    void stop() throws Exception {
        run(() -> openMenus().forEach(ContextMenu::hide));
        FxTestSupport.stop(app);
    }

    private static List<ContextMenu> openMenus() {
        List<ContextMenu> menus = new ArrayList<>();
        for (Window w : Window.getWindows()) {
            if (w instanceof ContextMenu menu && menu.isShowing()) menus.add(menu);
        }
        return menus;
    }

    /** CSS, layout, the stripe alignment, and layout again: what a pulse does. */
    private void settle() throws Exception {
        run(() -> {
            var root = window.stage().getScene().getRoot();
            root.applyCss();
            root.layout();
            tools.afterLayout();
            root.applyCss();
            root.layout();
        });
    }

    private boolean runCommand(String id) throws Exception {
        return call(() -> services.commands().run(id));
    }

    private Tab tab(String name) throws Exception {
        return call(() -> tabs.tabs().stream()
                .filter(t -> tabs.title(t).equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tab " + name)));
    }

    private void select(Tab tab) throws Exception {
        run(() -> tabs.tabPane().getSelectionModel().select(tab));
    }

    private List<String> titles() throws Exception {
        return call(() -> tabs.tabs().stream().map(tabs::title).toList());
    }

    private static Region header(Tab tab) {
        return (Region) Objects.requireNonNull(tab.getGraphic());
    }

    private static Label titleLabel(Tab tab) {
        return (Label) Objects.requireNonNull(header(tab).lookup(".tab-title"));
    }

    // ---- Tabs ----

    /** TABS-01: every tab operation is a command with a menu row; the tab's menu lists the per-tab ones. */
    @Test
    void everyTabOperationIsACommandWithAMenuRow() throws Exception {
        List<String> operations = List.of(
                "buffer.close",
                "buffer.closeOthers",
                "buffer.closeAll",
                "buffer.closeUnmodified",
                "buffer.closeLeft",
                "buffer.closeRight",
                "buffer.togglePin",
                "buffer.reopenClosed",
                "buffer.next",
                "buffer.previous");
        List<String> rows = call(() -> window.menus().groups().stream()
                .flatMap(List::stream)
                .map(AppMenus.Row::commandId)
                .toList());
        for (String id : operations) {
            assertNotNull(services.commands().get(id), id);
            assertTrue(rows.contains(id), id + " has no menu row");
            assertNotNull(Icons.forCommand(id), id + " has no glyph");
        }
        List<String> perTab = List.of(
                "buffer.close", "buffer.closeOthers", "buffer.closeLeft", "buffer.closeRight", "buffer.togglePin");
        assertTrue(TabContextMenu.COMMANDS.containsAll(perTab));

        // A right-click on a tab selects it first and opens its menu of commands.
        Tab notes = tab("notes.md");
        run(() -> Event.fireEvent(
                header(notes), new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, false, null)));
        assertSame(notes, call(tabs::selected));
        List<String> items = call(() -> tabs.menu().items().stream()
                .map(MenuItem::getId)
                .filter(Objects::nonNull)
                .toList());
        assertEquals(perTab.size() + 2, items.size()); // and Close Unmodified, Close All
        assertTrue(items.containsAll(perTab));
    }

    /** TABS-02, TABS-03: the header is the title, carrying the unsaved, pinned and read-only states. */
    @Test
    void theHeaderIsTheTitleAndCarriesTheStates() throws Exception {
        Tab notes = tab("notes.md");
        DocumentView document = Objects.requireNonNull(call(() -> tabs.document(notes)));
        assertEquals("", notes.getText());
        assertEquals("notes.md", call(() -> titleLabel(notes).getText()));
        assertEquals(document.path().toString(), call(() -> notes.getTooltip().getText()));

        select(notes);
        run(() -> document.text().appendText("more"));
        assertTrue(call(() -> header(notes).getStyleClass().contains("dirty")));
        assertEquals("• notes.md", call(() -> titleLabel(notes).getText()));
        assertTrue(call(() -> window.stage().getTitle()).startsWith("● notes.md — "), "TABS-10's marker");

        assertTrue(runCommand("buffer.togglePin"));
        assertTrue(call(() -> header(notes).getStyleClass().contains("pinned")));

        Tab license = tab("LICENSE");
        assertTrue(call(() -> header(license).getStyleClass().contains("read-only")));
        assertFalse(
                call(() -> Objects.requireNonNull(tabs.document(license)).text().isEditable()));
    }

    /** TABS-04, TABS-05: pinning moves to the pin boundary; bulk closes leave pinned tabs. */
    @Test
    void pinnedTabsSurviveBulkCloses() throws Exception {
        assertEquals(List.of("Welcome", "README.md", "notes.md", "LICENSE"), titles());
        select(tab("LICENSE"));
        assertTrue(runCommand("buffer.togglePin")); // to the end of the pinned group: after Welcome
        assertEquals(List.of("Welcome", "LICENSE", "README.md", "notes.md"), titles());

        select(tab("notes.md"));
        assertTrue(runCommand("buffer.closeLeft"));
        assertEquals(List.of("Welcome", "LICENSE", "notes.md"), titles());
        assertTrue(runCommand("buffer.closeAll"));
        assertEquals(List.of("Welcome", "LICENSE"), titles());
        assertFalse(runCommand("buffer.closeAll"), "nothing left to close but pinned tabs");

        select(tab("LICENSE"));
        assertTrue(runCommand("buffer.togglePin")); // unpinned: just after the pinned group
        assertEquals(List.of("Welcome", "LICENSE"), titles());
        assertFalse(call(() -> header(tab("LICENSE")).getStyleClass().contains("pinned")));
    }

    /** TABS-06: Reopen Closed Tab brings the file back with its caret, and what was typed in it. */
    @Test
    void reopenBringsBackTheFileAndItsCaret() throws Exception {
        Tab notes = tab("notes.md");
        select(notes);
        DocumentView document = Objects.requireNonNull(call(() -> tabs.document(notes)));
        run(() -> {
            document.text().appendText("\nadded");
            document.text().positionCaret(12);
        });
        String text = call(() -> document.text().getText());
        assertTrue(runCommand("buffer.close"));
        assertFalse(titles().contains("notes.md"));

        assertTrue(runCommand("buffer.reopenClosed"));
        DocumentView back = Objects.requireNonNull(call(tabs::activeDocument));
        assertEquals("notes.md", back.title());
        assertEquals(12, call(back::caret));
        assertEquals(text, call(() -> back.text().getText()));
        assertFalse(runCommand("buffer.reopenClosed"), "the list is empty again");
    }

    /** TABS-07: a dragged tab lands beside its target and stays in its group. */
    @Test
    void aDroppedTabLandsBesideItsTarget() throws Exception {
        Tab readme = tab("README.md");
        Tab license = tab("LICENSE");
        Tab welcome = tab("Welcome");
        run(() -> tabs.moveBeside(readme, license, true));
        assertEquals(List.of("Welcome", "notes.md", "LICENSE", "README.md"), titles());
        run(() -> tabs.moveBeside(readme, welcome, false)); // not ahead of a pinned tab
        assertEquals(List.of("Welcome", "README.md", "notes.md", "LICENSE"), titles());
    }

    /** TABS-08: a changed file's tab wears the class its Documents row wears. */
    @Test
    void aChangedFileReadsTheSameInTheTabAndTheDocumentsWindow() throws Exception {
        Tab readme = tab("README.md");
        assertTrue(call(() -> readme.getStyleClass().contains("git-status-modified")));
        assertTrue(runCommand("tool.documents"));
        settle();
        ToolWindow documents = window.documentsWindow();
        Region content = Objects.requireNonNull(documents.contentIfBuilt());
        List<String> rowClasses = call(() -> content.lookupAll(".list-cell").stream()
                .filter(n -> n instanceof ListCell<?> cell
                        && cell.getItem() instanceof SampleFiles.Sample s
                        && s.name().equals("README.md"))
                .flatMap(n -> n.getStyleClass().stream())
                .toList());
        assertTrue(rowClasses.contains("git-status-modified"), rowClasses::toString);
        Tab notes = tab("notes.md"); // clean: no class
        assertTrue(call(() -> notes.getStyleClass().stream().noneMatch(c -> c.startsWith("git-status-"))));
    }

    /** TABS-09: the switcher moves, closes the highlighted file, and keeps its width. */
    @Test
    void theSwitcherMovesClosesAndKeepsItsWidth() throws Exception {
        assertTrue(runCommand("switcher.show"));
        Switcher switcher = Objects.requireNonNull(window.switcher());
        assertTrue(call(() -> window.overlays().isShowing()));
        double width = call(() -> switcher.card().getPrefWidth());
        assertSame(
                call(tabs::selected),
                call(() -> switcher.list().getSelectionModel().getSelectedItem()));

        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.N, false, true, false, false)));
        assertEquals(1, call(() -> switcher.list().getSelectionModel().getSelectedIndex()));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.BACK_SPACE, false, false, false, false)));
        assertFalse(titles().contains("README.md"));
        assertEquals(width, call(() -> switcher.card().getPrefWidth()));

        // Releasing Ctrl after moving with it held activates the highlighted file.
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.N, false, true, false, false)));
        Tab highlighted = call(() -> switcher.list().getSelectionModel().getSelectedItem());
        run(() -> Event.fireEvent(
                switcher.list(),
                new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.CONTROL, false, false, false, false)));
        assertFalse(call(() -> window.overlays().isShowing()));
        assertSame(highlighted, call(tabs::selected));
    }

    /** TABS-09: opened with C-x C-b, letting go of Ctrl does not close the list. */
    @Test
    void releasingCtrlWithoutMovingKeepsTheSwitcherOpen() throws Exception {
        assertTrue(runCommand("switcher.show"));
        Switcher switcher = Objects.requireNonNull(window.switcher());
        run(() -> Event.fireEvent(
                switcher.list(),
                new KeyEvent(KeyEvent.KEY_RELEASED, "", "", KeyCode.CONTROL, false, false, false, false)));
        assertTrue(call(() -> window.overlays().isShowing()));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.ENTER, false, false, false, false)));
        assertFalse(call(() -> window.overlays().isShowing()));
    }

    /** TABS-12: a command hides the tab bar, and the choice is kept. */
    @Test
    void aCommandHidesTheTabBar() throws Exception {
        assertTrue(runCommand("view.toggleTabBar"));
        assertFalse(services.settings().showTabBar);
        assertTrue(call(() -> tabs.tabPane().getStyleClass().contains("no-tab-header")));
        assertTrue(runCommand("view.toggleTabBar"));
        assertFalse(call(() -> tabs.tabPane().getStyleClass().contains("no-tab-header")));
    }

    /** Next Tab and Previous Tab cycle the strip; Welcome comes back. */
    @Test
    void nextAndPreviousCycleTheStrip() throws Exception {
        select(tab("Welcome"));
        assertTrue(runCommand("buffer.previous"));
        assertEquals("LICENSE", call(() -> tabs.title(Objects.requireNonNull(tabs.selected()))));
        assertTrue(runCommand("buffer.next"));
        assertEquals("Welcome", call(() -> tabs.title(Objects.requireNonNull(tabs.selected()))));
        assertTrue(runCommand("buffer.close"));
        assertFalse(titles().contains("Welcome"));
        assertTrue(runCommand("help.welcome"));
        assertEquals("Welcome", titles().getFirst());
    }

    // ---- Tool panels ----

    /** TABS-13: each window has a tool.id command, and its stripe button runs it. */
    @Test
    void eachToolWindowHasACommandItsButtonRuns() throws Exception {
        for (ToolWindow tw : tools.all()) {
            Command command = services.commands().get(tw.commandId());
            assertNotNull(command, tw.id());
            assertEquals("tool." + tw.id(), tw.commandId());
        }
        Button button = Objects.requireNonNull(tools.stripeButton(window.documentsWindow()));
        run(button::fire);
        assertTrue(tools.isOpen(window.documentsWindow()));
        assertTrue(call(() -> button.getPseudoClassStates().contains(OverlayToggles.OPEN)));
        assertTrue(runCommand("tool.documents"));
        assertFalse(tools.isOpen(window.documentsWindow()));
        // M-1 … in the Emacs keymap.
        assertEquals(
                "M-1", com.example.app.command.Keymap.load("emacs").bindings().get("tool.documents"));
    }

    /** TABS-14: no closed window builds its content before the first paint. */
    @Test
    void noClosedToolWindowIsBuiltAtStart() throws Exception {
        for (ToolWindow tw : tools.all()) assertNull(tw.contentIfBuilt(), tw.id());
        assertNull(window.output());
        assertNull(window.outline());
        assertTrue(runCommand("tool.output"));
        assertNotNull(window.outputWindow().contentIfBuilt());
        assertNull(window.documentsWindow().contentIfBuilt());
    }

    /** TABS-15: what is open, where, in what order, hidden, and the sizes come back on the next launch. */
    @Test
    void theArrangementComesBackOnTheNextLaunch() throws Exception {
        assertTrue(runCommand("tool.documents"));
        settle();
        assertNull(call(tools::state).sizes.get("documents"), "no size of its own yet");
        double before = ToolWindowManager.DEFAULT_SHARE.get(ToolWindow.Side.LEFT);
        assertEquals(before, call(() -> tools.horizontalSplit().getDividerPositions()[0]), 0.002);
        run(() -> tools.moveTo(window.outputWindow(), ToolWindow.Side.LEFT));
        run(() -> tools.setVisible(window.outlineWindow(), false));
        run(() -> tools.resize(window.documentsWindow(), ToolWindowManager.RESIZE_STEP));
        Settings.ToolWindows saved = call(tools::state);
        assertEquals(List.of("documents"), saved.open);
        assertEquals("LEFT", saved.sides.get("output"));
        assertEquals(List.of("outline"), saved.hidden);
        double share = Objects.requireNonNull(saved.sizes.get("documents"));
        assertEquals(before + ToolWindowManager.RESIZE_STEP, share, 0.005);
        run(window::quit);
        FxTestSupport.stop(app);

        launch();
        assertTrue(tools.isOpen(window.documentsWindow()));
        assertEquals(ToolWindow.Side.LEFT, tools.side(window.outputWindow()));
        assertFalse(tools.isVisible(window.outlineWindow()));
        assertEquals(share, Objects.requireNonNull(call(tools::state).sizes.get("documents")), 1e-9);
        // and the divider is where the share says, after the first layout too
        assertEquals(share, call(() -> tools.horizontalSplit().getDividerPositions()[0]), 0.002);
        assertTrue(call(() -> tools.stripe(ToolWindow.Side.LEFT).getChildren().size() == 2));
    }

    /** TABS-16: a side holds two windows at most, stacked. */
    @Test
    void aSideHoldsTwoWindowsAtMost() throws Exception {
        run(() -> {
            tools.moveTo(window.outlineWindow(), ToolWindow.Side.LEFT);
            tools.moveTo(window.outputWindow(), ToolWindow.Side.LEFT);
        });
        select(tab("notes.md"));
        assertTrue(runCommand("tool.documents"));
        run(() -> tools.openInSplit(window.outlineWindow()));
        assertEquals(2, tools.openOn(ToolWindow.Side.LEFT).size());
        assertFalse(call(() -> tools.canSplitWith(window.outputWindow())), "a third would not fit");
        assertTrue(runCommand("tool.output")); // in place of what is open there
        assertEquals(List.of(window.outputWindow()), tools.openOn(ToolWindow.Side.LEFT));
    }

    /** TABS-18: float, maximize (one at a time), resize and close from the keyboard. */
    @Test
    void floatMaximizeResizeAndCloseAreCommands() throws Exception {
        List<String> emacs = List.of(
                "view.floatToolWindow",
                "view.maximizeToolWindow",
                "view.closeFocusedToolWindow",
                "view.resizeHorizontalToolWindowGreater",
                "view.resizeHorizontalToolWindowLess",
                "view.resizeBottomToolWindow",
                "view.resizeBottomToolWindowLess");
        var keymap = com.example.app.command.Keymap.load("emacs");
        for (String id : emacs) assertTrue(keymap.bindings().containsKey(id), id + " is not bound in Emacs");

        assertTrue(runCommand("tool.documents"));
        assertTrue(runCommand("tool.output"));
        double before = call(tools::state)
                .sizes
                .getOrDefault("documents", ToolWindowManager.DEFAULT_SHARE.get(ToolWindow.Side.LEFT));
        assertTrue(runCommand("view.resizeHorizontalToolWindowGreater"));
        assertEquals(
                before + ToolWindowManager.RESIZE_STEP, call(tools::state).sizes.get("documents"), 0.02);

        run(() -> tools.focus(window.documentsWindow()));
        assertTrue(runCommand("view.maximizeToolWindow"));
        assertSame(window.documentsWindow(), tools.maximized());
        run(() -> tools.toggleMaximized(window.outputWindow()));
        assertSame(window.outputWindow(), tools.maximized(), "one at a time");
        run(() -> tools.toggleMaximized(window.outputWindow()));
        assertNull(tools.maximized());

        run(() -> tools.focus(window.outputWindow()));
        assertTrue(runCommand("view.floatToolWindow"));
        assertTrue(tools.isFloating(window.outputWindow()));
        assertTrue(call(() -> Objects.requireNonNull(tools.floatingStage(window.outputWindow()))
                .isShowing()));
        // Focus left with the panel; the floating window's own Float button (its header) docks it again.
        run(() -> tools.toggleFloating(window.outputWindow()));
        assertFalse(tools.isFloating(window.outputWindow()));
        assertTrue(tools.isOpen(window.outputWindow()));

        run(() -> tools.focus(window.outputWindow()));
        assertTrue(runCommand("view.closeFocusedToolWindow"));
        assertFalse(tools.isOpen(window.outputWindow()));
    }

    /** TABS-19: closing the window that holds focus returns it to where it was in the documents. */
    @Test
    void closingTheFocusedWindowReturnsFocusToTheDocument() throws Exception {
        Tab notes = tab("notes.md");
        select(notes);
        DocumentView document = Objects.requireNonNull(call(() -> tabs.document(notes)));
        run(() -> document.text().requestFocus());
        assertTrue(runCommand("tool.documents"));
        settle();
        assertTrue(call(() -> tools.activeToolWindow() == window.documentsWindow()), "focus went into it");
        assertTrue(runCommand("view.closeFocusedToolWindow"));
        assertSame(document.text(), call(() -> window.stage().getScene().getFocusOwner()));
    }

    /** TABS-20: a button shows only when the user has it shown and it is available; neither clobbers the other. */
    @Test
    void hiddenIsNotUnavailable() throws Exception {
        ToolWindow outline = window.outlineWindow();
        Button button = Objects.requireNonNull(tools.stripeButton(outline));
        select(tab("Welcome")); // no document to outline: unavailable
        assertFalse(tools.isAvailable(outline));
        assertFalse(call(() -> tools.stripe(ToolWindow.Side.RIGHT).getChildren().contains(button)));
        assertTrue(tools.isVisible(outline), "the user's choice is untouched");
        assertFalse(runCommand("tool.outline"));

        select(tab("notes.md"));
        assertTrue(call(() -> tools.stripe(ToolWindow.Side.RIGHT).getChildren().contains(button)));
        run(() -> tools.setVisible(outline, false)); // the user hides it
        select(tab("Welcome"));
        select(tab("README.md"));
        assertFalse(call(() -> tools.stripe(ToolWindow.Side.RIGHT).getChildren().contains(button)));
        assertTrue(runCommand("view.restoreStripeButtons"));
        assertTrue(call(() -> tools.stripe(ToolWindow.Side.RIGHT).getChildren().contains(button)));
    }

    /** TABS-21: the Output window opens taller on its first open; the others keep their side's size. */
    @Test
    void anOutputWindowAsksForMoreOnItsFirstOpen() throws Exception {
        assertTrue(runCommand("tool.output"));
        assertEquals(0.36, Objects.requireNonNull(call(tools::state).sizes.get("output")), 1e-9);
        assertTrue(0.36 > ToolWindowManager.DEFAULT_SHARE.get(ToolWindow.Side.BOTTOM), "taller than the side's own");
        assertTrue(runCommand("tool.documents"));
        settle();
        assertNull(call(tools::state).sizes.get("documents"), "the others keep their side's size");
        assertEquals(
                ToolWindowManager.DEFAULT_SHARE.get(ToolWindow.Side.LEFT),
                call(() -> tools.horizontalSplit().getDividerPositions()[0]),
                0.002);
        run(() -> tools.resize(window.outputWindow(), -ToolWindowManager.RESIZE_STEP));
        run(() -> tools.close(window.outputWindow()));
        assertTrue(runCommand("tool.output"));
        assertEquals(0.32, Objects.requireNonNull(call(tools::state).sizes.get("output")), 0.02, "yours wins");
    }

    /** TABS-22: a busy window's stripe button badges a spinner without changing its size. */
    @Test
    void aBusyWindowBadgesItsButtonWithoutResizingIt() throws Exception {
        Button button = Objects.requireNonNull(tools.stripeButton(window.outputWindow()));
        settle();
        double width = call(button::getWidth);
        double height = call(button::getHeight);
        Node badge = Objects.requireNonNull(call(() -> button.lookup(".tool-stripe-busy")));
        assertFalse(call(badge::isVisible));

        assertTrue(runCommand("output.runSample"));
        assertTrue(call(() -> window.outputWindow().busyProperty().get()));
        assertFalse(runCommand("output.runSample"), "one run at a time");
        settle();
        assertTrue(call(badge::isVisible));
        assertEquals(width, call(button::getWidth), 0.01);
        assertEquals(height, call(button::getHeight), 0.01);

        run(() -> Objects.requireNonNull(window.output()).finish());
        assertFalse(call(badge::isVisible));
    }

    /** TABS-23: the Outline follows the active tab. */
    @Test
    void theOutlineFollowsTheActiveTab() throws Exception {
        select(tab("README.md"));
        assertTrue(runCommand("tool.outline"));
        SamplePanels.Outline outline = Objects.requireNonNull(window.outline());
        // The headings are read off the FX thread (PERF-01): each step waits for the scan to land.
        FxTestSupport.waitUntil(() -> "README.md".equals(first(outline)));
        select(tab("notes.md"));
        FxTestSupport.waitUntil(() -> "notes.md".equals(first(outline)));
        DocumentView notes = Objects.requireNonNull(call(tabs::activeDocument));
        run(() -> notes.text().appendText("\n## Added"));
        // waitUntil asks on the FX thread already.
        FxTestSupport.waitUntil(() -> !outline.list().getItems().isEmpty()
                && "Added".equals(outline.list().getItems().getLast()));
        // A heading's row puts the caret on its line, from the offsets the scan found.
        run(() -> {
            outline.list().getSelectionModel().selectLast();
            outline.list()
                    .fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
        });
        int caret = call(() -> notes.text().getCaretPosition());
        assertTrue(call(() -> notes.text().getText().substring(caret)).startsWith("## Added"));
    }

    // ---- the stripes are toolbars (TBAR-12, TBAR-13) ----

    /** TBAR-12: the tooltip and the accessible text are the title and the chord; Open in Split and Hide. */
    @Test
    void aStripeButtonIsBuiltLikeAToolbarButton() throws Exception {
        ToolWindow documents = window.documentsWindow();
        Button button = Objects.requireNonNull(tools.stripeButton(documents));
        String chord = services.keybindings().label("tool.documents");
        assertFalse(chord.isEmpty());
        assertEquals(
                Icons.withChord("Documents", chord),
                call(() -> button.getTooltip().getText()));
        assertEquals(call(() -> button.getTooltip().getText()), call(button::getAccessibleText));
        assertFalse(call(button::isFocusTraversable), "out of the Tab order, as a toolbar button");

        run(() -> documents.titleProperty().set("Files"));
        assertEquals(Icons.withChord("Files", chord), call(button::getAccessibleText), "bound to the title");

        run(() -> Event.fireEvent(
                button, new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 5, 5, 5, 5, false, null)));
        assertTrue(call(() -> button.getContextMenu() == null), "one shared menu, not one per button");
        List<String> items = call(() -> openMenus().stream()
                .flatMap(m -> m.getItems().stream())
                .map(MenuItem::getId)
                .filter(Objects::nonNull)
                .toList());
        assertEquals(List.of("toolWindow.openInSplit", "toolWindow.hide"), items);
        // Hide, run from the button's menu, hides that button.
        assertTrue(runCommand("toolWindow.hide"));
        assertFalse(tools.isVisible(documents));
    }

    /** TBAR-13: the right stripe's first glyph sits on the document's first line of text. */
    @Test
    void theRightStripesFirstIconSitsOnTheFirstLineOfText() throws Exception {
        Tab notes = tab("notes.md");
        select(notes);
        settle();
        settle();
        DocumentView document = Objects.requireNonNull(call(() -> tabs.document(notes)));
        Button first = call(
                () -> (Button) tools.stripe(ToolWindow.Side.RIGHT).getChildren().getFirst());
        double line = call(() -> firstLineCenter(document));
        assertEquals(line, call(() -> glyphCenter(first)), 2.0, "on the first line of text, not the tab strip");

        // Scrolling the document moves its text, not the stripe.
        run(() -> {
            document.text().appendText("\n".repeat(80) + "end");
            document.text().setScrollTop(200);
        });
        settle();
        settle();
        assertEquals(line, call(() -> glyphCenter(first)), 2.0, "the stripe stays where the first line belongs");
        run(() -> document.text().setScrollTop(0));
        settle();

        // Hiding the tab strip moves the text up, and the glyph follows it.
        assertTrue(runCommand("view.toggleTabBar"));
        settle();
        settle();
        double after = call(() -> firstLineCenter(document));
        assertTrue(after < line - 10, "the text moved up");
        assertEquals(after, call(() -> glyphCenter(first)), 2.0);
    }

    private static double firstLineCenter(DocumentView document) {
        Text text = (Text) Objects.requireNonNull(document.text().lookup(".text"));
        Text probe = new Text("Xg");
        probe.setFont(text.getFont());
        Bounds b = text.localToScene(text.getLayoutBounds());
        return b.getMinY() + probe.getLayoutBounds().getHeight() / 2;
    }

    private static double glyphCenter(Button button) {
        Node icon = Objects.requireNonNull(button.getGraphic());
        return icon.localToScene(icon.getLayoutBounds()).getCenterY();
    }
    // ---- the rest of the showcase, through its own controls ----

    /** The Documents window opens a sample on Enter or a double-click; the Outline puts the caret on a heading. */
    @Test
    void theSamplePanelsOpenFilesAndRevealHeadings() throws Exception {
        assertTrue(runCommand("tool.documents"));
        settle();
        var list = (javafx.scene.control.ListView<?>) Objects.requireNonNull(
                Objects.requireNonNull(window.documentsWindow().contentIfBuilt())
                        .lookup(".documents-list"));
        run(() -> list.getSelectionModel().select(2)); // guide.md
        run(() -> Event.fireEvent(list, FxTestSupport.key(KeyCode.ENTER, false, false, false, false)));
        assertEquals("guide.md", call(() -> tabs.title(Objects.requireNonNull(tabs.selected()))));
        run(() -> list.getSelectionModel().select(3)); // ideas.md
        run(() -> Event.fireEvent(list, click(list, 2)));
        assertEquals("ideas.md", call(() -> tabs.title(Objects.requireNonNull(tabs.selected()))));
        assertTrue(call(() -> tab("ideas.md").getStyleClass().contains("git-status-untracked")));

        assertTrue(runCommand("tool.outline"));
        SamplePanels.Outline outline = Objects.requireNonNull(window.outline());
        DocumentView document = Objects.requireNonNull(call(tabs::activeDocument));
        run(() -> outline.list().getSelectionModel().select(2)); // the Git status heading
        run(() -> Event.fireEvent(outline.list(), FxTestSupport.key(KeyCode.ENTER, false, false, false, false)));
        int caret = call(document::caret);
        assertTrue(call(() -> document.text().getText().substring(caret).startsWith("## ")), "on the heading");
        run(() -> document.text().positionCaret(0));
        run(() -> outline.list().getSelectionModel().select(1));
        run(() -> Event.fireEvent(outline.list(), click(outline.list(), 1)));
        assertTrue(call(document::caret) > 0);
    }

    private static javafx.scene.input.MouseEvent click(Node target, int count) {
        return new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                5,
                5,
                5,
                5,
                javafx.scene.input.MouseButton.PRIMARY,
                count,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                new javafx.scene.input.PickResult(target, 5, 5));
    }

    /** TABS-17: while a stripe button is dragged, an empty stripe shows as a dashed drop zone; moves re-dock. */
    @Test
    void anEmptyStripeIsADropZoneWhileDragging() throws Exception {
        var right = tools.stripe(ToolWindow.Side.RIGHT);
        assertFalse(call(right::isVisible), "Welcome: nothing on the right");
        run(() -> tools.setDragging(true));
        assertTrue(call(right::isVisible));
        assertTrue(call(() -> right.getStyleClass().contains("tool-stripe-drop-target")));
        run(() -> tools.setDragging(false));
        assertFalse(call(right::isVisible));

        assertTrue(runCommand("tool.documents"));
        run(() -> tools.moveTo(window.documentsWindow(), ToolWindow.Side.RIGHT)); // open, it reopens there
        assertEquals(List.of(window.documentsWindow()), tools.openOn(ToolWindow.Side.RIGHT));
        assertEquals("RIGHT", call(tools::state).sides.get("documents"));
        run(() -> tools.moveTo(window.documentsWindow(), ToolWindow.Side.LEFT)); // home again
        assertNull(call(tools::state).sides.get("documents"));
        assertTrue(tools.isOpen(window.documentsWindow()));
    }

    /** A floating window's own close button closes the tool window; float and maximize come back on launch. */
    @Test
    void floatingAndMaximizedComeBackAndAFloatingWindowCloses() throws Exception {
        assertTrue(runCommand("tool.output"));
        run(() -> tools.toggleFloating(window.outputWindow()));
        javafx.stage.Stage stage = Objects.requireNonNull(tools.floatingStage(window.outputWindow()));
        run(stage::hide);
        assertFalse(tools.isOpen(window.outputWindow()));
        assertTrue(call(tools::state).floating.isEmpty());

        assertTrue(runCommand("tool.output"));
        run(() -> tools.toggleFloating(window.outputWindow()));
        assertTrue(runCommand("tool.documents"));
        run(() -> tools.toggleMaximized(window.documentsWindow()));
        assertEquals("documents", call(tools::state).maximized);
        run(window::quit);
        FxTestSupport.stop(app);

        launch();
        assertTrue(tools.isFloating(window.outputWindow()));
        assertSame(window.documentsWindow(), tools.maximized());
        assertSame(
                window.documentsWindow().contentIfBuilt(),
                call(() -> ((javafx.scene.layout.VBox) tools.node().getCenter())
                        .getChildren()
                        .getLast()));
        run(() -> tools.close(window.outputWindow()));
        assertFalse(call(() -> Objects.requireNonNull(window.outputWindow().contentIfBuilt())
                                .getScene()
                        != null
                && window.outputWindow().contentIfBuilt().getScene().getWindow().isShowing()));
    }

    /** A divider the user drags is kept when they let go of it (TABS-15). */
    @Test
    void aDraggedDividerIsKept() throws Exception {
        assertTrue(runCommand("tool.documents"));
        settle();
        var split = tools.horizontalSplit();
        Node divider = Objects.requireNonNull(call(() -> split.lookup(".split-pane-divider")));
        run(() -> {
            Event.fireEvent(split, press(divider, javafx.scene.input.MouseEvent.MOUSE_PRESSED));
            split.setDividerPositions(0.3); // where the drag took it
            Event.fireEvent(split, press(divider, javafx.scene.input.MouseEvent.MOUSE_RELEASED));
        });
        assertEquals(0.3, Objects.requireNonNull(call(tools::state).sizes.get("documents")), 1e-6);
        assertEquals(
                0.3,
                Objects.requireNonNull(
                        services.settings().window.toolWindows.sizes.get("documents")),
                1e-6);
    }

    private static javafx.scene.input.MouseEvent press(
            Node target, javafx.event.EventType<javafx.scene.input.MouseEvent> type) {
        return new javafx.scene.input.MouseEvent(
                type,
                1,
                1,
                1,
                1,
                javafx.scene.input.MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                true,
                new javafx.scene.input.PickResult(target, 1, 1));
    }

    /** A tool window's header buttons act on their own window, whichever has focus. */
    @Test
    void aPanelsHeaderButtonsActOnItsOwnWindow() throws Exception {
        assertTrue(runCommand("tool.documents"));
        assertTrue(runCommand("tool.output"));
        settle();
        Region panel = (Region) Objects.requireNonNull(window.documentsWindow().contentIfBuilt())
                .getParent();
        List<Button> buttons = call(() -> panel.lookupAll(".tool-window-header-button").stream()
                .map(Button.class::cast)
                .toList());
        assertEquals(3, buttons.size()); // float, maximize, close
        run(() -> buttons.get(1).fire());
        assertSame(window.documentsWindow(), tools.maximized());
        run(() -> buttons.get(1).fire());
        assertNull(tools.maximized());
        run(() -> buttons.get(2).fire());
        assertFalse(tools.isOpen(window.documentsWindow()));
        assertTrue(tools.isOpen(window.outputWindow()));
    }

    /** TBAR-03: a header button's tooltip and accessible name carry the chord of the keymap in use. */
    @Test
    void aPanelsHeaderButtonsFollowAKeymapChange() throws Exception {
        assertTrue(runCommand("tool.documents"));
        settle();
        Region panel = (Region) Objects.requireNonNull(window.documentsWindow().contentIfBuilt())
                .getParent();
        Button close = call(() -> panel.lookupAll(".tool-window-header-button").stream()
                .map(Button.class::cast)
                .toList()
                .get(2));
        String id = "view.closeFocusedToolWindow";
        run(() -> services.store().update(s -> s.keybindings.put(id, "C-M-F9")));
        String chord = call(() -> services.keybindings().label(id));
        assertFalse(chord.isEmpty());
        assertTrue(call(close::getAccessibleText).endsWith("(" + chord + ")"), call(close::getAccessibleText));
        assertEquals(
                call(close::getAccessibleText), call(() -> close.getTooltip().getText()));
    }

    /** The other closes, and the switcher's other keys. */
    @Test
    void theOtherClosesAndSwitcherKeys() throws Exception {
        select(tab("README.md"));
        assertTrue(runCommand("buffer.closeRight"));
        assertEquals(List.of("Welcome", "README.md"), titles());
        assertTrue(runCommand("buffer.reopenClosed"));
        assertTrue(runCommand("buffer.reopenClosed"));
        select(tab("notes.md"));
        assertTrue(runCommand("buffer.closeOthers"));
        assertEquals(List.of("Welcome", "notes.md"), titles());
        assertTrue(runCommand("buffer.closeUnmodified"));
        assertEquals(List.of("Welcome"), titles());
        assertFalse(runCommand("buffer.next"), "one tab: nothing to cycle");

        assertTrue(runCommand("buffer.reopenClosed"));
        assertTrue(runCommand("switcher.show"));
        Switcher switcher = Objects.requireNonNull(window.switcher());
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.TAB, false, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.TAB, true, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.DOWN, false, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.UP, false, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.A, false, false, false, false)));
        run(() -> Event.fireEvent(switcher.list(), FxTestSupport.key(KeyCode.DELETE, false, false, false, false)));
        assertEquals(1, titles().size());
        run(() -> Event.fireEvent(switcher.list(), click(switcher.list(), 1)));
        assertFalse(call(() -> window.overlays().isShowing()));
    }

    /** PERF-11: the Output console is bounded, and the newest lines are the ones it keeps. */
    @Test
    void theOutputConsoleKeepsTheNewestLinesUpToItsCap() throws Exception {
        SamplePanels.Output output = call(SamplePanels.Output::new);
        int total = SamplePanels.Output.MAX_LINES + 25;
        run(() -> {
            for (int i = 1; i <= total; i++) output.append("line " + i + "\n");
        });
        String text = call(() -> output.console().getText());
        assertEquals(SamplePanels.Output.MAX_LINES, text.split("\n").length);
        assertTrue(text.startsWith("line 26\n"), "the oldest went first");
        assertTrue(text.endsWith("line " + total + "\n"), "the newest is kept");
    }

    private static String first(SamplePanels.Outline outline) {
        try {
            return call(() -> outline.list().getItems().isEmpty()
                    ? ""
                    : outline.list().getItems().getFirst());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
