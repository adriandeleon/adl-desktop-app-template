package com.example.app.ui;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Window;

import com.example.app.config.Settings;
import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The tool windows around the document tabs (Pauta's Tabs and tool panels, Tool panels), after Editora's
 * {@code ToolWindowManager}: a stripe of buttons on each of three sides, and each side's open windows docked
 * between its stripe and the tabs, at most two stacked.
 *
 * <ul>
 *   <li>TABS-13: each window has a {@code tool.<id>} command; its stripe button runs that command.
 *   <li>TABS-14: a window's content is built on its first open ({@link ToolWindow#content}).
 *   <li>TABS-15: the arrangement is kept in {@code settings.json}'s {@code window.toolWindows} and read
 *       back at start.
 *   <li>TABS-16: three sides ({@link ToolWindow.Side}), {@link #MAX_PER_SIDE} windows on each.
 *   <li>TABS-17: a stripe button drags to another place or stripe ({@link ToolWindowDock}); an empty
 *       stripe shows as a dashed drop zone while one is dragged.
 *   <li>TABS-18: float, maximize (one at a time), resize and close are commands, bound in the Emacs keymap.
 *   <li>TABS-19: closing the window that holds focus gives it back to where it was in the documents.
 *   <li>TABS-20: the user's hide ({@code hidden}) and a passing unavailability are kept apart
 *       ({@link ToolWindowVisibility}).
 *   <li>TABS-21: a bottom window can ask for more height on its first open.
 *   <li>TABS-22: a busy window's stripe button badges a spinner.
 *   <li>TBAR-12: a stripe button is a toolbar button, named by its title and chord, with Open in Split and
 *       Hide on its right-click menu. TBAR-13: the vertical stripes' first icon sits on the documents' first
 *       line of text ({@link #alignStripes}).
 * </ul>
 */
final class ToolWindowManager {

    /** At most two windows stacked on a side (TABS-16). */
    static final int MAX_PER_SIDE = 2;

    /** A side's default share of the window, until a window there is sized. */
    static final Map<ToolWindow.Side, Double> DEFAULT_SHARE =
            Map.of(ToolWindow.Side.LEFT, 0.25, ToolWindow.Side.RIGHT, 0.23, ToolWindow.Side.BOTTOM, 0.24);

    /** How much a resize command moves a window's edge, as a share of the window. */
    static final double RESIZE_STEP = 0.04;

    /** A stripe button whose window holds the keyboard focus. */
    static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("tw-active");

    /** Before a document has been measured: where its first line sits below the tab strip. */
    static final double DEFAULT_TEXT_OFFSET = 17;

    private final AppServices services;
    private final Node editor;
    private final BorderPane root = new BorderPane();
    private final SplitPane horizontal = new SplitPane();
    private final SplitPane middle = new SplitPane();
    private final Map<ToolWindow.Side, Pane> stripes = new EnumMap<>(ToolWindow.Side.class);

    private final Map<String, ToolWindow> byId = new LinkedHashMap<>();
    private final Map<ToolWindow, Button> stripeButtons = new HashMap<>();
    private final Map<ToolWindow, StringProperty> chords = new HashMap<>();
    private final Map<String, StringProperty> headerChords = new HashMap<>();
    private final Map<ToolWindow, Region> panels = new HashMap<>();
    private final Map<ToolWindow.Side, List<ToolWindow>> open = new EnumMap<>(ToolWindow.Side.class);
    private final Map<ToolWindow, Stage> floating = new HashMap<>();
    private final Set<ToolWindow> unavailable = new HashSet<>();
    private final Settings.ToolWindows state;
    private final CommandMenu stripeMenu;

    private @Nullable ToolWindow maximized;
    private @Nullable ToolWindow activeToolWindow;
    /** The window a command acts on when none holds focus: the last one opened or focused. */
    private @Nullable ToolWindow lastActive;
    /** The window whose stripe button's right-click menu is open. */
    private @Nullable ToolWindow menuTarget;

    private WeakReference<@Nullable Node> lastEditorFocus = new WeakReference<>(null);
    private boolean dragging;
    private boolean quitting;
    private boolean userDragging;
    private double textOffset = DEFAULT_TEXT_OFFSET;
    private String stripeStyle = "";

    ToolWindowManager(AppServices services, Node editor) {
        this.services = services;
        this.editor = editor;
        this.state = services.settings().window.toolWindows.copy();
        for (ToolWindow.Side side : ToolWindow.Side.values()) open.put(side, new ArrayList<>());
        stripes.put(ToolWindow.Side.LEFT, stripe(new VBox(), ToolWindow.Side.LEFT));
        stripes.put(ToolWindow.Side.RIGHT, stripe(new VBox(), ToolWindow.Side.RIGHT));
        stripes.put(ToolWindow.Side.BOTTOM, stripe(new HBox(), ToolWindow.Side.BOTTOM));
        root.setLeft(stripes.get(ToolWindow.Side.LEFT));
        root.setRight(stripes.get(ToolWindow.Side.RIGHT));
        root.setBottom(stripes.get(ToolWindow.Side.BOTTOM));
        root.getStyleClass().add("tool-window-area");
        horizontal.getStyleClass().add("tool-window-split");
        middle.getStyleClass().add("tool-window-split");
        middle.setOrientation(Orientation.VERTICAL);
        // Nothing here holds a divider back with a minimum size: a kept size comes back as it was (TABS-15).
        horizontal.setMinSize(0, 0);
        middle.setMinSize(0, 0);
        // The kept shares are the sizes (TABS-15): a SplitPane hands a change of space out evenly, which would
        // walk every size a little on each launch and each resize, so the dividers are read only when the user
        // lets go of one, and put back from the shares after every layout.
        watchDrags(horizontal);
        watchDrags(middle);
        stripeMenu = new CommandMenu(services, List.of("toolWindow.openInSplit", "toolWindow.hide"));
        stripeMenu.menu().setOnHidden(e -> Platform.runLater(() -> menuTarget = null));
        services.onKeybindingsChanged(this::refreshTooltips);
        root.sceneProperty().addListener((o, was, scene) -> {
            if (scene != null) watch(scene);
        });
        layoutDocks();
    }

    private Pane stripe(Pane pane, ToolWindow.Side side) {
        pane.getStyleClass().addAll("tool-stripe", "tool-stripe-" + side.name().toLowerCase(java.util.Locale.ROOT));
        pane.getStyleClass().add(side == ToolWindow.Side.BOTTOM ? "tool-stripe-horizontal" : "tool-stripe-vertical");
        if (pane instanceof VBox box) box.setAlignment(Pos.TOP_CENTER);
        if (pane instanceof HBox box) box.setAlignment(Pos.CENTER_LEFT);
        pane.setOnDragOver(e -> {
            if (dragSource(e) != null) {
                e.acceptTransferModes(TransferMode.MOVE);
                pane.getStyleClass().remove("tool-stripe-drop-over");
                pane.getStyleClass().add("tool-stripe-drop-over");
            }
            e.consume();
        });
        pane.setOnDragExited(e -> pane.getStyleClass().remove("tool-stripe-drop-over"));
        pane.setOnDragDropped(e -> {
            pane.getStyleClass().remove("tool-stripe-drop-over");
            ToolWindow src = dragSource(e);
            if (src != null) {
                // A drop on the stripe's empty space: last on this side.
                state.order = ToolWindowDock.dropAtEnd(order(), src.id());
                moveTo(src, side);
            }
            e.setDropCompleted(src != null);
            e.consume();
        });
        return pane;
    }

    /** The whole area: the stripes, the docks and the documents between them. */
    BorderPane node() {
        return root;
    }

    /** The split of the side windows and the documents, for the tests that read where its dividers are. */
    SplitPane horizontalSplit() {
        return horizontal;
    }

    Pane stripe(ToolWindow.Side side) {
        return java.util.Objects.requireNonNull(stripes.get(side));
    }

    // ---- registering ----

    /**
     * Adds a window and its stripe button, which runs the window's {@code tool.<id>} command (TABS-13; the
     * command is registered in {@link Commands}).
     */
    void register(ToolWindow tw) {
        byId.put(tw.id(), tw);
        if (!state.order.contains(tw.id())) state.order.add(tw.id());

        StringProperty chord = new SimpleStringProperty(services.keybindings().label(tw.commandId()));
        chords.put(tw, chord);
        Button button = Icons.toolbarButton(
                stripeIcon(tw), tw.title(), () -> services.commands().run(tw.commandId()));
        button.getStyleClass().add("tool-stripe-button");
        button.setId("stripe-" + tw.id());
        // One string for the tooltip and the accessible text, bound to the window's title (TBAR-12, TBAR-01).
        StringBinding name =
                Bindings.createStringBinding(() -> Icons.withChord(tw.title(), chord.get()), tw.titleProperty(), chord);
        Icons.bindName(button, name);
        button.setOnContextMenuRequested(e -> {
            menuTarget = tw;
            stripeMenu.show(button, e);
        });
        enableReorderDrag(tw, button);
        stripeButtons.put(tw, button);
        layoutStripes();
    }

    /** Opens the windows the last session left open; call once every window is registered. */
    void restore() {
        String keptMaximized = state.maximized; // read first: each open below lays the docks out again
        maximized = null;
        for (String id : List.copyOf(state.open)) {
            ToolWindow tw = byId.get(id);
            if (tw == null) continue;
            if (state.floating.contains(id)) floatWindow(tw);
            else openWindow(tw, openList(side(tw)).size() < MAX_PER_SIDE);
        }
        ToolWindow max = byId.get(keptMaximized);
        setMaximized(max != null && panels.containsKey(max) ? max : null);
        layoutDocks();
    }

    /**
     * The stripe button's glyph, with a small spinner badged on its corner while the window is busy (TABS-22).
     * The badge is laid over the glyph rather than beside it, so the button keeps its size.
     */
    private static Node stripeIcon(ToolWindow tw) {
        StackPane icon = new StackPane(Icons.toolbarLine(tw.glyph()));
        ProgressIndicator badge = new ProgressIndicator();
        badge.getStyleClass().add("tool-stripe-busy");
        for (var size : List.of(
                badge.minWidthProperty(),
                badge.minHeightProperty(),
                badge.prefWidthProperty(),
                badge.prefHeightProperty(),
                badge.maxWidthProperty(),
                badge.maxHeightProperty())) {
            UiZoom.bind(size, 10);
        }
        badge.setMouseTransparent(true);
        badge.setTranslateX(4);
        badge.setTranslateY(4);
        StackPane.setAlignment(badge, Pos.BOTTOM_RIGHT);
        badge.visibleProperty().bind(tw.busyProperty());
        icon.getChildren().add(badge);
        return icon;
    }

    /** Re-reads every stripe and header button's chord after a keymap change (TBAR-03). */
    void refreshTooltips() {
        chords.forEach((tw, chord) -> chord.set(services.keybindings().label(tw.commandId())));
        headerChords.forEach((id, chord) -> chord.set(services.keybindings().label(id)));
    }

    /** The chord of a panel header's command, one property per command that every header's button binds. */
    private StringProperty headerChord(String id) {
        return headerChords.computeIfAbsent(
                id, k -> new SimpleStringProperty(services.keybindings().label(k)));
    }

    // ---- queries ----

    @Nullable
    ToolWindow get(String id) {
        return byId.get(id);
    }

    List<ToolWindow> all() {
        return List.copyOf(byId.values());
    }

    @Nullable
    Button stripeButton(ToolWindow tw) {
        return stripeButtons.get(tw);
    }

    boolean isOpen(ToolWindow tw) {
        return panels.containsKey(tw) || floating.containsKey(tw);
    }

    boolean isFloating(ToolWindow tw) {
        return floating.containsKey(tw);
    }

    @Nullable
    Stage floatingStage(ToolWindow tw) {
        return floating.get(tw);
    }

    @Nullable
    ToolWindow maximized() {
        return maximized;
    }

    private List<ToolWindow> openList(ToolWindow.Side side) {
        return java.util.Objects.requireNonNull(open.get(side));
    }

    /** The windows docked open on {@code side}, top (or left) first. */
    List<ToolWindow> openOn(ToolWindow.Side side) {
        return List.copyOf(openList(side));
    }

    /** The side {@code tw} docks on now: where the user moved it, else its home. */
    ToolWindow.Side side(ToolWindow tw) {
        String stored = state.sides.get(tw.id());
        if (stored != null) {
            try {
                return ToolWindow.Side.valueOf(stored);
            } catch (IllegalArgumentException e) {
                // a side this build does not know: its home
            }
        }
        return tw.homeSide();
    }

    /** The kept state, as it would be saved now. */
    Settings.ToolWindows state() {
        return state.copy();
    }

    /** The window a command acts on: the stripe button's whose menu is open, the focused one, the last one. */
    @Nullable
    ToolWindow target() {
        ToolWindow fromMenu = menuTarget;
        if (fromMenu != null) return fromMenu;
        ToolWindow focused = activeToolWindow;
        if (focused != null && isOpen(focused)) return focused;
        ToolWindow last = lastActive;
        if (last != null && isOpen(last)) return last;
        for (List<ToolWindow> side : open.values()) {
            if (!side.isEmpty()) return side.getLast();
        }
        return floating.keySet().stream().findFirst().orElse(null);
    }

    private List<String> order() {
        return state.order;
    }

    // ---- availability (TABS-20) ----

    /**
     * Marks a window unavailable (or available again) for the moment: its stripe button hides, and the
     * user's own show/hide choice is left as it was.
     */
    void setAvailable(ToolWindow tw, boolean available) {
        boolean changed = available ? unavailable.remove(tw) : unavailable.add(tw);
        if (changed) layoutStripes();
    }

    boolean isAvailable(ToolWindow tw) {
        return !unavailable.contains(tw);
    }

    /** Whether the user has the stripe button shown. */
    boolean isVisible(ToolWindow tw) {
        return !state.hidden.contains(tw.id());
    }

    /** Hides or shows a stripe button by the user's choice, kept (TABS-20). */
    void setVisible(ToolWindow tw, boolean visible) {
        state.hidden.remove(tw.id());
        if (!visible) state.hidden.add(tw.id());
        layoutStripes();
        save();
    }

    @Nullable
    String unavailableReason(ToolWindow tw) {
        return unavailable.contains(tw) ? Messages.tr("palette.disabled.toolWindowUnavailable", tw.title()) : null;
    }

    // ---- opening and closing ----

    /** The {@code tool.<id>} command: opens the window, or closes it when it is open. */
    void toggle(ToolWindow tw) {
        if (isOpen(tw)) close(tw);
        else {
            openWindow(tw, false);
            focus(tw);
        }
    }

    /** Opens {@code tw} docked on its side: in place of what is open there, or beside it when {@code split}. */
    void openWindow(ToolWindow tw, boolean split) {
        if (isOpen(tw)) return;
        ToolWindow.Side side = side(tw);
        List<ToolWindow> there = openList(side);
        if (!split || there.size() >= MAX_PER_SIDE) {
            for (ToolWindow other : List.copyOf(there)) closeDocked(other);
        }
        if (!state.sizes.containsKey(tw.id()) && tw.firstOpenShare() > 0) {
            // TABS-21: more room on the first open; the user's own size wins from here on.
            state.sizes.put(tw.id(), Math.max(shareOf(side), tw.firstOpenShare()));
        }
        there.add(tw);
        panels.put(tw, panel(tw));
        setMaximized(null);
        markOpen(tw, true);
        lastActive = tw;
        layoutDocks();
        save();
    }

    /** Whether {@code tw} can open beside what is open on its side (Open in Split). */
    boolean canSplitWith(ToolWindow tw) {
        List<ToolWindow> there = openOn(side(tw));
        return !there.isEmpty() && !there.contains(tw) && there.size() < MAX_PER_SIDE;
    }

    /** Open in Split: {@code tw} stacked with what is open on its side. */
    void openInSplit(ToolWindow tw) {
        if (isOpen(tw)) {
            focus(tw);
            return;
        }
        openWindow(tw, true);
        focus(tw);
    }

    /** Closes {@code tw}, docked or floating; focus goes back to the documents if it was inside (TABS-19). */
    void close(ToolWindow tw) {
        boolean hadFocus = holdsFocus(tw);
        Stage stage = floating.remove(tw);
        if (stage != null) {
            state.floating.remove(tw.id());
            stage.setOnHidden(null);
            stage.close();
            if (stage.getScene() != null) stage.getScene().setRoot(new Region());
        } else {
            closeDocked(tw);
            layoutDocks();
        }
        markOpen(tw, false);
        save();
        if (hadFocus) focusEditor();
    }

    private void closeDocked(ToolWindow tw) {
        openList(side(tw)).remove(tw);
        panels.remove(tw);
        if (maximized == tw) setMaximized(null);
        markOpen(tw, false);
    }

    private void markOpen(ToolWindow tw, boolean isOpen) {
        state.open.remove(tw.id());
        if (isOpen) state.open.add(tw.id());
        Button button = stripeButtons.get(tw);
        if (button != null) button.pseudoClassStateChanged(OverlayToggles.OPEN, isOpen);
    }

    /** Moves keyboard focus into {@code tw}'s content. */
    void focus(ToolWindow tw) {
        Region content = tw.contentIfBuilt();
        if (content == null) return;
        Node target = content.lookup(".list-view");
        if (target == null) target = content.lookup(".text-area");
        (target != null ? target : content).requestFocus();
        lastActive = tw;
    }

    // ---- float, maximize, resize (TABS-18) ----

    /** Floats {@code tw} in a window of its own, or docks it back when it floats. */
    void toggleFloating(ToolWindow tw) {
        if (floating.containsKey(tw)) {
            Stage stage = java.util.Objects.requireNonNull(floating.remove(tw));
            state.floating.remove(tw.id());
            stage.setOnHidden(null);
            stage.close();
            if (stage.getScene() != null) stage.getScene().setRoot(new Region());
            markOpen(tw, false);
            openWindow(tw, false);
            focus(tw);
        } else {
            if (isOpen(tw)) {
                closeDocked(tw);
                layoutDocks();
            }
            floatWindow(tw);
            focus(tw);
        }
        save();
    }

    private void floatWindow(ToolWindow tw) {
        Stage stage = new Stage();
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        if (owner != null) stage.initOwner(owner);
        Region panel = panel(tw);
        Scene scene = Themes.style(new Scene(panel, 520, 360));
        stage.setScene(scene);
        stage.titleProperty().bind(tw.titleProperty());
        stage.setOnHidden(e -> {
            if (quitting) return; // a quit closes every window: this one is kept floating for the next launch
            // The window's own close button closes the tool window.
            floating.remove(tw);
            state.floating.remove(tw.id());
            markOpen(tw, false);
            save();
        });
        floating.put(tw, stage);
        if (!state.floating.contains(tw.id())) state.floating.add(tw.id());
        markOpen(tw, true);
        lastActive = tw;
        stage.show();
    }

    /** Maximizes {@code tw} over the documents and the other windows, or restores it; one at a time. */
    void toggleMaximized(ToolWindow tw) {
        if (!panels.containsKey(tw)) return;
        setMaximized(maximized == tw ? null : tw);
        layoutDocks();
        focus(tw);
        save();
    }

    private void setMaximized(@Nullable ToolWindow tw) {
        maximized = tw;
        state.maximized = tw == null ? "" : tw.id();
    }

    /** Grows (or with a negative step, shrinks) {@code tw}'s share of the window. */
    void resize(ToolWindow tw, double step) {
        if (!panels.containsKey(tw)) return;
        ToolWindow.Side side = side(tw);
        List<ToolWindow> there = openOn(side);
        ToolWindow first = there.getFirst();
        double share = Math.clamp(shareOf(first) + step, 0.08, 0.85);
        for (ToolWindow other : there) state.sizes.put(other.id(), share);
        layoutDocks();
        save();
    }

    /** The open window on a side the horizontal resize acts on: the target if it is beside the documents. */
    @Nullable
    ToolWindow horizontalTarget() {
        ToolWindow tw = target();
        if (tw != null && panels.containsKey(tw) && side(tw) != ToolWindow.Side.BOTTOM) return tw;
        for (ToolWindow.Side side : List.of(ToolWindow.Side.LEFT, ToolWindow.Side.RIGHT)) {
            List<ToolWindow> there = openOn(side);
            if (!there.isEmpty()) return there.getFirst();
        }
        return null;
    }

    /** The open bottom window the vertical resize acts on. */
    @Nullable
    ToolWindow bottomTarget() {
        List<ToolWindow> there = openOn(ToolWindow.Side.BOTTOM);
        return there.isEmpty() ? null : there.getFirst();
    }

    // ---- re-docking (TABS-17) ----

    /** Moves {@code tw} to {@code side}; open, it reopens there. */
    void moveTo(ToolWindow tw, ToolWindow.Side side) {
        boolean wasOpen = panels.containsKey(tw);
        if (wasOpen) {
            closeDocked(tw);
        }
        if (side == tw.homeSide()) state.sides.remove(tw.id());
        else state.sides.put(tw.id(), side.name());
        if (wasOpen) openWindow(tw, true);
        layoutStripes();
        layoutDocks();
        save();
    }

    private void enableReorderDrag(ToolWindow tw, Button button) {
        button.setOnDragDetected(e -> {
            Dragboard db = button.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(tw.id());
            db.setContent(content);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            db.setDragView(button.snapshot(params, null), e.getX(), e.getY());
            button.getStyleClass().add("tool-stripe-button-dragging");
            setDragging(true);
            e.consume();
        });
        button.setOnDragOver(e -> {
            ToolWindow src = dragSource(e);
            if (src != null && src != tw) {
                e.acceptTransferModes(TransferMode.MOVE);
                boolean after = after(button, e);
                boolean vertical = side(tw) != ToolWindow.Side.BOTTOM;
                clearDropMarkers(button);
                button.getStyleClass()
                        .add(
                                vertical
                                        ? (after ? "tool-drop-bottom" : "tool-drop-top")
                                        : (after ? "tool-drop-right" : "tool-drop-left"));
            }
            e.consume();
        });
        button.setOnDragExited(e -> clearDropMarkers(button));
        button.setOnDragDropped(e -> {
            clearDropMarkers(button);
            ToolWindow src = dragSource(e);
            if (src != null && src != tw) {
                state.order = ToolWindowDock.dropOnto(order(), src.id(), tw.id(), after(button, e));
                moveTo(src, side(tw));
            }
            e.setDropCompleted(src != null);
            e.consume();
        });
        button.setOnDragDone(e -> {
            button.getStyleClass().remove("tool-stripe-button-dragging");
            setDragging(false);
        });
    }

    private boolean after(Button button, DragEvent e) {
        return side(java.util.Objects.requireNonNull(dragTargetOf(button))) == ToolWindow.Side.BOTTOM
                ? e.getX() > button.getWidth() / 2
                : e.getY() > button.getHeight() / 2;
    }

    private @Nullable ToolWindow dragTargetOf(Button button) {
        for (Map.Entry<ToolWindow, Button> entry : stripeButtons.entrySet()) {
            if (entry.getValue() == button) return entry.getKey();
        }
        return null;
    }

    private static void clearDropMarkers(Node button) {
        button.getStyleClass().removeAll("tool-drop-top", "tool-drop-bottom", "tool-drop-left", "tool-drop-right");
    }

    private @Nullable ToolWindow dragSource(DragEvent e) {
        Dragboard db = e.getDragboard();
        return db.hasString() && dragging ? byId.get(db.getString()) : null;
    }

    /** While a stripe button is dragged, every stripe shows, an empty one as a drop zone (TABS-17). */
    void setDragging(boolean on) {
        dragging = on;
        layoutStripes();
    }

    boolean isDragging() {
        return dragging;
    }

    // ---- layout ----

    /** Puts every shown stripe button on its stripe in order, and shows the stripes that have one. */
    void layoutStripes() {
        for (ToolWindow.Side side : ToolWindow.Side.values())
            stripe(side).getChildren().clear();
        List<String> order = new ArrayList<>(order());
        for (String id : byId.keySet()) {
            if (!order.contains(id)) order.add(id);
        }
        for (String id : order) {
            ToolWindow tw = byId.get(id);
            Button button = tw == null ? null : stripeButtons.get(tw);
            if (tw == null || button == null) continue;
            if (ToolWindowVisibility.buttonShown(isVisible(tw), unavailable.contains(tw))) {
                stripe(side(tw)).getChildren().add(button);
            }
        }
        for (ToolWindow.Side side : ToolWindow.Side.values()) {
            Pane stripe = stripe(side);
            boolean empty = stripe.getChildren().isEmpty();
            boolean shown = ToolWindowVisibility.stripeShown(empty, dragging);
            stripe.setVisible(shown);
            stripe.setManaged(shown);
            TabHeaders.toggle(stripe, "tool-stripe-drop-target", dragging && empty);
        }
    }

    /** Lays the open windows out around the documents, at their kept sizes. */
    private void layoutDocks() {
        ToolWindow max = maximized;
        if (max != null && panels.containsKey(max)) {
            root.setCenter(panels.get(max));
            return;
        }
        Node left = dock(ToolWindow.Side.LEFT);
        Node right = dock(ToolWindow.Side.RIGHT);
        Node bottom = dock(ToolWindow.Side.BOTTOM);
        middle.getItems().setAll(bottom == null ? List.of(editor) : List.of(editor, bottom));
        List<Node> row = new ArrayList<>();
        if (left != null) row.add(left);
        row.add(middle);
        if (right != null) row.add(right);
        horizontal.getItems().setAll(row);
        root.setCenter(horizontal);
        applyShares();
    }

    /** Sets the dividers from the kept shares. */
    private void applyShares() {
        if (root.getCenter() != horizontal) return;
        horizontal.setDividerPositions(horizontalTargets());
        if (middle.getItems().size() == 2) middle.setDividerPositions(1 - shareOf(ToolWindow.Side.BOTTOM));
        for (SplitPane inner : innerSplits()) inner.setDividerPositions(innerTarget(inner));
    }

    /** Where the side-by-side dividers belong: the left window's share, and the right one's from the end. */
    private double[] horizontalTargets() {
        List<Node> row = horizontal.getItems();
        boolean left = row.indexOf(middle) == 1;
        boolean right = row.indexOf(middle) < row.size() - 1;
        double leftShare = left ? shareOf(ToolWindow.Side.LEFT) : 0;
        double rightShare = right ? shareOf(ToolWindow.Side.RIGHT) : 0;
        if (left && right) return new double[] {leftShare, 1 - rightShare};
        if (left) return new double[] {leftShare};
        if (right) return new double[] {1 - rightShare};
        return new double[0];
    }

    private double innerTarget(SplitPane inner) {
        return inner.getProperties().get(ToolWindow.Side.class) instanceof ToolWindow.Side side
                ? state.splits.getOrDefault(side.name(), 0.5)
                : 0.5;
    }

    /** The splits that stack two windows on a side. */
    private List<SplitPane> innerSplits() {
        List<SplitPane> out = new ArrayList<>();
        for (SplitPane pane : List.of(horizontal, middle)) {
            for (Node item : pane.getItems()) {
                if (item instanceof SplitPane inner && inner.getProperties().containsKey(ToolWindow.Side.class)) {
                    out.add(inner);
                }
            }
        }
        return out;
    }

    /** Reads the dividers into the shares when the user lets go of one they dragged. */
    private void watchDrags(SplitPane split) {
        split.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (onDivider(e.getPickResult().getIntersectedNode(), split)) userDragging = true;
        });
        split.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!userDragging) return;
            userDragging = false;
            rememberSizes();
            save();
        });
    }

    private static boolean onDivider(@Nullable Node node, SplitPane split) {
        for (Node n = node; n != null && n != split; n = n.getParent()) {
            if (n.getStyleClass().contains("split-pane-divider")) return true;
        }
        return false;
    }

    /**
     * After every layout: the stripes lined up with the text (TBAR-13), and the dividers put back where the
     * shares say, unless the user is dragging one.
     */
    void afterLayout() {
        if (!userDragging && dividersDrifted()) applyShares();
        alignStripes();
    }

    /** Whether a layout moved a divider off its share (by more than a rounding). */
    private boolean dividersDrifted() {
        if (root.getCenter() != horizontal) return false;
        if (differs(horizontal.getDividerPositions(), horizontalTargets())) return true;
        if (middle.getItems().size() == 2
                && differs(middle.getDividerPositions(), new double[] {1 - shareOf(ToolWindow.Side.BOTTOM)})) {
            return true;
        }
        for (SplitPane inner : innerSplits()) {
            if (differs(inner.getDividerPositions(), new double[] {innerTarget(inner)})) return true;
        }
        return false;
    }

    private static boolean differs(double[] now, double[] target) {
        if (now.length != target.length) return false; // the split has not caught up with its items yet
        for (int i = 0; i < now.length; i++) {
            if (Math.abs(now[i] - target[i]) > 0.001) return true;
        }
        return false;
    }

    /** A side's open windows: one panel, or two in a split; null when none is open. */
    private @Nullable Node dock(ToolWindow.Side side) {
        List<ToolWindow> there = openOn(side);
        if (there.isEmpty()) return null;
        if (there.size() == 1) return panels.get(there.getFirst());
        SplitPane split = new SplitPane();
        split.getStyleClass().add("tool-window-split");
        split.setOrientation(side == ToolWindow.Side.BOTTOM ? Orientation.HORIZONTAL : Orientation.VERTICAL);
        for (ToolWindow tw : there) split.getItems().add(panels.get(tw));
        split.setDividerPositions(state.splits.getOrDefault(side.name(), 0.5));
        split.getProperties().put(ToolWindow.Side.class, side);
        watchDrags(split);
        return split;
    }

    /** A side's share of the window: its first open window's kept size, else the side's default. */
    private double shareOf(ToolWindow.Side side) {
        List<ToolWindow> there = openOn(side);
        return there.isEmpty() ? java.util.Objects.requireNonNull(DEFAULT_SHARE.get(side)) : shareOf(there.getFirst());
    }

    private double shareOf(ToolWindow tw) {
        Double own = state.sizes.get(tw.id());
        return own != null ? own : java.util.Objects.requireNonNull(DEFAULT_SHARE.get(side(tw)));
    }

    /** Reads the dividers into each open window's size, before the layout changes under them. */
    private void rememberSizes() {
        if (maximized != null || root.getCenter() != horizontal) return;
        List<Node> row = horizontal.getItems();
        double[] h = horizontal.getDividerPositions();
        int at = row.indexOf(middle);
        if (at == 1 && h.length >= 1) remember(ToolWindow.Side.LEFT, h[0]);
        if (at >= 0 && at < row.size() - 1 && h.length > at) remember(ToolWindow.Side.RIGHT, 1 - h[at]);
        double[] v = middle.getDividerPositions();
        if (middle.getItems().size() == 2 && v.length == 1) remember(ToolWindow.Side.BOTTOM, 1 - v[0]);
        for (Node node : List.of(horizontal, middle)) {
            if (!(node instanceof SplitPane pane)) continue;
            for (Node item : pane.getItems()) {
                if (item instanceof SplitPane inner
                        && inner.getProperties().get(ToolWindow.Side.class) instanceof ToolWindow.Side side
                        && inner.getDividerPositions().length == 1) {
                    state.splits.put(side.name(), inner.getDividerPositions()[0]);
                }
            }
        }
    }

    private void remember(ToolWindow.Side side, double share) {
        if (!(share > 0 && share < 1)) return;
        for (ToolWindow tw : openOn(side)) state.sizes.put(tw.id(), share);
    }

    /** Keeps the arrangement in the settings (TABS-15). */
    void save() {
        Settings.ToolWindows snapshot = state.copy();
        services.store().update(s -> s.window.toolWindows = snapshot);
    }

    /** Called on quit, before the app's windows close: the sizes the user dragged to and what floats, kept. */
    void saveForQuit() {
        quitting = true;
        save();
    }

    /** A docked window: a header with its glyph and title and its buttons, over its content (built now). */
    private Region panel(ToolWindow tw) {
        Label title = new Label();
        title.textProperty().bind(tw.titleProperty());
        title.setGraphic(Icons.line(tw.glyph()));
        title.getStyleClass().add("tool-window-title");
        title.setMinWidth(Region.USE_PREF_SIZE); // the spacer gives way first; the name stays whole
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(4, title, spacer);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("tool-window-header");
        for (String id : List.of("view.floatToolWindow", "view.maximizeToolWindow", "view.closeFocusedToolWindow")) {
            String glyph = Icons.forCommand(id);
            var command = services.commands().get(id);
            if (glyph == null || command == null) continue;
            StringProperty chord = headerChord(id);
            Button button = Icons.button(
                    Icons.line(glyph),
                    Icons.withChord(command.title(), chord.get()),
                    () -> {
                        menuTarget = tw; // the header's buttons act on their own window
                        try {
                            services.commands().run(id);
                        } finally {
                            menuTarget = null;
                        }
                    },
                    "button-icon",
                    "flat",
                    "tool-window-header-button");
            button.setFocusTraversable(false);
            // The tooltip and the accessible name follow a keymap change (TBAR-03).
            Icons.bindName(
                    button, Bindings.createStringBinding(() -> Icons.withChord(command.title(), chord.get()), chord));
            header.getChildren().add(button);
        }
        Region content = tw.content();
        VBox.setVgrow(content, Priority.ALWAYS);
        VBox panel = new VBox(header, content);
        panel.getStyleClass().add("tool-window-panel");
        panel.setMinSize(0, 0);
        return panel;
    }

    // ---- focus (TABS-19) and the active window ----

    private void watch(Scene scene) {
        scene.focusOwnerProperty().addListener((o, was, now) -> updateActivePanel(now));
        scene.addPostLayoutPulseListener(this::afterLayout);
    }

    private void updateActivePanel(@Nullable Node focusOwner) {
        if (focusOwner != null && isDescendant(focusOwner, editor)) lastEditorFocus = new WeakReference<>(focusOwner);
        activeToolWindow = null;
        for (ToolWindow tw : byId.values()) {
            Region panel = panels.get(tw);
            boolean active = panel != null && focusOwner != null && isDescendant(focusOwner, panel);
            if (active) {
                activeToolWindow = tw;
                lastActive = tw;
            }
            Button button = stripeButtons.get(tw);
            if (button != null) button.pseudoClassStateChanged(ACTIVE, active);
        }
    }

    @Nullable
    ToolWindow activeToolWindow() {
        return activeToolWindow;
    }

    private boolean holdsFocus(ToolWindow tw) {
        Region panel = panels.get(tw);
        Scene scene = panel == null ? null : panel.getScene();
        Node owner = scene == null ? null : scene.getFocusOwner();
        return owner != null && panel != null && isDescendant(owner, panel);
    }

    /** Gives focus back to the documents: the node that last had it there, else the active document. */
    void focusEditor() {
        Node target = lastEditorFocus.get();
        if (target == null || target.getScene() == null || !isDescendant(target, editor)) {
            target = editor.lookup(".document-text");
        }
        (target != null ? target : editor).requestFocus();
    }

    private static boolean isDescendant(Node node, @Nullable Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) return true;
        }
        return false;
    }

    // ---- TBAR-13: the vertical stripes' first icon on the documents' first line of text ----

    /**
     * Pads the vertical stripes so their first glyph sits beside the documents' first line of text, not the
     * tab strip. Measured on every layout pulse from the tab strip's height and the document text's first
     * line; changes the stripes' style only when the measurement moved.
     */
    void alignStripes() {
        double header = 0;
        if (editor.lookup(".tab-header-area") instanceof Region area && area.isVisible()) header = area.getHeight();
        Double measured = firstLineBelowHeader(header);
        if (measured != null) textOffset = measured;
        double firstLine = header + textOffset;
        double glyphCenter = STRIPE_BUTTON_TOP_INSET + Icons.TOOLBAR_SIZE / 2;
        for (Node child : stripe(ToolWindow.Side.RIGHT).getChildren()) {
            if (child instanceof Button button
                    && button.getGraphic() instanceof Region glyph
                    && glyph.getHeight() > 0) {
                glyphCenter = glyph.getBoundsInParent().getCenterY(); // where the button lays its glyph out
                break;
            }
        }
        long top = Math.max(0, Math.round(firstLine - glyphCenter));
        String style = "-fx-padding: " + top + "px 0 8px 0;";
        if (style.equals(stripeStyle)) return;
        stripeStyle = style;
        stripe(ToolWindow.Side.LEFT).setStyle(style);
        stripe(ToolWindow.Side.RIGHT).setStyle(style);
    }

    /** Mirrors the top padding of {@code .toolbar-button} in {@code app.css}: the fallback before a button is laid out. */
    static final double STRIPE_BUTTON_TOP_INSET = 3;

    /** The middle of the selected document's first line, below the tab strip, or null with none showing. */
    private @Nullable Double firstLineBelowHeader(double header) {
        if (editor.getScene() == null) return null;
        Node text = null;
        for (Node candidate : editor.lookupAll(".document-text .text")) {
            if (candidate instanceof javafx.scene.text.Text t && t.isVisible() && isShowing(t)) {
                text = t;
                break;
            }
        }
        if (!(text instanceof javafx.scene.text.Text first)) return null;
        javafx.scene.text.Text probe = new javafx.scene.text.Text("Xg");
        probe.setFont(first.getFont());
        double lineHeight = probe.getLayoutBounds().getHeight();
        // Where the first line sits with the document scrolled to the top: its place in the scrolled content,
        // from the top of the viewport. Scrolling moves the text, not the stripes.
        double top = first.localToScene(first.getLayoutBounds()).getMinY();
        Node content = null;
        for (Node n = first.getParent(); n != null && n != editor; n = n.getParent()) {
            if (content == null && n.getStyleClass().contains("content")) content = n;
            else if (content != null && n.getStyleClass().contains("viewport")) {
                top = top
                        - content.localToScene(content.getLayoutBounds()).getMinY()
                        + n.localToScene(n.getLayoutBounds()).getMinY();
                break;
            }
        }
        double editorTop = editor.localToScene(editor.getLayoutBounds()).getMinY();
        return top + lineHeight / 2 - editorTop - header;
    }

    private static boolean isShowing(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) return false;
        }
        return true;
    }
}
