package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Bytes;
import dev.sibarum.vexplore.core.Messages.Level;
import dev.sibarum.vexplore.core.Messages.LevelWanted;
import dev.sibarum.vexplore.core.Messages.Place;
import dev.sibarum.vexplore.core.Messages.Picked;
import dev.sibarum.vexplore.core.Messages.Places;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Shown;
import dev.sibarum.vexplore.core.Messages.Suggested;
import dev.sibarum.vexplore.core.Messages.Summary;
import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Paint;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.widget.Breadcrumb;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.Cue;
import dev.vexelray.gui.widget.SplitPane;
import dev.vexelray.gui.widget.StatusBar;
import dev.vexelray.gui.widget.Table;
import dev.vexelray.gui.widget.TextField;
import dev.vexelray.gui.widget.TitleBar;
import dev.vexelray.gui.widget.TreeView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The window, in the handoff's regions: the framework's title bar; the top bar, a breadcrumb of the folder shown; the
 * tree of places beside the file list, and the Suggestion Rail ({@link RailPanel}) at its right; a status line under
 * all of it. docs/shape.md is the sketch. The Preview Dock comes under the list with M4.
 *
 * <h2>It holds no state</h2>
 *
 * <p>Every label here says what the explorer last told the view, and the view tells it ({@link #show},
 * {@link #counts}, {@link #status}). Nothing is remembered here to be shown later, which is the rule worth keeping as
 * this grows: a label that remembers a value can disagree with the session. The widgets keep their own (the list's
 * selection, which levels of the tree are open), and say what changed as messages.
 *
 * <h2>The path field takes the breadcrumb's place</h2>
 *
 * <p>The design's top bar has a breadcrumb and no field. Ctrl+L puts a field in the same box, holding the folder
 * shown; Enter goes, and Esc or Enter puts the breadcrumb back ({@link #editPath}, {@link #closePath}). Only one of
 * the two is shown at a time, and the bar never changes height.
 *
 * <h2>A row of the tree is a place and a folder</h2>
 *
 * <p>The same folder is reachable from two places at once (Documents, and C: down through Users), and a tree's items
 * are its rows' identities. So a row is a {@link Spot}: the folder, and the place it was reached from. Two rows, two
 * spots, and opening one never opens the other.
 *
 * <p>Built in the {@code TREE} phase, before there is a window, and needing none: that is what lets a test build the
 * real tree headless. Written to from the view's lane, which is the framework's own idiom: a prop written off the GUI
 * thread is queued and applied by the next drain.
 *
 * <p>The title bar is the framework's. Chrome placement belongs to whoever owns the window, so that the screenshot
 * instrument in it means the same thing in every window on the desk; this application places the node and
 * supplies every colour in it through {@link Look}.
 */
final class Ui {

    /**
     * How many of the breadcrumb's segments show before the oldest fold into its ellipsis. A count, as the breadcrumb
     * wants: a deep folder's chain is cut at its far end, and the top bar never scrolls.
     */
    private static final int CRUMBS = 4;

    /** A row of the tree of places: {@code folder}, reached from the place at {@code place}. */
    record Spot(Path place, Path folder) {
    }

    private final Gui gui;
    private final Breadcrumb<Path> crumbs;
    private final TextField path;
    private final TreeView<Spot> tree;
    private final Table<Row> list;
    private final RailPanel rail;
    private final StatusBar status;
    private final Motion motion;
    private final Color danger;

    Ui(Gui gui, Motion motion, TitleBar titleBar) {
        this.gui = gui;
        this.motion = motion;
        this.danger = gui.theme().color(Role.DANGER);

        crumbs = new Breadcrumb<Path>(gui, Ui::label).name(Landmarks.CRUMBS).maxSegments(CRUMBS);
        gui.landmark(Landmarks.CRUMBS, crumbs.node());
        path = new TextField(gui, "").name(Landmarks.PATH);
        path.node().width(Length.FILL).font(Type.MONO).textSize(Type.SMALL).visible(false);
        gui.landmark(Landmarks.PATH, path.node());
        Node top = gui.row()
                .width(Length.FILL).height(Type.TOP_BAR)
                .padding(Type.TIGHT, Type.SIDE)
                .alignItems(AlignItems.CENTER)
                .background(Look.CHROME)
                .scroll(false, false)
                .clip(true)
                .children(crumbs.node(), path.node());

        // It asks for its top as it is built; the view answers when ListPlaces does.
        tree = new TreeView<Spot>(gui, Landmarks.TREE).motion(motion.arrival);
        tree.node().width(Length.FILL).height(Length.FILL);
        gui.landmark(Landmarks.TREE, tree.node());
        list = new Table<>(gui, Columns.ROW_REM, Columns.of()).name(Landmarks.LIST)
                .headers(Type.MONO, Type.SMALL, Role.DIM);
        list.node().width(Length.FILL).height(Length.FILL);
        gui.landmark(Landmarks.LIST, list.node());

        list.rows().looks(Role.SELECTION, Look.TARGETED);

        // The rail is there whenever the list is, empty or not: one that appeared with its first suggestion would move
        // every row. It is a share of the room beside the tree rather than the handoff's 25 rem, which a split's
        // minimum cannot protect the list from in a narrow window (framework-notes FN-2).
        rail = new RailPanel(gui);
        SplitPane work = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, region(Look.LIST, list.node()),
                rail.node()).sized(SplitPane.Pane.SECOND).size(Type.RAIL)
                .minFirst(Type.LIST_MIN).minSecond(Type.RAIL_MIN);
        SplitPane body = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, region(Look.CHROME, tree.node()),
                work.node()).size(Type.TREE);
        body.node().width(Length.FILL).height(Length.grow(1f));

        status = new StatusBar(gui)
                .slot(Landmarks.STATUS_COUNTS, StatusBar.Side.LEFT, "")
                .slot(Landmarks.STATUS_MESSAGE, StatusBar.Side.RIGHT, "");
        gui.landmark(Landmarks.STATUS, status.node());
        gui.landmark(Landmarks.STATUS_COUNTS, status.slot(Landmarks.STATUS_COUNTS));
        gui.landmark(Landmarks.STATUS_MESSAGE, status.slot(Landmarks.STATUS_MESSAGE));

        gui.root().direction(Direction.COLUMN)
                .background(gui.theme().color(Role.PAGE))
                .children(titleBar.node(), top, body.node(), status.node());
    }

    /** A region of the body: a column of its token's colour, holding its one widget. */
    private Node region(Paint colour, Node content) {
        return gui.column().width(Length.FILL).height(Length.FILL).background(colour).children(content);
    }

    /** A segment's or a tree row's words: the folder's own name, or a drive's root as it is written ({@code C:\}). */
    private static String label(Path p) {
        Path name = p.getFileName();
        return name == null ? p.toString() : name.toString();
    }

    // ------------------------------------------------------------------ addresses, which the widgets' messages carry

    String crumbsAddress() {
        return crumbs.address();
    }

    String pathAddress() {
        return path.address();
    }

    String treeAddress() {
        return tree.address();
    }

    /** The table's: what its selection is said with. */
    String listAddress() {
        return list.address();
    }

    /** The table's rows': what a row activated is said with, since the rows are a list of the table's own. */
    String rowsAddress() {
        return list.rows().address();
    }

    // ------------------------------------------------------------------ the folder shown

    /** The folder the breadcrumb shows, or null for none yet: its last segment. */
    Path shown() {
        List<Path> chain = crumbs.path();
        return chain.isEmpty() ? null : chain.getLast();
    }

    /** How many rows the list holds, for a test. */
    int rows() {
        return list.rows().count();
    }

    /**
     * Show what the explorer listed: the breadcrumb from the folder's root down to it, the field holding it for Ctrl+L,
     * and its rows, with the rows it names selected and the first scrolled to. Selecting them is said like any
     * selection, so the explorer counts them.
     */
    void show(Shown s) {
        List<Path> chain = new ArrayList<>();
        for (Path p = s.folder(); p != null; p = p.getParent()) {
            chain.addFirst(p);
        }
        crumbs.path(chain);
        path.text(s.folder().toString());
        list.items(s.rows());
        List<Row> select = s.rows().stream().filter(r -> s.select().contains(r.path())).toList();
        if (!select.isEmpty()) {
            list.selection().set(select);
            list.rows().reveal(select.getFirst());
        }
    }

    /** The rows a {@code Table.Selected} names, as the paths they are. */
    static Set<Path> paths(Set<Object> chosen) {
        Set<Path> out = new HashSet<>();
        for (Object o : chosen) {
            if (o instanceof Row r) {
                out.add(r.path());
            }
        }
        return out;
    }

    /** The status line's counts, in words: {@code 12 items · 2 selected · 4.2 MB}. */
    void counts(Summary s) {
        StringBuilder b = new StringBuilder().append(s.items()).append(s.items() == 1 ? " item" : " items");
        if (s.selected() > 0) {
            b.append(" · ").append(s.selected()).append(" selected");
            if (s.bytes() > 0) {
                b.append(" · ").append(Bytes.format(s.bytes()));
            }
        }
        status.text(Landmarks.STATUS_COUNTS, b.toString());
    }

    // ------------------------------------------------------------------ the Suggestion Rail

    /**
     * What the rail suggests: its chips and card, and the rows its effect would reach marked in the list. Marked, never
     * selected, so ignoring the rail costs nothing. A row is a value, so the explorer's rows are the list's own.
     */
    void suggested(Suggested s) {
        rail.show(s);
        list.rows().marks(s.reach());
    }

    /** Whether {@code address} is the card's button. */
    boolean go(String address) {
        return rail.go(address);
    }

    /** Whether {@code address} is the card's Stop. */
    boolean stop(String address) {
        return rail.stop(address);
    }

    /** The marks kept, by name. */
    void marks(java.util.List<String> names) {
        rail.marks(names);
    }

    /** Which mark {@code address} recalls, or -1. */
    int recall(String address) {
        return rail.recall(address);
    }

    /** Which mark {@code address} forgets, or -1. */
    int forget(String address) {
        return rail.forget(address);
    }

    /** The rename field's address. */
    String nameAddress() {
        return rail.nameAddress();
    }

    /** Rename was picked: the field holds {@code current}, ready to type over. */
    void naming(String current) {
        rail.naming(current);
    }

    /** Whether {@code address} is {@code Choose folder…}. */
    boolean choose(String address) {
        return rail.choose(address);
    }

    /** The pick a chip's press stands for, or null when it is none of the rail's or changes nothing. */
    Picked picked(Button.Toggled t) {
        return rail.picked(t);
    }

    // ------------------------------------------------------------------ the tree of places

    /** What to ask {@code ListLevel} for when the tree opens a row; null for its top, which is {@code ListPlaces}'. */
    static LevelWanted wanted(Object parent) {
        return parent instanceof Spot s ? new LevelWanted(s.place(), s.folder()) : null;
    }

    /** The tree's top: each place, a row of its own that opens. */
    TreeView.Children places(Places p) {
        List<TreeView.Entry> entries = new ArrayList<>(p.places().size());
        for (Place place : p.places()) {
            entries.add(TreeView.Entry.branch(new Spot(place.path(), place.path()), place.label())
                    .icon(FileFamily.FOLDERS));
        }
        return new TreeView.Children(treeAddress(), null, entries);
    }

    /** A folder's subfolders, under the row that asked. */
    TreeView.Children level(Level l) {
        List<TreeView.Entry> entries = new ArrayList<>(l.folders().size());
        for (Path folder : l.folders()) {
            entries.add(TreeView.Entry.branch(new Spot(l.place(), folder), label(folder)).icon(FileFamily.FOLDERS));
        }
        return new TreeView.Children(treeAddress(), new Spot(l.place(), l.folder()), entries);
    }

    // ------------------------------------------------------------------ the path field

    /** Ctrl+L: the field in the breadcrumb's place, the keyboard in it and its text selected, so typing replaces it. */
    void editPath() {
        crumbs.node().visible(false);
        path.node().visible(true);
        gui.focus(path.node());
        path.select(0, path.text().length());
    }

    /**
     * Esc, or Enter once the field is sent: the breadcrumb back, and the keyboard in the list, where the folder the
     * field named is about to be. A hidden field keeps the keyboard otherwise, and typing would land in it unseen
     * (framework-notes FN-3).
     */
    void closePath() {
        path.node().visible(false);
        crumbs.node().visible(true);
        gui.focus(list.rows().node());
    }

    /** The path field's node, for the Esc that is claimed only while it has the keyboard. */
    Node pathNode() {
        return path.node();
    }

    /** The node of the list that takes the keyboard, for the Backspace that is claimed only while it has it. */
    Node rowsNode() {
        return list.rows().node();
    }

    /** Say something on the status line, and when {@code alert}, draw the eye to it: something was refused or failed. */
    void status(String message, boolean alert) {
        status.text(Landmarks.STATUS_MESSAGE, message);
        if (alert) {
            motion.cues.play(status.node(), Cue.ring(danger, 2));
        }
    }
}
