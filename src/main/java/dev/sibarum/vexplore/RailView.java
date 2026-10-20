package dev.sibarum.vexplore;

import dev.sibarum.vexplore.Doc.Mark;
import dev.sibarum.vexplore.Doc.Work;
import dev.sibarum.vexplore.files.Destinations;
import dev.sibarum.vexplore.files.Destinations.Dest;
import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;
import dev.sibarum.vexplore.ops.Plan;
import dev.sibarum.vexplore.remote.Host;
import dev.sibarum.vexplore.remote.Remotes;
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
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.IconButton;
import dev.vexelray.gui.widget.TextField;

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
                       Work work, Plan plan, Set<Path> selected, Path folder, boolean loading, boolean connecting) {
    }

    /** How many of the config's other hosts the remote panel offers to go to. */
    private static final int OTHER_HOSTS = 4;

    /** How much of a file name the header's "based on" shows before cutting it in the middle. */
    private static final int BASIS_CHARS = 28;

    private final Gui gui;
    private final Model model;
    private final Actor actor;
    private final Browser browser;
    private final Hosts hosts;
    /** Try the address typed in the Connect panel: remember it if it answers, and go to it either way. */
    private final Consumer<String> connect;
    private final Node body;
    private final List<Node> kids = new ArrayList<>();
    private Key shown;
    /** Whether the panel being built is the remote one: read by the pieces both panels share. */
    private boolean remote;
    /** The text fields the panel being shown holds (an address, a folder name, a new name), closed on a rebuild. */
    private final List<TextField> fields = new ArrayList<>();

    RailView(Gui gui, Model model, Actor actor, Browser browser, Hosts hosts, Consumer<String> connect) {
        this.gui = gui;
        this.model = model;
        this.actor = actor;
        this.browser = browser;
        this.hosts = hosts;
        this.connect = connect;
        this.body = gui.column().width(Length.FILL).height(Length.FILL)
                .padding(Length.rem(1.25f)).gap(Length.rem(0.9f)).scroll(false, true);
    }

    Node node() {
        return body;
    }

    /** Redraw if anything the rail shows has changed. Returns whether it did. */
    synchronized boolean show(Doc doc) {
        Key key = new Key(doc.rail(), doc.pick(), doc.intent(), doc.input().shift(), doc.input().control(),
                doc.work(), doc.plan(), doc.selected(), doc.folder(), doc.loading(), doc.input().connecting());
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
        for (TextField t : fields) {
            t.close();
        }
        fields.clear();
        remote = doc.remote();
        if (doc.input().connecting()) {
            rebuildConnect(doc);
            return;
        }
        if (remote) {
            rebuildRemote(doc, key);
            return;
        }
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
        add(section(3, "Action", actions(rail.acts(), doc.pick().act())));
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

    // ------------------------------------------------------------------ the Connect panel

    /**
     * New host, from the title bar's Open menu: a host typed in, in the rail, because the rail is the place that
     * exists to change and nothing in this window is drawn over anything else. Connect (or Enter) goes to its home
     * folder and, if it answers, remembers it beside the config's hosts; Cancel puts the rail back as it was.
     */
    private void rebuildConnect(Doc doc) {
        add(gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).children(
                gui.text("Connect").font(Type.UI).textSize(Type.HEADING).textColor(gui.theme().color(Role.INK))));
        Work work = doc.work();
        if (!work.busy().isEmpty() || !work.notice().isEmpty()) {
            add(notice(work));
        }

        TextField address = field("");
        address.node().width(Length.FILL);
        address.onSubmit(connect);
        gui.landmark(Landmarks.HOST, address.node());
        Button go = new Button(gui, "Connect").kind(Button.Kind.PRIMARY).onPress(() -> connect.accept(address.text()));
        gui.landmark(Landmarks.CONNECT, go.node());
        Button cancel = new Button(gui, "Cancel").kind(Button.Kind.GHOST).onPress(() -> model.connecting(false));
        Node buttons = gui.row().gap(Length.rem(0.6f)).scroll(false, false).children(go.node(), cancel.node());
        add(section(0, "New host", gui.column().width(Length.FILL).gap(Length.rem(0.6f)).children(
                address.node(),
                note("A host as ssh takes it: user@host, user@host:2222, or a name from ~/.ssh/config. "
                        + "Vexplore runs your own ssh, so its keys, agent and config apply, and nothing here asks "
                        + "for a password."),
                buttons)));

        List<Host> known = hosts.added();
        if (!known.isEmpty()) {
            add(section(0, "Added before", note(String.join(", ",
                    known.stream().map(Host::authority).toList()))));
        }
        add(gui.box().width(Length.FILL).height(Length.grow(1f)));
        add(note("A host that answers is kept in Vexplore's own settings and offered in the Open menu and the "
                + "tree. Your ssh config is never changed."));
    }

    // ------------------------------------------------------------------ the remote panel

    /**
     * The rail for a folder on another host. The same column and the same steps, with what is offered there: Select
     * and Condition read only the listing, so they are as they are here; Action is <em>Download to…</em>, a rename, a
     * delete into the host's own trash, or a mark, and a download's step 4 offers places on this computer, never the
     * server's neighbours. With nothing selected the panel says where it is and whether that host answered, and
     * offers where to go next (home, up, the other hosts, back to this computer, or an address typed in) and a new
     * folder.
     */
    private void rebuildRemote(Doc doc, Key key) {
        Rail rail = key.rail();
        Host host = Remotes.host(doc.folder());
        String tag = rail.basis().isEmpty() ? "on " + host.label() : "based on " + shorten(rail.basis(), BASIS_CHARS);
        Node basis = gui.text(tag).font(Type.MONO).textSize(Type.SMALL).textColor(gui.theme().color(Look.ANCHOR))
                .wordWrap(false);
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
            add(section(0, "Remote", connection(doc, host)));
            add(section(0, "Go to", places(doc, host)));
            if (!doc.loading() && Remotes.connected(doc.folder())) {
                add(section(0, "New folder", newFolder()));
            }
            if (!work.marks().isEmpty()) {
                add(marks(work));
            }
            add(gui.box().width(Length.FILL).height(Length.grow(1f)));
            add(note("Select a file and Vexplore will say what it would do with it: bring it to this computer, "
                    + "rename it, or delete it into a trash on the server. Nothing changes until you press."));
            return;
        }

        add(select(doc, key, rail));
        add(section(2, "Condition", chips(rail.conditions(), effectiveCondition(doc, rail),
                c -> model.pick(p -> p.with(c)))));
        add(section(3, "Action", actions(List.of(Act.COPY, Act.RENAME, Act.DELETE, Act.MARK), doc.pick().act())));
        if (doc.pick().act() == Act.COPY) {
            add(section(4, "Download to", places(doc, rail, work)));
        }
        if (!work.marks().isEmpty()) {
            add(marks(work));
        }
        add(gui.box().width(Length.FILL).height(Length.grow(1f)));
        add(summary(doc, rail));
    }

    /**
     * Where this is, said as an address, and whether the host answered. When it did not, ssh's own words say why
     * ("Could not resolve hostname", "Permission denied (publickey)"), because an unreachable folder lists as an
     * empty one and only this tells the two apart.
     */
    private Node connection(Doc doc, Host host) {
        String problem = Remotes.problem(doc.folder());
        boolean up = Remotes.connected(doc.folder());
        String state = doc.loading() ? "connecting to " + host.label() + "…"
                : up ? "connected over SFTP"
                : problem.isEmpty() ? "not connected" : "could not connect";
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.35f)).children(
                gui.text(Remotes.address(doc.folder())).font(Type.MONO).textSize(Type.SMALL)
                        .textColor(gui.theme().color(Role.INK)).wordWrap(true).width(Length.FILL),
                gui.text(state).font(Type.UI).textSize(Type.META).textColor(gui.theme().color(Role.DIM)));
        if (!doc.loading() && !up && !problem.isEmpty()) {
            Node why = gui.text(problem).font(Type.UI).textSize(Type.META)
                    .textColor(gui.theme().color(Look.ANCHOR)).wordWrap(true).width(Length.FILL);
            gui.landmark(Landmarks.PROBLEM, why);
            column.append(why);
        }
        return column;
    }

    /**
     * Where to go from here: this host's home and the folder above, the config's other hosts, this computer, and an
     * address field for a host the config does not name.
     */
    private Node places(Doc doc, Host host) {
        List<Option<Runnable>> options = new ArrayList<>();
        Path folder = doc.folder();
        options.add(new Option<>(() -> browser.go(Remotes.home(host)), "Home folder", 0));
        if (folder.getParent() != null) {
            options.add(new Option<>(() -> browser.go(folder.getParent()), "Up one folder", 0));
        }
        int others = 0;
        for (Host h : hosts.all()) {
            if (!h.equals(host) && others++ < OTHER_HOSTS) {
                options.add(new Option<>(() -> browser.go(Remotes.home(h)), h.label(), 0));
            }
        }
        options.add(new Option<>(() -> browser.go(Startup.downloads()), "This computer", 0));
        Node column = chips(options, null, Runnable::run);

        TextField address = field("");
        address.node().width(Length.FILL);
        address.onSubmit(text -> Remotes.parse(text).ifPresentOrElse(browser::go,
                () -> model.work(w -> w.noting("Not an address: " + text.strip()
                        + ". Try user@host:path or sftp://host/path."))));
        gui.landmark(Landmarks.ADDRESS, address.node());
        column.append(address.node());
        column.append(note("Another host: type user@host:path or sftp://host/path and press Enter. "
                + "It is reached with your own ssh, so its keys and config apply."));
        return column;
    }

    /** A name, and Enter: an empty folder here, selected once it is listed, and undone by Ctrl+Z while still empty. */
    private Node newFolder() {
        TextField name = field("");
        name.node().width(Length.FILL);
        name.onSubmit(actor::makeFolder);
        gui.landmark(Landmarks.NEW_FOLDER, name.node());
        return gui.column().width(Length.FILL).gap(Length.rem(0.5f)).children(name.node(),
                note("Type a name and press Enter to make an empty folder here."));
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
                .enabled(!busy && actor.history().canUndo()).onPress(() -> gui.undo(actor.history()));
        gui.landmark(Landmarks.UNDO, undo.node());
        return gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).gap(Length.rem(0.5f))
                .scroll(false, false)
                .children(text, undo.node());
    }

    private Node marks(Work work) {
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.5f));
        for (Mark m : work.marks()) {
            Button recall = new Button(gui, m.name()).onPress(() -> actor.recall(m));
            IconButton drop = new IconButton(gui, Icon.CLOSE, "Forget " + m.name())
                    .onPress(() -> actor.unmark(m.name()));
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
        boolean planned = plan != null && (act == Act.DELETE || act == Act.ARCHIVE || act == Act.RENAME
                || work.destination() != null);
        // A rename is of the one selected; what it says about counting is the selection, whatever the plan holds.
        boolean counted = planned && act != Act.RENAME;
        int n = counted ? plan.count() : fx.count();
        long bytes = counted ? plan.bytes() : fx.bytes();
        String head = n + (n == 1 ? " file" : " files") + " · " + Bytes.format(bytes);
        List<String> names = (counted ? plan.steps().stream().map(s -> s.from().getFileName().toString()).toList()
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
        if (act == Act.RENAME && plan != null) {
            card.append(rename(doc, plan));
        } else if (planned && !plan.skipped().isEmpty()) {
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
        } else {
            String verb = switch (act) {
                case MOVE -> "Move";
                case COPY -> remote ? "Download" : work.destination() != null && Remotes.is(work.destination())
                        ? "Upload" : "Copy";
                case DELETE -> "Delete";
                case RENAME -> "Rename";
                default -> "Archive";
            };
            boolean ready = plan != null && plan.count() > 0 && work.busy().isEmpty()
                    && (act == Act.DELETE || act == Act.ARCHIVE || act == Act.RENAME || work.destination() != null);
            boolean needsPlace = (act == Act.MOVE || act == Act.COPY) && work.destination() == null;
            b = new Button(gui, needsPlace ? "Choose where to " + verb.toLowerCase()
                    : act == Act.RENAME ? verb
                    : plan != null && plan.count() == 0 ? "Nothing to " + verb.toLowerCase()
                    : verb + " " + files).enabled(ready).onPress(actor::run);
        }
        b.kind(Button.Kind.PRIMARY);
        gui.landmark(Landmarks.GO, b.node());
        return b;
    }


    /**
     * A rename's part of the card: the new name, typed and then Enter, and what that would do or why it would not.
     * Enter is what puts the name in the plan, so the card can say "to final.txt" before the button does it; the
     * field starts from the name being changed.
     */
    private Node rename(Doc doc, Plan plan) {
        Work work = doc.work();
        String current = plan.steps().isEmpty() ? plan.skipped().isEmpty() ? "" : name(plan.skipped().get(0).path())
                : name(plan.steps().get(0).from());
        TextField field = field(work.name() != null ? work.name() : current);
        field.node().width(Length.FILL);
        field.onSubmit(text -> model.work(w -> w.naming(text)));
        gui.landmark(Landmarks.RENAME_TO, field.node());
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.4f)).children(field.node());
        if (plan.count() == 1) {
            column.append(gui.text("to " + name(plan.steps().get(0).to())).font(Type.MONO).textSize(Type.SMALL)
                    .textColor(gui.theme().color(Role.INK)).wordWrap(true).width(Length.FILL));
        } else if (!plan.skipped().isEmpty()) {
            // Nothing typed yet is a prompt, not a problem; a name that will not do is said in the warning colour.
            String why = plan.skipped().get(0).reason();
            column.append(gui.text(Character.toUpperCase(why.charAt(0)) + why.substring(1) + ".").font(Type.UI)
                    .textSize(Type.META).textColor(gui.theme().color(work.name() == null ? Role.DIM : Look.ANCHOR))
                    .wordWrap(true).width(Length.FILL));
        }
        return column;
    }

    private static String name(Path p) {
        return p.getFileName() == null ? p.toString() : p.getFileName().toString();
    }

    /** The card's line saying where a move or copy goes; the places to choose from are step 4. */
    private Node destination(Work work) {
        Node where = gui.text(work.destination() == null ? "to … (pick a place)"
                        : "to " + Remotes.address(work.destination()))
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
        if (!remote && rail.acts().contains(Act.ARCHIVE)
                && Destinations.archives(doc.folder(), Startup.home(), kinds)) {
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

    private String hint(Act act, Plan plan) {
        if (act == null) {
            return "Choose an action to see exactly what it would do. Nothing happens until you press it.";
        }
        if (remote && act == Act.COPY) {
            return "A download is a copy: the server is not changed. Ctrl+Z removes what was downloaded.";
        }
        if (remote && act == Act.DELETE) {
            return "Deleted files wait in " + Remotes.TRASH + " in your home folder on the server. "
                    + "Ctrl+Z puts them back.";
        }
        return switch (act) {
            case DELETE -> "Deleted files wait in Vexplore's trash. Ctrl+Z puts them back.";
            case MOVE, COPY -> "Ctrl+Z undoes it. A name that already exists there is never overwritten.";
            case ARCHIVE -> "The originals stay where they are. Ctrl+Z removes the archive.";
            case MARK -> "A mark is a group kept under a name, to come back to.";
            case RENAME -> "Type the new name and press Enter to see it here; nothing is renamed until you press "
                    + "Rename. Ctrl+Z puts the old name back.";
        };
    }

    /** A text field that lives as long as the panel it is in, starting from {@code text}. */
    private TextField field(String text) {
        TextField t = new TextField(gui, text);
        fields.add(t);
        return t;
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

    private Node actions(List<Act> acts, Act current) {
        Node column = gui.column().width(Length.FILL).gap(Length.rem(0.5f));
        Node row = null;
        for (int i = 0; i < acts.size(); i++) {
            if (i % 2 == 0) {
                row = gui.row().gap(Length.rem(0.5f)).scroll(false, false);
                column.append(row);
            }
            Act a = acts.get(i);
            String text = remote && a == Act.COPY ? "Download to…" : label(a);
            Button b = new Button(gui, text).toggle(true).show(a == current)
                    .onToggle(pressed -> model.pick(p -> p.with(a)));
gui.landmark("act." + a.name().toLowerCase(), b.node());
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
