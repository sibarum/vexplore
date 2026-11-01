package dev.sibarum.vexplore;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.widget.Cue;
import dev.vexelray.gui.widget.SplitPane;
import dev.vexelray.gui.widget.StatusBar;
import dev.vexelray.gui.widget.TitleBar;

/**
 * The window: the framework's title bar, the navigator beside an empty pane, and a status line under both. The pane is
 * where the file list, the Suggestion Rail and the Preview Dock come (docs/rebuild.md, M0).
 *
 * <h2>It holds no state</h2>
 *
 * <p>Every label here says what the editor last told the view, and the view tells it ({@link #status}). Nothing is
 * remembered here to be shown later, which is the rule worth keeping as this grows: a label that remembers a value can
 * disagree with the session.
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

    /** The navigator's width on a first run. Type-relative, so it grows with the zoom like the text in it. */
    private static final Length NAVIGATOR = Length.rem(16);

    private final StatusBar status;
    private final Navigator navigator;
    private final Motion motion;
    private final Color danger;

    Ui(Gui gui, Motion motion, TitleBar titleBar) {
        this.motion = motion;
        this.danger = gui.theme().color(Role.DANGER);

        status = new StatusBar(gui).slot(Landmarks.STATUS_MESSAGE, StatusBar.Side.LEFT, "");
        gui.landmark(Landmarks.STATUS, status.node());
        gui.landmark(Landmarks.STATUS_MESSAGE, status.slot(Landmarks.STATUS_MESSAGE));

        navigator = new Navigator(gui, motion);
        Node pane = gui.column().width(Length.FILL).height(Length.FILL);

        SplitPane split = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, navigator.node(), pane)
                .size(NAVIGATOR);
        split.node().width(Length.FILL).height(Length.grow(1f));

        gui.root().direction(Direction.COLUMN)
                .background(gui.theme().color(Role.PAGE))
                .children(titleBar.node(), split.node(), status.node());
    }

    Navigator navigator() {
        return navigator;
    }

    /** Say something on the status line, and when {@code alert}, draw the eye to it: something was refused or failed. */
    void status(String message, boolean alert) {
        status.text(Landmarks.STATUS_MESSAGE, message);
        if (alert) {
            motion.cues.play(status.node(), Cue.ring(danger, 2));
        }
    }
}
