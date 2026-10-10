package dev.sibarum.vexplore;

import dev.sibarum.vexplore.Doc.Mark;
import dev.sibarum.vexplore.Doc.Work;
import dev.sibarum.vexplore.files.Destinations;
import dev.sibarum.vexplore.files.Destinations.Dest;
import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;
import dev.sibarum.vexplore.ops.Plan;
import dev.sibarum.vexplore.suggest.Bytes;
import dev.sibarum.vexplore.suggest.Intents.Candidate;
import dev.sibarum.vexplore.suggest.Suggestions;
import dev.sibarum.vexplore.suggest.Suggestions.Act;
import dev.sibarum.vexplore.suggest.Suggestions.Option;
import dev.sibarum.vexplore.suggest.Suggestions.Rail;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.widget.Button;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Suggestion Rail's contents: Select, Condition, Action, Destination for a move or copy, and the card that
 * states the effect first.
 *
 * <p>The rail is a fixed column and this rewrites <em>only its contents</em>. A suggestion that arrives, changes or
 * leaves therefore moves no row of the list and covers nothing; at worst it moves another part of the rail, which
 * is a place that exists to be glanced at.
 *
 * <h2>Held modifiers answer in the Select slot</h2>
 * Holding Shift or Control replaces the Select chips with what the key is announcing — where a range probably ends,
 * or the rule the examples suggest — in the same place, so the eye does not have to find them. Letting go puts the
 * ordinary chips back. Accepting one is the Select step made without naming it; ignoring them all changes nothing.
 *
 * <h2>The card never lies about what will happen</h2>
 * Everything it says comes from {@link Doc#plan()}, the same value the action then carries out: how many files, how
 * many bytes, what will be left alone and why. A button that would do nothing is disabled and says why, and
 * everything that writes says so ("nothing has changed yet") until it has.
 */
final class RailView {

    private record Key(Rail rail, Suggestions.Pick pick, List<Candidate> intent, boolean shift, boolean control,
                       Work work, Plan plan, Set<Path> selected) {
    }

    /** How much of a file name the header's "based on" shows before cutting it in the middle. */
    private static final int BASIS_CHARS = 28;

    private final Gui gui;
    private final Model model;
    private final Actor actor;
    private final Node body;
    private final List<Node> kids = new ArrayList<>();
    private Key shown;

    RailView(Gui gui, Model model, Actor actor) {
        this.gui = gui;
        this.model = model;
        this.actor = actor;
        this.body = gui.column().width(Length.FILL).height(Length.FILL)
                .padding(Length.rem(1.25f)).gap(Length.rem(0.9f)).scroll(false, true);
    }

    Node node() {
        return body;
    }

    /** Redraw if anything the rail shows has changed. Returns whether it did. */
    synchronized boolean show(Doc doc) {
        Key key = new Key(doc.rail(), doc.pick(), doc.intent(), doc.input().shift(), doc.input().control(),
                doc.work(), doc.plan(), doc.selected());
        if (key.equals(shown)) {
            return false;
        }
        shown = key;
        rebuild(doc, key);
        return true;
    }

    // ------------------------------------------------------------------ building

    private void rebuild(Doc doc, Key key) {
        for (Node n : kids) {
            n.remove();
        }
        kids.clear();
        Rail rail = key.rail();

        // A long name is shortened by count, keeping its extension, and whatever still does not fit is clipped by
        // its box: the header never widens the rail.
        Node basis = gui.text(rail.basis().isEmpty() ? "" : "based on " + shorten(rail.basis(), BASIS_CHARS))
                .font(Type.MONO).textSize(Type.SMALL).textColor(gui.theme().color(Look.ANCHOR)).wordWrap(false);
        gui.landmark(Landmarks.BASIS, basis);
        Node basisBox = gui.row().width(Length.grow(1f)).alignItems(AlignItems.CENTER)
                .justify(Justify.END).scroll(false, false).clip(true).children(basis);
        add(gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).gap(Length.rem(0.6f)).children(
                gui.text("Suggestions").font(Type.UI).textSize(Type.HEADING).textColor(gui.theme().color(Role.INK)),
                basisBox));

        Work work = doc.work();
        if (!work.busy().isEmpty() || !work.notice().isEmpty()) {
            add(notice(work));
        }

        if (rail == Rail.EMPTY) {
            if (!work.marks().isEmpty()) {
                add(marks(work));
            }
            add(gui.box().width(Length.FILL).height(Length.grow(1f)));
            add(note("Select a file and Vexplore will say what it would do with the others. "
                    + "Nothing here covers anything else."));
            return;
        }

        add(select(doc, key, rail));
        add(section(2, "Condition", chips(rail.conditions(), effectiveCondition(doc, rail),
                c -> model.pick(p -> p.with(c)))));
        add(section(3, "Action", actions(rail, doc.pick().act())));
        Act act = doc.pick().act();
        if (act == Act.MOVE || act == Act.COPY) {
            add(section(4, "Destination", places(doc, rail, work)));
        }
        if (!work.marks().isEmpty()) {
            add(marks(work));
        }
        add(gui.box().width(Length.FILL).height(Length.grow(1f)));
        add(summary(doc, rail));
    }

    private Node select(Doc doc, Key key, Rail rail) {
        boolean held = key.shift() || key.control();
        if (held && !key.intent().isEmpty()) {
            List<Option<Set<Path>>> options = new ArrayList<>();
            for (Candidate c : key.intent()) {
                options.add(new Option<>(c.paths(), c.label(), c.count()));
            }
            Node chips = chips(options, key.selected(), paths -> model.select(paths), 1);
            return section(1, key.shift() ? "Select · a range" : "Select · a rule", chips);
        }
        Node chips = chips(rail.scopes(), doc.pick().scope(), s -> model.pick(p -> p.with(s)));
        if (held) {
            Node column = gui.column().width(Length.FILL).gap(Length.rem(0.5f)).children(chips,
                    note(key.shift() ? "Shift is held, and there is no range worth proposing here."
                            : "Control is held, and the picks are not yet examples of a rule."));
            return section(1, "Select", column);
        }
        return section(1, "Select", chips);
    }

    private Suggestions.Condition effectiveCondition(Doc doc, Rail rail) {
        return rail.conditions().stream().anyMatch(o -> o.value() == doc.pick().condition())
                ? doc.pick().condition() : Suggestions.Condition.ALL;
    }

    private Node notice(Work work) {
        boolean busy = !work.busy().isEmpty();
        Node text = gui.text(busy ? work.busy() : work.notice()).font(Type.UI).textSize(Type.META)
                .textColor(gui.theme().color(busy ? Look.TEXT : Role.DIM)).wordWrap(true)
                .width(Length.grow(1f));
        gui.landmark(Landmarks.NOTICE, text);
        Button undo = new Button(gui, "Undo").kind(Button.Kind.GHOST)
                .enabled(!busy && !work.history().isEmpty()).onPress(actor::undo);
        gui.landmark(Landmarks.UNDO, undo.node());
        return gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).gap(Length.rem(0.5f))
                .children(text, undo.node());
    }

    private Node marks(Work work) {
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.5f));
        for (Mark m : work.marks()) {
            Button recall = new Button(gui, m.name()).onPress(() -> actor.recall(m));
            Button drop = new Button(gui, "×").kind(Button.Kind.GHOST).onPress(() -> actor.unmark(m.name()));
            column.append(gui.row().gap(Length.rem(0.3f)).scroll(false, false)
                    .alignItems(AlignItems.CENTER).children(recall.node(), drop.node()));
        }
        return section(0, "Marks", column);
    }

    // ------------------------------------------------------------------ the card

    /** The effect, stated before anything happens: what would be touched, how much, and that nothing has been. */
    private Node summary(Doc doc, Rail rail) {
        Suggestions.Effect fx = rail.effect();
        Act act = doc.pick().act();
        Plan plan = doc.plan();
        Work work = doc.work();
        boolean writes = plan != null;
        // Until a destination is chosen there is no plan to speak of, only the reach of the rail: say that, and
        // nothing about files being "left alone" for want of somewhere to go.
        boolean planned = plan != null && (act == Act.DELETE || act == Act.ARCHIVE || work.destination() != null);
        int n = planned ? plan.count() : fx.count();
        long bytes = planned ? plan.bytes() : fx.bytes();
        String head = n + (n == 1 ? " file" : " files") + " · " + Bytes.format(bytes);
        List<String> names = (planned ? plan.steps().stream().map(s -> s.from().getFileName().toString()).toList()
                : fx.targets().stream().map(Entry::name).toList());
        String list = names.size() <= 3 ? String.join(", ", names)
                : String.join(", ", names.subList(0, 3)) + " and " + (names.size() - 3)
                + " more, all highlighted in the list";

        Node top = gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).children(
                gui.text(head).font(Type.UI).textSize(Type.HEADING).textColor(gui.theme().color(Role.INK)),
                gui.box().width(Length.grow(1f)).height(Length.rem(1f)),
                gui.text(writes || act == Act.MARK ? "nothing has changed yet" : "").font(Type.MONO)
                        .textSize(Type.SMALL).textColor(gui.theme().color(Role.FAINT)).wordWrap(false));

        Node card = gui.column().width(Length.FILL).gap(Length.rem(0.6f))
                .padding(Length.rem(1f)).corner(Length.rem(0.6f))
                .background(gui.theme().color(Look.CARD))
                .border(Length.dp(1), gui.theme().color(Look.LINE_STRONG));
        card.append(top);
        card.append(gui.text(list).font(Type.UI).textSize(Type.META).textColor(gui.theme().color(Look.TEXT))
                .wordWrap(true).width(Length.FILL));

        if (act == Act.MOVE || act == Act.COPY) {
            card.append(destination(work));
        }
        if (planned && !plan.skipped().isEmpty()) {
            card.append(gui.text(plan.skipped().size() + " will be left alone: "
                    + plan.skipped().get(0).reason() + ".").font(Type.UI).textSize(Type.META)
                    .textColor(gui.theme().color(Look.ANCHOR)).wordWrap(true).width(Length.FILL));
        }
        if (act == Act.ARCHIVE && plan != null) {
            card.append(gui.text("into " + plan.archive().toString()).font(Type.MONO).textSize(Type.SMALL)
                    .textColor(gui.theme().color(Role.DIM)).wordWrap(true).width(Length.FILL));
        }

        Button go = primary(act, plan, work, n);
        Node buttons = gui.row().gap(Length.rem(0.6f)).scroll(false, false).children(go.node());
        if (act != Act.MARK) {
            Button keep = new Button(gui, "Keep as Mark").onPress(actor::mark);
            buttons.append(keep.node());
        }
        card.append(buttons);
        card.append(note(hint(act, plan)));
        gui.landmark(Landmarks.SUMMARY, card);
        return card;
    }

    private Button primary(Act act, Plan plan, Work work, int n) {
        String files = n + (n == 1 ? " file" : " files");
        Button b;
        if (act == null) {
            b = new Button(gui, "Choose an action").enabled(false);
        } else if (act == Act.MARK) {
            b = new Button(gui, "Keep as Mark").onPress(actor::mark);
        } else if (act == Act.RENAME) {
            b = new Button(gui, "Rename is not built yet").enabled(false);
        } else {
            String verb = switch (act) {
                case MOVE -> "Move";
                case COPY -> "Copy";
                case DELETE -> "Delete";
                default -> "Archive";
            };
            boolean ready = plan != null && plan.count() > 0 && work.busy().isEmpty()
                    && (act == Act.DELETE || act == Act.ARCHIVE || work.destination() != null);
            boolean needsPlace = (act == Act.MOVE || act == Act.COPY) && work.destination() == null;
            b = new Button(gui, needsPlace ? "Choose where to " + verb.toLowerCase()
                    : plan != null && plan.count() == 0 ? "Nothing to " + verb.toLowerCase()
                    : verb + " " + files).enabled(ready).onPress(actor::run);
        }
        b.kind(Button.Kind.PRIMARY);
        gui.landmark(Landmarks.GO, b.node());
        return b;
    }


    /** The card's line saying where a move or copy goes; the places to choose from are step 4. */
    private Node destination(Work work) {
        Node where = gui.text(work.destination() == null ? "to … (pick a place)" : "to " + work.destination())
                .font(Type.MONO).textSize(Type.SMALL).textColor(gui.theme().color(Role.INK)).wordWrap(true)
                .width(Length.FILL);
        gui.landmark(Landmarks.DESTINATION, where);
        return where;
    }

    /**
     * Step 4, for a move or a copy: where to, guessed from the first three steps. The person's own folder for the
     * kind comes first, then the neighbours that already hold it; a mixed bunch in Desktop, Downloads or Documents
     * is also offered a new archive, which is the Archive action. The folder dialog is last, for everything else.
     */
    private Node places(Doc doc, Rail rail, Work work) {
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.5f));
        int shownCount = 0;
        for (Dest d : work.suggested()) {
            if (shownCount++ >= 3) {
                break;
            }
            String name = d.path().getFileName() == null ? d.path().toString() : d.path().getFileName().toString();
            Button chip = new Button(gui, name + " · " + d.reason()).toggle(true)
                    .show(d.path().equals(work.destination()))
                    .onToggle(v -> actor.destination(d.path()));
            if (shownCount == 1) {
                gui.landmark(Landmarks.DEST_FIRST, chip.node());
            }
            column.append(chip.node());
        }
        List<Kind> kinds = rail.effect().targets().stream().map(Entry::kind).toList();
        if (rail.acts().contains(Act.ARCHIVE) && Destinations.archives(doc.folder(), Startup.home(), kinds)) {
            Button archive = new Button(gui, "To compressed archive")
                    .onPress(() -> model.pick(p -> p.with(Act.ARCHIVE)));
            gui.landmark(Landmarks.DEST_ARCHIVE, archive.node());
            column.append(archive.node());
        }
        Button choose = new Button(gui, "Choose folder…").onPress(actor::choose);
        gui.landmark(Landmarks.CHOOSE, choose.node());
        column.append(choose.node());
        return column;
    }

    /**
     * {@code name} in at most {@code max} characters, cut in the middle so the start and the extension both stay.
     * By count rather than by measuring, as the breadcrumb does, so the header does not re-decide itself as the
     * rail is resized.
     */
    static String shorten(String name, int max) {
        if (name.length() <= max) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        int tail = dot > 0 && name.length() - dot <= max / 3 ? name.length() - dot + 3 : max / 3;
        int head = max - 1 - tail;
        return name.substring(0, head) + "…" + name.substring(name.length() - tail);
    }

    private static String hint(Act act, Plan plan) {
        if (act == null) {
            return "Choose an action to see exactly what it would do. Nothing happens until you press it.";
        }
        return switch (act) {
            case DELETE -> "Deleted files wait in Vexplore's trash. Ctrl+Z puts them back.";
            case MOVE, COPY -> "Ctrl+Z undoes it. A name that already exists there is never overwritten.";
            case ARCHIVE -> "The originals stay where they are. Ctrl+Z removes the archive.";
            case MARK -> "A mark is a group kept under a name, to come back to.";
            case RENAME -> "Renaming by pattern comes next.";
        };
    }

    // ------------------------------------------------------------------ pieces

    private void add(Node n) {
        kids.add(n);
        body.append(n);
    }

    private Node note(String text) {
        return gui.text(text).font(Type.UI).textSize(Type.META).textColor(gui.theme().color(Role.FAINT))
                .wordWrap(true).width(Length.FILL);
    }

    private Node section(int number, String title, Node content) {
        Node label = gui.row().gap(Length.rem(0.6f)).alignItems(AlignItems.CENTER).children(
                gui.text(number == 0 ? "•" : String.valueOf(number)).font(Type.MONO).textSize(Type.SMALL)
                        .textColor(gui.theme().color(Role.FAINT)),
                gui.text(title).font(Type.UI).textSize(Type.RAIL).textColor(gui.theme().color(Role.INK)));
        return gui.column().width(Length.FILL).gap(Length.rem(0.55f)).children(label, content);
    }

    /**
     * Chips, two to a row. The layout engine has no wrapping row yet, so the rail wraps by counting; the chips are
     * the same size whatever they say, and nothing reflows when a label changes.
     */
    private <T> Node chips(List<Option<T>> options, T current, Consumer<T> pick) {
        return chips(options, current, pick, 2);
    }

    /** As above, {@code perRow} to a row: modifier suggestions are sentences, and one fits where two do not. */
    private <T> Node chips(List<Option<T>> options, T current, Consumer<T> pick, int perRow) {
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.5f));
        Node row = null;
        for (int i = 0; i < options.size(); i++) {
            if (i % perRow == 0) {
                row = gui.row().gap(Length.rem(0.5f)).scroll(false, false);
                column.append(row);
            }
            Option<T> o = options.get(i);
            String text = o.count() > 0 ? o.label() + " · " + o.count() : o.label();
            Button chip = new Button(gui, text).toggle(true).show(o.value().equals(current))
                    .onToggle(pressed -> pick.accept(o.value()));
            row.append(chip.node());
        }
        return column;
    }

    private Node actions(Rail rail, Act current) {
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.5f));
        Node row = null;
        List<Act> acts = rail.acts();
        for (int i = 0; i < acts.size(); i++) {
            if (i % 2 == 0) {
                row = gui.row().gap(Length.rem(0.5f)).scroll(false, false);
                column.append(row);
            }
            Act a = acts.get(i);
            Button b = new Button(gui, label(a)).toggle(true).show(a == current)
                    .onToggle(pressed -> model.pick(p -> p.with(a)));
gui.landmark("act." + a.name().toLowerCase(), b.node());
            if (a == Act.RENAME) {
                b.enabled(false);
            }
            row.append(b.node());
        }
        return column;
    }

    static String label(Act a) {
        return switch (a) {
            case MOVE -> "Move to…";
            case COPY -> "Copy to…";
            case ARCHIVE -> "Archive";
            case RENAME -> "Rename…";
            case MARK -> "Mark for later";
            case DELETE -> "Delete (recoverable)";
        };
    }
}
