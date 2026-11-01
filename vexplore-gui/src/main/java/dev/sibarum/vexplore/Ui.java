package dev.sibarum.vexplore;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Paint;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.widget.Breadcrumb;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.Cue;
import dev.vexelray.gui.widget.SplitPane;
import dev.vexelray.gui.widget.StatusBar;
import dev.vexelray.gui.widget.TextField;
import dev.vexelray.gui.widget.TitleBar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The window, in the handoff's regions: the framework's title bar; the top bar, a breadcrumb of the folder shown; the
 * tree of places beside the file list, and the Suggestion Rail at the right; a status line under all of it.
 * docs/shape.md is the sketch. The tree, the list and the rail are empty until M1 and M2 fill them; the Preview Dock
 * comes under the list with M4.
 *
 * <h2>It holds no state</h2>
 *
 * <p>Every label here says what the explorer last told the view, and the view tells it ({@link #show},
 * {@link #status}). Nothing is remembered here to be shown later, which is the rule worth keeping as this grows: a
 * label that remembers a value can disagree with the session.
 *
 * <h2>The path field takes the breadcrumb's place</h2>
 *
 * <p>The design's top bar has a breadcrumb and no field. Ctrl+L puts a field in the same box, holding the folder
 * shown; Enter goes, and Esc or Enter puts the breadcrumb back ({@link #editPath}, {@link #closePath}). Only one of
 * the two is shown at a time, and the bar never changes height.
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

    private final Gui gui;
    private final Breadcrumb<Path> crumbs;
    private final TextField path;
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

        Node tree = region(Look.CHROME, Landmarks.TREE);
        Node list = region(Look.LIST, Landmarks.LIST);
        Node rail = region(Look.RAIL, Landmarks.RAIL);
        SplitPane right = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, list, rail)
                .sized(SplitPane.Pane.SECOND).size(Type.RAIL).minFirst(Type.LIST_MIN);
        right.node().width(Length.FILL).height(Length.FILL);
        SplitPane body = new SplitPane(gui, SplitPane.Orientation.SIDE_BY_SIDE, tree, right.node())
                .size(Type.TREE);
        body.node().width(Length.FILL).height(Length.grow(1f));

        status = new StatusBar(gui).slot(Landmarks.STATUS_MESSAGE, StatusBar.Side.RIGHT, "");
        gui.landmark(Landmarks.STATUS, status.node());
        gui.landmark(Landmarks.STATUS_MESSAGE, status.slot(Landmarks.STATUS_MESSAGE));

        gui.root().direction(Direction.COLUMN)
                .background(gui.theme().color(Role.PAGE))
                .children(titleBar.node(), top, body.node(), status.node());
    }

    /** One of the regions M1 and M2 fill: a column of its token's colour, named for a script. */
    private Node region(Paint colour, String landmark) {
        Node n = gui.column().width(Length.FILL).height(Length.FILL).background(colour);
        gui.landmark(landmark, n);
        return n;
    }

    /** A segment's words: the folder's own name, or a drive's root as it is written ({@code C:\}). */
    private static String label(Path p) {
        Path name = p.getFileName();
        return name == null ? p.toString() : name.toString();
    }

    /** The folder the breadcrumb shows, or null for none yet: its last segment. */
    Path shown() {
        List<Path> chain = crumbs.path();
        return chain.isEmpty() ? null : chain.getLast();
    }

    /** The breadcrumb's address, which its messages carry. */
    String crumbsAddress() {
        return crumbs.address();
    }

    /** The path field's address, which its messages carry. */
    String pathAddress() {
        return path.address();
    }

    /** Show {@code folder}: the breadcrumb from its root down to it, and the field holding it for Ctrl+L. */
    void show(Path folder) {
        List<Path> chain = new ArrayList<>();
        for (Path p = folder; p != null; p = p.getParent()) {
            chain.addFirst(p);
        }
        crumbs.path(chain);
        path.text(folder.toString());
    }

    /** Ctrl+L: the field in the breadcrumb's place, with the keyboard in it and its text selected, so typing replaces it. */
    void editPath() {
        crumbs.node().visible(false);
        path.node().visible(true);
        gui.focus(path.node());
        path.select(0, path.text().length());
    }

    /**
     * Esc, or Enter once the field is sent: the breadcrumb back, and the keyboard on its nearest ancestor. A hidden
     * field keeps the keyboard otherwise, and typing would land in it unseen (framework-notes FN-3).
     */
    void closePath() {
        path.node().visible(false);
        crumbs.node().visible(true);
        List<Button> ancestors = crumbs.ancestors();
        if (!ancestors.isEmpty()) {
            gui.focus(ancestors.getLast().node());
        }
    }

    /** The path field's node, for the Esc that is claimed only while it has the keyboard. */
    Node pathNode() {
        return path.node();
    }

    /** Say something on the status line, and when {@code alert}, draw the eye to it: something was refused or failed. */
    void status(String message, boolean alert) {
        status.text(Landmarks.STATUS_MESSAGE, message);
        if (alert) {
            motion.cues.play(status.node(), Cue.ring(danger, 2));
        }
    }
}
