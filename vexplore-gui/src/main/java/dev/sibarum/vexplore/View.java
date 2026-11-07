package dev.sibarum.vexplore;

import dev.sibarum.vexplore.Intent.Command;
import dev.sibarum.vexplore.core.Messages.Accept;
import dev.sibarum.vexplore.core.Messages.Activated;
import dev.sibarum.vexplore.core.Messages.Back;
import dev.sibarum.vexplore.core.Messages.Carry;
import dev.sibarum.vexplore.core.Messages.Chosen;
import dev.sibarum.vexplore.core.Messages.Compared;
import dev.sibarum.vexplore.core.Messages.Destine;
import dev.sibarum.vexplore.core.Messages.Conflict;
import dev.sibarum.vexplore.core.Messages.Docked;
import dev.sibarum.vexplore.core.Messages.Dropped;
import dev.sibarum.vexplore.core.Messages.Fresh;
import dev.sibarum.vexplore.core.Messages.ForgetMark;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.sibarum.vexplore.core.Messages.Marks;
import dev.sibarum.vexplore.core.Messages.Menu;
import dev.sibarum.vexplore.core.Messages.Navigate;
import dev.sibarum.vexplore.core.Messages.Naming;
import dev.sibarum.vexplore.core.Messages.OpenWith;
import dev.sibarum.vexplore.core.Messages.Ordered;
import dev.sibarum.vexplore.core.Messages.OpenWith.With;
import dev.sibarum.vexplore.core.Messages.Offers;
import dev.sibarum.vexplore.core.Messages.Resolve;
import dev.sibarum.vexplore.core.Messages.Pair;
import dev.sibarum.vexplore.core.Messages.Picked;
import dev.sibarum.vexplore.core.Messages.Places;
import dev.sibarum.vexplore.core.Messages.Place;
import dev.sibarum.vexplore.core.Messages.PlacesWanted;
import dev.sibarum.vexplore.core.Messages.RecallMark;
import dev.sibarum.vexplore.core.Messages.Recorded;
import dev.sibarum.vexplore.core.Messages.Redo;
import dev.sibarum.vexplore.core.Messages.Rename;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Shown;
import dev.sibarum.vexplore.core.Messages.Side;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.sibarum.vexplore.core.Messages.Stop;
import dev.sibarum.vexplore.core.Messages.Suggested;
import dev.sibarum.vexplore.core.Messages.Summary;
import dev.sibarum.vexplore.core.Messages.Swap;
import dev.sibarum.vexplore.core.Messages.TwoFolders;
import dev.sibarum.vexplore.core.Messages.Undo;
import dev.sibarum.vexplore.core.Messages.Use;
import dev.sibarum.vexplore.core.Plan;
import dev.sibarum.vexplore.core.Preview;
import dev.vexelray.framework.api.Subscribe;
import dev.vexelray.framework.core.PickFile;
import dev.vexelray.framework.core.WindowOpened;
import dev.vexelray.gui.core.Feed;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.edit.History;
import dev.vexelray.gui.core.input.ClaimScope;
import dev.vexelray.gui.core.input.Shortcut;
import dev.vexelray.gui.keymap.Keymap;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.widget.Breadcrumb;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.ListView;
import dev.vexelray.gui.widget.Table;
import dev.vexelray.gui.widget.TextField;
import sibarum.atchung.Sender;
import sibarum.tactroller.api.Key;
import sibarum.tactroller.api.Modifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The view: what the explorer decided, shown, and what the person did, told. A {@code @View}, so it runs on the GUI
 * thread, in each frame's widget tick (threading.md, T7.4).
 *
 * <h2>It decides nothing</h2>
 *
 * <p>Every decision is the {@code Explorer}'s, in {@code -core}: what a typed path names, which folder to show, what a
 * row activated does. This turns the explorer's messages into widgets ({@code Shown} is the breadcrumb and the list,
 * {@code Summary} the status line's counts, {@code Suggested} the rail) and the person's gestures into messages
 * ({@code Go}, {@code Navigate}, {@code Chosen}, {@code Activated}, {@code Picked}). The one thing it asks itself is
 * the places beside the list, which are the view's and its job's: it asks {@code ListPlaces} once, and a place
 * clicked is a {@code Navigate} like any other.
 *
 * <h2>Everything it hears is a message, in the frame</h2>
 *
 * <p>Every {@code @Subscribe} here is drained on the GUI thread, after input is dispatched and before layout: what the
 * explorer sends, what the jobs answer, and what the widgets say they did (a breadcrumb segment clicked, a field
 * submitted, a row selected). So a click is heard, and what the view writes about it drawn, in the frame the click
 * arrived, and the widgets are only ever touched here, with no lock. A widget's messages carry its address, and every
 * widget of a kind shares a topic, so each handler first asks which of the view's widgets a message is about.
 *
 * <h2>Gestures are declared, not handled</h2>
 *
 * <p>A chord and a menu item each name the message they send ({@link Intent}, a {@code Messages} record) through a
 * {@link Sender}; the dispatcher sends it, and nothing of the application's runs at the moment of the click (T7.5).
 * What needs the view comes back to it as one of these handlers.
 *
 * <h2>What it sends is in its constructor</h2>
 *
 * <p>One {@link Sender} for each message it sends, and nothing else to send with: the wiring finds each one's topic by
 * its payload, beside the record in {@code Messages}, so the processor sees every message the view can send without
 * reading a line of its body (ROADMAP fork 5).
 */
@dev.vexelray.framework.api.View
final class View {

    /**
     * What the view's folder picker is asked as, and answered by: the rail's destination, or a pane's own folder in Two
     * Folders. It asks for nothing else.
     */
    private static final long PICK_FOLDER = 1L;
    private static final long PICK_FROM = 2L;
    private static final long PICK_TO = 3L;

    private final Gui gui;
    private final Ui ui;

    private final Sender<Go> go;
    private final Sender<Navigate> navigate;
    private final Sender<Chosen> chosen;
    private final Sender<Ordered> ordered;
    private final Sender<Activated> activated;
    private final Sender<Picked> picked;
    private final Sender<Carry> carry;
    private final Sender<Stop> stop;
    private final Sender<Undo> undo;
    private final Sender<Redo> redo;
    private final Sender<PickFile> pickFile;
    private final Sender<Destine> destine;
    private final Sender<Rename> rename;
    private final Sender<RecallMark> recallMark;
    private final Sender<ForgetMark> forgetMark;
    private final Sender<OpenWith> openWith;
    private final Sender<Menu> menu;
    private final Sender<Use> use;
    private final Sender<TwoFolders> twoFolders;
    private final Sender<Swap> swap;
    private final Sender<PlacesWanted> placesWanted;
    private final Sender<Back> back;
    private final Sender<Dropped> dropped;
    private final Sender<Accept> accept;
    private final Sender<Resolve> resolve;
    private final Sender<Intent> intents;

    /** The dock's picture, and the one it replaced, which may still be fading out: the view's to give back. */
    private Feed shown;
    private Feed fading;

    /**
     * Whether there are two lists and which is used, as the explorer last said: which list the rail's reach is marked
     * in, and which side the counts are.
     */
    private Pair pair = new Pair(false, Side.FROM, Optional.empty(), false, Plan.Op.COPY);

    /**
     * The selection's rail and Two Folders' offers, as the explorer last said: between them, which rows are marked in
     * which list.
     */
    private Suggested suggested = Suggested.NONE;
    private Offers offers = Offers.NONE;

    /** The conflict the dock shows, as the explorer last said: the pair a press of its buttons carries. */
    private Conflict conflict = Conflict.NONE;

    View(Gui gui, Keymap keys, Ui ui, Sender<Go> go, Sender<Navigate> navigate, Sender<Chosen> chosen,
         Sender<Ordered> ordered, Sender<Activated> activated, Sender<Picked> picked, Sender<Carry> carry,
         Sender<Stop> stop, Sender<Undo> undo, Sender<Redo> redo,
         Sender<PickFile> pickFile, Sender<Destine> destine, Sender<Rename> rename,
         Sender<RecallMark> recallMark, Sender<ForgetMark> forgetMark, Sender<OpenWith> openWith, Sender<Menu> menu,
         Sender<Use> use, Sender<TwoFolders> twoFolders, Sender<Swap> swap,
         Sender<PlacesWanted> placesWanted, Sender<Back> back, Sender<Dropped> dropped, Sender<Accept> accept,
         Sender<Resolve> resolve, Sender<Intent> intents) {
        this.gui = gui;
        this.ui = ui;
        this.go = go;
        this.navigate = navigate;
        this.chosen = chosen;
        this.ordered = ordered;
        this.activated = activated;
        this.picked = picked;
        this.carry = carry;
        this.stop = stop;
        this.undo = undo;
        this.redo = redo;
        this.pickFile = pickFile;
        this.destine = destine;
        this.rename = rename;
        this.recallMark = recallMark;
        this.forgetMark = forgetMark;
        this.openWith = openWith;
        this.menu = menu;
        this.use = use;
        this.twoFolders = twoFolders;
        this.swap = swap;
        this.placesWanted = placesWanted;
        this.back = back;
        this.dropped = dropped;
        this.accept = accept;
        this.resolve = resolve;
        this.intents = intents;
        commands(keys);
        // Open with drops its menu on a left click, Enter or Space, and each line names the program.
        ui.openWith(dev.vexelray.gui.core.input.Menu.builder()
                .item(FileFamily.IMAGES, "Rasterware", openWith, new OpenWith(With.RASTERWARE))
                .item(Icon.DISPLAY, "The desktop's own program", openWith, new OpenWith(With.DESKTOP))
                .build());
    }

    /**
     * Every command the keyboard reaches across the window, as a declaration: a stable id, what the person calls it,
     * the chords it starts with, and the {@link Intent} it sends. The chords are the person's to change: the
     * framework's key bindings list these, and a rebind moves the claim without this view hearing of it. The id is
     * what the person's changes are stored under, so it is the part to keep stable. Chords a text field leaves alone,
     * so a focused field never needs to outrank them, except two that are claimed only where they mean something, and
     * stay the code's: Esc on the path field, and Backspace on the list, which in a field deletes a character. Zoom is
     * here too: which chord zooms, or whether zooming exists at all, is the application's to say; how far it goes is
     * {@code Appearance.ZoomRange}, the framework's.
     */
    private void commands(Keymap keys) {
        command(keys, "go.path", "Edit the path", Command.EDIT_PATH, Shortcut.of(Key.L, Modifier.CONTROL));
        gui.claim(ui.pathNode(), Shortcut.of(Key.ESCAPE), ClaimScope.FOCUSED, intents, new Intent(Command.CLOSE_PATH));
        command(keys, "go.up", "Up a folder", Command.UP, Shortcut.of(Key.UP, Modifier.ALT));
        command(keys, "go.back", "Back", Command.BACK, Shortcut.of(Key.LEFT, Modifier.ALT));
        for (Side side : Side.values()) {
            gui.claim(ui.rowsNode(side), Shortcut.of(Key.BACKSPACE), ClaimScope.FOCUSED, intents,
                    new Intent(Command.UP));
        }
        command(keys, "view.zoom-in", "Zoom in", Command.ZOOM_IN,
                Shortcut.of(Key.EQUAL, Modifier.CONTROL), Shortcut.of(Key.NUMPAD_ADD, Modifier.CONTROL));
        command(keys, "view.zoom-out", "Zoom out", Command.ZOOM_OUT,
                Shortcut.of(Key.MINUS, Modifier.CONTROL), Shortcut.of(Key.NUMPAD_SUBTRACT, Modifier.CONTROL));
        command(keys, "view.zoom-reset", "Reset zoom", Command.ZOOM_RESET,
                Shortcut.of(Key.DIGIT_0, Modifier.CONTROL), Shortcut.of(Key.NUMPAD_0, Modifier.CONTROL));
    }

    private void command(Keymap keys, String id, String label, Command command, Shortcut... chords) {
        keys.action(id, label, chords).sends(intents, new Intent(command));
    }

    @Subscribe
    public void intent(Intent intent) {
        intent.command().on(this);
    }

    // ------------------------------------------------------------------ what the explorer says

    @Subscribe
    public void shown(Shown m) {
        ui.show(m);
    }

    /** Whether there are two lists, which is used, and its folder for the breadcrumb. */
    @Subscribe
    public void pair(Pair m) {
        pair = m;
        ui.pair(m);
    }

    @Subscribe
    public void summary(Summary m) {
        ui.counts(m, pair.used());
    }

    @Subscribe
    public void status(Status m) {
        ui.status(m.message(), m.alert());
    }

    /** The rail: its chips and card, and the rows it would reach marked in the list. */
    @Subscribe
    public void suggested(Suggested m) {
        suggested = m;
        ui.suggested(m);
        ui.reach(pair.used(), suggested, offers);
    }

    /** Two Folders' offers: the tiles and cards, and what the first would reach while nothing is chosen. */
    @Subscribe
    public void offers(Offers m) {
        offers = m;
        ui.offers(m);
        ui.reach(pair.used(), suggested, offers);
    }

    /** Two Folders' conflict, shown in the dock in place of the preview. */
    @Subscribe
    public void conflict(Conflict m) {
        conflict = m;
        ui.conflict(m);
    }

    // ------------------------------------------------------------------ what the jobs answer

    /**
     * The main window is open: ask for the places, once, as the explorer asks for the session on the same message. Not
     * as the view is built, when nothing would hear the ask yet; and a test that builds the tree headless, which never
     * opens a window, asks for none.
     */
    @Subscribe
    public void opened(WindowOpened m) {
        if (m.main()) {
            placesWanted.send(new PlacesWanted());
        }
    }

    /** The places, for the sidebar. */
    @Subscribe
    public void places(Places m) {
        ui.places(m);
    }

    // ------------------------------------------------------------------ what the widgets say

    /** The path field was submitted: what the text names is the explorer's call, and the breadcrumb comes back. */
    @Subscribe
    public void fieldSubmitted(TextField.Submitted m) {
        if (m.field().equals(ui.pathAddress())) {
            go.send(new Go(m.text().strip()));
            ui.closePath(pair);
        } else if (m.field().equals(ui.nameAddress())) {
            rename.send(new Rename(m.text()));
        }
    }

    /** A breadcrumb segment: go to that folder. */
    @Subscribe
    public void crumbNavigated(Breadcrumb.Navigated m) {
        if (m.breadcrumb().equals(ui.crumbsAddress()) && m.item() instanceof Path p) {
            navigate.send(new Navigate(p));
        }
    }

    /** A list's selection, as paths, and which list: the explorer counts it. */
    @Subscribe
    public void listSelected(Table.Selected m) {
        Side side = ui.listSide(m.table());
        if (side != null) {
            chosen.send(new Chosen(side, Ui.paths(m.chosen())));
        }
    }

    /** A list sorted by a header click, or put back in the listing's order: the explorer reasons in what is shown. */
    @Subscribe
    public void listSorted(Table.Sorted m) {
        Side side = ui.listSide(m.table());
        if (side != null) {
            ordered.send(new Ordered(side, Columns.order(m.column(), m.direction())));
        }
    }

    /** Enter or a double click on a row: the explorer goes into a folder or opens a file. Enter on a place goes. */
    @Subscribe
    public void rowActivated(ListView.Activated m) {
        Side side = ui.rowsSide(m.list());
        if (side != null && m.item() instanceof Row r) {
            activated.send(new Activated(side, r.path()));
        } else if (m.list().equals(ui.placesAddress()) && m.item() instanceof Place p) {
            navigate.send(new Navigate(p.path()));
        }
    }

    /**
     * A click in a list: the explorer works from that one now, even when the click changed no selection. A click on a
     * place goes there, in the list used.
     */
    @Subscribe
    public void rowClicked(ListView.Clicked m) {
        Side side = ui.rowsSide(m.list());
        if (side != null) {
            use.send(new Use(side));
        } else if (m.list().equals(ui.placesAddress()) && m.item() instanceof Place p) {
            navigate.send(new Navigate(p.path()));
        }
    }

    /**
     * Rows of one list let go on the other: the explorer moves or copies them there, as the lists said while they were
     * carried. A list takes only the other's rows, so the list landed on is the one they go to.
     */
    @Subscribe
    public void landed(ListView.Landed m) {
        Side side = ui.rowsSide(m.list());
        if (side == null || !(m.held() instanceof List<?> held)) {
            return;
        }
        List<Path> paths = new ArrayList<>();
        for (Object o : held) {
            if (o instanceof Row r) {
                paths.add(r.path());
            }
        }
        // What the drop became: the move or copy declared, or the other if a key held at the release chose it.
        Plan.Op op = m.effect() == dev.vexelray.gui.core.drop.DropEffect.COPY ? Plan.Op.COPY : Plan.Op.MOVE;
        dropped.send(new Dropped(side, paths, op));
    }

    /**
     * A row menu's choice: about the selection, which the right click has already made and the explorer knows. It says
     * which list, since a right click on a row already selected changes no selection and so says nothing else.
     */
    @Subscribe
    public void rowMenu(ListView.Chosen m) {
        Side side = ui.rowsSide(m.at());
        if (side != null) {
            menu.send(new Menu(side, m.action()));
        }
    }

    /** A chip of the rail pressed: its row and place, for the explorer to resolve against what it offered. */
    @Subscribe
    public void chipToggled(Button.Toggled m) {
        Picked p = ui.picked(m);
        if (p != null) {
            picked.send(p);
        }
    }

    /**
     * The card's button carries out what it states; its Stop ends the run between two steps. A tile of the rail's Then
     * picks its action, as a chip did.
     */
    @Subscribe
    public void pressed(Button.Pressed m) {
        Picked tile = ui.tile(m.button());
        if (tile != null) {
            picked.send(tile);
        } else if (ui.accept(m.button()) != null) {
            accept.send(new Accept(ui.accept(m.button())));
        } else if (ui.keep(m.button()) != null) {
            if (conflict.twins().isPresent()) {
                resolve.send(new Resolve(conflict.twins().get(), ui.keep(m.button())));
            }
        } else if (ui.go(m.button())) {
            carry.send(new Carry());
        } else if (ui.stop(m.button())) {
            stop.send(new Stop());
        } else if (ui.back(m.button())) {
            back();
        } else if (ui.up(m.button())) {
            up();
        } else if (ui.twoFolders(m.button())) {
            twoFolders.send(new TwoFolders(true));
        } else if (ui.oneFolder(m.button())) {
            twoFolders.send(new TwoFolders(false));
        } else if (ui.swap(m.button())) {
            swap.send(new Swap());
        } else if (ui.choose(m.button())) {
            Path shown = ui.shown();
            pickFile.send(PickFile.folder(PICK_FOLDER, shown == null ? "" : shown.toString()));
        } else if (ui.paneBack(m.button()) != null) {
            back.send(new Back(Optional.of(ui.paneBack(m.button()))));
        } else if (ui.paneUp(m.button()) != null) {
            Side side = ui.paneUp(m.button());
            Path shown = ui.folder(side);
            if (shown != null && shown.getParent() != null) {
                navigate.send(new Navigate(Optional.of(side), shown.getParent()));
            }
        } else if (ui.paneChoose(m.button()) != null) {
            Side side = ui.paneChoose(m.button());
            Path shown = ui.folder(side);
            String at = shown == null ? "" : shown.toString();
            pickFile.send(PickFile.folder(side == Side.TO ? PICK_TO : PICK_FROM, at));
        } else if (ui.recall(m.button()) >= 0) {
            recallMark.send(new RecallMark(ui.recall(m.button())));
        } else if (ui.forget(m.button()) >= 0) {
            forgetMark.send(new ForgetMark(ui.forget(m.button())));
        }
    }

    /** What the dock shows. */
    @Subscribe
    public void docked(Docked m) {
        ui.docked(m);
        Feed feed = m.preview().flatMap(Preview::picture).orElse(null);
        if (feed != shown) {
            // The picture before last: the one just replaced is still crossfading out, so it is kept a change longer.
            if (fading != null && fading != feed) {
                fading.close();
            }
            fading = shown;
            shown = feed;
        }
    }

    /** What landed in the folder shown lately: tagged new in the list, never selected. */
    @Subscribe
    public void fresh(Fresh m) {
        ui.fresh(m.side(), m.paths());
    }

    /** The marks kept: the rail lists them. */
    @Subscribe
    public void marks(Marks m) {
        ui.marks(m.names());
    }

    /** Rename was picked: the card's field holds the file's name, once, to be typed over. */
    @Subscribe
    public void naming(Naming m) {
        ui.naming(m.name());
    }

    /**
     * The folder picker answered: for the rail, a folder picked is the destination; for a pane's Choose folder, it is
     * shown in that pane. A cancel changes nothing.
     */
    @Subscribe
    public void folderPicked(dev.vexelray.framework.core.Picked m) {
        if (m.isCancelled()) {
            return;
        }
        if (m.id() == PICK_FOLDER) {
            destine.send(new Destine(Path.of(m.path())));
        } else if (m.id() == PICK_FROM || m.id() == PICK_TO) {
            navigate.send(new Navigate(Optional.of(m.id() == PICK_TO ? Side.TO : Side.FROM), Path.of(m.path())));
        }
    }

    /** How each list's rows stand to the other's, in Two Folders: marked in that list by their group. */
    @Subscribe
    public void compared(Compared m) {
        ui.compared(m.side(), m.rows());
    }

    /**
     * A run landed that can be taken back: an entry in the window's history, so Ctrl+Z asks the explorer to take it
     * back. None in a tree built with no window, which has no history.
     */
    @Subscribe
    public void recorded(Recorded m) {
        History history = gui.history();
        if (history != null) {
            history.record(new Journaled(m.entry(), true, undo, redo));
        }
    }

    // ------------------------------------------------------------------ what the person did, by chord

    void editPath() {
        ui.editPath();
    }

    void closePath() {
        ui.closePath(pair);
    }

    /**
     * Backspace in the list, Alt+Up, or the top bar's Up: the folder this one is in. A drive's root has none, and
     * nothing happens.
     */
    void up() {
        Path shown = ui.shown();
        if (shown != null && shown.getParent() != null) {
            navigate.send(new Navigate(shown.getParent()));
        }
    }

    /** Alt+Left, or the top bar's Back: the folder shown before this one, which is the explorer's to know. */
    void back() {
        back.send(new Back());
    }

    /** In ({@code 1}), out ({@code -1}), or back to where it started ({@code 0}). */
    void zoom(int direction) {
        if (direction > 0) {
            gui.zoomIn();
        } else if (direction < 0) {
            gui.zoomOut();
        } else {
            gui.resetZoom();
        }
    }
}
