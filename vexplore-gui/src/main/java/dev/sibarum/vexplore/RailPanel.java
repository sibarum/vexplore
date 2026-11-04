package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.Card;
import dev.sibarum.vexplore.core.Messages.Chip;
import dev.sibarum.vexplore.core.Messages.Offer;
import dev.sibarum.vexplore.core.Messages.Offers;
import dev.sibarum.vexplore.core.Messages.Picked;
import dev.sibarum.vexplore.core.Messages.Slot;
import dev.sibarum.vexplore.core.Messages.Suggested;
import dev.sibarum.vexplore.core.Messages.Tally;
import dev.sibarum.vexplore.core.Suggestions.Act;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.IconSource;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.icons.IconView;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.IconButton;
import dev.vexelray.gui.widget.TextField;
import dev.vexelray.text.TextLayout.HAlign;
import dev.vexelray.text.TextLayout.VAlign;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Suggestion Rail, in Orbit's words and shapes (M8): <i>Which files</i>, <i>Only if</i> and <i>Then</i>, a bar of
 * three steps over them, and the card that states the effect first, with a round button that carries it out. Drawn
 * from {@link Suggested}, which says everything on it.
 *
 * <h2>Every node is built once</h2>
 *
 * <p>The rail is a fixed column, and a suggestion that arrives, changes or leaves rewrites what its nodes say and
 * whether they show; it never builds or removes one. So nothing on screen changes in one frame (the framework fades a
 * changed word and a node shown or hidden), and nothing in the list moves when the rail changes. <i>Which files</i>,
 * <i>Only if</i> and the destinations are pools of pills, one to a line, as long as the most they can offer; a pill
 * with nothing to say is hidden in its place. <i>Then</i> is the six actions as tiles, always all six, an action the
 * rail does not offer for the selection greyed in its place.
 *
 * <h2>A pill is known by its place</h2>
 *
 * <p>Pressing a pill says its row and its place ({@link Picked}); the explorer knows what it offered there. A tile is
 * an action, so it finds its place among the action chips the explorer last sent by their words ({@link Act#chip}),
 * which are the explorer's own, and says that place.
 *
 * <h2>A pill flips itself</h2>
 *
 * <p>The widget library's chip ({@link Button#toggle}) changes its own pressed state when clicked and then says so,
 * before the model has answered (framework-notes FN-7). Which files and Only if always have exactly one pill picked,
 * so a picked pill clicked again is shown picked again at once ({@link #held}), in the frame it was clicked; any other
 * press waits for the explorer's {@link Suggested}, which shows every pill as it is.
 *
 * <h2>A count after a dot</h2>
 *
 * <p>Orbit puts a pill's count in a badge at its end. A {@link Button} is one label and holds nothing beside it
 * (framework-notes FN-14), so the count follows the words, {@code Just this file · 1}, as it did.
 */
final class RailPanel {

    /** How many pills each row can offer: the engine's scopes and conditions. */
    private static final int SELECTS = 4;
    private static final int CONDITIONS = 3;
    /** Places for a move or a copy: the destination picked, when it is not suggested, and three suggested. */
    private static final int PLACES = 4;

    /** A pill: Orbit's 44 px, round-ended. */
    private static final Length PILL = Length.dp(44);
    /** A tile of Then: three to a line in the rail. */
    private static final Length TILE = Length.rem(5.25f);
    /** The run button: round, as Orbit draws it. */
    private static final Length RUN = Length.dp(44);

    /** The card's tag while nothing runs: the design's promise, kept until the button is pressed. */
    private static final String UNCHANGED = "nothing has changed yet";

    private static final String EMPTY = "Select a file and Vexplore will say what it would do with the others. "
            + "Nothing here covers anything else, and nothing changes until you say so.";

    private final Gui gui;
    private final Node node;
    private final Node basisChip;
    private final Node basis;
    private final Node empty;
    private final List<Node> steps = new ArrayList<>();
    private final List<Node> sections = new ArrayList<>();
    private final List<Button> select = new ArrayList<>();
    private final List<Button> condition = new ArrayList<>();
    private final List<IconButton> tiles = new ArrayList<>();
    private final List<Button> place = new ArrayList<>();
    private final Node places;
    private final Button choose;
    private final Node card;
    private final Node headline;
    private final Node detail;
    private final Node tag;
    private final IconButton go;
    private final Node run;
    private final Button stop;
    private final TextField name;

    /** Where each action's chip was in what the explorer last sent, by {@link Act}'s order; -1 for not offered. */
    private final int[] offered = new int[Act.values().length];

    /** Two Folders' offers: the tiles' count and label, and each offer's card, by {@link Offer.Kind}'s order. */
    private final Node comparison;
    private final List<Node[]> tallies = new ArrayList<>();
    private final List<OfferCard> offerCards = new ArrayList<>();
    /** Whether the offers are shown, which the empty note gives way to: as the explorer last said. */
    private boolean comparing;
    /** Whether the selection's rail has nothing to say, as the explorer last said. */
    private boolean nothing = true;

    RailPanel(Gui gui) {
        this.gui = gui;

        // The basis as Orbit's chip: an amber dot with a clock in it, and the words, on a dark amber ground.
        Node dot = dot(gui);
        basis = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Look.ANCHOR_INK).wordWrap(false);
        gui.landmark(Landmarks.RAIL_BASIS, basis);
        basisChip = gui.row().gap(Length.dp(8)).alignItems(AlignItems.CENTER)
                .padding(Length.dp(4), Length.dp(10)).corner(Length.dp(14)).background(Look.ANCHOR_WASH)
                .scroll(false, false).clip(true).children(dot, basis);
        Node header = gui.column().width(Length.FILL).gap(Length.rem(0.5f)).children(
                gui.text("Suggestions").font(Type.UI).textSize(Type.HEADING).textColor(Role.INK), basisChip);

        // Three steps, a bar each, lit as each is chosen.
        Node bar = gui.row().width(Length.FILL).gap(Length.dp(6));
        for (int i = 0; i < 3; i++) {
            Node step = gui.box().width(Length.grow(1f)).height(Length.dp(6)).corner(Length.dp(3))
                    .background(Role.TRACK);
            steps.add(step);
            bar.append(step);
        }

        empty = note(EMPTY);
        gui.landmark(Landmarks.RAIL_EMPTY, empty);

        Node selects = section(1, "Which files", pills(select, SELECTS, Landmarks.RAIL_SELECT));
        Node conditions = section(2, "Only if", pills(condition, CONDITIONS, Landmarks.RAIL_CONDITION));
        Node actions = section(3, "Then", tiles());
        sections.addAll(List.of(bar, selects, conditions, actions));
        // The fourth step, for a move or a copy only: where to. The places suggested, and the system's folder picker.
        choose = new Button(gui, "Choose folder…").kind(Button.Kind.GHOST).name(Landmarks.RAIL_CHOOSE);
        gui.landmark(Landmarks.RAIL_CHOOSE, choose.node());
        Node where = pills(place, PLACES, Landmarks.RAIL_PLACE);
        where.append(choose.node());
        places = section(4, "Where to", where);

        headline = gui.text("").font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK).wordWrap(false);
        gui.landmark(Landmarks.RAIL_CARD, headline);
        tag = gui.text(UNCHANGED).font(Type.MONO).textSize(Type.SMALL).textColor(Role.FAINT)
                .wordWrap(false);
        gui.landmark(Landmarks.RAIL_TAG, tag);
        detail = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(true)
                .width(Length.FILL);
        Node words = gui.column().width(Length.grow(1f)).gap(Length.rem(0.2f)).scroll(false, false).clip(true)
                .children(headline, tag);
        // The one filled button on the screen, round as Orbit draws it: what happens if the person does nothing but
        // agree, its words its name. While a run goes, its Stop stands beside the words instead.
        go = new IconButton(gui, Icon.ARROW_RIGHT, "").name(Landmarks.RAIL_GO).size(RUN, Length.dp(18));
        go.node().corner(Length.dp(22));
        go.ink(Role.ON_ACTION);
        // The button paints its own fill under the pointer only, so the violet disc is behind it, greyed with it.
        run = gui.box().size(RUN, RUN).corner(Length.dp(22)).background(Role.ACTION).children(go.node());
        gui.landmark(Landmarks.RAIL_GO, go.node());
        stop = new Button(gui, "Stop").name(Landmarks.RAIL_STOP);
        gui.landmark(Landmarks.RAIL_STOP, stop.node());
        Node top = gui.row().width(Length.FILL).gap(Length.rem(0.75f)).alignItems(AlignItems.CENTER)
                .scroll(false, false).children(words, run, stop.node());
        // A rename's new name, typed here and carried out by Enter: the card is where what would happen is said.
        name = new TextField(gui, "").name(Landmarks.RAIL_NAME);
        name.node().width(Length.FILL).font(Type.UI).textSize(Type.RAIL_TEXT);
        gui.landmark(Landmarks.RAIL_NAME, name.node());
        card = gui.column().width(Length.FILL).padding(Length.rem(0.9f), Length.rem(1f)).gap(Length.rem(0.5f))
                .background(Role.RAISED).corner(Length.dp(20))
                .children(top, detail, name.node());

        comparison = comparison();

        // The steps scroll when a short window cannot hold them: a column that does not is laid out by shrinking its
        // children, which flattened the chips to slivers under a card of five lines. The card does not scroll with
        // them: it is pinned under them, so the button that carries it out is always in sight.
        Node column = gui.column().width(Length.FILL).height(Length.grow(1f)).gap(Length.rem(1.1f))
                .scroll(false, true).clip(true)
                .children(header, comparison, empty, bar, selects, conditions, actions, places);
        node = gui.column().width(Length.FILL).height(Length.FILL)
                .padding(Length.rem(1.25f)).gap(Length.rem(1.1f))
                .background(Look.RAIL).corner(Look.PANEL_CORNER).scroll(false, false).clip(true)
                .children(column, card);
        gui.landmark(Landmarks.RAIL, node);
        show(Suggested.NONE);
    }

    Node node() {
        return node;
    }

    /**
     * Say what the explorer suggests: every pill and tile, the steps, the basis and the card, or the empty note, which
     * Two Folders' offers speak in place of.
     */
    void show(Suggested s) {
        boolean none = s.empty();
        nothing = none;
        empty.visible(none && !comparing);
        for (Node n : sections) {
            n.visible(!none);
        }
        String running = s.card().running();
        card.visible(!none || !running.isEmpty());
        basisChip.visible(!none);
        basis.text(none ? "" : "based on " + s.basis());
        fill(select, s.select());
        fill(condition, s.condition());
        boolean acting = tiles(s.action());
        fill(place, s.places());
        places.visible(!none && s.placing());
        lit(0, s.select().stream().anyMatch(Chip::on));
        lit(1, s.condition().stream().anyMatch(Chip::on));
        lit(2, acting);
        headline.text(s.card().headline());
        detail.text(s.card().detail());
        tag.text(running.isEmpty() ? UNCHANGED : running);
        go.label(s.card().go());
        boolean ready = running.isEmpty() && !s.card().go().isEmpty();
        go.enabled(ready);
        run.background(ready ? Role.ACTION : Role.TRACK);
        run.visible(running.isEmpty());
        stop.node().visible(!running.isEmpty());
        name.node().visible(running.isEmpty() && s.card().naming());
    }

    /**
     * Two Folders' offers, above the rest: the comparison's counts as four tiles, and a card for each offer, its words
     * the explorer's and its button its own. None in one folder, where the rail is the selection's alone.
     */
    void offers(Offers o) {
        comparing = !o.tally().here().isEmpty();
        comparison.visible(comparing);
        Tally t = o.tally();
        tally(0, t.onlyHere(), "only in " + t.here());
        tally(1, t.different(), "different");
        tally(2, t.identical(), "identical");
        tally(3, t.probably(), "probably identical");
        Offer.Kind[] kinds = Offer.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            Offer offer = null;
            for (Offer each : o.offers()) {
                if (each.kind() == kinds[i]) {
                    offer = each;
                }
            }
            OfferCard c = offerCards.get(i);
            c.node.visible(offer != null);
            if (offer != null) {
                Card card = offer.card();
                boolean going = !card.running().isEmpty();
                c.headline.text(card.headline());
                c.detail.text(card.detail());
                c.tag.text(going ? card.running() : UNCHANGED);
                // While its check or its run goes, the button is its Stop; the explorer knows which it means.
                c.button.label(going ? "Stop" : card.go()).kind(going ? Button.Kind.SECONDARY : Button.Kind.PRIMARY)
                        .enabled(going || !card.go().isEmpty());
            }
        }
        empty.visible(nothing && !comparing);
    }

    /** Which offer {@code address} is the button of, or null. */
    Offer.Kind accept(String address) {
        Offer.Kind[] kinds = Offer.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            if (offerCards.get(i).button.address().equals(address)) {
                return kinds[i];
            }
        }
        return null;
    }

    private void tally(int i, int count, String label) {
        tallies.get(i)[0].text(String.valueOf(count));
        tallies.get(i)[1].text(label);
    }

    /** One offer's card: its words, how far it has got, and its own button. */
    private record OfferCard(Node node, Node headline, Node detail, Node tag, Button button) {
    }

    /**
     * The comparison, as the Two folders board draws it: where it comes from, the four counts two by two (the
     * board's three, and probably identical, which is a group of its own), and a card per offer. Built once, hidden in
     * one folder.
     */
    private Node comparison() {
        Node from = gui.text("From comparing both folders").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM)
                .wordWrap(false);
        String[] names = {"only", "different", "identical", "probably"};
        Role[] washes = {Look.FROM_WASH, Look.ANCHOR_WASH, Look.QUIET_WASH, Look.QUIET_WASH};
        Role[] counts = {Look.FROM_INK, Look.ANCHOR, Look.QUIET_INK, Look.QUIET_INK};
        Role[] labels = {Role.INK, Look.ANCHOR_INK, Look.QUIET_INK, Look.QUIET_INK};
        Node grid = gui.column().width(Length.FILL).gap(Length.dp(6));
        Node line = null;
        for (int i = 0; i < names.length; i++) {
            if (i % 2 == 0) {
                line = gui.row().width(Length.FILL).gap(Length.dp(6)).scroll(false, false);
                grid.append(line);
            }
            Node count = gui.text("0").font(Type.UI).textSize(Type.HEADING).textColor(counts[i]).wordWrap(false);
            Node label = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(labels[i]).wordWrap(false);
            Node tile = gui.column().width(Length.grow(1f)).padding(Length.dp(10)).gap(Length.dp(2))
                    .corner(Length.dp(14)).background(washes[i]).scroll(false, false).clip(true)
                    .children(count, label);
            gui.landmark(Landmarks.RAIL_TALLY + "." + names[i], tile);
            tallies.add(new Node[] {count, label});
            line.append(tile);
        }
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.75f)).children(from, grid);
        for (Offer.Kind kind : Offer.Kind.values()) {
            String landmark = Landmarks.RAIL_OFFER + "." + (kind == Offer.Kind.MOVE_ONLY_HERE ? "move" : "remove");
            Node headline = gui.text("").font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK).wordWrap(true)
                    .width(Length.FILL);
            Node detail = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(true)
                    .width(Length.FILL);
            Node tag = gui.text(UNCHANGED).font(Type.MONO).textSize(Type.SMALL).textColor(Role.FAINT)
                    .wordWrap(false);
            Button button = new Button(gui, "").kind(Button.Kind.PRIMARY).undoable(false).name(landmark + ".go");
            gui.landmark(landmark + ".go", button.node());
            Node card = gui.column().width(Length.FILL).padding(Length.rem(0.9f), Length.rem(1f))
                    .gap(Length.rem(0.5f)).background(Role.RAISED).corner(Length.dp(18))
                    .children(headline, detail, gui.row().width(Length.FILL).alignItems(AlignItems.CENTER)
                            .gap(Length.rem(0.75f)).scroll(false, false)
                            .children(tag, gui.row().width(Length.grow(1f)), button.node()));
            card.visible(false);
            gui.landmark(landmark, card);
            offerCards.add(new OfferCard(card, headline, detail, tag, button));
            column.append(card);
        }
        column.visible(false);
        return column;
    }

    private void lit(int step, boolean on) {
        steps.get(step).background(on ? Role.ACCENT : Role.TRACK);
    }

    /** Whether {@code address} is the card's button, which carries out what the card states. */
    boolean go(String address) {
        return go.address().equals(address);
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
     * The pick a pill's {@link Button.Toggled} stands for, or null when it is none of the rail's. A picked Which files,
     * Only if or Where to pill clicked again is shown picked at once and says nothing: one of its row stays picked, so
     * the click changed nothing.
     */
    Picked picked(Button.Toggled t) {
        Picked p = find(select, Slot.SELECT, t.button());
        if (p == null) {
            p = find(condition, Slot.CONDITION, t.button());
        }
        if (p == null) {
            p = find(place, Slot.DESTINATION, t.button());
        }
        if (p != null && !t.pressed()) {
            held(p);
            return null;
        }
        return p;
    }

    /** The pick a tile's press stands for: its action's place among the chips last sent, or null. */
    Picked picked(String address) {
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).address().equals(address) && offered[i] >= 0) {
                return new Picked(Slot.ACTION, offered[i]);
            }
        }
        return null;
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

    /**
     * Each action's tile: lit when its chip is on, greyed when the explorer did not offer it. Whether one is picked.
     */
    private boolean tiles(List<Chip> chips) {
        boolean any = false;
        Act[] acts = Act.values();
        for (int i = 0; i < acts.length; i++) {
            offered[i] = -1;
            boolean on = false;
            for (int j = 0; j < chips.size(); j++) {
                if (chips.get(j).label().equals(acts[i].chip())) {
                    offered[i] = j;
                    on = chips.get(j).on();
                }
            }
            tiles.get(i).enabled(offered[i] >= 0).on(on);
            any |= on;
        }
        return any;
    }

    /** A pool of {@code count} pills, one to a line, named {@code name.0}, {@code name.1}, ... */
    private Node pills(List<Button> into, int count, String name) {
        Node column = gui.column().width(Length.FILL).gap(Length.dp(6));
        for (int i = 0; i < count; i++) {
            Button b = new Button(gui, "").toggle(true).undoable(false).name(name + "." + i);
            b.node().font(Type.UI).textSize(Type.RAIL_TEXT).width(Length.FILL).height(PILL).corner(Length.dp(22))
                    .padding(Length.ZERO, Length.dp(16)).align(HAlign.LEFT, VAlign.MIDDLE);
            gui.landmark(name + "." + i, b.node());
            into.add(b);
            column.append(b.node());
        }
        return column;
    }

    /** Then: the six actions as tiles, three to a line, each named {@code rail.action.<act>} for scripts. */
    private Node tiles() {
        Node column = gui.column().width(Length.FILL).gap(Length.dp(8));
        Node line = null;
        Act[] acts = Act.values();
        for (int i = 0; i < acts.length; i++) {
            if (i % 3 == 0) {
                line = gui.row().width(Length.FILL).gap(Length.dp(8)).scroll(false, false);
                column.append(line);
            }
            String landmark = Landmarks.RAIL_ACTION + "." + acts[i].name().toLowerCase(Locale.ROOT);
            IconButton t = new IconButton(gui, icon(acts[i]), word(acts[i])).name(landmark).toggle(true)
                    .tile(TILE, Length.dp(26));
            if (acts[i] == Act.DELETE) {
                t.ink(Role.DANGER);
            }
            gui.landmark(landmark, t.node());
            tiles.add(t);
            line.append(t.node());
        }
        return column;
    }

    /** An action's icon, from the VexelRay sets: the suite's, a family's, or Vexplore's own Move. */
    static IconSource icon(Act a) {
        return a == Act.MOVE ? VexploreIcon.MOVE
                : a == Act.COPY ? Icon.COPY
                : a == Act.ARCHIVE ? FileFamily.ARCHIVES
                : a == Act.RENAME ? Icon.EDIT
                : a == Act.MARK ? Icon.BOOKMARK
                : Icon.TRASH;
    }

    /** A tile's word: the chip's, without its ellipsis or its "(recoverable)", which the card says. */
    private static String word(Act a) {
        String chip = a.chip();
        int cut = chip.indexOf(' ');
        String first = cut < 0 ? chip : chip.substring(0, cut);
        return first.endsWith("…") ? first.substring(0, first.length() - 1) : first;
    }

    private Node section(int number, String title, Node content) {
        Node label = gui.row().gap(Length.rem(0.6f)).alignItems(AlignItems.CENTER).children(
                gui.text(String.valueOf(number)).font(Type.MONO).textSize(Type.SMALL).textColor(Role.ACCENT),
                gui.text(title).font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK));
        return gui.column().width(Length.FILL).gap(Length.rem(0.6f)).children(label, content);
    }

    private Node note(String text) {
        return gui.text(text).font(Type.UI).textSize(Type.SMALL).textColor(Role.FAINT).wordWrap(true)
                .width(Length.FILL);
    }

    /** The basis chip's dot: the clock, in the chip's ground, on an amber disc. */
    private static Node dot(Gui gui) {
        Node clock = IconView.of(gui, VexploreIcon.CLOCK).size(Length.dp(11)).tint(Look.ANCHOR_WASH).node();
        return gui.row().size(Length.dp(18), Length.dp(18)).corner(Length.dp(9)).background(Look.ANCHOR)
                .alignItems(AlignItems.CENTER).justify(Justify.CENTER).children(clock);
    }
}
