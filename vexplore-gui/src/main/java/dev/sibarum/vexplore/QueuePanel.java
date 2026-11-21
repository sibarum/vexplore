package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.Line;
import dev.sibarum.vexplore.core.Messages.Queue;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.IconButton;
import dev.vexelray.gui.widget.Progress;

import java.util.ArrayList;
import java.util.List;

/**
 * The rail's queue (docs/shape.md, M9), pinned under the card: the run in flight, its words, how far it has got and its
 * Stop; then what waits behind it, each line with its number, which the rows it decides wear in the lists, and an ×
 * that takes it off; <i>Hold</i>, and <i>Run all</i> while held. Drawn from {@link Queue}, which says all of it.
 *
 * <p>Every node is built once, as {@link RailPanel}'s are: {@link #LINES} lines, a line with nothing to say hidden in
 * its place, and <i>and n more</i> after them. Its heading is always there, so a batch can be held before its first
 * item: with nothing running and nothing waiting it says <i>empty</i>.
 *
 * <p>Hold is a toggle, and flips itself before the explorer has answered (framework-notes FN-7); the explorer's
 * {@link Queue} shows it as it is.
 */
final class QueuePanel {

    /** How many lines waiting it shows. */
    static final int LINES = 5;

    private final Gui gui;
    private final Motion motion;
    private final Node node;
    private final Node count;
    private final Button hold;
    private final Button runAll;
    private final Node running;
    private final Node runWords;
    private final Node runTag;
    private final Progress progress;
    private final Button stop;
    private final List<Node> lines = new ArrayList<>();
    private final List<Node> numberNodes = new ArrayList<>();
    private final List<Node> wordNodes = new ArrayList<>();
    private final List<IconButton> removes = new ArrayList<>();
    private final Node more;
    /** The number of the item each line shows, as last shown; 0 for a line hidden. */
    private final long[] numbers = new long[LINES];
    /** Whether the bar was showing a run, as last shown. */
    private boolean going;

    QueuePanel(Gui gui, Motion motion) {
        this.gui = gui;
        this.motion = motion;

        Node title = gui.text("Queue").font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK).wordWrap(false);
        count = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Role.FAINT).wordWrap(false);
        gui.landmark(Landmarks.QUEUE_COUNT, count);
        hold = new Button(gui, "Hold").toggle(true).undoable(false).name(Landmarks.QUEUE_HOLD);
        gui.landmark(Landmarks.QUEUE_HOLD, hold.node());
        runAll = new Button(gui, "Run all").kind(Button.Kind.PRIMARY).undoable(false).name(Landmarks.QUEUE_RUN_ALL);
        gui.landmark(Landmarks.QUEUE_RUN_ALL, runAll.node());
        Node header = gui.row().width(Length.FILL).gap(Length.rem(0.6f)).alignItems(AlignItems.CENTER)
                .scroll(false, false).clip(true)
                .children(title, count, gui.row().width(Length.grow(1f)), runAll.node(), hold.node());

        runWords = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.INK).wordWrap(false);
        gui.landmark(Landmarks.QUEUE_RUNNING, runWords);
        runTag = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Role.FAINT).wordWrap(false);
        gui.landmark(Landmarks.QUEUE_TAG, runTag);
        stop = new Button(gui, "Stop").undoable(false).name(Landmarks.RAIL_STOP);
        gui.landmark(Landmarks.RAIL_STOP, stop.node());
        Node said = gui.column().width(Length.grow(1f)).gap(Length.rem(0.2f)).scroll(false, false).clip(true)
                .children(runWords, runTag);
        Node top = gui.row().width(Length.FILL).gap(Length.rem(0.75f)).alignItems(AlignItems.CENTER)
                .scroll(false, false).children(said, stop.node());
        progress = new Progress(gui).transition(motion.change);
        progress.node().width(Length.FILL);
        gui.landmark(Landmarks.RAIL_PROGRESS, progress.node());
        running = gui.column().width(Length.FILL).gap(Length.rem(0.5f)).children(top, progress.node());

        Node waiting = gui.column().width(Length.FILL).gap(Length.dp(4));
        for (int i = 0; i < LINES; i++) {
            Node number = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Role.ACCENT).wordWrap(false)
                    .padding(Length.dp(2), Length.dp(6)).corner(Length.dp(6)).background(Look.CHROME);
            Node words = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(false)
                    .width(Length.grow(1f));
            IconButton remove = new IconButton(gui, Icon.CLOSE, "Take off the queue")
                    .name(Landmarks.QUEUE_LINE + "." + i + ".remove").size(Length.dp(26), Length.dp(12));
            gui.landmark(Landmarks.QUEUE_LINE + "." + i + ".remove", remove.node());
            Node line = gui.row().width(Length.FILL).gap(Length.rem(0.5f)).alignItems(AlignItems.CENTER)
                    .scroll(false, false).clip(true).children(number, words, remove.node());
            gui.landmark(Landmarks.QUEUE_LINE + "." + i, line);
            line.visible(false);
            lines.add(line);
            numberNodes.add(number);
            wordNodes.add(words);
            removes.add(remove);
            waiting.append(line);
        }
        more = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.FAINT).wordWrap(false);
        more.visible(false);
        waiting.append(more);

        node = gui.column().width(Length.FILL).padding(Length.rem(0.9f), Length.rem(1f)).gap(Length.rem(0.6f))
                .background(Role.RAISED).corner(Length.dp(20)).children(header, running, waiting);
        gui.landmark(Landmarks.QUEUE, node);
        show(Queue.NONE);
    }

    Node node() {
        return node;
    }

    /** Say what runs and what waits. */
    void show(Queue q) {
        int n = q.waiting().size();
        String held = q.held() ? " · held" : "";
        count.text(n > 0 ? n + " waiting" + held : q.held() ? "held" : q.running().isPresent() ? "" : "empty");
        hold.show(q.held());
        runAll.node().visible(q.held() && n > 0);

        running.visible(q.running().isPresent());
        Line r = q.running().orElse(null);
        runWords.text(r == null ? "" : r.words());
        runTag.text(r == null ? "" : r.going());
        boolean now = r != null;
        if (now && !going) {
            progress.transition(null).show(r.progress()).transition(motion.change);
        }
        if (now) {
            progress.state(Progress.State.RUNNING).show(r.progress());
        } else {
            progress.state(Progress.State.IDLE);
        }
        going = now;

        for (int i = 0; i < LINES; i++) {
            boolean shown = i < n;
            lines.get(i).visible(shown);
            if (shown) {
                Line w = q.waiting().get(i);
                numbers[i] = w.number();
                numberNodes.get(i).text(String.valueOf(w.number()));
                wordNodes.get(i).text(w.words());
            } else {
                numbers[i] = 0L;
            }
        }
        more.visible(n > LINES);
        more.text(n > LINES ? "and " + (n - LINES) + " more" : "");
    }

    /** Whether {@code address} is the run's Stop. */
    boolean stop(String address) {
        return stop.address().equals(address);
    }

    /** Whether {@code address} is Run all. */
    boolean runAll(String address) {
        return runAll.address().equals(address);
    }

    /** Whether {@code address} is the Hold toggle. */
    boolean hold(String address) {
        return hold.address().equals(address);
    }

    /** The number of the item whose × {@code address} is, or 0 when it is none. */
    long remove(String address) {
        for (int i = 0; i < LINES; i++) {
            if (removes.get(i).address().equals(address)) {
                return numbers[i];
            }
        }
        return 0L;
    }
}
