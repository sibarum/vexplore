package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Bytes;
import dev.sibarum.vexplore.core.Messages.Docked;
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
import dev.sibarum.vexplore.core.Plan;
import dev.sibarum.vexplore.core.Suggestions.Act;
import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.drop.DropEffect;
import dev.vexelray.gui.core.drop.PayloadType;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Paint;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.widget.Breadcrumb;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.Cue;
import dev.vexelray.gui.widget.IconButton;
import dev.vexelray.gui.widget.MenuAction;
import dev.vexelray.gui.widget.SplitPane;
import dev.vexelray.gui.widget.StatusBar;
import dev.vexelray.gui.widget.Table;
import dev.vexelray.gui.widget.TextField;
import dev.vexelray.gui.widget.TitleBar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The window, in Orbit's regions (rebuild.md, M8): the framework's title bar; the top bar, with Back and Up, the path
 * of the folder shown as a pill, and the view switch; the places and the marks ({@link Sidebar}) on the ground beside
 * the file list, the Preview Dock under it, and the Suggestion Rail ({@link RailPanel}) at its right; a status line
 * under all of it. docs/shape.md is the sketch.
 *
 * <h2>It holds no state</h2>
 *
 * <p>Every label here says what the explorer last told the view, and the view tells it ({@link #show},
 * {@link #counts}, {@link #status}). Nothing is remembered here to be shown later, which is the rule worth keeping as
 * this grows: a label that remembers a value can disagree with the session. The widgets keep their own (the list's
 * selection, the places shown), and say what changed as messages. {@link Tags} is one of those: the list's second
 * marked set, which the list cannot hold itself (framework-notes FN-12), one in each {@link FolderList}.
 *
 * <h2>The path field takes the breadcrumb's place</h2>
 *
 * <p>The top bar has a breadcrumb and no field. Ctrl+L puts a field in the same pill, holding the folder shown; Enter
 * goes, and Esc or Enter puts the breadcrumb back ({@link #editPath}, {@link #closePath}). Only one of the two is
 * shown at a time, and the bar never changes height.
 *
 * <p>Built in the {@code TREE} phase, before there is a window, and needing none: that is what lets a test build the
 * real tree headless. Written to from the view's lane, which is the framework's own idiom: a prop written off the GUI
 * thread is queued and applied by the next drain.
 *
 * <p>The title bar is the framework's, and stays a strip of its own where Orbit draws the window's buttons in the top
 * bar: chrome belongs to whoever owns the window, and an application puts no control of its own in it
 * ({@code WindowInstrument}'s ruling), so that the screenshot instrument means the same thing in every window on the
 * desk. This application places the node and supplies every colour in it through {@link Look}.
 */
final class Ui {

    /**
     * How many of the breadcrumb's segments show before the oldest fold into its ellipsis. A count, as the breadcrumb
     * wants: a deep folder's chain is cut at its far end, and the top bar never scrolls.
     */
    private static final int CRUMBS = 4;

    /** A round button of the top bar: Orbit's 40 px, its icon 18. */
    private static final Length ROUND = Length.dp(40);
    private static final Length ROUND_ICON = Length.dp(18);

    /** The rows of each list, as a drag carries them: two types, so each list takes only the other's. */
    private static final PayloadType<List<Row>> FROM_ROWS = PayloadType.of("vexplore.from-rows");
    private static final PayloadType<List<Row>> TO_ROWS = PayloadType.of("vexplore.to-rows");

    private final Gui gui;
    private final Breadcrumb<Path> crumbs;
    private final TextField path;
    private final IconButton back;
    private final IconButton up;
    private final IconButton one;
    private final IconButton two;
    private final Button swap;
    private final Sidebar sidebar;
    private final FolderList from;
    private final FolderList to;
    private final RailPanel rail;
    private final DockPanel dock;
    private final StatusBar status;
    private final Motion motion;
    private final Color danger;

    Ui(Gui gui, Motion motion, TitleBar titleBar) {
        this.gui = gui;
        this.motion = motion;
        this.danger = gui.theme().color(Role.DANGER);

        // Back and Up, round, as Orbit draws them: Back to the folder shown before, Up to the one above.
        back = round(gui, Icon.ARROW_LEFT, "Back", Landmarks.BACK).enabled(false);
        up = round(gui, Icon.ARROW_UP, "Up one folder", Landmarks.UP);
        crumbs = new Breadcrumb<Path>(gui, Ui::label).name(Landmarks.CRUMBS).maxSegments(CRUMBS);
        gui.landmark(Landmarks.CRUMBS, crumbs.node());
        path = new TextField(gui, "").name(Landmarks.PATH);
        path.node().width(Length.FILL).font(Type.MONO).textSize(Type.SMALL).visible(false);
        gui.landmark(Landmarks.PATH, path.node());
        // The path as a pill: the breadcrumb, or the field in its place.
        Node where = gui.row().width(Length.grow(1f)).height(ROUND).alignItems(AlignItems.CENTER)
                .padding(Length.ZERO, Length.dp(6)).corner(Length.dp(20)).background(Look.LIST)
                .border(Length.dp(1), Role.LINE).scroll(false, false).clip(true)
                .children(crumbs.node(), path.node());
        // The view switch: one folder or two, the one shown lit. The gallery joins it with M8's sixth slice.
        one = new IconButton(gui, VexploreIcon.LIST, "One folder").name(Landmarks.ONE_FOLDER).toggle(true).on(true)
                .size(Length.dp(40), Length.dp(17));
        gui.landmark(Landmarks.ONE_FOLDER, one.node());
        two = new IconButton(gui, VexploreIcon.COLUMNS, "Two folders").name(Landmarks.TWO_FOLDERS).toggle(true)
                .size(Length.dp(40), Length.dp(17));
        gui.landmark(Landmarks.TWO_FOLDERS, two.node());
        one.node().corner(Length.dp(16));
        two.node().corner(Length.dp(16));
        Node views = gui.row().alignItems(AlignItems.CENTER).gap(Length.dp(4)).padding(Length.dp(4))
                .corner(Length.dp(20)).background(Look.LIST).border(Length.dp(1), Role.LINE)
                .children(one.node(), two.node());
        Node top = gui.row()
                .width(Length.FILL).height(Type.TOP_BAR)
                .padding(Length.ZERO, Look.GAP)
                .gap(Look.GAP)
                .alignItems(AlignItems.CENTER)
                .background(Look.CHROME)
                .scroll(false, false)
                .clip(true)
                .children(gui.row().gap(Length.dp(6)).children(back.node(), up.node()), where, views);

        // The places and the marks, on the ground, where the tree was.
        sidebar = new Sidebar(gui);
        // FROM always, and TO beside it in Two Folders, sharing the list's room equally; TO hidden takes none. Swap
        // sides is TO's to offer, in its header.
        from = new FolderList(gui, Landmarks.LIST, Landmarks.HEADER_FROM, "FROM");
        to = new FolderList(gui, Landmarks.LIST_TO, Landmarks.HEADER_TO, "TO");
        to.node().visible(false);
        // Each list carries its rows as a type of its own and takes only the other's, so a list never offers to take what
        // came from itself. What a drop does is declared with each Pair, from the two folders' drives.
        from.carries(FROM_ROWS);
        to.carries(TO_ROWS);
        swap = new Button(gui, "Swap sides").kind(Button.Kind.GHOST).name(Landmarks.SWAP);
        gui.landmark(Landmarks.SWAP, swap.node());
        to.append(swap.node());
        Node lists = gui.row().width(Length.FILL).height(Length.FILL).scroll(false, false)
                .children(from.node(), to.node());

        // The rail is there whenever the list is, empty or not: one that appeared with its first suggestion would move
        // every row. It is a share of the room beside the places rather than the handoff's 25 rem, which a split's
        // minimum cannot protect the list from in a narrow window (framework-notes FN-2).
        rail = new RailPanel(gui);
        // The dock under the list, the sized pane of their split so a taller window gives the list the room. The rail
        // stays beside both.
        dock = new DockPanel(gui);
        // Orbit's panels: the list, the dock and the rail each a rounded panel on the ground, the gaps between them the
        // splits' gutters, so the gap is what a pointer drags. The places sit on the ground itself.
        Node listPanel = region(Look.LIST, lists).corner(Look.PANEL_CORNER).clip(true);
        SplitPane listing = new SplitPane(gui, SplitPane.Orientation.STACKED, listPanel,
                dock.node()).sized(SplitPane.Pane.SECOND).size(Type.DOCK).minFirst(Type.LIST_MIN_HEIGHT)
                .gutter(Look.GAP).line(Look.SEAM);
        SplitPane work = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, listing.node(), rail.node())
                .sized(SplitPane.Pane.SECOND).size(Type.RAIL)
                .minFirst(Type.LIST_MIN).minSecond(Type.RAIL_MIN)
                .gutter(Look.GAP).line(Look.SEAM);
        SplitPane body = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, region(Look.CHROME, sidebar.node()),
                work.node()).size(Type.SIDEBAR).gutter(Look.GAP).line(Look.SEAM);
        body.node().width(Length.FILL).height(Length.grow(1f));
        Node ground = gui.column().width(Length.FILL).height(Length.grow(1f)).padding(Length.ZERO, Look.GAP)
                .children(body.node());

        status = new StatusBar(gui)
                .slot(Landmarks.STATUS_COUNTS, StatusBar.Side.LEFT, "")
                .slot(Landmarks.STATUS_MESSAGE, StatusBar.Side.RIGHT, "");
        gui.landmark(Landmarks.STATUS, status.node());
        gui.landmark(Landmarks.STATUS_COUNTS, status.slot(Landmarks.STATUS_COUNTS));
        gui.landmark(Landmarks.STATUS_MESSAGE, status.slot(Landmarks.STATUS_MESSAGE));

        gui.root().direction(Direction.COLUMN)
                .background(gui.theme().color(Role.PAGE))
                .children(titleBar.node(), top, ground, status.node());
    }

    /** A round button of the top bar, bordered on a panel's fill as Orbit draws it, named for scripts. */
    private static IconButton round(Gui gui, Icon icon, String name, String landmark) {
        IconButton b = new IconButton(gui, icon, name).name(landmark).size(ROUND, ROUND_ICON);
        b.node().corner(Length.dp(20)).border(Length.dp(1), Role.LINE).background(Look.LIST);
        gui.landmark(landmark, b.node());
        return b;
    }

    /** A region of the body: a column of its token's colour, holding its one widget. */
    private Node region(Paint colour, Node content) {
        return gui.column().width(Length.FILL).height(Length.FILL).background(colour).children(content);
    }

    /** A segment's words: the folder's own name, or a drive's root as it is written ({@code C:\}). */
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

    /** Whether {@code address} is the top bar's Back, Up, One folder or Two folders, or TO's Swap sides. */
    boolean back(String address) {
        return back.address().equals(address);
    }

    boolean up(String address) {
        return up.address().equals(address);
    }

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
     * used, the view switch lit for the mode, Back for whether there is a folder to go back to, the place filled, and
     * the breadcrumb and the field holding the used list's folder.
     */
    void pair(Pair p) {
        to.node().visible(p.two());
        DropEffect effect = p.drop() == Plan.Op.MOVE ? DropEffect.MOVE : DropEffect.COPY;
        from.takes(TO_ROWS, effect);
        to.takes(FROM_ROWS, effect);
        from.side(p.two(), p.used() == Side.FROM);
        to.side(p.two(), p.used() == Side.TO);
        one.on(!p.two());
        two.on(p.two());
        back.enabled(p.back());
        sidebar.at(p.at());
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

    /** Whether {@code address} is the preview strip's Open with. */
    boolean openWith(String address) {
        return dock.openWith(address);
    }

    /** Open with's node, for the menu the view declares on it. */
    Node openWithNode() {
        return dock.openWithNode();
    }

    /** Whether {@code address} is the card's Stop. */
    boolean stop(String address) {
        return rail.stop(address);
    }

    /** The marks kept, by name. */
    void marks(java.util.List<String> names) {
        sidebar.marks(names);
    }

    /** Which mark {@code address} recalls, or -1. */
    int recall(String address) {
        return sidebar.recall(address);
    }

    /** Which mark {@code address} forgets, or -1. */
    int forget(String address) {
        return sidebar.forget(address);
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

    /** The pick a Then tile's press stands for, or null when it is none of the rail's tiles or not offered. */
    Picked tile(String address) {
        return rail.picked(address);
    }

    // ------------------------------------------------------------------ the places

    /** The places' rows', which a place clicked or entered is said with. */
    String placesAddress() {
        return sidebar.placesAddress();
    }

    /** The places, as {@code ListPlaces} found them. */
    void places(Places p) {
        sidebar.places(p.places());
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
