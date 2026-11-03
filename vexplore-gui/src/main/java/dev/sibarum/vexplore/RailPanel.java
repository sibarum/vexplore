package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.Chip;
import dev.sibarum.vexplore.core.Messages.Picked;
import dev.sibarum.vexplore.core.Messages.Slot;
import dev.sibarum.vexplore.core.Messages.Suggested;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.IconButton;
import dev.vexelray.gui.widget.TextField;

import java.util.ArrayList;
import java.util.List;

/**
 * The Suggestion Rail: Select, Condition and Action as chips, and the card that states the effect first. The handoff's
 * screen 01, drawn from {@link Suggested}, which says everything on it.
 *
 * <h2>Every node is built once</h2>
 *
 * <p>The rail is a fixed column, and a suggestion that arrives, changes or leaves rewrites what its nodes say and
 * whether they show; it never builds or removes one. So nothing on screen changes in one frame (the framework fades a
 * changed word and a node shown or hidden), and nothing in the list moves when the rail changes. Each row of chips is
 * a pool as long as the most that row can offer, two to a line, since a row of the layout does not wrap; a chip with
 * nothing to say is hidden in its place.
 *
 * <h2>A chip is known by its place</h2>
 *
 * <p>Pressing a chip says its row and its place ({@link Picked}); the explorer knows what it offered there. This holds
 * no record of what any chip stood for, as {@link Ui} holds none.
 *
 * <h2>A chip flips itself</h2>
 *
 * <p>The widget library's chip ({@link Button#toggle}) changes its own pressed state when clicked and then says so,
 * before the model has answered (framework-notes FN-7). Select and Condition always have exactly one chip picked, so a
 * picked chip clicked again is shown picked again at once ({@link #held}), in the frame it was clicked; any other
 * press waits for the explorer's {@link Suggested}, which shows every chip as it is.
 */
final class RailPanel {

    /** How many chips each row can offer: the engine's scopes, conditions and actions. */
    private static final int SELECTS = 4;
    private static final int CONDITIONS = 3;
    private static final int ACTIONS = 6;
    /** Places for a move or a copy: the destination picked, when it is not suggested, and three suggested. */
    private static final int PLACES = 4;
    /** The marks listed, as many as the explorer keeps. */
    private static final int MARKS = 6;

    /**
     * Chips to a line: one for Select and Condition, whose chips are phrases with counts, and two for Action's short
     * verbs. A second phrase on a line was clipped out of reach in a narrow rail; a line of the layout does not wrap.
     */
    private static final int PHRASES = 1;
    private static final int VERBS = 2;

    /** The card's tag while nothing runs: the design's promise, kept until the button is pressed. */
    private static final String UNCHANGED = "nothing has changed yet";

    private static final String EMPTY = "Select a file and Vexplore will say what it would do with the others. "
            + "Nothing here covers anything else, and nothing changes until you say so.";

    private final Gui gui;
    private final Node node;
    private final Node basis;
    private final Node empty;
    private final List<Node> sections = new ArrayList<>();
    private final List<Button> select = new ArrayList<>();
    private final List<Button> condition = new ArrayList<>();
    private final List<Button> action = new ArrayList<>();
    private final List<Button> place = new ArrayList<>();
    private final Node places;
    private final Button choose;
    private final List<Button> mark = new ArrayList<>();
    private final List<IconButton> forget = new ArrayList<>();
    private final List<Node> markRows = new ArrayList<>();
    private final Node marks;
    private final Node card;
    private final Node headline;
    private final Node detail;
    private final Node tag;
    private final Button go;
    private final Button stop;
    private final TextField name;

    RailPanel(Gui gui) {
        this.gui = gui;

        basis = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Look.ANCHOR).wordWrap(false);
        gui.landmark(Landmarks.RAIL_BASIS, basis);
        // Under the title rather than beside it, as the handoff has it: beside it, a narrow rail clipped it to "bas".
        Node basisBox = gui.row().width(Length.FILL).scroll(false, false).clip(true).children(basis);
        Node header = gui.column().width(Length.FILL).gap(Length.rem(0.25f)).children(
                gui.text("Suggestions").font(Type.UI).textSize(Type.HEADING).textColor(Role.INK), basisBox);

        empty = note(EMPTY);
        gui.landmark(Landmarks.RAIL_EMPTY, empty);

        Node selects = section(1, "Select", chips(select, SELECTS, PHRASES, Landmarks.RAIL_SELECT));
        Node conditions = section(2, "Condition", chips(condition, CONDITIONS, PHRASES, Landmarks.RAIL_CONDITION));
        Node actions = section(3, "Action", chips(action, ACTIONS, VERBS, Landmarks.RAIL_ACTION));
        sections.addAll(List.of(selects, conditions, actions));
        // The fourth step, for a move or a copy only: where to. The places suggested, and the system's folder picker.
        choose = new Button(gui, "Choose folder…").kind(Button.Kind.GHOST).name(Landmarks.RAIL_CHOOSE);
        gui.landmark(Landmarks.RAIL_CHOOSE, choose.node());
        Node where = chips(place, PLACES, PHRASES, Landmarks.RAIL_PLACE);
        where.append(choose.node());
        places = section(4, "Destination", where);

        // The marks kept: always there when there are some, nothing selected or not, since recalling one is how a
        // selection made before comes back.
        Node kept = gui.column().width(Length.FILL).gap(Length.rem(0.5f));
        for (int i = 0; i < MARKS; i++) {
            Button recall = new Button(gui, "").name(Landmarks.RAIL_MARK + "." + i);
            recall.node().font(Type.UI).textSize(Type.RAIL_TEXT);
            gui.landmark(Landmarks.RAIL_MARK + "." + i, recall.node());
            IconButton drop = new IconButton(gui, Icon.CLOSE, "Forget this mark").name(Landmarks.RAIL_FORGET + "." + i);
            gui.landmark(Landmarks.RAIL_FORGET + "." + i, drop.node());
            Node row = gui.row().width(Length.FILL).gap(Length.rem(0.3f)).alignItems(AlignItems.CENTER)
                    .scroll(false, false).clip(true).children(recall.node(), drop.node());
            mark.add(recall);
            forget.add(drop);
            markRows.add(row);
            kept.append(row);
        }
        marks = section(0, "Marks", kept);
        marks.visible(false);

        headline = gui.text("").font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK).wordWrap(false);
        gui.landmark(Landmarks.RAIL_CARD, headline);
        tag = gui.text(UNCHANGED).font(Type.MONO).textSize(Type.SMALL).textColor(Role.FAINT)
                .wordWrap(false);
        Node top = gui.column().width(Length.FILL).gap(Length.rem(0.2f)).scroll(false, false).clip(true)
                .children(headline, tag);
        detail = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(true)
                .width(Length.FILL);
        gui.landmark(Landmarks.RAIL_TAG, tag);
        // The one filled button on the screen: what happens if the person does nothing but agree. While a run goes,
        // its Stop stands in the same place.
        go = new Button(gui, "").kind(Button.Kind.PRIMARY).name(Landmarks.RAIL_GO);
        gui.landmark(Landmarks.RAIL_GO, go.node());
        stop = new Button(gui, "Stop").name(Landmarks.RAIL_STOP);
        gui.landmark(Landmarks.RAIL_STOP, stop.node());
        // A rename's new name, typed here and carried out by Enter: the card is where what would happen is said.
        name = new TextField(gui, "").name(Landmarks.RAIL_NAME);
        name.node().width(Length.FILL).font(Type.UI).textSize(Type.RAIL_TEXT);
        gui.landmark(Landmarks.RAIL_NAME, name.node());
        Node buttons = gui.row().width(Length.FILL).gap(Length.rem(0.5f)).scroll(false, false).clip(true)
                .children(go.node(), stop.node());
        card = gui.column().width(Length.FILL).padding(Length.rem(1f)).gap(Length.rem(0.5f))
                .background(Role.PANEL).border(Length.dp(1), Role.LINE).corner(Length.rem(0.5f))
                .children(top, detail, name.node(), buttons);

        // The steps scroll when a short window cannot hold them: a column that does not is laid out by shrinking its
        // children, which flattened the chips to slivers under a card of five lines. The card does not scroll with
        // them: it is pinned under them, so the button that carries it out is always in sight.
        Node steps = gui.column().width(Length.FILL).height(Length.grow(1f)).gap(Length.rem(1.1f))
                .scroll(false, true).clip(true)
                .children(header, empty, selects, conditions, actions, places, marks);
        node = gui.column().width(Length.FILL).height(Length.FILL)
                .padding(Length.rem(1.25f)).gap(Length.rem(1.1f))
                .background(Look.RAIL).corner(Look.PANEL_CORNER).scroll(false, false).clip(true)
                .children(steps, card);
        gui.landmark(Landmarks.RAIL, node);
        show(Suggested.NONE);
    }

    Node node() {
        return node;
    }

    /** Say what the explorer suggests: every chip, the basis and the card, or the empty rail's note. */
    void show(Suggested s) {
        boolean none = s.empty();
        empty.visible(none);
        for (Node n : sections) {
            n.visible(!none);
        }
        String running = s.card().running();
        card.visible(!none || !running.isEmpty());
        basis.text(none ? "" : "based on " + s.basis());
        fill(select, s.select());
        fill(condition, s.condition());
        fill(action, s.action());
        fill(place, s.places());
        places.visible(!none && s.placing());
        headline.text(s.card().headline());
        detail.text(s.card().detail());
        tag.text(running.isEmpty() ? UNCHANGED : running);
        go.label(s.card().go());
        go.node().visible(running.isEmpty() && !s.card().go().isEmpty());
        stop.node().visible(!running.isEmpty());
        name.node().visible(running.isEmpty() && s.card().naming());
    }

    /** Whether {@code address} is the card's button, which carries out what the card states. */
    boolean go(String address) {
        return go.address().equals(address);
    }

    /** The marks kept, by name, newest first: one row each, the rest hidden in their places. */
    void marks(List<String> names) {
        for (int i = 0; i < MARKS; i++) {
            boolean there = i < names.size();
            mark.get(i).label(there ? names.get(i) : "");
            markRows.get(i).visible(there);
        }
        marks.visible(!names.isEmpty());
    }

    /** Which mark {@code address} recalls, or -1 for none. */
    int recall(String address) {
        for (int i = 0; i < mark.size(); i++) {
            if (mark.get(i).address().equals(address)) {
                return i;
            }
        }
        return -1;
    }

    /** Which mark {@code address} forgets, or -1 for none. */
    int forget(String address) {
        for (int i = 0; i < forget.size(); i++) {
            if (forget.get(i).address().equals(address)) {
                return i;
            }
        }
        return -1;
    }

    /** The name field's address: what its {@code Submitted} says it with. */
    String nameAddress() {
        return name.address();
    }

    /**
     * Rename was picked: the field holds the file's name, with the keyboard in it and the name before its extension
     * selected, so typing replaces the name and keeps the extension, as a file manager's rename does.
     */
    void naming(String current) {
        name.text(current);
        int dot = current.lastIndexOf('.');
        name.select(0, dot > 0 ? dot : current.length());
        gui.focus(name.node());
    }

    /** Whether {@code address} is {@code Choose folder…}. */
    boolean choose(String address) {
        return choose.address().equals(address);
    }

    /** Whether {@code address} is the card's Stop. */
    boolean stop(String address) {
        return stop.address().equals(address);
    }

    /**
     * The pick a chip's {@link Button.Toggled} stands for, or null when it is none of the rail's. A picked Select,
     * Condition or Destination chip clicked again is shown picked at once and says nothing: one of its row stays
     * picked, so the click changed nothing.
     */
    Picked picked(Button.Toggled t) {
        Picked p = find(select, Slot.SELECT, t.button());
        if (p == null) {
            p = find(condition, Slot.CONDITION, t.button());
        }
        if (p == null) {
            p = find(action, Slot.ACTION, t.button());
        }
        if (p == null) {
            p = find(place, Slot.DESTINATION, t.button());
        }
        if (p != null && p.slot() != Slot.ACTION && !t.pressed()) {
            held(p);
            return null;
        }
        return p;
    }

    private void held(Picked p) {
        (p.slot() == Slot.SELECT ? select : p.slot() == Slot.CONDITION ? condition : place).get(p.index()).show(true);
    }

    private static Picked find(List<Button> row, Slot slot, String address) {
        for (int i = 0; i < row.size(); i++) {
            if (row.get(i).address().equals(address)) {
                return new Picked(slot, i);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ building

    private void fill(List<Button> row, List<Chip> chips) {
        for (int i = 0; i < row.size(); i++) {
            Button b = row.get(i);
            if (i < chips.size()) {
                Chip c = chips.get(i);
                b.label(c.count() > 0 ? c.label() + " · " + c.count() : c.label()).show(c.on());
                b.node().visible(true);
            } else {
                b.show(false);
                b.node().visible(false);
            }
        }
    }

    /** A pool of {@code count} chips, {@code perLine} to a line, named {@code name.0}, {@code name.1}, ... */
    private Node chips(List<Button> into, int count, int perLine, String name) {
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.5f));
        Node line = null;
        for (int i = 0; i < count; i++) {
            if (i % perLine == 0) {
                line = gui.row().width(Length.FILL).gap(Length.rem(0.5f)).scroll(false, false).clip(true);
                column.append(line);
            }
            Button b = new Button(gui, "").toggle(true).undoable(false).name(name + "." + i);
            b.node().font(Type.UI).textSize(Type.RAIL_TEXT);
            gui.landmark(name + "." + i, b.node());
            into.add(b);
            line.append(b.node());
        }
        return column;
    }

    private Node section(int number, String title, Node content) {
        Node label = gui.row().gap(Length.rem(0.6f)).alignItems(AlignItems.CENTER).children(
                gui.text(number == 0 ? "•" : String.valueOf(number)).font(Type.MONO).textSize(Type.SMALL)
                        .textColor(Role.FAINT),
                gui.text(title).font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK));
        return gui.column().width(Length.FILL).gap(Length.rem(0.55f)).children(label, content);
    }

    private Node note(String text) {
        return gui.text(text).font(Type.UI).textSize(Type.SMALL).textColor(Role.FAINT).wordWrap(true)
                .width(Length.FILL);
    }
}
