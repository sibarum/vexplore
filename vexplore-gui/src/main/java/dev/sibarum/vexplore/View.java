package dev.sibarum.vexplore;

import dev.sibarum.vexplore.Intent.Command;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.sibarum.vexplore.core.Messages.Listed;
import dev.sibarum.vexplore.core.Messages.Listing;
import dev.sibarum.vexplore.core.Messages.ShowFolder;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.vexelray.framework.api.Subscribe;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.input.Shortcut;
import dev.vexelray.gui.widget.TextField;
import dev.vexelray.gui.widget.TreeView;
import sibarum.atchung.Sender;
import sibarum.tactroller.api.Key;
import sibarum.tactroller.api.Modifier;

import java.nio.file.Path;

/**
 * The view: what the editor decided, shown, and what the person did, told. A {@code @View}, so it runs on the GUI
 * thread, in each frame's widget tick (threading.md, T7.4).
 *
 * <h2>It decides nothing</h2>
 *
 * <p>Every decision is the {@code Editor}'s, in {@code -core}: what a typed path names, which folder to show. This turns
 * the editor's messages into widgets ({@code ShowFolder} is the navigator's root) and the person's gestures into
 * messages ({@code Go}).
 *
 * <h2>Everything it hears is a message, in the frame</h2>
 *
 * <p>Every {@code @Subscribe} here is drained on the GUI thread, after input is dispatched and before layout: what the
 * editor and the jobs send, and what the widgets say they did (a tree's row opened or its menu chosen, a field
 * submitted). So a click is heard, and what the view writes about it drawn, in the frame the click arrived, and the
 * widgets and the {@link Navigator} are only ever touched here, with no lock. A widget's messages carry its address,
 * and every widget of a kind shares a topic, so each handler first asks which of the view's widgets a message is about.
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
 *
 * <h2>Slow work is a job</h2>
 *
 * <p>Reading a folder for the tree is {@code ListFolder}'s: the view asks and acts on the answer, because its time is
 * the frame's.
 */
@dev.vexelray.framework.api.View
final class View {

    private final Gui gui;
    private final Ui ui;
    private final Navigator nav;

    private final Sender<Go> go;
    private final Sender<Listing> listing;
    private final Sender<TreeView.Children> children;
    private final Sender<Intent> intents;

    View(Gui gui, Ui ui, Sender<Go> go, Sender<Listing> listing, Sender<TreeView.Children> children,
         Sender<Intent> intents) {
        this.gui = gui;
        this.ui = ui;
        this.nav = ui.navigator();
        this.go = go;
        this.listing = listing;
        this.children = children;
        this.intents = intents;
        shortcuts();
    }

    /**
     * Every chord, as a declaration: the chord and the {@link Intent} it sends. Chords a text field leaves alone, so a
     * focused field never needs to outrank them. Zoom is here too: which chord zooms, or whether zooming exists at all,
     * is the application's to say; how far it goes is {@code Appearance.ZoomRange}, the framework's.
     */
    private void shortcuts() {
        chord(Shortcut.of(Key.L, Modifier.CONTROL), Command.FOCUS_PATH);
        chord(Shortcut.of(Key.E, Modifier.CONTROL, Modifier.SHIFT), Command.FOCUS_TREE);
        chord(Shortcut.of(Key.EQUAL, Modifier.CONTROL), Command.ZOOM_IN);
        chord(Shortcut.of(Key.MINUS, Modifier.CONTROL), Command.ZOOM_OUT);
        chord(Shortcut.of(Key.DIGIT_0, Modifier.CONTROL), Command.ZOOM_RESET);
        chord(Shortcut.of(Key.NUMPAD_ADD, Modifier.CONTROL), Command.ZOOM_IN);
        chord(Shortcut.of(Key.NUMPAD_SUBTRACT, Modifier.CONTROL), Command.ZOOM_OUT);
        chord(Shortcut.of(Key.NUMPAD_0, Modifier.CONTROL), Command.ZOOM_RESET);
    }

    private void chord(Shortcut shortcut, Command command) {
        gui.shortcut(shortcut, intents, new Intent(command));
    }

    @Subscribe
    public void intent(Intent intent) {
        intent.command().on(this);
    }

    // ------------------------------------------------------------------ what the editor says

    @Subscribe
    public void showFolder(ShowFolder m) {
        nav.show(m.path());
    }

    @Subscribe
    public void status(Status m) {
        ui.status(m.message(), m.alert());
    }

    // ------------------------------------------------------------------ what the jobs answer

    @Subscribe
    public void listed(Listed m) {
        if (m.tree().equals(nav.treeAddress())) {
            children.send(nav.children(m));
        }
    }

    // ------------------------------------------------------------------ what the widgets say

    /** The path bar was submitted: what the text names is the editor's call. */
    @Subscribe
    public void fieldSubmitted(TextField.Submitted m) {
        if (m.field().equals(nav.pathAddress())) {
            go.send(new Go(m.text().strip()));
        }
    }

    /** The tree wants a level: ask {@code ListFolder}, or answer an empty top when no folder is shown yet. */
    @Subscribe
    public void treeWanted(TreeView.ChildrenWanted m) {
        if (!m.tree().equals(nav.treeAddress())) {
            return;
        }
        Listing wanted = nav.wanted(m.parent());
        if (wanted != null) {
            listing.send(wanted);
        } else {
            children.send(nav.nothing());
        }
    }

    /** Enter on a folder opens it in place. */
    @Subscribe
    public void treeActivated(TreeView.Activated m) {
        if (m.tree().equals(nav.treeAddress()) && m.item() instanceof Path p && nav.isFolder(p)) {
            nav.expand(p);
        }
    }

    /** One of a row's menu actions. */
    @Subscribe
    public void treeChosen(TreeView.Chosen m) {
        if (!m.at().equals(nav.treeAddress()) || !(m.item() instanceof Path p)) {
            return;
        }
        if (m.action().equals(Navigator.SHOW)) {
            go.send(new Go(p.toString()));
        } else if (m.action().equals(Navigator.COPY_PATH)) {
            gui.clipboard().set(p.toString());
        }
    }

    // ------------------------------------------------------------------ what the person did, by chord

    void focusPath() {
        nav.focusPath();
    }

    void focusTree() {
        nav.focusTree();
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
