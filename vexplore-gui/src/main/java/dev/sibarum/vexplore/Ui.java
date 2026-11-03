package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Bytes;
import dev.sibarum.vexplore.core.Messages.Docked;
import dev.sibarum.vexplore.core.Messages.Level;
import dev.sibarum.vexplore.core.Messages.LevelWanted;
import dev.sibarum.vexplore.core.Messages.Menu;
import dev.sibarum.vexplore.core.Messages.Pair;
import dev.sibarum.vexplore.core.Messages.Place;
import dev.sibarum.vexplore.core.Messages.Picked;
import dev.sibarum.vexplore.core.Messages.Places;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Shown;
import dev.sibarum.vexplore.core.Messages.Side;
import dev.sibarum.vexplore.core.Messages.Suggested;
import dev.sibarum.vexplore.core.Messages.Summary;
import dev.sibarum.vexplore.core.Suggestions.Act;
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
import dev.vexelray.gui.widget.MenuAction;
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
import java.util.stream.Collectors;

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
 * selection, which levels of the tree are open), and say what changed as messages. {@link Tags} is one of those: the
 * list's second marked set, which the list cannot hold itself (framework-notes FN-12), one in each {@link FolderList}.
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
    private final FolderList from;
    private final FolderList to;
    private final Button two;
    private final Button swap;
    private final Button one;
    private final RailPanel rail;
    private final DockPanel dock;
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
        // Two folders, and in Two Folders its Swap sides and Back to one folder, at the bar's right end: each shown or
        // hidden, never added, so the breadcrumb never moves for them.
        two = new Button(gui, "Two folders").name(Landmarks.TWO_FOLDERS);
        gui.landmark(Landmarks.TWO_FOLDERS, two.node());
        swap = new Button(gui, "Swap sides").name(Landmarks.SWAP);
        swap.node().visible(false);
        gui.landmark(Landmarks.SWAP, swap.node());
        one = new Button(gui, "Back to one folder").name(Landmarks.ONE_FOLDER);
        one.node().visible(false);
        gui.landmark(Landmarks.ONE_FOLDER, one.node());
        Node where = gui.row().width(Length.grow(1f)).height(Length.FILL).alignItems(AlignItems.CENTER)
                .scroll(false, false).clip(true).children(crumbs.node(), path.node());
        Node top = gui.row()
                .width(Length.FILL).height(Type.TOP_BAR)
                .padding(Type.TIGHT, Type.SIDE)
                .gap(Type.TIGHT)
                .alignItems(AlignItems.CENTER)
                .background(Look.CHROME)
                .scroll(false, false)
                .clip(true)
                .children(where, two.node(), swap.node(), one.node());

        // It asks for its top as it is built; the view answers when ListPlaces does.
        tree = new TreeView<Spot>(gui, Landmarks.TREE).motion(motion.arrival);
        tree.node().width(Length.FILL).height(Length.FILL);
        gui.landmark(Landmarks.TREE, tree.node());
        // FROM always, and TO beside it in Two Folders, sharing the list's room equally; TO hidden takes none.
        from = new FolderList(gui, Landmarks.LIST, Landmarks.HEADER_FROM, "FROM");
        to = new FolderList(gui, Landmarks.LIST_TO, Landmarks.HEADER_TO, "TO");
        to.node().visible(false);
        Node lists = gui.row().width(Length.FILL).height(Length.FILL).scroll(false, false)
                .children(from.node(), to.node());

        // The rail is there whenever the list is, empty or not: one that appeared with its first suggestion would move
        // every row. It is a share of the room beside the tree rather than the handoff's 25 rem, which a split's
        // minimum cannot protect the list from in a narrow window (framework-notes FN-2).
        rail = new RailPanel(gui);
        // The dock under the list, the sized pane of their split so a taller window gives the list the room: the
        // handoff's screen 03. The rail stays beside both.
        dock = new DockPanel(gui);
        SplitPane listing = new SplitPane(gui, SplitPane.Orientation.STACKED, region(Look.LIST, lists),
                dock.node()).sized(SplitPane.Pane.SECOND).size(Type.DOCK).minFirst(Type.LIST_MIN_HEIGHT);
        SplitPane work = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, listing.node(), rail.node())
                .sized(SplitPane.Pane.SECOND).size(Type.RAIL)
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

    /** The list on {@code side}. */
    private FolderList list(Side side) {
        return side == Side.TO ? to : from;
    }

    /** Which list's table {@code address} is, or null: what a selection is said with. */
    Side listSide(String address) {
        return from.address().equals(address) ? Side.FROM : to.address().equals(address) ? Side.TO : null;
    }

    /** Which list's rows {@code address} is, or null: what a row activated, clicked or chosen is said with. */
    Side rowsSide(String address) {
        return from.rowsAddress().equals(address) ? Side.FROM : to.rowsAddress().equals(address) ? Side.TO : null;
    }

    /** Whether {@code address} is the top bar's Two folders, Swap sides, or Back to one folder. */
    boolean twoFolders(String address) {
        return two.address().equals(address);
    }

    boolean swap(String address) {
        return swap.address().equals(address);
    }

    boolean oneFolder(String address) {
        return one.address().equals(address);
    }

    // ------------------------------------------------------------------ the folders shown

    /** The folder the breadcrumb shows, or null for none yet: its last segment. */
    Path shown() {
        List<Path> chain = crumbs.path();
        return chain.isEmpty() ? null : chain.getLast();
    }

    /** How many rows the FROM list holds, for a test. */
    int rows() {
        return from.count();
    }

    /** What the explorer listed, in the list it is for. */
    void show(Shown s) {
        list(s.side()).show(s);
    }

    /**
     * Whether there are two lists and which is used: TO shown or hidden, each header in the accent while its list is
     * used, the top bar's buttons for the mode, and the breadcrumb and the field holding the used list's folder.
     */
    void pair(Pair p) {
        to.node().visible(p.two());
        from.header(p.two(), p.used() == Side.FROM);
        to.header(p.two(), p.used() == Side.TO);
        two.node().visible(!p.two());
        swap.node().visible(p.two());
        one.node().visible(p.two());
        if (p.at().isPresent()) {
            List<Path> chain = new ArrayList<>();
            for (Path f = p.at().get(); f != null; f = f.getParent()) {
                chain.addFirst(f);
            }
            crumbs.path(chain);
            path.text(p.at().get().toString());
        }
    }

    /**
     * The rows to tag new in the list on {@code side}: what landed lately, tagged rather than marked, since the rail's
     * reach is the list's one marked set (framework-notes FN-12).
     */
    void fresh(Side side, Set<Path> paths) {
        list(side).fresh(paths);
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

    /**
     * The status line's counts, in words: {@code 12 items · 2 selected · 4.2 MB}, and in Two Folders each side's,
     * {@code left 9 · right 6 · 2 selected}, the selection the used list's.
     */
    void counts(Summary s, Side used) {
        StringBuilder b = new StringBuilder();
        if (s.beside().isPresent()) {
            int left = used == Side.FROM ? s.items() : s.beside().get();
            int right = used == Side.FROM ? s.beside().get() : s.items();
            b.append("left ").append(left).append(" · right ").append(right);
        } else {
            b.append(s.items()).append(s.items() == 1 ? " item" : " items");
        }
        if (s.selected() > 0) {
            b.append(" · ").append(s.selected()).append(" selected");
            if (s.bytes() > 0) {
                b.append(" · ").append(Bytes.format(s.bytes()));
            }
        }
        status.text(Landmarks.STATUS_COUNTS, b.toString());
    }

    // ------------------------------------------------------------------ the Preview Dock

    /** What the dock shows: one file's preview, or a note. */
    void docked(Docked d) {
        dock.show(d);
    }

    // ------------------------------------------------------------------ the Suggestion Rail

    /**
     * What the rail suggests: its chips and card, and the rows its effect would reach marked in the list. Marked, never
     * selected, so ignoring the rail costs nothing. A row is a value, so the explorer's rows are the list's own.
     */
    void suggested(Suggested s, Side used) {
        rail.show(s);
        list(used).marks(s.reach());
        list(used.other()).marks(List.of());
    }

    /** Whether {@code address} is the card's button. */
    boolean go(String address) {
        return rail.go(address);
    }

    /** Whether {@code address} is the dock's Open in Rasterware. */
    boolean openIn(String address) {
        return dock.open(address);
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
    void closePath(Side used) {
        path.node().visible(false);
        crumbs.node().visible(true);
        gui.focus(list(used).rowsNode());
    }

    /** The path field's node, for the Esc that is claimed only while it has the keyboard. */
    Node pathNode() {
        return path.node();
    }

    /** The node of a list that takes the keyboard, for the Backspace that is claimed only while it has it. */
    Node rowsNode(Side side) {
        return list(side).rowsNode();
    }

    /** Say something on the status line, and when {@code alert}, draw the eye to it: something was refused or failed. */
    void status(String message, boolean alert) {
        status.text(Landmarks.STATUS_MESSAGE, message);
        if (alert) {
            motion.cues.play(status.node(), Cue.ring(danger, 2));
        }
    }
}
