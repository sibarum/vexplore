package dev.sibarum.vexplore;

import dev.sibarum.vexplore.Intent.Command;
import dev.sibarum.vexplore.core.Messages.Activated;
import dev.sibarum.vexplore.core.Messages.Chosen;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.sibarum.vexplore.core.Messages.Level;
import dev.sibarum.vexplore.core.Messages.LevelWanted;
import dev.sibarum.vexplore.core.Messages.Navigate;
import dev.sibarum.vexplore.core.Messages.Picked;
import dev.sibarum.vexplore.core.Messages.Places;
import dev.sibarum.vexplore.core.Messages.PlacesWanted;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Shown;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.sibarum.vexplore.core.Messages.Suggested;
import dev.sibarum.vexplore.core.Messages.Summary;
import dev.vexelray.framework.api.Subscribe;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.input.ClaimScope;
import dev.vexelray.gui.core.input.Shortcut;
import dev.vexelray.gui.widget.Breadcrumb;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.ListView;
import dev.vexelray.gui.widget.Table;
import dev.vexelray.gui.widget.TextField;
import dev.vexelray.gui.widget.TreeView;
import sibarum.atchung.Sender;
import sibarum.tactroller.api.Key;
import sibarum.tactroller.api.Modifier;

import java.nio.file.Path;

/**
 * The view: what the explorer decided, shown, and what the person did, told. A {@code @View}, so it runs on the GUI
 * thread, in each frame's widget tick (threading.md, T7.4).
 *
 * <h2>It decides nothing</h2>
 *
 * <p>Every decision is the {@code Explorer}'s, in {@code -core}: what a typed path names, which folder to show, what a
 * row activated does. This turns the explorer's messages into widgets ({@code Shown} is the breadcrumb and the list,
 * {@code Summary} the status line's counts, {@code Suggested} the rail) and the person's gestures into messages
 * ({@code Go}, {@code Navigate}, {@code Chosen}, {@code Activated}, {@code Picked}). The one thing it answers itself
 * is the tree of places, which is the view's and its jobs': the tree asks, the view asks {@code ListPlaces} or {@code ListLevel}, and the answer goes back to the tree.
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

    private final Gui gui;
    private final Ui ui;

    private final Sender<Go> go;
    private final Sender<Navigate> navigate;
    private final Sender<Chosen> chosen;
    private final Sender<Activated> activated;
    private final Sender<Picked> picked;
    private final Sender<PlacesWanted> placesWanted;
    private final Sender<LevelWanted> levelWanted;
    private final Sender<TreeView.Children> children;
    private final Sender<Intent> intents;

    View(Gui gui, Ui ui, Sender<Go> go, Sender<Navigate> navigate, Sender<Chosen> chosen, Sender<Activated> activated,
         Sender<Picked> picked, Sender<PlacesWanted> placesWanted, Sender<LevelWanted> levelWanted,
         Sender<TreeView.Children> children, Sender<Intent> intents) {
        this.gui = gui;
        this.ui = ui;
        this.go = go;
        this.navigate = navigate;
        this.chosen = chosen;
        this.activated = activated;
        this.picked = picked;
        this.placesWanted = placesWanted;
        this.levelWanted = levelWanted;
        this.children = children;
        this.intents = intents;
        shortcuts();
    }

    /**
     * Every chord, as a declaration: the chord and the {@link Intent} it sends. Chords a text field leaves alone, so a
     * focused field never needs to outrank them, except two that are claimed only where they mean something: Esc on the
     * path field, and Backspace on the list, which in a field deletes a character. Zoom is here too: which chord zooms,
     * or whether zooming exists at all, is the application's to say; how far it goes is {@code Appearance.ZoomRange},
     * the framework's.
     */
    private void shortcuts() {
        chord(Shortcut.of(Key.L, Modifier.CONTROL), Command.EDIT_PATH);
        gui.claim(ui.pathNode(), Shortcut.of(Key.ESCAPE), ClaimScope.FOCUSED, intents, new Intent(Command.CLOSE_PATH));
        chord(Shortcut.of(Key.UP, Modifier.ALT), Command.UP);
        gui.claim(ui.rowsNode(), Shortcut.of(Key.BACKSPACE), ClaimScope.FOCUSED, intents, new Intent(Command.UP));
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

    // ------------------------------------------------------------------ what the explorer says

    @Subscribe
    public void shown(Shown m) {
        ui.show(m);
    }

    @Subscribe
    public void summary(Summary m) {
        ui.counts(m);
    }

    @Subscribe
    public void status(Status m) {
        ui.status(m.message(), m.alert());
    }

    /** The rail: its chips and card, and the rows it would reach marked in the list. */
    @Subscribe
    public void suggested(Suggested m) {
        ui.suggested(m);
    }

    // ------------------------------------------------------------------ what the jobs answer

    @Subscribe
    public void places(Places m) {
        children.send(ui.places(m));
    }

    @Subscribe
    public void level(Level m) {
        children.send(ui.level(m));
    }

    // ------------------------------------------------------------------ what the widgets say

    /** The path field was submitted: what the text names is the explorer's call, and the breadcrumb comes back. */
    @Subscribe
    public void fieldSubmitted(TextField.Submitted m) {
        if (m.field().equals(ui.pathAddress())) {
            go.send(new Go(m.text().strip()));
            ui.closePath();
        }
    }

    /** A breadcrumb segment: go to that folder. */
    @Subscribe
    public void crumbNavigated(Breadcrumb.Navigated m) {
        if (m.breadcrumb().equals(ui.crumbsAddress()) && m.item() instanceof Path p) {
            navigate.send(new Navigate(p));
        }
    }

    /** The tree wants a level: its top is the places, and below that a folder's subfolders. */
    @Subscribe
    public void treeWanted(TreeView.ChildrenWanted m) {
        if (!m.tree().equals(ui.treeAddress())) {
            return;
        }
        LevelWanted wanted = Ui.wanted(m.parent());
        if (wanted == null) {
            placesWanted.send(new PlacesWanted());
        } else {
            levelWanted.send(wanted);
        }
    }

    /** A row of the tree, clicked or walked to: show that folder, and keep the keyboard in the tree for the walk. */
    @Subscribe
    public void treeSelected(TreeView.Selected m) {
        if (m.tree().equals(ui.treeAddress()) && m.item() instanceof Ui.Spot s) {
            navigate.send(new Navigate(s.folder()));
        }
    }

    /** The list's selection, as paths: the explorer counts it. */
    @Subscribe
    public void listSelected(Table.Selected m) {
        if (m.table().equals(ui.listAddress())) {
            chosen.send(new Chosen(Ui.paths(m.chosen())));
        }
    }

    /** Enter or a double click on a row: the explorer goes into a folder or opens a file. */
    @Subscribe
    public void rowActivated(ListView.Activated m) {
        if (m.list().equals(ui.rowsAddress()) && m.item() instanceof Row r) {
            activated.send(new Activated(r.path()));
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

    // ------------------------------------------------------------------ what the person did, by chord

    void editPath() {
        ui.editPath();
    }

    void closePath() {
        ui.closePath();
    }

    /** Backspace in the list, or Alt+Up: the folder this one is in. A drive's root has none, and nothing happens. */
    void up() {
        Path shown = ui.shown();
        if (shown != null && shown.getParent() != null) {
            navigate.send(new Navigate(shown.getParent()));
        }
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
