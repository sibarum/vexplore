package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Folders;
import dev.sibarum.vexplore.suggest.Bytes;
import dev.sibarum.vexplore.suggest.Dates;
import dev.sibarum.vexplore.suggest.Order;
import dev.sibarum.vexplore.suggest.Suggestions;
import dev.sibarum.vexplore.suggest.Suggestions.Act;
import dev.sibarum.vexplore.suggest.Suggestions.Option;
import dev.sibarum.vexplore.suggest.Suggestions.Rail;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.krono.KronoGui;
import dev.vexelray.gui.widget.Breadcrumb;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.SplitPane;
import dev.vexelray.gui.widget.StatusBar;
import dev.vexelray.gui.widget.Table;
import dev.vexelray.gui.widget.TitleBar;
import dev.vexelray.gui.widget.TreeView;
import dev.vexelray.text.TextLayout.HAlign;
import dev.vexelray.text.TextLayout.VAlign;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The tree. Builds it, holds the handles the application needs afterwards, and owns nothing else.
 *
 * <h2>The layout is the design's, and its rules are the design's</h2>
 * Tree, list and rail are three fixed places, and nothing is ever drawn over another. That is a property of this
 * file rather than of any widget: there is no popup, no tooltip and no overlay here, because the Suggestion Rail
 * exists so that none is needed. A suggestion that arrives, changes or leaves rewrites the contents of the rail's
 * column and touches nothing else, so no row of the list ever moves because Vexplore had something to say.
 *
 * <h2>It holds no state of the application's</h2>
 * {@link #show} takes a whole {@link Doc} and writes everything derived from it. What it does hold is the last
 * thing it drew — {@code shown}, compared by identity — which is not state but a cache: it is what lets a change
 * to the selection leave the list's rows alone, and a change to the folder leave the rail's chips alone.
 *
 * <p>{@code show} runs on the committing thread, which is a worker. A prop written off the GUI thread is queued and
 * applied by the next drain, so that is correct here; what is not allowed is reading the model from the frame loop.
 */
final class Ui {

    private static final Length ROW = Length.rem(2.125f);
    private static final float ROW_REM = 2.125f;

    /** A place the tree can show: a folder, and the name to give it (a drive is {@code D:}, not empty). */
    record Place(Path path, String label) {
        static Place of(Path p) {
            Path n = p.getFileName();
            return new Place(p, n == null ? p.toString() : n.toString());
        }
    }

    private final Gui gui;
    private final Model model;
    private final Browser browser;
    private final Opener opener;
    private final TitleBar titleBar;

    private final Breadcrumb<Path> crumbs;
    private final Table<Entry> table;
    private final StatusBar status;
    private final Node sortNote;
    private final Dock dock;
    private final RailView rail;

    /** The last document drawn, so each region redraws only when its own inputs changed. */
    private Doc shown;

    /** Which rows the rail would reach. Read by the list to paint them, replaced when the rail changes. */
    private volatile Set<Path> targeted = Set.of();

    Ui(Gui gui, KronoGui krono, Model model, Browser browser, Opener opener, Actor actor, TitleBar titleBar) {
        this.gui = gui;
        this.model = model;
        this.browser = browser;
        this.opener = opener;
        this.titleBar = titleBar;

        Node tree = tree();
        this.crumbs = new Breadcrumb<Path>(gui, Ui::crumbLabel).font(Type.UI).onNavigate(browser::go);
        this.sortNote = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(gui.theme().color(Role.FAINT))
                .wordWrap(false);
        this.table = table();
        this.status = new StatusBar(gui).font(Type.MONO)
                .slot("items", StatusBar.Side.LEFT, "")
                .slot("selected", StatusBar.Side.LEFT, "")
                .slot("path", StatusBar.Side.RIGHT, "");
        gui.landmark(Landmarks.ITEMS, status.slot("items"));
        gui.landmark(Landmarks.SELECTED, status.slot("selected"));
        this.rail = new RailView(gui, model, actor);

        Node topBar = gui.row().width(Length.FILL).height(Length.rem(3f))
                .alignItems(AlignItems.CENTER)
                .padding(Length.ZERO, Length.rem(1.25f))
                .background(gui.theme().color(Look.CHROME))
                .children(crumbs.node(), gui.box().width(Length.grow(1f)).height(Length.FILL), sortNote);
        gui.landmark(Landmarks.TOP_BAR, topBar);

        Node treePane = gui.column().width(Length.FILL).height(Length.FILL)
                .background(gui.theme().color(Look.CHROME)).padding(Length.rem(0.6f)).children(tree);
        this.dock = new Dock(gui, opener);
        gui.landmark(Landmarks.DOCK, dock.node());
        SplitPane listAndDock = new SplitPane(gui, SplitPane.Orientation.STACKED, table.node(), dock.node())
                .sized(SplitPane.Pane.SECOND).size(Length.rem(15f))
                .minFirst(Length.rem(8f)).minSecond(Length.rem(6f));
        Node listPane = gui.column().width(Length.FILL).height(Length.FILL)
                .background(gui.theme().color(Look.LIST)).children(listAndDock.node());
        SplitPane split = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, treePane, listPane)
                .size(Length.rem(15.625f)).minFirst(Length.rem(9f)).minSecond(Length.rem(24f));
        gui.landmark(Landmarks.SPLIT, split.node());

        Node railPane = gui.column().width(Length.rem(25f)).height(Length.FILL)
                .background(gui.theme().color(Look.RAIL)).children(rail.node());
        gui.landmark(Landmarks.RAIL, railPane);
        Node railEdge = gui.box().width(Length.dp(1)).height(Length.FILL).background(gui.theme().color(Role.LINE));

        Node main = gui.row().width(Length.FILL).height(Length.grow(1f))
                .children(split.node(), railEdge, railPane);
        split.node().width(Length.grow(1f));

        status.node().width(Length.FILL).height(Length.rem(1.875f)).background(gui.theme().color(Look.CHROME));
        gui.landmark(Landmarks.STATUS, status.node());

        gui.root().direction(Direction.COLUMN)
                .background(gui.theme().color(Role.PAGE))
                .children(titleBar.node(), topBar, main, status.node());
    }

    // ------------------------------------------------------------------ the tree

    private Node tree() {
        TreeView<Place> tree = new TreeView<>(gui, new TreeView.Source<Place>() {
            @Override
            public List<Place> roots() {
                return places();
            }

            @Override
            public String label(Place item) {
                return item.label();
            }

            @Override
            public boolean hasChildren(Place item) {
                return true;
            }

            @Override
            public List<Place> children(Place item) {
                return Folders.subfolders(item.path()).stream().map(Place::of).toList();
            }
        });
        tree.onSelect(place -> {
            if (place != null) {
                browser.go(place.path());
            }
        });
        tree.node().width(Length.FILL).height(Length.FILL);
        gui.landmark(Landmarks.TREE, tree.node());
        return tree.node();
    }

    /** The user's own folders, then the drives: what a file browser opens onto. */
    private static List<Place> places() {
        List<Place> out = new ArrayList<>();
        Path home = Path.of(System.getProperty("user.home"));
        for (String name : List.of("Desktop", "Documents", "Downloads", "Music", "Pictures", "Videos")) {
            Path p = home.resolve(name);
            if (java.nio.file.Files.isDirectory(p)) {
                out.add(new Place(p, name));
            }
        }
        for (Path root : java.nio.file.FileSystems.getDefault().getRootDirectories()) {
            out.add(new Place(root, root.toString().replaceAll("[\\\\/]+$", "")));
        }
        return out;
    }

    // ------------------------------------------------------------------ the list

    private Table<Entry> table() {
        Comparator<Entry> byName = Comparator.comparing(e -> e.name().toLowerCase(java.util.Locale.ROOT));
        Table<Entry> t = new Table<>(gui, ROW_REM, List.of(
                Table.Column.of("NAME", Length.grow(1), (g, e) -> cell(g, e.name(), Type.UI, Type.BODY, Role.INK,
                        HAlign.LEFT), byName),
                Table.Column.of("TYPE", Length.rem(5f), (g, e) -> cell(g, e.typeLabel(), Type.MONO, Type.SMALL,
                        Role.DIM, HAlign.LEFT), Comparator.comparing(Entry::typeLabel)),
                Table.Column.of("SIZE", Length.rem(6.5f), (g, e) -> cell(g, e.folder() ? "" : Bytes.format(e.size()),
                        Type.UI, Type.BODY, Role.INK, HAlign.RIGHT), Comparator.comparingLong(Entry::size)),
                Table.Column.of("MODIFIED", Length.rem(9.5f), (g, e) -> cell(g,
                        Dates.column(e.modified(), Instant.now(), ZoneId.systemDefault()), Type.UI, Type.BODY,
                        Role.DIM, HAlign.LEFT), Comparator.comparing(Entry::modified))));
        t.node().width(Length.FILL).height(Length.FILL);
        t.headers(Type.MONO, Type.SMALL, Role.DIM);
        t.rows().looks(Look.SELECTED, Look.TARGETED).marked(e -> targeted.contains(e.path()));
        t.selection().onChange(sel -> model.select(paths(sel)));
        t.rows().onActivate(e -> {
            if (e.folder()) {
                browser.go(e.path());
            } else {
                opener.open(e.path());
            }
        });
        t.onSort((column, direction) -> {
            sortNote.text(sortWords(column, direction));
            model.sorted(column < 0 || direction == Table.Sort.NONE ? Order.LISTED
                    : new Order(column, direction == Table.Sort.DESCENDING));
        });
        gui.landmark(Landmarks.LIST, t.node());
        t.sortBy(3, Table.Sort.DESCENDING);
        return t;
    }

    private Node cell(Gui g, String text, int face, Length size, Role ink, HAlign align) {
        return g.text(text).font(face).textSize(size).textColor(g.theme().color(ink)).wordWrap(false)
                .height(Length.FILL).padding(Length.ZERO, Length.dp(10)).align(align, VAlign.MIDDLE);
    }

    private static Set<Path> paths(Set<Entry> entries) {
        java.util.HashSet<Path> out = new java.util.HashSet<>();
        for (Entry e : entries) {
            out.add(e.path());
        }
        return Set.copyOf(out);
    }

    private static String sortWords(int column, Table.Sort direction) {
        if (column < 0 || direction == Table.Sort.NONE) {
            return "";
        }
        String name = List.of("name", "type", "size", "modified").get(column);
        String order = switch (column) {
            case 2 -> direction == Table.Sort.ASCENDING ? "smallest first" : "largest first";
            case 3 -> direction == Table.Sort.ASCENDING ? "oldest first" : "newest first";
            default -> direction == Table.Sort.ASCENDING ? "A to Z" : "Z to A";
        };
        return "sorted by " + name + ", " + order;
    }

    private static String crumbLabel(Path p) {
        Path n = p.getFileName();
        return n == null ? p.toString().replaceAll("[\\\\/]+$", "") : n.toString();
    }

    // ------------------------------------------------------------------ writing a document

    /** Write everything derived from the document. One method taking the whole value. */
    synchronized void show(Doc doc) {
        Doc before = shown;
        shown = doc;

        if (before == null || before.folder() != doc.folder()) {
            if (doc.folder() != null) {
                crumbs.path(Folders.chain(doc.folder()));
                status.text("path", doc.folder().toString());
            }
        }
        if (before == null || before.entries() != doc.entries()) {
            table.items(doc.entries());
        }

        // The document is the truth about what is selected, and a selection can arrive from elsewhere (a session
        // restored, a script). Pushed to the list only when the document moved, and only if the list disagrees:
        // a change that began at the list finds it already agreeing, so the user's own click is never overwritten
        // by the version of itself that comes back round.
        if (before == null || !before.selected().equals(doc.selected())) {
            java.util.HashSet<Entry> want = new java.util.HashSet<>();
            for (Entry e : doc.entries()) {
                if (doc.selected().contains(e.path())) {
                    want.add(e);
                }
            }
            if (!table.selection().selection().equals(want)) {
                table.selection().set(want);
                // One file chosen from outside (another app's "show this file") may be far down a long folder.
                if (want.size() == 1) {
                    table.rows().reveal(want.iterator().next());
                }
            }
        }


        Suggestions.Rail railNow = doc.rail();
        if (rail.show(doc)) {
            targeted = railNow.effect().count() > 1 || doc.pick().act() != null
                    ? Suggestions.paths(railNow.effect()) : Set.of();
            table.rows().remark();
        }

        dock.show(doc.preview());

        // Last, so that a script awaiting the status line finds everything else already written.
        status.text("items", doc.loading() ? "reading..." : doc.entries().size() + " items");
        int reach = railNow.effect().count();
        status.text("selected", doc.selected().isEmpty() ? ""
                : reach > doc.selected().size()
                ? reach + " in scope · " + Bytes.format(railNow.effect().bytes())
                : doc.selected().size() + " selected · " + Bytes.format(doc.selectedBytes()));
    }

    /** The title bar this window uses, for a test to find. */
    TitleBar titleBar() {
        return titleBar;
    }
}
