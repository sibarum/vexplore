package dev.sibarum.vexplore;

import dev.sibarum.suite.git.Status;
import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;
import dev.sibarum.vexplore.files.Folders;
import dev.sibarum.vexplore.files.Previews;
import dev.sibarum.vexplore.remote.Host;
import dev.sibarum.vexplore.remote.Remotes;
import dev.sibarum.vexplore.suggest.Bytes;
import dev.sibarum.vexplore.suggest.Order;
import dev.sibarum.vexplore.suggest.Suggestions;
import dev.sibarum.vexplore.suggest.Suggestions.Act;
import dev.sibarum.vexplore.suggest.Suggestions.Option;
import dev.sibarum.vexplore.suggest.Suggestions.Rail;
import dev.vexelray.codec.image.gui.Textures;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.input.MenuSink;
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
import dev.vexelray.gui.widget.Unclip;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The tree. Builds it, holds the handles the application needs afterwards, and owns nothing else.
 *
 * <h2>The layout is the design's, and its rules are the design's</h2>
 * Tree, list and rail are three fixed places, and nothing is ever drawn over another. That is a property of this
 * file rather than of any widget: there is no popup, no tooltip and no overlay here, because the Suggestion Rail
 * exists so that none is needed. The one exception is a clipped name, in the list or the tree, shown whole in place
 * while it is hovered ({@link Unclip}): it is already covered by whatever is beside it, and hovering only swaps which
 * one is covered. Nothing is ever hit there, so nothing under it stops being reachable.
 * A suggestion that arrives, changes or leaves rewrites the contents of the rail's
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
    /** What opening a row means, for a double-click and the menu alike. */
    private final Routes routes;
    private final TitleBar titleBar;
    /** The hosts the tree and the Open menu offer, and where New host remembers one. */
    private final Hosts hosts;
    /** The tree, refreshed when a host is added so it shows there at once. */
    private TreeView<Place> treeView;
    private final Viewer viewer;

    private final Breadcrumb<Path> crumbs;
    private final Table<Entry> table;
    private final StatusBar status;
    private final Node sortNote;
    private final Dock dock;
    private final RailView rail;
    private final Node railPane;
    private final Node railEdge;
    /** The second folder, in the rail's place while {@link #two}. */
    private final FolderPane other;
    private final Node twoEdge;
    /** The first folder's accent strip: shown only with two folders, accent while the tree's clicks go there. */
    private final Node firstStrip;
    private final Button twoButton;
    /** Whether the second folder is shown instead of the rail. */
    private volatile boolean two;
    /** Whether the tree's clicks go to the second folder. */
    private volatile boolean secondActive;
    /** Shows a list row's cut-off name whole while it is hovered: the window's reveal, which the tree's is too. */
    private final Unclip names;

    /** The last document drawn, so each region redraws only when its own inputs changed. */
    private Doc shown;

    /** What git said about the folder shown, or null. Read by the list's Git cells as they are built. */
    private volatile Status gitShown;

    /** Which rows the rail would reach. Read by the list to paint them, replaced when the rail changes. */
    private volatile Set<Path> targeted = Set.of();

    /**
     * True on the thread pushing the document's selection into the list. The list announces a selection it was
     * given exactly as one the user made, and that announcement can land after the document has moved on: a viewer
     * stepping quickly would have each step undone by the list repeating the one before it.
     */
    private final ThreadLocal<Boolean> following = ThreadLocal.withInitial(() -> false);

    Ui(Gui gui, KronoGui krono, Model model, Browser browser, Opener opener, Textures textures, Viewer viewer,
       Actor actor, TitleBar titleBar, Hosts hosts) {
        this.gui = gui;
        this.model = model;
        this.browser = browser;
        this.opener = opener;
        this.routes = Routes.standard(gui.offload(), opener, browser::go);
        this.titleBar = titleBar;
        this.hosts = hosts;
        this.viewer = viewer;
        this.names = Unclip.of(gui);   // the window's reveal: it fades as the framework says every reveal does

        Node tree = tree();
        this.crumbs = new Breadcrumb<Path>(gui, Ui::crumbLabel).font(Type.UI).onNavigate(p -> {
            activate(false);
            browser.go(p);
        });
        this.sortNote = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(gui.theme().color(Role.FAINT))
                .wordWrap(false);
        this.table = table();
        this.status = new StatusBar(gui).font(Type.MONO)
                .slot("items", StatusBar.Side.LEFT, "")
                .slot("selected", StatusBar.Side.LEFT, "")
                .slot("work", StatusBar.Side.RIGHT, "")
                .slot("branch", StatusBar.Side.RIGHT, "")
                .slot("path", StatusBar.Side.RIGHT, "");
        gui.landmark(Landmarks.ITEMS, status.slot("items"));
        gui.landmark(Landmarks.BRANCH, status.slot("branch"));
        gui.landmark(Landmarks.SELECTED, status.slot("selected"));
        gui.landmark(Landmarks.WORK, status.slot("work"));
        this.rail = new RailView(gui, model, actor, browser, hosts, this::connect);
        this.other = new FolderPane(gui, opener, actor, names, () -> activate(true));
        Drops.wire(gui, table.rows(), () -> model.doc().folder(), actor);

        this.twoButton = new Button(gui, "Two folders").kind(Button.Kind.GHOST).toggle(true)
                .onToggle(this::twoFolders);
        gui.landmark(Landmarks.TWO, twoButton.node());
        Node topBar = gui.row().width(Length.FILL).height(Length.rem(3f))
                .alignItems(AlignItems.CENTER).gap(Type.GAP)
                .padding(Length.ZERO, Length.rem(1.25f))
                .background(gui.theme().color(Look.CHROME))
                .children(crumbs.node(), gui.box().width(Length.grow(1f)).height(Length.FILL), sortNote,
                        twoButton.node());
        gui.landmark(Landmarks.TOP_BAR, topBar);

        Node treePane = gui.column().width(Length.FILL).height(Length.FILL)
                .background(gui.theme().color(Look.CHROME)).padding(Length.rem(0.6f)).children(tree);
        this.dock = new Dock(gui, krono, opener, textures, this::view);
        gui.landmark(Landmarks.DOCK, dock.node());
        SplitPane listAndDock = new SplitPane(gui, SplitPane.Orientation.STACKED, table.node(), dock.node())
                .sized(SplitPane.Pane.SECOND).size(Length.rem(15f))
                .minFirst(Length.rem(8f)).minSecond(Length.rem(6f));
        listAndDock.node().width(Length.FILL).height(Length.grow(1f));
        // With two folders, the first and the second are the same width (grow 1 each), and the one the tree's clicks
        // go to wears the accent strip at its top.
        this.firstStrip = gui.box().width(Length.FILL).height(Length.dp(2))
                .background(gui.theme().color(Role.ACCENT)).visible(false);
        Node first = gui.column().width(Length.grow(1f)).height(Length.FILL)
                .children(firstStrip, listAndDock.node());
        this.twoEdge = gui.box().width(Length.dp(1)).height(Length.FILL).background(gui.theme().color(Role.LINE))
                .visible(false);
        other.node().visible(false);
        Node listPane = gui.row().width(Length.FILL).height(Length.FILL)
                .background(gui.theme().color(Look.LIST)).children(first, twoEdge, other.node());
        SplitPane split = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, treePane, listPane)
                .size(Length.rem(15.625f)).minFirst(Length.rem(9f)).minSecond(Length.rem(24f));
        gui.landmark(Landmarks.SPLIT, split.node());

        this.railPane = gui.column().width(Length.rem(25f)).height(Length.FILL)
                .background(gui.theme().color(Look.RAIL)).children(rail.node());
        gui.landmark(Landmarks.RAIL, railPane);
        this.railEdge = gui.box().width(Length.dp(1)).height(Length.FILL)
                .background(gui.theme().color(Role.LINE));

        Node main = gui.row().width(Length.FILL).height(Length.grow(1f))
                .children(split.node(), railEdge, railPane);
        split.node().width(Length.grow(1f));

        status.node().width(Length.FILL).height(Length.rem(1.875f)).background(gui.theme().color(Look.CHROME));
        gui.landmark(Landmarks.STATUS, status.node());

        // Open sits in the bar's leading slot, after the title, where an application's own menu goes.
        titleBar.addLeading(new OpenMenu(gui, this::go, () -> model.doc().folder(), actor, opener, hosts,
                this::newHost).node());

        gui.root().direction(Direction.COLUMN)
                .background(gui.theme().color(Role.PAGE))
                .children(titleBar.node(), topBar, main, status.node());
    }

    // ------------------------------------------------------------------ two folders

    /**
     * Swap the Suggestion Rail for a second folder, or put the rail back. The second opens on the folder it last
     * showed, or the first time on the one the first shows. Nothing else moves: the rail's column and the second
     * folder are one place, and the first folder gives up width to make the two equal.
     */
    void twoFolders(boolean on) {
        if (two == on) {
            twoButton.show(on);
            return;
        }
        two = on;
        if (on && other.folder() == null && model.doc().folder() != null) {
            other.go(model.doc().folder());
        }
        railEdge.visible(!on);
        railPane.visible(!on);
        twoEdge.visible(on);
        other.node().visible(on);
        firstStrip.visible(on);
        twoButton.show(on);
        activate(false);
        status.text("work", on ? work(model.doc()) : "");
    }

    /**
     * New host, from the Open menu: the rail becomes the Connect panel. The rail is put back first if two folders
     * had taken its place, since that is where the panel is.
     */
    void newHost() {
        twoFolders(false);
        model.connecting(true);
    }

    /**
     * Connect to the host typed in the Connect panel and open its home folder, where the tree's clicks would. It is
     * remembered only if it answers; one that does not still opens, empty, with ssh's reason on the rail, as any
     * unreachable host does. A typing mistake is said on the panel and nothing is tried.
     */
    void connect(String typed) {
        Host host;
        try {
            host = Host.parse(typed);
        } catch (IllegalArgumentException e) {
            model.work(w -> w.noting("Not a host: " + e.getMessage() + "."));
            return;
        }
        Path home = Remotes.home(host);
        model.work(w -> w.running("Connecting to " + host.label() + "…"));
        gui.offload().execute(() -> {
            try {
                Remotes.absolute(home);   // connects, or says why not
                hosts.add(host);
                treeView.refresh();
            } catch (java.io.IOException e) {
                // Not remembered; the remote panel the listing lands on says why.
            }
            model.work(w -> w.running(""));
            go(home);
        });
    }

    /** Show {@code folder} as the second folder. */
    void showSecond(Path folder) {
        other.go(folder);
        twoFolders(true);
    }

    /** Which folder the tree's clicks go to: the second, or the first. Only ever the second while it is shown. */
    /** Open {@code folder} where the tree's clicks go: the second folder while it is the active one, else the first. */
    private void go(Path folder) {
        if (secondActive) {
            other.go(folder);
        } else {
            browser.go(folder);
        }
    }

    private void activate(boolean second) {
        secondActive = second && two;
        other.active(secondActive);
        firstStrip.background(gui.theme().color(secondActive ? Role.LINE : Role.ACCENT));
    }

    /** What is running, or how the last thing went: the rail's notice, on the status line while the rail is put away. */
    private static String work(Doc doc) {
        return doc.work().busy().isEmpty() ? doc.work().notice() : doc.work().busy();
    }

    /**
     * View the picture {@code file}: in Pix when it is installed, otherwise in Vexplore's own viewer, which shows the
     * selection and so selects the file first.
     */
    private void view(Path file) {
        if (Kind.of(file) == Kind.VIDEO) {
            // The viewer shows pictures; a video with no Pix to play it goes where the shell would send it.
            opener.view(file, () -> Opener.shell(file));
            return;
        }
        opener.view(file, () -> {
            model.select(Set.of(file));
            viewer.open();
        });
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

            /**
             * A host is not opened until it is connected: opening is a listing, and a tree that connected to every
             * host in the config to draw its chevrons (or to search, which walks) would be reaching out unasked.
             * Clicking a host connects, and it can be opened from then on.
             */
            @Override
            public boolean hasChildren(Place item) {
                Path p = item.path();
                return !Remotes.is(p) || p.isAbsolute() || Remotes.connected(p);
            }

            @Override
            public List<Place> children(Place item) {
                try {
                    return Folders.subfolders(Remotes.absolute(item.path())).stream().map(Place::of).toList();
                } catch (java.io.IOException e) {
                    return List.of();
                }
            }
        });
        this.treeView = tree;
        Remotes.onConnect(tree::refresh);
        tree.onSelect(place -> {
            if (place != null) {
                go(place.path());
            }
        });
        tree.node().width(Length.FILL).height(Length.FILL);
        tree.searchable(true);   // Ctrl+F walks the filesystem from the top
        gui.landmark(Landmarks.TREE, tree.node());
        return tree.node();
    }

    /**
     * The user's own folders, then the drives: what a file browser opens onto. Then the hosts named in
     * {@code ~/.ssh/config} and those added with New host, each opening on its home folder; naming one connects to
     * nothing.
     */
    private List<Place> places() {
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
        for (Host h : hosts.all()) {
            out.add(new Place(Remotes.home(h), h.label()));
        }
        return out;
    }

    // ------------------------------------------------------------------ the list

    private Table<Entry> table() {
        // Git is read from gitShown as each row is built, so only rows on screen ever ask, and the rows are rebuilt
        // when git answers (see show).
        Table<Entry> t = new Table<>(gui, Columns.ROW_REM, Columns.of(names, () -> gitShown));
        t.node().width(Length.FILL).height(Length.FILL);
        t.headers(Type.MONO, Type.SMALL, Role.DIM);
        t.rows().looks(Look.SELECTED, Look.TARGETED).marked(e -> targeted.contains(e.path()));
        t.selection().onChange(sel -> {
            if (!following.get()) {
                model.select(paths(sel));
            }
        });
        t.rows().onContextMenu(this::rowMenu);
        t.rows().onActivate(routes::open);
        t.rows().onRowClicked(e -> activate(false));
        t.onSort((column, direction) -> {
            sortNote.text(sortWords(column, direction));
            model.sorted(column < 0 || direction == Table.Sort.NONE ? Order.LISTED
                    : new Order(column, direction == Table.Sort.DESCENDING));
        });
        gui.landmark(Landmarks.LIST, t.node());
        t.sortBy(3, Table.Sort.DESCENDING);
        return t;
    }

    /**
     * A row's right-click menu. The list has already selected the row, unless it was part of a selection, in which
     * case the menu is about every selected row, in the order shown.
     *
     * <p><b>Opening is immediate; acting is staged.</b> Open does what a double-click does ({@link Routes}), named for
     * where it goes; View, Open in Pix (for a video), Open in Vex (for text) and Open with default app are the other
     * ways in. Move, Copy,
     * Archive, Delete and Mark put the files and the action on the Suggestion Rail, exactly as choosing the Action chip
     * would, so the summary card says what will happen ("9 files, 6.5 GB, nothing has moved yet") and its button does
     * it: an action shows its effect before it runs, whichever door it came in by. Hence the ellipsis on each.
     */
    private void rowMenu(Entry clicked, MenuSink menu) {
        Set<Entry> selected = table.selection().selection();
        List<Entry> on = new ArrayList<>();
        if (selected.contains(clicked)) {
            for (Entry e : model.doc().ordered()) {
                if (selected.contains(e)) {
                    on.add(e);
                }
            }
        }
        if (on.isEmpty()) {
            on.add(clicked);
        }
        Set<Path> paths = paths(Set.copyOf(on));
        boolean one = on.size() == 1;
        boolean remote = Remotes.is(clicked.path());
        if (one) {
            // Open is the route a double-click takes, named for where it goes; the rest are the other doors.
            Routes.Route way = routes.pick(clicked);
            menu.item(way.label(), () -> routes.open(clicked));
            if (!clicked.folder()) {
                if (clicked.kind() == Kind.IMAGE && !way.label().equals(Routes.IN_PIX)) {
                    menu.item("View", () -> view(clicked.path()));
                }
                // Said even when Pix is not installed, as Open in Vex is, so the way to play a video is visible.
                if (clicked.kind() == Kind.VIDEO && !way.label().equals(Routes.IN_PIX)) {
                    menu.item(Routes.IN_PIX, opener.pixInstalled(), () -> opener.play(clicked.path()));
                }
                // Only for text: a picture or a video in the editor is a screen of bytes.
                if (!way.label().equals(Routes.IN_VEX) && Previews.isText(clicked.path())) {
                    menu.item(Routes.IN_VEX, opener.editorInstalled(), () -> opener.edit(clicked.path()));
                }
                if (way != routes.fallback()) {
                    menu.item("Open with default app", () -> routes.openByDefault(clicked));
                }
            }
            if (!remote) {
                menu.item("Open MainFrame Here", opener.mainframeInstalled(),
                        () -> opener.mainframe(clicked.path(), clicked.folder()));
                menu.item("Open in Windows Explorer", () -> Opener.explorer(clicked.path(), clicked.folder()));
            }
            menu.separator();
        }
        if (remote) {
            // What the remote panel offers (RailView): download, rename, delete into the host's trash, mark.
            menu.item("Download to…", () -> stage(paths, Act.COPY));
            if (one) {
                menu.item("Rename…", () -> stage(paths, Act.RENAME));
            }
            menu.item("Delete…", () -> stage(paths, Act.DELETE));
            menu.item("Mark for later…", () -> stage(paths, Act.MARK));
            menu.separator();
            menu.item(one ? "Copy address" : "Copy " + on.size() + " addresses",
                    () -> gui.clipboard().set(join(on, e -> Remotes.address(e.path()))));
            menu.item(one ? "Copy name" : "Copy " + on.size() + " names",
                    () -> gui.clipboard().set(join(on, Entry::name)));
            return;
        }
        menu.item("Move to…", () -> stage(paths, Act.MOVE));
        if (one) {
            menu.item("Rename…", () -> stage(paths, Act.RENAME));
        }
        menu.item("Copy to…", () -> stage(paths, Act.COPY));
        menu.item("Archive…", () -> stage(paths, Act.ARCHIVE));
        menu.item("Delete…", () -> stage(paths, Act.DELETE));
        menu.item("Mark for later…", () -> stage(paths, Act.MARK));
        menu.separator();
        menu.item(one ? "Copy path" : "Copy " + on.size() + " paths",
                () -> gui.clipboard().set(join(on, e -> e.path().toString())));
        menu.item(one ? "Copy name" : "Copy " + on.size() + " names", () -> gui.clipboard().set(join(on, Entry::name)));
    }

    /** Put {@code paths} and {@code act} on the rail: those files selected, and nothing else chosen but the action. */
    private void stage(Set<Path> paths, Act act) {
        twoFolders(false);   // the rail is where a staged action is shown and done
        model.change(d -> {
            Doc s = d.selected().equals(paths) ? d : d.selecting(paths);
            return s.picking(Suggestions.Pick.NONE.with(act));
        });
    }

    /** One line each, for the clipboard. */
    private static String join(List<Entry> entries, java.util.function.Function<Entry, String> line) {
        StringBuilder out = new StringBuilder();
        for (Entry e : entries) {
            out.append(out.isEmpty() ? "" : System.lineSeparator()).append(line.apply(e));
        }
        return out.toString();
    }

    static Set<Path> paths(Set<Entry> entries) {
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

    static String crumbLabel(Path p) {
        Path n = p.getFileName();
        if (n == null && Remotes.is(p)) {
            return Remotes.host(p).label();   // a host's "/" would otherwise be a crumb with no words
        }
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
                status.text("path", Remotes.address(doc.folder()));
            }
        }
        // Git answers after the listing, so its arrival rebuilds the rows on screen with the same entries: the
        // selection survives (the entries are the same objects) and nothing moves, since the column's width is fixed.
        if (before == null || before.entries() != doc.entries() || before.git() != doc.git()) {
            gitShown = doc.git();
            table.items(doc.entries());
            status.text("branch", doc.git() == null ? "" : doc.git().branch().label());
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
                following.set(true);
                try {
                    table.selection().set(want);
                } finally {
                    following.set(false);
                }
                // Chosen from outside (another app's "show this file", files a drop put here): may be far down.
                revealFirst(table, doc, want);
            }
        }


        Suggestions.Rail railNow = doc.rail();
        if (rail.show(doc)) {
            targeted = railNow.effect().count() > 1 || doc.pick().act() != null
                    ? Suggestions.paths(railNow.effect()) : Set.of();
            table.rows().remark();
        }

        dock.show(doc.single(), doc.preview());

        if (two) {
            status.text("work", work(doc));
        }

        // Last, so that a script awaiting the status line finds everything else already written.
        status.text("items", doc.loading() ? "reading..." : doc.entries().size() + " items");
        int reach = railNow.effect().count();
        status.text("selected", doc.selected().isEmpty() ? ""
                : reach > doc.selected().size()
                ? reach + " in scope · " + Bytes.format(railNow.effect().bytes())
                : doc.selected().size() + " selected · " + Bytes.format(doc.selectedBytes()));
    }

    /** Scroll {@code table} to the first of {@code want} in the order shown, if it is not on screen. */
    static void revealFirst(Table<Entry> table, Doc doc, Set<Entry> want) {
        if (want.isEmpty()) {
            return;
        }
        for (Entry e : doc.ordered()) {
            if (want.contains(e)) {
                table.rows().reveal(e);
                return;
            }
        }
    }

    /** The title bar this window uses, for a test to find. */
    TitleBar titleBar() {
        return titleBar;
    }
}
