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
import dev.vexelray.gui.widget.Button;

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

    /**
     * Chips to a line: one for Select and Condition, whose chips are phrases with counts, and two for Action's short
     * verbs. A second phrase on a line was clipped out of reach in a narrow rail; a line of the layout does not wrap.
     */
    private static final int PHRASES = 1;
    private static final int VERBS = 2;

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
    private final Node card;
    private final Node headline;
    private final Node detail;

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

        headline = gui.text("").font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK).wordWrap(false);
        gui.landmark(Landmarks.RAIL_CARD, headline);
        Node tag = gui.text("nothing has changed yet").font(Type.MONO).textSize(Type.SMALL).textColor(Role.FAINT)
                .wordWrap(false);
        Node top = gui.column().width(Length.FILL).gap(Length.rem(0.2f)).scroll(false, false).clip(true)
                .children(headline, tag);
        detail = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(true)
                .width(Length.FILL);
        card = gui.column().width(Length.FILL).padding(Length.rem(1f)).gap(Length.rem(0.5f))
                .background(Role.PANEL).border(Length.dp(1), Role.LINE).corner(Length.rem(0.5f))
                .children(top, detail);

        node = gui.column().width(Length.FILL).height(Length.FILL)
                .padding(Length.rem(1.25f)).gap(Length.rem(1.1f))
                // It scrolls when a short window cannot hold it: a column that does not is laid out by shrinking its
                // children, which flattened the chips to slivers under a card of five lines.
                .background(Look.RAIL).scroll(false, true).clip(true)
                .children(header, empty, selects, conditions, actions,
                        gui.box().width(Length.FILL).height(Length.grow(1f)), card);
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
        card.visible(!none);
        basis.text(none ? "" : "based on " + s.basis());
        fill(select, s.select());
        fill(condition, s.condition());
        fill(action, s.action());
        headline.text(s.card().headline());
        detail.text(s.card().detail());
    }

    /**
     * The pick a chip's {@link Button.Toggled} stands for, or null when it is none of the rail's. A picked Select or
     * Condition chip clicked again is shown picked at once and says nothing: one of its row is always picked, so the
     * click changed nothing.
     */
    Picked picked(Button.Toggled t) {
        Picked p = find(select, Slot.SELECT, t.button());
        if (p == null) {
            p = find(condition, Slot.CONDITION, t.button());
        }
        if (p == null) {
            p = find(action, Slot.ACTION, t.button());
        }
        if (p != null && p.slot() != Slot.ACTION && !t.pressed()) {
            held(p);
            return null;
        }
        return p;
    }

    private void held(Picked p) {
        (p.slot() == Slot.SELECT ? select : condition).get(p.index()).show(true);
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
                gui.text(String.valueOf(number)).font(Type.MONO).textSize(Type.SMALL).textColor(Role.FAINT),
                gui.text(title).font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK));
        return gui.column().width(Length.FILL).gap(Length.rem(0.55f)).children(label, content);
    }

    private Node note(String text) {
        return gui.text(text).font(Type.UI).textSize(Type.SMALL).textColor(Role.FAINT).wordWrap(true)
                .width(Length.FILL);
    }
}
