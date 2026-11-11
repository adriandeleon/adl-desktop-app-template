package com.example.app.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure decisions behind the tabs and the tool windows (Pauta's Tabs and tool panels), no toolkit. */
class TabsAndPanelsTest {

    private static final Path A = Path.of("/p/a.md");
    private static final Path B = Path.of("/p/b.md");

    /** TABS-06: newest first, bounded, one entry per file, the caret kept. */
    @Test
    void closedTabsKeepOrderBoundOneEntryPerFileAndTheCaret() {
        ClosedTabs closed = new ClosedTabs();
        assertEquals(20, ClosedTabs.DEFAULT_CAPACITY);
        closed.closed(A, 5);
        closed.closed(B, 7);
        closed.closed(A, 9); // closed again: to the top, listed once, with its new caret
        closed.closed(null, 3); // a tab with no file is not listed
        assertEquals(List.of(new ClosedTabs.Entry(A, 9), new ClosedTabs.Entry(B, 7)), closed.entries());

        ClosedTabs bounded = new ClosedTabs();
        for (int i = 0; i < 25; i++) bounded.closed(Path.of("/p/" + i), i);
        assertEquals(20, bounded.size());
        assertEquals(Path.of("/p/24"), bounded.entries().getFirst().path());
        assertEquals(Path.of("/p/5"), bounded.entries().getLast().path());

        assertEquals(new ClosedTabs.Entry(A, 9), closed.pop());
        closed.forget(B); // opened again some other way
        assertNull(closed.pop());
        closed.closed(A, -4);
        assertEquals(0, java.util.Objects.requireNonNull(closed.pop()).caret());
    }

    /** TABS-04: no bulk close takes a pinned tab. */
    @Test
    void bulkCloseSkipsPinnedTabs() {
        List<String> tabs = List.of("p1", "p2", "a", "b", "c", "d");
        Predicate<String> pinned = Set.of("p1", "p2")::contains;
        Predicate<String> dirty = Set.of("b")::contains;
        assertEquals(List.of("a", "c", "d"), TabStrip.closeOtherTabs(tabs, "b", pinned));
        assertEquals(List.of("a", "b", "c", "d"), TabStrip.closeAllTabs(tabs, pinned));
        assertEquals(List.of("a", "c", "d"), TabStrip.closeUnmodifiedTabs(tabs, pinned, dirty));
        assertEquals(List.of("a", "b"), TabStrip.eligibleToLeft(tabs, "c", pinned));
        assertEquals(List.of("d"), TabStrip.eligibleToRight(tabs, "c", pinned));
        assertEquals(List.of(), TabStrip.eligibleToLeft(tabs, "a", pinned));
        assertEquals(List.of(), TabStrip.eligibleToRight(tabs, "missing", pinned));
    }

    /** TABS-05: pinning goes to the end of the pinned group, unpinning to just after it. */
    @Test
    void pinningMovesToThePinBoundary() {
        List<String> tabs = List.of("p1", "p2", "a", "b", "c");
        // "c" just pinned: after p1 and p2.
        assertEquals(2, TabStrip.moveToPinBoundary(tabs, "c", Set.of("p1", "p2", "c")::contains));
        // "p1" just unpinned: just after the one pinned tab left.
        assertEquals(1, TabStrip.moveToPinBoundary(tabs, "p1", Set.of("p2")::contains));
    }

    /** TABS-07: a dragged tab lands beside its target, held to its own group. */
    @Test
    void aDraggedTabStaysInItsGroup() {
        List<String> tabs = List.of("p1", "p2", "a", "b", "c");
        Predicate<String> pinned = Set.of("p1", "p2")::contains;
        assertEquals(4, TabStrip.dropIndex(tabs, "a", "c", true, pinned)); // after c, "a" taken out
        assertEquals(2, TabStrip.dropIndex(tabs, "c", "p1", false, pinned)); // not into the pinned group
        assertEquals(1, TabStrip.dropIndex(tabs, "p1", "b", true, pinned)); // a pinned one stays pinned
        assertEquals(-1, TabStrip.dropIndex(tabs, "a", "a", true, pinned));
        assertEquals(0, TabStrip.cycle(4, 5, 1));
        assertEquals(4, TabStrip.cycle(0, 5, -1));
        assertEquals(-1, TabStrip.cycle(0, 0, 1));
    }

    /** TABS-10: the file, the project and the app, with a marker for an unsaved file. */
    @Test
    void theWindowTitleNamesTheFileTheProjectAndTheApp() {
        assertEquals("● notes.md — Samples — ExampleApp", DocumentTabs.windowTitle("notes.md", true));
        assertEquals("notes.md — Samples — ExampleApp", DocumentTabs.windowTitle("notes.md", false));
        assertEquals("Samples — ExampleApp", DocumentTabs.windowTitle(null, false));
    }

    /** TABS-08: a tab wears the class its Documents row wears, one per status, none for a clean file. */
    @Test
    void aChangedFileWearsTheDocumentsWindowsClass() {
        for (SampleFiles.Sample sample : SampleFiles.all()) {
            SampleFiles.GitStatus status = sample.status();
            assertEquals(status == null ? null : status.cssClass(), TabGitStatus.classFor(sample), sample.name());
        }
        assertEquals("git-status-modified", SampleFiles.GitStatus.MODIFIED.cssClass());
        assertEquals(
                Set.of(SampleFiles.GitStatus.values()),
                Set.copyOf(SampleFiles.all().stream()
                        .map(SampleFiles.Sample::status)
                        .filter(java.util.Objects::nonNull)
                        .toList()),
                "the samples show every status");
        assertNull(TabGitStatus.classFor(null));
        assertNotNull(SampleFiles.at(SampleFiles.root().resolve("LICENSE")));
        assertNull(SampleFiles.at(A));
    }

    /** The Outline's headings: lines that start with #, the marks stripped. */
    @Test
    void headingsAreTheLinesThatStartWithAHash() {
        assertEquals(List.of("Title", "Part"), SampleFiles.headings("# Title\n\ntext\n## Part\n#\nmore"));
    }

    /** TABS-20: a button shows only when the user has it visible and it is available. */
    @Test
    void aStripeButtonShowsOnlyWhenVisibleAndAvailable() {
        assertTrue(ToolWindowVisibility.buttonShown(true, false));
        assertFalse(ToolWindowVisibility.buttonShown(true, true));
        assertFalse(ToolWindowVisibility.buttonShown(false, false));
        assertFalse(ToolWindowVisibility.buttonShown(false, true));
        // TABS-17: an empty stripe shows only while a button is dragged, to be dropped on.
        assertTrue(ToolWindowVisibility.stripeShown(false, false));
        assertFalse(ToolWindowVisibility.stripeShown(true, false));
        assertTrue(ToolWindowVisibility.stripeShown(true, true));
    }

    /** TABS-17: dropping a stripe button reorders one list across the stripes. */
    @Test
    void dockOrderArithmetic() {
        List<String> order = List.of("a", "b", "c");
        assertEquals(List.of("b", "c", "a"), ToolWindowDock.dropOnto(order, "a", "c", true));
        assertEquals(List.of("b", "a", "c"), ToolWindowDock.dropOnto(order, "a", "c", false));
        assertEquals(List.of("c", "a", "b"), ToolWindowDock.dropOnto(order, "c", "a", false));
        assertEquals(order, ToolWindowDock.dropOnto(order, "a", "a", true));
        assertEquals(List.of("b", "c", "a"), ToolWindowDock.dropOnto(order, "a", "zz", true));
        assertEquals(List.of("b", "c", "a"), ToolWindowDock.dropAtEnd(order, "a"));
    }

    /** TABS-16: three sides, at most two windows on each. */
    @Test
    void threeSidesTwoWindowsEach() {
        assertEquals(3, ToolWindow.Side.values().length);
        assertEquals(2, ToolWindowManager.MAX_PER_SIDE);
    }

    @Test
    void theToolWindowReasons() {
        assertNull(Chrome.toolWindowUnavailable(true));
        assertNotNull(Chrome.toolWindowUnavailable(false));
        assertNull(Chrome.splitUnavailable(true));
        assertNotNull(Chrome.splitUnavailable(false));
        assertNull(Chrome.restoreStripeButtonsUnavailable(true));
        assertNotNull(Chrome.restoreStripeButtonsUnavailable(false));
        assertNull(Chrome.sampleTaskUnavailable(false));
        assertNotNull(Chrome.sampleTaskUnavailable(true));
    }
}
