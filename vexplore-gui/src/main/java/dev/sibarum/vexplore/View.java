package dev.sibarum.vexplore;

import dev.sibarum.vexplore.Intent.Command;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.sibarum.vexplore.core.Messages.ShowFolder;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.vexelray.framework.api.Subscribe;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.input.ClaimScope;
import dev.vexelray.gui.core.input.Shortcut;
import dev.vexelray.gui.widget.Breadcrumb;
import dev.vexelray.gui.widget.TextField;
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
 * <p>Every decision is the {@code Explorer}'s, in {@code -core}: what a typed path names, which folder to show. This
 * turns the explorer's messages into widgets ({@code ShowFolder} is the breadcrumb) and the person's gestures into
 * messages ({@code Go}).
 *
 * <h2>Everything it hears is a message, in the frame</h2>
 *
 * <p>Every {@code @Subscribe} here is drained on the GUI thread, after input is dispatched and before layout: what the
 * explorer sends, and what the widgets say they did (a breadcrumb segment clicked, a field submitted). So a click is
 * heard, and what the view writes about it drawn, in the frame the click arrived, and the widgets are only ever
 * touched here, with no lock. A widget's messages carry its address, and every widget of a kind shares a topic, so
 * each handler first asks which of the view's widgets a message is about.
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
    private final Sender<Intent> intents;

    View(Gui gui, Ui ui, Sender<Go> go, Sender<Intent> intents) {
        this.gui = gui;
        this.ui = ui;
        this.go = go;
        this.intents = intents;
        shortcuts();
    }

    /**
     * Every chord, as a declaration: the chord and the {@link Intent} it sends. Chords a text field leaves alone, so a
     * focused field never needs to outrank them, except Esc, which belongs to the path field only while it has the
     * keyboard. Zoom is here too: which chord zooms, or whether zooming exists at all, is the application's to say; how
     * far it goes is {@code Appearance.ZoomRange}, the framework's.
     */
    private void shortcuts() {
        chord(Shortcut.of(Key.L, Modifier.CONTROL), Command.EDIT_PATH);
        gui.claim(ui.pathNode(), Shortcut.of(Key.ESCAPE), ClaimScope.FOCUSED, intents, new Intent(Command.CLOSE_PATH));
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
    public void showFolder(ShowFolder m) {
        ui.show(m.path());
    }

    @Subscribe
    public void status(Status m) {
        ui.status(m.message(), m.alert());
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
            go.send(new Go(p.toString()));
        }
    }

    // ------------------------------------------------------------------ what the person did, by chord

    void editPath() {
        ui.editPath();
    }

    void closePath() {
        ui.closePath();
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
