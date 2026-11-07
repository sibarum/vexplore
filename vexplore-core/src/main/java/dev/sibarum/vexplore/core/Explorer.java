package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Accept;
import dev.sibarum.vexplore.core.Messages.Activated;
import dev.sibarum.vexplore.core.Messages.Back;
import dev.sibarum.vexplore.core.Messages.Carry;
import dev.sibarum.vexplore.core.Messages.Card;
import dev.sibarum.vexplore.core.Messages.Chip;
import dev.sibarum.vexplore.core.Messages.Dest;
import dev.sibarum.vexplore.core.Messages.Destine;
import dev.sibarum.vexplore.core.Messages.Destinations;
import dev.sibarum.vexplore.core.Messages.Destining;
import dev.sibarum.vexplore.core.Messages.Conflict;
import dev.sibarum.vexplore.core.Messages.Docked;
import dev.sibarum.vexplore.core.Messages.Dropped;
import dev.sibarum.vexplore.core.Messages.Check;
import dev.sibarum.vexplore.core.Messages.Checked;
import dev.sibarum.vexplore.core.Messages.Checking;
import dev.sibarum.vexplore.core.Messages.Chosen;
import dev.sibarum.vexplore.core.Messages.Compared;
import dev.sibarum.vexplore.core.Messages.Find;
import dev.sibarum.vexplore.core.Messages.ForgetMark;
import dev.sibarum.vexplore.core.Messages.Found;
import dev.sibarum.vexplore.core.Messages.Fresh;
import dev.sibarum.vexplore.core.Messages.Freshness;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.sibarum.vexplore.core.Messages.HandedOff;
import dev.sibarum.vexplore.core.Messages.Handing;
import dev.sibarum.vexplore.core.Messages.Is;
import dev.sibarum.vexplore.core.Messages.Listed;
import dev.sibarum.vexplore.core.Messages.Listing;
import dev.sibarum.vexplore.core.Messages.Mark;
import dev.sibarum.vexplore.core.Messages.Marks;
import dev.sibarum.vexplore.core.Messages.Menu;
import dev.sibarum.vexplore.core.Messages.Navigate;
import dev.sibarum.vexplore.core.Messages.Naming;
import dev.sibarum.vexplore.core.Messages.OpenWith;
import dev.sibarum.vexplore.core.Messages.OpenWith.With;
import dev.sibarum.vexplore.core.Messages.Offer;
import dev.sibarum.vexplore.core.Messages.Offers;
import dev.sibarum.vexplore.core.Messages.Pair;
import dev.sibarum.vexplore.core.Messages.Ordered;
import dev.sibarum.vexplore.core.Messages.Picked;
import dev.sibarum.vexplore.core.Messages.Planned;
import dev.sibarum.vexplore.core.Messages.Planning;
import dev.sibarum.vexplore.core.Messages.Previewed;
import dev.sibarum.vexplore.core.Messages.Previewing;
import dev.sibarum.vexplore.core.Messages.Progress;
import dev.sibarum.vexplore.core.Messages.Ran;
import dev.sibarum.vexplore.core.Messages.Recall;
import dev.sibarum.vexplore.core.Messages.Recalled;
import dev.sibarum.vexplore.core.Messages.RecallMark;
import dev.sibarum.vexplore.core.Messages.Recorded;
import dev.sibarum.vexplore.core.Messages.Redo;
import dev.sibarum.vexplore.core.Messages.Rename;
import dev.sibarum.vexplore.core.Messages.Remember;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Run;
import dev.sibarum.vexplore.core.Messages.Shown;
import dev.sibarum.vexplore.core.Messages.Side;
import dev.sibarum.vexplore.core.Messages.Slot;
import dev.sibarum.vexplore.core.Messages.Stirred;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.sibarum.vexplore.core.Messages.StopCheck;
import dev.sibarum.vexplore.core.Messages.Suggested;
import dev.sibarum.vexplore.core.Messages.Swap;
import dev.sibarum.vexplore.core.Messages.Summary;
import dev.sibarum.vexplore.core.Messages.Tally;
import dev.sibarum.vexplore.core.Messages.Resolve;
import dev.sibarum.vexplore.core.Messages.Twins;
import dev.sibarum.vexplore.core.Messages.TwoFolders;
import dev.sibarum.vexplore.core.Messages.Undo;
import dev.sibarum.vexplore.core.Messages.Unwatch;
import dev.sibarum.vexplore.core.Messages.Unwatched;
import dev.sibarum.vexplore.core.Messages.Use;
import dev.sibarum.vexplore.core.Messages.Watch;
import dev.sibarum.vexplore.core.Messages.Why;
import dev.sibarum.vexplore.core.Suggestions.Act;
import dev.sibarum.vexplore.core.Suggestions.Pick;
import dev.sibarum.vexplore.core.Suggestions.Rail;
import dev.sibarum.vexplore.core.Work.Dock;
import dev.sibarum.vexplore.core.Work.Entry;
import dev.sibarum.vexplore.core.Work.Running;
import dev.sibarum.vexplore.core.Work.Watched;
import dev.sibarum.vexplore.core.Work.Way;
import dev.vexelray.framework.api.Component;
import dev.vexelray.framework.api.Hears;
import dev.vexelray.framework.api.Publishes;
import dev.vexelray.framework.api.Withers;
import dev.vexelray.framework.core.CloseRequested;
import dev.vexelray.framework.core.Modifiers;
import dev.vexelray.framework.core.Open;
import dev.vexelray.framework.core.Opened;
import dev.vexelray.framework.core.WindowOpened;
import sibarum.atchung.Inbox;
import sibarum.atchung.Ledger;
import sibarum.atchung.Out;
import sibarum.atchung.Step;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


/**
 * The controller: every decision the application makes, as declared steps on one state, reached only by messages.
 * What the template's editor became once its files and tabs were gone: which folder is shown and what is in
 * it, what is selected there, what the path field names, and the folder brought back from the last session.
 *
 * <h2>A state, and the steps that change it</h2>
 *
 * <p>Everything it knows is one immutable value, a {@link State}, held by a {@link Ledger}. Nothing changes it but a
 * step declared below, and a step is a static method of the shape {@code (state, message, out) → next state}: it reads
 * the state and the message, says what it sends through its {@link Out}, and returns what the state is now. The ledger
 * runs one step at a time on the logic lane and commits what it returns, so no step sees another half-done, nothing
 * needs a lock, and a step that throws changes nothing and sends nothing. The processor reads each step's body and
 * refuses one that captures, waits or touches the disk (chains.md's rule 1, design F): a step sees its state, its
 * message and its {@code Out}, and that is all.
 *
 * <h2>What it hears</h2>
 *
 * <p>Each message it acts on runs one step ({@link Ledger.Builder#on}): what the view reports, and what the jobs reply.
 * The list in the constructor is the whole of what it hears, the way a component's {@code @Subscribe} methods are.
 *
 * <h2>Effects are jobs</h2>
 *
 * <p>The disk is the jobs' ({@link ListFolder}, {@link FindPath}, {@link ReadSession}, {@link WriteSession}, and
 * the framework's own {@code Open} for handing a file to the desktop): a step sends a request and a later step acts
 * on the reply. A slow disk is a slow answer, never a stalled lane. Jobs answer in no order, so a listing carries a
 * ticket, and one overtaken by a later ask is dropped: a slow share answering after the person has moved on never
 * shows the folder they left.
 *
 * <h2>Where it is built</h2>
 *
 * <p>A {@code @Component} that takes an {@link Inbox} is given its own, on the framework's logic lane: that is where
 * its messages arrive and where its steps run, on one thread with every other such component and never two at once.
 * So it declares no {@code @Subscribe}: what it hears is the list above, declared again as {@code @Hears} for the
 * processor, which cannot read a constructor (the lane checks at startup that the two agree). {@link Core} lists it, so
 * {@code -gui}'s generated wiring builds it; {@code ExplorerTest} builds it on a tick of its own, which is the same
 * thing without a window.
 */
@Component
@Hears({WindowOpened.class, Recalled.class, Go.class, Found.class, Navigate.class, Listed.class, Chosen.class,
        Activated.class, Opened.class, Modifiers.class, Ordered.class, Picked.class, Carry.class, Progress.class,
        Ran.class, Undo.class, Redo.class, Destinations.class, Destine.class, Planned.class,
        Rename.class, RecallMark.class, ForgetMark.class, Previewed.class, OpenWith.class, HandedOff.class,
        Menu.class, Stirred.class, Unwatched.class, Freshness.class, Use.class, TwoFolders.class, Swap.class,
        Back.class, Dropped.class, Accept.class, Checking.class, Checked.class, Resolve.class})
@Publishes({Shown.class, Summary.class, Status.class, Find.class, Listing.class, Open.class, Recall.class,
        Remember.class, Suggested.class, Run.class, Recorded.class,
        Destining.class, Planning.class, Naming.class, Marks.class,
        Previewing.class, Docked.class, Handing.class, Watch.class, Unwatch.class, Pair.class,
        Fresh.class, Compared.class, Offers.class, Check.class, StopCheck.class, Conflict.class})
public final class Explorer {

    /** How many of each message the inbox holds before a sender is refused. A person sends one at a time. */
    static final int CAPACITY = 256;

    // ------------------------------------------------------------------ the state

    /**
     * Everything the explorer knows, in one value.
     *
     * <p>Each list's folder is a {@link Pane}: {@code from} always, and {@code to} in Two Folders. The tree, the
     * breadcrumb, the rail and the dock work from the one {@code used}, so {@link #folder}, {@link #rows},
     * {@link #selected} and the rest read that one, and a step that knows nothing of two folders reads them as before.
     *
     * @param from    the left list's folder, the one there always is
     * @param to      the right list's, in Two Folders; none in one
     * @param used    the list clicked last, which the tree and the rail work from
     * @param tickets the count of listings asked for, both lists': a reply's ticket says which list asked
     * @param pick    the rail's Select, Condition and Action so far
     * @param rail    what the rail last offered: a chip pressed is resolved against it
     * @param started the folder the application started in, shown when there is no other
     * @param armed   whether the restore is done: before then a write would overwrite what is being restored
     * @param last    the session last sent to be remembered, so an unchanged one is not written again
     * @param work    what has been carried out and can be taken back, and the run in flight
     * @param held    the modifier keys held in the main window: Shift or Control held turns the rail's Select row
     *                into what the key proposes ({@link Intents})
     */
    @Withers
    record State(Pane from, Optional<Pane> to, Side used, long tickets, Pick pick, Rail rail,
                 Optional<Path> started, boolean armed, Optional<Remember> last, Work work, Modifiers held)
            implements ExplorerStateWithers {

        static State initial() {
            return new State(Pane.NONE, Optional.empty(), Side.FROM, 0L, Pick.NONE, Rail.EMPTY, Optional.empty(),
                    false, Optional.empty(), Work.NONE, Modifiers.released(CloseRequested.MAIN));
        }

        /** Whether a key that proposes is held: Shift for a range, Control for a rule. */
        boolean proposing() {
            return held.shift() || held.control();
        }

        /** What the held key proposes for the selection in the list used, or nothing. */
        List<Intents.Candidate> intents() {
            if (selected().isEmpty()) {
                return List.of();
            }
            if (held.shift()) {
                return Intents.shift(pane().shown(), selected(), zone());
            }
            return held.control() ? Intents.control(pane().shown(), selected(), today(), zone()) : List.of();
        }

        /** The list on {@code side}: FROM's when there is no TO. */
        Pane pane(Side side) {
            return side == Side.TO && to.isPresent() ? to.get() : from;
        }

        /** The list used. */
        Pane pane() {
            return pane(used);
        }

        /** Which list asked for listing {@code ticket}, or none: one overtaken by a later ask. */
        Optional<Side> asker(long ticket) {
            if (from.asked() == ticket) {
                return Optional.of(Side.FROM);
            }
            return to.isPresent() && to.get().asked() == ticket ? Optional.of(Side.TO) : Optional.empty();
        }

        Optional<Path> folder() {
            return pane().folder();
        }

        long asked() {
            return pane().asked();
        }

        Set<Path> select() {
            return pane().select();
        }

        List<Row> rows() {
            return pane().rows();
        }

        LocalDate today() {
            return pane().today();
        }

        ZoneId zone() {
            return pane().zone();
        }

        Set<Path> selected() {
            return pane().selected();
        }

        /** The list on {@code side} is now {@code value}. */
        State withPane(Side side, Pane value) {
            return side == Side.TO && to.isPresent() ? withTo(Optional.of(value)) : withFrom(value);
        }

        /** A new listing asked for: the next ticket, and the rail put down, since it was about rows going away. */
        private State askingAnew() {
            return withTickets(tickets + 1).withPick(Pick.NONE).withRail(Rail.EMPTY);
        }

        /** Asking for {@code value}'s listing in the list used: nothing is picked in a folder not yet shown. */
        State asking(Path value, Set<Path> then) {
            return askingAnew().withPane(used, pane().asking(value, then, tickets + 1));
        }

        /** Back, in the list used: its folder before this one asked for, nothing picked in it yet. */
        State returning() {
            return askingAnew().withPane(used, pane().returning(tickets + 1));
        }

        /** The list on {@code side} used from now: the rail is put down, since it was about the other list's rows. */
        State using(Side side) {
            return withUsed(side).withPick(Pick.NONE).withRail(Rail.EMPTY);
        }

        /** Two Folders with TO asking for {@code folder}'s listing, or back to one, FROM used. */
        State twoFolders(Optional<Path> folder) {
            if (folder.isEmpty()) {
                return withTo(Optional.empty()).using(Side.FROM);
            }
            return withTickets(tickets + 1).withTo(Optional.of(Pane.NONE.asking(folder.get(), Set.of(), tickets + 1)));
        }

        /** FROM and TO trade places, and their watches with them; the list used is the same folder as before. */
        State swapped() {
            Work w = work.withWatched(Side.FROM, work.watched(Side.TO)).withWatched(Side.TO, work.watched(Side.FROM));
            return withFrom(to.orElseThrow()).withTo(Optional.of(from)).withUsed(used.other()).withWork(w);
        }

        /**
         * The list on {@code side} asked for again after a run: what is selected is kept, so the rows still there
         * stay selected in the explorer as they do in the list. The pick is put down: what it picked has been done.
         */
        State relisting(Side side) {
            return askingAnew().withPane(side, pane(side).relisting(tickets + 1));
        }

        /**
         * The list on {@code side} asked for again because its folder changed on the disk: the selection and the
         * rail's pick are kept, since the person did nothing. A download writing beside a pick half made never
         * undoes it.
         */
        State stirring(Side side) {
            return withTickets(tickets + 1).withPane(side, pane(side).relisting(tickets + 1));
        }

        State withSelected(Set<Path> value) {
            return withPane(used, pane().withSelected(value));
        }

        State withStarted(Path value) {
            return withStarted(Optional.of(value));
        }

        State withArmed() {
            return withArmed(true);
        }

        State withLast(Remember value) {
            return withLast(Optional.of(value));
        }

        /** The row at {@code path} in the list used, or none: one the list showed before this listing, or never. */
        Optional<Row> row(Path path) {
            return pane().row(path);
        }
    }

    // ------------------------------------------------------------------ the declarations

    private final Ledger<State> ledger;

    public Explorer(Inbox inbox) {
        Ledger.Builder<State> b = Ledger.of(State.initial());

        Step<State, WindowOpened> begin = b.step("begin", Explorer::begin);
        Step<State, Recalled> recalled = b.step("recalled", Explorer::recalled);
        Step<State, Go> go = b.step("go", Explorer::go);
        Step<State, Found> found = b.step("found", Explorer::found);
        Step<State, Navigate> navigate = b.step("navigate", Explorer::navigate);
        Step<State, Listed> listed = b.step("listed", Explorer::listed);
        Step<State, Chosen> chosen = b.step("chosen", Explorer::chosen);
        Step<State, Activated> activated = b.step("activated", Explorer::activated);
        Step<State, Opened> started = b.step("started", Explorer::started);
        Step<State, Modifiers> held = b.step("held", Explorer::held);
        Step<State, Ordered> ordered = b.step("ordered", Explorer::ordered);
        Step<State, Picked> picked = b.step("picked", Explorer::picked);
        Step<State, Carry> carry = b.step("carry", Explorer::carry);
        Step<State, Progress> progress = b.step("progress", Explorer::progress);
        Step<State, Ran> ran = b.step("ran", Explorer::ran);
        Step<State, Undo> undo = b.step("undo", Explorer::undo);
        Step<State, Redo> redo = b.step("redo", Explorer::redo);
        Step<State, Destinations> destinations = b.step("destinations", Explorer::destinations);
        Step<State, Destine> destine = b.step("destine", Explorer::destine);
        Step<State, Planned> planned = b.step("planned", Explorer::planned);
        Step<State, Rename> rename = b.step("rename", Explorer::rename);
        Step<State, RecallMark> recallMark = b.step("recallMark", Explorer::recallMark);
        Step<State, ForgetMark> forgetMark = b.step("forgetMark", Explorer::forgetMark);
        Step<State, Previewed> previewed = b.step("previewed", Explorer::previewed);
        Step<State, OpenWith> openWith = b.step("openWith", Explorer::openWith);
        Step<State, HandedOff> handedOff = b.step("handedOff", Explorer::handedOff);
        Step<State, Menu> menu = b.step("menu", Explorer::menu);
        Step<State, Stirred> stirred = b.step("stirred", Explorer::stirred);
        Step<State, Unwatched> unwatched = b.step("unwatched", Explorer::unwatched);
        Step<State, Freshness> freshness = b.step("freshness", Explorer::freshness);
        Step<State, Use> use = b.step("use", Explorer::used);
        Step<State, TwoFolders> twoFolders = b.step("twoFolders", Explorer::twoFolders);
        Step<State, Swap> swap = b.step("swap", Explorer::swap);
        Step<State, Back> back = b.step("back", Explorer::back);
        Step<State, Dropped> dropped = b.step("dropped", Explorer::dropped);
        Step<State, Accept> accept = b.step("accept", Explorer::accept);
        Step<State, Checking> checking = b.step("checking", Explorer::checking);
        Step<State, Checked> checked = b.step("checked", Explorer::checked);
        Step<State, Resolve> resolve = b.step("resolve", Explorer::resolve);

        b.on(WindowOpened.TOPIC, begin)
                .on(Messages.RECALLED_TOPIC, recalled)
                .on(Messages.GO_TOPIC, go)
                .on(Messages.FOUND_TOPIC, found)
                .on(Messages.NAVIGATE_TOPIC, navigate)
                .on(Messages.LISTED_TOPIC, listed)
                .on(Messages.CHOSEN_TOPIC, chosen)
                .on(Messages.ACTIVATED_TOPIC, activated)
                .on(Opened.TOPIC, started)
                .on(Modifiers.TOPIC, held)
                .on(Messages.ORDERED_TOPIC, ordered)
                .on(Messages.PICKED_TOPIC, picked)
                .on(Messages.CARRY_TOPIC, carry)
                .on(Messages.PROGRESS_TOPIC, progress)
                .on(Messages.RAN_TOPIC, ran)
                .on(Messages.UNDO_TOPIC, undo)
                .on(Messages.REDO_TOPIC, redo)
                .on(Messages.DESTINATIONS_TOPIC, destinations)
                .on(Messages.DESTINE_TOPIC, destine)
                .on(Messages.PLANNED_TOPIC, planned)
                .on(Messages.RENAME_TOPIC, rename)
                .on(Messages.RECALL_MARK_TOPIC, recallMark)
                .on(Messages.FORGET_MARK_TOPIC, forgetMark)
                .on(Messages.PREVIEWED_TOPIC, previewed)
                .on(Messages.OPEN_WITH_TOPIC, openWith)
                .on(Messages.HANDED_OFF_TOPIC, handedOff)
                .on(Messages.MENU_TOPIC, menu)
                .on(Messages.STIRRED_TOPIC, stirred)
                .on(Messages.UNWATCHED_TOPIC, unwatched)
                .on(Messages.FRESHNESS_TOPIC, freshness)
                .on(Messages.USE_TOPIC, use)
                .on(Messages.TWO_FOLDERS_TOPIC, twoFolders)
                .on(Messages.SWAP_TOPIC, swap)
                .on(Messages.BACK_TOPIC, back)
                .on(Messages.DROPPED_TOPIC, dropped)
                .on(Messages.ACCEPT_TOPIC, accept)
                .on(Messages.CHECKING_TOPIC, checking)
                .on(Messages.CHECKED_TOPIC, checked)
                .on(Messages.RESOLVE_TOPIC, resolve);
        ledger = b.build(inbox, "explorer", CAPACITY);
    }

    /** The explorer's state, as last committed: for reading, from any thread. */
    public State state() {
        return ledger.state().value();
    }

    // ------------------------------------------------------------------ the session, brought back

    /**
     * The main window exists: ask what there is to bring back. Heard from the framework, so a test that builds the tree
     * headless, which never opens a window, restores nothing and writes no settings. Any other window opening (the
     * framework's About) changes nothing here.
     */
    private static State begin(State s, WindowOpened opened, Out out) {
        if (!opened.main()) {
            return s;
        }
        out.send(Messages.RECALL_TOPIC, new Recall());
        return s;
    }

    /**
     * Show the path the command line named, else the folder the last session showed, else the folder the application
     * started in. A path is looked at before it is shown, since what was remembered may have gone.
     */
    private static State recalled(State s, Recalled r, Out out) {
        State next = s.withStarted(r.started())
                .withWork(s.work().withTrash(r.trash()).withMarks(r.marks()));
        out.send(Messages.MARKS_TOPIC, marks(next));
        if (!r.arguments().isEmpty()) {
            out.send(Messages.FIND_TOPIC, new Find(r.arguments().getFirst(), Why.ARGUMENT));
            return next;
        }
        if (r.folder().isPresent()) {
            out.send(Messages.FIND_TOPIC, new Find(r.folder().get(), Why.RESTORE_FOLDER));
            return next;
        }
        return remember(show(next, r.started(), Set.of(), out).withArmed(), out);
    }

    /**
     * Send the session to be remembered if it differs from what was last sent. Not before the restore is done, which
     * would overwrite what is being brought back.
     */
    private static State remember(State s, Out out) {
        if (!s.armed()) {
            return s;
        }
        Remember now = new Remember(s.from().folder(), s.work().marks());
        if (s.last().equals(Optional.of(now))) {
            return s;
        }
        out.send(Messages.REMEMBER_TOPIC, now);
        return s.withLast(now);
    }

    // ------------------------------------------------------------------ the path field

    /** What the path field was given. A relative path is relative to the folder shown. */
    private static State go(State s, Go go, Out out) {
        String text = go.text().strip();
        if (text.isEmpty()) {
            return s;
        }
        Path typed;
        try {
            typed = Path.of(text);
        } catch (InvalidPathException e) {
            refuse(out, "Not a path: " + text);
            return s;
        }
        Path target = (typed.isAbsolute() || s.folder().isEmpty() ? typed : s.folder().get().resolve(typed))
                .toAbsolutePath().normalize();
        out.send(Messages.FIND_TOPIC, new Find(target, Why.GO));
        return s;
    }

    /**
     * What {@link FindPath} found at a path, acted on for whoever asked: a folder is shown, and a file shows the folder
     * it is in with its row selected. A remembered folder that has gone is forgotten, and the folder the application
     * started in is shown.
     */
    private static State found(State s, Found f, Out out) {
        Path shown = f.is() == Is.FOLDER ? f.path() : f.is() == Is.FILE ? f.path().getParent() : null;
        Set<Path> select = f.is() == Is.FILE ? Set.of(f.path()) : Set.of();
        if (shown == null) {
            if (f.why() == Why.GO) {
                refuse(out, "Nothing at " + f.path());
                return s;
            }
            if (f.why() == Why.ARGUMENT) {
                refuse(out, "Nothing at " + f.path());
            }
            shown = s.started().orElse(f.path());
        }
        State next = show(s, shown, select, out);
        return f.why() == Why.GO ? remember(next, out) : remember(next.withArmed(), out);
    }

    // ------------------------------------------------------------------ the folder shown

    /**
     * The breadcrumb, a place, Enter on a folder's row, Backspace or Alt+Up: show that folder in the list used. A
     * pane's own Up or Choose folder: in that pane, which is used from then, as a click in it would make it.
     */
    private static State navigate(State was, Navigate n, Out out) {
        if (!there(was, n.side())) {
            return was;
        }
        State s = n.side().map(side -> use(was, side, out)).orElse(was);
        return remember(show(s, n.folder(), Set.of(), out), out);
    }

    /**
     * The top bar's Back, or a pane's own: the folder that list showed before this one, and nothing when it showed
     * none. A pane's is used from then.
     */
    private static State back(State was, Back b, Out out) {
        if (!there(was, b.side())) {
            return was;
        }
        State s = b.side().map(side -> use(was, side, out)).orElse(was);
        if (s.pane().back().isEmpty()) {
            return s;
        }
        State next = s.returning();
        out.send(Messages.LISTING_TOPIC, new Listing(next.asked(), next.folder().get()));
        return remember(next, out);
    }

    /** Whether a pane's button names a list there is: TO's is gone once there is one folder again. */
    private static boolean there(State s, Optional<Side> side) {
        return side.isEmpty() || side.get() == Side.FROM || s.to().isPresent();
    }

    /**
     * Ask for {@code folder}'s rows, to select {@code select} among them when they land. Nothing is shown until they do,
     * so the breadcrumb and the list never disagree about which folder this is.
     */
    private static State show(State s, Path folder, Set<Path> select, Out out) {
        State next = s.asking(folder, select);
        out.send(Messages.LISTING_TOPIC, new Listing(next.asked(), folder));
        return next;
    }

    /**
     * A folder's rows, for the list that asked: shown, unless a later ask has overtaken them. A folder that could not be
     * read is shown empty, the breadcrumb where the person asked to be, and the status line says why. By the folder's
     * name: the breadcrumb says where it is, and a status line that holds a whole path runs over the counts
     * (framework-notes FN-6). The rail and the dock follow only the list used.
     */
    private static State listed(State s, Listed l, Out out) {
        Optional<Side> asker = s.asker(l.ticket());
        if (asker.isEmpty()) {
            return s;
        }
        Side side = asker.get();
        if (l.problem() != null) {
            refuse(out, "Can't open " + ListFolder.name(l.folder()) + ": " + l.problem());
        }
        out.send(Messages.SHOWN_TOPIC, new Shown(side, l.folder(), l.rows(), s.pane(side).select()));
        State next = watch(s.withPane(side, s.pane(side).withRows(l)), side, l, out);
        summary(next, out);
        compare(next, out);
        if (side != next.used()) {
            return next;
        }
        pair(next, out);
        return dock(suggest(next, true, out), out);
    }

    /**
     * In Two Folders, each list's rows against the other's, sent whole whenever either changes: the other folder's
     * listing is what a row's relation depends on, so both are worked out again. In one folder, nothing; the step
     * back to one says so once.
     */
    private static void compare(State s, Out out) {
        if (s.to().isEmpty()) {
            return;
        }
        List<Row> from = s.pane(Side.FROM).rows();
        List<Row> to = s.pane(Side.TO).rows();
        Map<Twins, Boolean> checked = s.work().comparing().checked();
        out.send(Messages.COMPARED_TOPIC, new Compared(Side.FROM, Compare.of(from, to, checked)));
        out.send(Messages.COMPARED_TOPIC, new Compared(Side.TO, Compare.of(to, from, checked)));
        offers(s, out);
        conflict(s, out);
    }

    // ------------------------------------------------------------------ the dock's conflict

    /**
     * The conflict the dock shows, sent whenever the comparison or the selection changes: the one row selected in the
     * list used, if its name is <em>different in both</em>; with nothing selected, FROM's first such name; else none.
     */
    private static void conflict(State s, Out out) {
        if (s.to().isEmpty()) {
            return;
        }
        Optional<Twins> twins = conflicted(s);
        if (twins.isEmpty()) {
            out.send(Messages.CONFLICT_TOPIC, Conflict.NONE);
            return;
        }
        Twins t = twins.get();
        Relation rel = relations(s).get(t.here().path());
        Optional<Side> newer = rel == Relation.NEWER ? Optional.of(Side.FROM)
                : rel == Relation.OLDER ? Optional.of(Side.TO) : Optional.empty();
        String note = newer.isPresent() ? "Same name, different file"
                : t.here().folder() != t.there().folder() ? "Neither is newer: one is a folder"
                : t.here().size() == t.there().size() ? "Neither is newer: the same time, read different"
                : "Neither is newer: the same time, a different size";
        out.send(Messages.CONFLICT_TOPIC, new Conflict(twins, newer, note));
    }

    /** The pair the dock shows, FROM's row first, by {@link #conflict}'s rule. */
    private static Optional<Twins> conflicted(State s) {
        if (s.pane(Side.FROM).folder().isEmpty() || s.pane(Side.TO).folder().isEmpty()) {
            return Optional.empty();
        }
        Map<Path, Relation> relations = relations(s);
        Map<String, Row> there = new HashMap<>();
        for (Row r : s.pane(Side.TO).rows()) {
            there.put(r.name(), r);
        }
        Set<Path> selected = s.selected();
        if (selected.size() == 1) {
            Optional<Row> one = s.pane().row(selected.iterator().next());
            Optional<Row> here = one.flatMap(r -> s.used() == Side.FROM ? one
                    : s.pane(Side.FROM).rows().stream().filter(f -> f.name().equals(r.name())).findFirst());
            return here.filter(r -> conflicts(relations, r) && there.containsKey(r.name()))
                    .map(r -> new Twins(r, there.get(r.name())));
        }
        if (!selected.isEmpty()) {
            return Optional.empty();
        }
        return s.pane(Side.FROM).rows().stream()
                .filter(r -> conflicts(relations, r) && there.containsKey(r.name()))
                .min(java.util.Comparator.comparing(Row::name))
                .map(r -> new Twins(r, there.get(r.name())));
    }

    private static boolean conflicts(Map<Path, Relation> relations, Row r) {
        Relation rel = relations.get(r.path());
        return rel != null && rel.group() == Relation.Group.DIFFERENT;
    }

    /**
     * The dock's Keep newer or Keep both, for the pair it showed. Refused and said if either copy has changed or gone
     * since, if it is no longer a conflict, or, for Keep newer, if neither copy is newer. Both move, on one drive or
     * two, and end with the file in TO: the newer alone, or both under two names.
     */
    private static State resolve(State s, Resolve r, Out out) {
        if (s.to().isEmpty() || s.pane(Side.FROM).folder().isEmpty() || s.pane(Side.TO).folder().isEmpty()) {
            return s;
        }
        Twins t = r.twins();
        String name = t.here().name();
        if (!s.pane(Side.FROM).row(t.here().path()).equals(Optional.of(t.here()))
                || !s.pane(Side.TO).row(t.there().path()).equals(Optional.of(t.there()))) {
            refuse(out, name + " has changed since it was shown: look again");
            return s;
        }
        Relation rel = relations(s).get(t.here().path());
        if (rel == null || rel.group() != Relation.Group.DIFFERENT) {
            refuse(out, name + " is no longer different in both");
            return s;
        }
        Path to = s.pane(Side.TO).folder().get();
        String from = ListFolder.name(s.pane(Side.FROM).folder().get());
        String into = ListFolder.name(to);
        if (r.keep() == Resolve.Keep.NEWER) {
            if (rel == Relation.DIFFERENT) {
                refuse(out, "Neither copy of " + name + " is newer");
                return s;
            }
            if (rel == Relation.NEWER) {
                return start(s, Plan.keepNewer(t.here(), t.there(), to), Way.DO, 0L, "Move the newer " + name
                        + " into " + into + ", the older into Vexplore's trash", out);
            }
            return start(s, Plan.keepNewer(t.there(), t.here(), to), Way.DO, 0L, "Move the older " + name
                    + " from " + from + " into Vexplore's trash", out);
        }
        Set<String> taken = new HashSet<>();
        for (Row row : s.pane(Side.TO).rows()) {
            taken.add(row.name());
        }
        Plan plan = Plan.aside(t.here(), to, taken);
        String as = ListFolder.name(plan.steps().getFirst().to());
        return start(s, plan, Way.DO, 0L, "Move " + name + " from " + from + " into " + into + " as " + as, out);
    }

    // ------------------------------------------------------------------ Two Folders' offers

    /** FROM's rows by relation to TO's, as far as the listings and the copies read can say. */
    private static Map<Path, Relation> relations(State s) {
        return Compare.of(s.pane(Side.FROM).rows(), s.pane(Side.TO).rows(), s.work().comparing().checked());
    }

    /** FROM's rows only in FROM, in the list's order: what the move offer reaches. */
    private static List<Row> onlyHere(State s, Map<Path, Relation> relations) {
        return s.pane(Side.FROM).rows().stream().filter(r -> relations.get(r.path()) == Relation.ONLY_HERE).toList();
    }

    /** FROM's identical and probably identical files, each with its copy in TO: what the remove offer reaches. */
    private static List<Twins> twins(State s, Map<Path, Relation> relations) {
        Map<String, Row> there = new HashMap<>();
        for (Row r : s.pane(Side.TO).rows()) {
            there.put(r.name(), r);
        }
        List<Twins> out = new ArrayList<>();
        for (Row r : s.pane(Side.FROM).rows()) {
            Relation rel = relations.get(r.path());
            if (rel == Relation.IDENTICAL || rel == Relation.PROBABLY_IDENTICAL) {
                out.add(new Twins(r, there.get(r.name())));
            }
        }
        return out;
    }

    /** What is only in FROM, planned into TO's folder against its names: a move on one drive, a copy across two. */
    private static Plan moving(State s, List<Row> rows) {
        Set<String> taken = new HashSet<>();
        for (Row r : s.pane(Side.TO).rows()) {
            taken.add(r.name());
        }
        return Plan.to(drop(s), rows, s.pane(Side.TO).folder().orElseThrow(), taken);
    }

    /** {@code Move 5 files only in Downloads to Videos}: the move offer's words, and its entry's. */
    private static String moveWords(State s, Plan plan) {
        int n = plan.count();
        return (plan.op() == Plan.Op.MOVE ? "Move " : "Copy ") + n + (n == 1 ? " file" : " files") + " only in "
                + ListFolder.name(s.pane(Side.FROM).folder().orElseThrow()) + " to "
                + ListFolder.name(s.pane(Side.TO).folder().orElseThrow());
    }

    /** {@code Remove 3 identical copies from Downloads}: the remove offer's words, and its entry's. */
    private static String removeWords(State s, int n) {
        return "Remove " + n + (n == 1 ? " identical copy" : " identical copies") + " from "
                + ListFolder.name(s.pane(Side.FROM).folder().orElseThrow());
    }

    /** The first few names, and how many more. */
    private static String named(List<Row> rows) {
        StringBuilder b = new StringBuilder();
        int named = Math.min(rows.size(), 3);
        for (int i = 0; i < named; i++) {
            b.append(i == 0 ? "" : ", ").append(rows.get(i).name());
        }
        if (rows.size() > named) {
            b.append(" and ").append(rows.size() - named).append(" more");
        }
        return b.toString();
    }

    private static String sized(long bytes) {
        return bytes > 0 ? " · " + Bytes.format(bytes) : "";
    }

    /**
     * Two Folders' rail, from the comparison: FROM's counts and the offers, each worded with how far it has got while
     * its check or its run goes. Nothing is offered while both folders are not yet listed.
     */
    private static void offers(State s, Out out) {
        if (s.to().isEmpty() || s.pane(Side.FROM).folder().isEmpty() || s.pane(Side.TO).folder().isEmpty()) {
            return;
        }
        Map<Path, Relation> relations = relations(s);
        int[] by = new int[Relation.Group.values().length];
        for (Relation r : relations.values()) {
            by[r.group().ordinal()]++;
        }
        Tally tally = new Tally(ListFolder.name(s.pane(Side.FROM).folder().get()),
                by[Relation.Group.ONLY_HERE.ordinal()],
                by[Relation.Group.DIFFERENT.ordinal()], by[Relation.Group.IDENTICAL.ordinal()],
                by[Relation.Group.PROBABLY_IDENTICAL.ordinal()]);
        Work.Comparing c = s.work().comparing();
        String running = s.work().running().map(Running::said).orElse("");
        List<Offer> offers = new ArrayList<>();
        List<Row> only = onlyHere(s, relations);
        Plan move = only.isEmpty() ? null : moving(s, only);
        if (move != null && move.count() > 0) {
            String words = moveWords(s, move);
            List<Row> moved = move.steps().stream().map(st -> s.pane(Side.FROM).row(st.from()).orElseThrow()).toList();
            long bytes = moved.stream().mapToLong(Row::size).sum();
            boolean going = c.offer().equals(Optional.of(Offer.Kind.MOVE_ONLY_HERE));
            offers.add(new Offer(Offer.Kind.MOVE_ONLY_HERE, new Card(words + sized(bytes), named(moved),
                    words.substring(0, words.indexOf(" only in ")), going ? running : "", false), Set.copyOf(moved)));
        }
        List<Twins> twins = twins(s, relations);
        if (!twins.isEmpty()) {
            List<Row> copies = twins.stream().map(Twins::here).toList();
            long bytes = copies.stream().mapToLong(Row::size).sum();
            long unread = twins.stream().filter(t -> !c.checked().containsKey(t)).count();
            long probably = twins.stream().filter(t -> relations.get(t.here().path()) == Relation.PROBABLY_IDENTICAL)
                    .count();
            String there = ListFolder.name(s.pane(Side.TO).folder().get());
            String detail;
            String go;
            if (unread > 0) {
                detail = named(copies) + ". Each is read against its copy in " + there + " first, and nothing is"
                        + " removed until it matches" + (probably == 0 ? "" : ". " + probably + " only probably"
                        + " identical: the same size, a different time");
                go = "Check them";
            } else {
                detail = named(copies) + ". Read byte for byte: each matches its copy in " + there
                        + ". Into Vexplore's trash; Ctrl+Z puts them back";
                go = "Remove " + copies.size();
            }
            boolean mine = c.offer().equals(Optional.of(Offer.Kind.REMOVE_IDENTICAL));
            String how = !mine ? "" : c.checking().map(k -> "Checking · " + k.done() + " of " + k.total())
                    .orElse(running);
            offers.add(new Offer(Offer.Kind.REMOVE_IDENTICAL, new Card(removeWords(s, copies.size()) + sized(bytes),
                    detail, go, how, false), Set.copyOf(copies)));
        }
        out.send(Messages.OFFERS_TOPIC, new Offers(tally, offers));
    }

    /**
     * An offer's button. The move is carried out at once, as the card says. The remove reads every pair not read yet
     * first, and is carried out only once each has matched; pressed while its check goes, it stops the check. One
     * thing at a time: a press while anything else goes is refused.
     */
    private static State accept(State s, Accept a, Out out) {
        if (s.to().isEmpty() || s.pane(Side.FROM).folder().isEmpty() || s.pane(Side.TO).folder().isEmpty()) {
            return s;
        }
        Work.Comparing c = s.work().comparing();
        if (c.checking().isPresent()) {
            if (a.kind() == Offer.Kind.REMOVE_IDENTICAL) {
                out.send(Messages.STOP_CHECK_TOPIC, new StopCheck());
            } else {
                refuse(out, "Something is already being done: wait for it, or press Stop");
            }
            return s;
        }
        Map<Path, Relation> relations = relations(s);
        if (a.kind() == Offer.Kind.MOVE_ONLY_HERE) {
            List<Row> only = onlyHere(s, relations);
            if (only.isEmpty()) {
                return s;
            }
            Plan plan = moving(s, only);
            return plan.count() == 0 ? s : offering(s, start(s, plan, Way.DO, 0L, moveWords(s, plan), out), a, out);
        }
        List<Twins> twins = twins(s, relations);
        if (twins.isEmpty()) {
            return s;
        }
        List<Twins> unread = twins.stream().filter(t -> !c.checked().containsKey(t)).toList();
        if (unread.isEmpty()) {
            Plan plan = Plan.delete(twins.stream().map(Twins::here).toList());
            return offering(s, start(s, plan, Way.DO, 0L, removeWords(s, twins.size()), out), a, out);
        }
        if (s.work().running().isPresent()) {
            refuse(out, "Something is already being done: wait for it, or press Stop");
            return s;
        }
        Checking k = new Checking(s.work().runs() + 1, 0, unread.size());
        out.send(Messages.CHECK_TOPIC, new Check(k.run(), unread));
        State next = s.withWork(s.work().checking(k, a.kind()));
        offers(next, out);
        return next;
    }

    /** A run an offer started: its card says how far it has got. Nothing, if the run was refused. */
    private static State offering(State was, State next, Accept a, Out out) {
        if (next.work().running().isEmpty() || was.work().running().isPresent()) {
            return next;
        }
        State marked = next.withWork(next.work().withComparing(next.work().comparing()
                .offering(Optional.of(a.kind()))));
        offers(marked, out);
        return marked;
    }

    /** How far the check has got: its card's count. */
    private static State checking(State s, Checking k, Out out) {
        if (!s.work().comparing().checks(k.run())) {
            return s;
        }
        Work.Comparing c = s.work().comparing();
        State next = s.withWork(s.work().withComparing(new Work.Comparing(c.checked(), Optional.of(k), c.offer())));
        offers(next, out);
        return next;
    }

    /**
     * What reading the pairs found, kept: a pair that matched is identical from now, one that did not is different,
     * in the lists' marks, the tiles and the offers. Said on the status line, and what could not be read is said too.
     */
    private static State checked(State s, Checked k, Out out) {
        if (!s.work().comparing().checks(k.run())) {
            return s;
        }
        int total = s.work().comparing().checking().get().total();
        Map<Twins, Boolean> found = new HashMap<>();
        k.same().forEach(t -> found.put(t, true));
        k.differ().forEach(t -> found.put(t, false));
        State next = s.withWork(s.work().withComparing(s.work().comparing().found(found)));
        int read = k.same().size() + k.differ().size();
        StringBuilder words = new StringBuilder();
        if (read == 0) {
            words.append(k.problem() == null ? "Stopped before any pair was read" : "No pair could be read");
        } else {
            words.append("Read ").append(read).append(read == 1 ? " pair: " : " pairs: ");
            if (k.differ().isEmpty()) {
                words.append(read == 1 ? "it matches" : "all match");
            } else {
                words.append(k.same().size()).append(" match, ").append(k.differ().iterator().next().here().name())
                        .append(k.differ().size() == 1 ? " differs"
                                : " and " + (k.differ().size() - 1) + " more differ");
            }
            if (read < total && k.problem() == null) {
                words.append(". Stopped after ").append(read).append(" of ").append(total);
            }
        }
        if (k.problem() != null) {
            words.append(". Could not read ").append(k.problem());
        }
        out.send(Messages.STATUS_TOPIC, new Status(words.toString(), k.problem() != null));
        compare(next, out);
        return next;
    }

    /** The status line's counts: the list used, and in Two Folders the other one's rows. */
    private static void summary(State s, Out out) {
        long bytes = 0L;
        for (Row r : s.rows()) {
            bytes += s.selected().contains(r.path()) ? r.size() : 0L;
        }
        Optional<Integer> beside = s.to().map(t -> s.pane(s.used().other()).rows().size());
        out.send(Messages.SUMMARY_TOPIC, new Summary(s.rows().size(), beside, s.selected().size(), bytes));
    }

    /**
     * Whether there are two lists, which is used, and its folder, for the breadcrumb and the top bar; and what a drop
     * between them does, for the lists to declare while rows are carried.
     */
    private static void pair(State s, Out out) {
        out.send(Messages.PAIR_TOPIC, new Pair(s.to().isPresent(), s.used(), s.folder(), !s.pane().back().isEmpty(),
                drop(s)));
    }

    /**
     * What a drop between the two lists does: a move when both folders are on one drive, which costs nothing and
     * leaves no copy behind, and a copy across two, which a move would turn into a copy and a delete. Held keys (0.1's
     * Ctrl to copy, Shift to move) wait on a modifiers topic (framework-notes FN-9).
     */
    static Plan.Op drop(State s) {
        Optional<Path> from = s.pane(Side.FROM).folder().map(Path::getRoot);
        Optional<Path> to = s.to().flatMap(Pane::folder).map(Path::getRoot);
        return from.isPresent() && from.equals(to) ? Plan.Op.MOVE : Plan.Op.COPY;
    }

    /**
     * Rows of one list let go on the other: planned against the names the other folder holds, as {@code PlanAction}
     * plans a move or a copy, so nothing goes over a name already there, and carried out at once, since letting go is
     * the person agreeing to it. What was left alone is said; nothing at all to do is said rather than run. Undone by
     * Ctrl+Z, as anything carried out is.
     */
    private static State dropped(State s, Dropped d, Out out) {
        Side from = d.onto().other();
        Optional<Path> into = s.pane(d.onto()).folder();
        if (s.to().isEmpty() || into.isEmpty() || d.held().isEmpty()) {
            return s;
        }
        List<Row> rows = new ArrayList<>();
        for (Path p : d.held()) {
            s.pane(from).row(p).ifPresent(rows::add);
        }
        Set<String> taken = new HashSet<>();
        for (Row r : s.pane(d.onto()).rows()) {
            taken.add(r.name());
        }
        Plan.Op op = drop(s);
        Plan plan = Plan.to(op, rows, into.get(), taken);
        String where = ListFolder.name(into.get());
        if (plan.count() == 0) {
            Plan.Skip first = plan.skipped().isEmpty() ? null : plan.skipped().getFirst();
            refuse(out, "Nothing to " + (op == Plan.Op.MOVE ? "move" : "copy") + " to " + where
                    + (first == null ? "" : ": " + ListFolder.name(first.path()) + ", " + first.reason()));
            return s;
        }
        String files = plan.count() == 1 ? " file" : " files";
        String words = (op == Plan.Op.MOVE ? "Move " : "Copy ") + plan.count() + files
                + " to " + where + (plan.skipped().isEmpty() ? "" : ", " + plan.skipped().size() + " left alone ("
                + plan.skipped().getFirst().reason() + ")");
        return start(s, plan, Way.DO, 0L, words, out);
    }

    /**
     * Watch the folder whose rows just landed in the list on {@code side}, unless it already is. Asked when they land
     * rather than when they are asked for, so a folder that cannot be read is not watched; and then the one before it,
     * no longer shown, is not.
     */
    private static State watch(State s, Side side, Listed l, Out out) {
        Optional<Path> was = s.work().watched(side).folder();
        if (l.problem() != null) {
            if (was.isEmpty()) {
                return s;
            }
            out.send(Messages.UNWATCH_TOPIC, new Unwatch(side));
            return watched(s, side, Watched.NONE, out);
        }
        if (was.equals(Optional.of(l.folder()))) {
            return s;
        }
        out.send(Messages.WATCH_TOPIC, new Watch(side, l.folder()));
        return watched(s, side, new Watched(Optional.of(l.folder()), Set.of()), out);
    }

    /**
     * A list's folder changed on the disk: list it again, keeping the selection and the pick. Said of a folder the list
     * no longer shows, or while another is being asked for, it is dropped, as a stale listing is.
     */
    private static State stirred(State s, Stirred m, Out out) {
        Optional<Path> shown = s.pane(m.side()).folder();
        if (!shown.equals(Optional.of(m.folder())) || !s.work().watched(m.side()).folder().equals(shown)
                || (m.side() == Side.TO && s.to().isEmpty())) {
            return s;
        }
        State next = s.stirring(m.side());
        out.send(Messages.LISTING_TOPIC, new Listing(next.pane(m.side()).asked(), m.folder()));
        return next;
    }

    /**
     * A watch ended. On the folder still watched (it was moved or removed), it is no longer: the listing its last
     * {@link Stirred} asked for says so, and a folder shown again is watched again.
     */
    private static State unwatched(State s, Unwatched u, Out out) {
        if (!s.work().watched(u.side()).folder().equals(Optional.of(u.folder()))) {
            return s;
        }
        return watched(s, u.side(), Watched.NONE, out);
    }

    /**
     * What landed in a list's folder lately, as {@link WatchFolder} keeps it: the rows to tag new. Its clock, not this
     * one, says when a file stops being fresh. Said of a folder no longer watched, it is dropped.
     */
    private static State freshness(State s, Freshness f, Out out) {
        Watched w = s.work().watched(f.side());
        if (!w.folder().equals(Optional.of(f.folder()))) {
            return s;
        }
        return watched(s, f.side(), new Watched(w.folder(), f.fresh()), out);
    }

    /** Watching {@code value} for a list: the view is told its rows to tag when that is not what it was told last. */
    private static State watched(State s, Side side, Watched value, Out out) {
        if (!value.fresh().equals(s.work().watched(side).fresh())) {
            out.send(Messages.FRESH_TOPIC, new Fresh(side, value.fresh()));
        }
        return s.withWork(s.work().withWatched(side, value));
    }

    /**
     * What a list says is selected: counted for the status line, with the selected files' bytes. A path that is not
     * one of its folder's rows (the list had not caught up with a new listing) is not counted. Selecting in a list is
     * using it.
     */
    private static State chosen(State s, Chosen c, Out out) {
        State used = use(s, c.side(), out);
        Set<Path> selected = new HashSet<>();
        for (Row r : used.rows()) {
            if (c.paths().contains(r.path())) {
                selected.add(r.path());
            }
        }
        State next = used.withSelected(selected);
        summary(next, out);
        return dock(suggest(next, out), out);
    }

    /** Enter or a double click on a row: a folder is gone into, and a file opened as the desktop would open it. */
    private static State activated(State s, Activated a, Out out) {
        State used = use(s, a.side(), out);
        Optional<Row> row = used.row(a.path());
        if (row.isEmpty()) {
            return used;
        }
        if (row.get().folder()) {
            return remember(show(used, a.path(), Set.of(), out), out);
        }
        out.send(Open.TOPIC, new Open(a.path().toString()));
        return used;
    }

    // ------------------------------------------------------------------ Two Folders

    /** A click in a list: from now the tree, the breadcrumb, the rail and the dock work from it. */
    private static State used(State s, Use u, Out out) {
        return use(s, u.side(), out);
    }

    /**
     * Use the list on {@code side}, if it is not already used and is there: the breadcrumb shows its folder, and the
     * rail and the dock are worked out again from its rows and selection.
     */
    private static State use(State s, Side side, Out out) {
        if (side == s.used() || (side == Side.TO && s.to().isEmpty())) {
            return s;
        }
        State next = s.using(side);
        pair(next, out);
        summary(next, out);
        return dock(suggest(next, true, out), out);
    }

    /**
     * The top bar's Two folders: TO opens on the folder FROM shows, as 0.1 did, and FROM stays used. Back to one
     * folder: TO goes, its watch with it, and FROM is used.
     */
    private static State twoFolders(State s, TwoFolders t, Out out) {
        if (t.on() == s.to().isPresent()) {
            return s;
        }
        if (t.on()) {
            if (s.from().folder().isEmpty()) {
                return s;
            }
            State next = s.twoFolders(s.from().folder());
            out.send(Messages.LISTING_TOPIC, new Listing(next.tickets(), s.from().folder().get()));
            pair(next, out);
            return next;
        }
        boolean wasTo = s.used() == Side.TO;
        State next = s.twoFolders(Optional.empty());
        if (s.work().watched(Side.TO).folder().isPresent()) {
            out.send(Messages.UNWATCH_TOPIC, new Unwatch(Side.TO));
            next = watched(next, Side.TO, Watched.NONE, out);
        }
        for (Side side : Side.values()) {
            out.send(Messages.COMPARED_TOPIC, new Compared(side, Map.of()));
        }
        out.send(Messages.OFFERS_TOPIC, Offers.NONE);
        out.send(Messages.CONFLICT_TOPIC, Conflict.NONE);
        pair(next, out);
        summary(next, out);
        return wasTo ? dock(suggest(next, true, out), out) : next;
    }

    /**
     * The top bar's Swap sides: each list shows what the other did, with its selection, its watch and its tags, and
     * the list used is the same folder as before, now on the other side.
     */
    private static State swap(State s, Swap w, Out out) {
        if (s.to().isEmpty()) {
            return s;
        }
        State next = s.swapped();
        for (Side side : Side.values()) {
            Pane p = next.pane(side);
            p.folder().ifPresent(f -> out.send(Messages.SHOWN_TOPIC, new Shown(side, f, p.rows(), p.selected())));
            Watched watched = next.work().watched(side);
            watched.folder().ifPresent(f -> out.send(Messages.WATCH_TOPIC, new Watch(side, f)));
            out.send(Messages.FRESH_TOPIC, new Fresh(side, watched.fresh()));
        }
        compare(next, out);
        pair(next, out);
        summary(next, out);
        return next;
    }

    /** What opening a file came to: said either way, since what opens it is another program and may take a moment. */
    private static State started(State s, Opened st, Out out) {
        String name = ListFolder.name(Path.of(st.target()));
        if (!st.opened()) {
            refuse(out, "Can't open " + name + ": " + st.problem());
        } else {
            out.send(Messages.STATUS_TOPIC, new Status("Opened " + name, false));
        }
        return s;
    }

    // ------------------------------------------------------------------ the row menu

    /**
     * A row menu's choice, about the selection. Open is Enter's, on the one row selected. One of the rail's actions is
     * picked on the rail, as its chip would pick it, and not carried out: the card states its effect and its button
     * carries it out, as every other way in does. Chosen when it is already picked, it stays picked, where a chip
     * pressed again would let it go. One the rail does not offer for this selection (Rename, with several) is said.
     */
    private static State menu(State was, Menu m, Out out) {
        State s = use(was, m.side(), out);
        if (Menu.OPEN.equals(m.action())) {
            if (s.selected().size() != 1) {
                refuse(out, "Open is for one item at a time");
                return s;
            }
            return activated(s, new Activated(s.used(), s.selected().iterator().next()), out);
        }
        for (Act a : Act.values()) {
            if (!a.name().equals(m.action())) {
                continue;
            }
            if (!s.rail().acts().contains(a)) {
                refuse(out, a.verb() + " is not offered for " + (s.selected().size() == 1 ? "this file" : "these"));
                return s;
            }
            return a == s.rail().applied().act() ? s : suggest(s.withPick(s.pick().with(a)), true, out);
        }
        return s;
    }

    // ------------------------------------------------------------------ the Suggestion Rail

    /**
     * A chip pressed:the pick it makes, resolved against the rail last sent, which is what the person saw. A place past
     * the end of its row (the rail shrank under the pointer) changes nothing.
     *
     * <p>The rail is sent even when the pick changed nothing: a chip changes its own look when it is clicked, before
     * this has answered (framework-notes FN-7), and the rail sent back shows every chip as it is.
     */
    private static State picked(State s, Picked p, Out out) {
        if (s.rail().equals(Rail.EMPTY)) {
            return s;
        }
        if (p.slot() == Slot.SELECT && s.proposing()) {
            List<Intents.Candidate> offered = s.intents();
            if (!offered.isEmpty()) {
                return p.index() < offered.size() ? choose(s, offered.get(p.index()).paths(), out)
                        : suggest(s, true, out);
            }
        }
        if (p.slot() == Slot.DESTINATION) {
            List<Dest> offered = s.work().aim().offered();
            return p.index() < offered.size()
                    ? suggest(s.withWork(s.work().withAim(s.work().aim().to(offered.get(p.index()).path()))), true, out)
                    : suggest(s, true, out);
        }
        return suggest(s.withPick(p.slot().press(s.rail(), s.pick(), p.index())), true, out);
    }

    private static State suggest(State s, Out out) {
        return suggest(s, false, out);
    }

    /**
     * A key held or let go in the main window. Shift or Control held turns the rail's Which files row into what the key
     * proposes, and letting go turns it back; any other change of the keys changes nothing shown. Another window's
     * keys are its own.
     */
    private static State held(State s, Modifiers m, Out out) {
        if (!m.main()) {
            return s;
        }
        State next = s.withHeld(m);
        boolean same = m.shift() == s.held().shift() && m.control() == s.held().control();
        if (same || next.rail().equals(Rail.EMPTY)) {
            return next;
        }
        out.send(Messages.SUGGESTED_TOPIC, said(next));
        return next;
    }

    /**
     * A list ordered anew, by a header click: kept with the list, so what the explorer reasons about in order is what
     * the person sees. Only a held key's proposals read the order, so only they are said again.
     */
    private static State ordered(State s, Ordered o, Out out) {
        State next = s.withPane(o.side(), s.pane(o.side()).withOrder(o.order()));
        if (o.side() == next.used() && next.proposing() && !next.rail().equals(Rail.EMPTY)) {
            out.send(Messages.SUGGESTED_TOPIC, said(next));
        }
        return next;
    }

    /**
     * A proposal chosen: {@code paths} become the selection in the list used, the list is told to show them selected,
     * and the rail is worked out again for them, as a selection the person made in the list would be.
     */
    private static State choose(State s, Set<Path> paths, Out out) {
        State next = s.withSelected(paths);
        Pane p = next.pane();
        p.folder().ifPresent(f -> out.send(Messages.SHOWN_TOPIC, new Shown(next.used(), f, p.rows(), paths)));
        summary(next, out);
        return dock(suggest(next, true, out), out);
    }

    /**
     * Work the rail out again from the rows, the selection and the pick, and send it if it says anything different, or
     * when {@code always}. The pick kept is the one asked for, not the one that applied, so a pick that falls back while
     * the selection is too narrow for it comes back when the selection is wide enough again.
     */
    private static State suggest(State s, boolean always, Out out) {
        Rail rail = Suggestions.of(s.rows(), s.selected(), s.pick(), s.today(), s.zone());
        if (!always && rail.equals(s.rail())) {
            return s;
        }
        if (rail.applied().act() == Act.RENAME && !rail.targets().isEmpty()
                && (s.rail().applied().act() != Act.RENAME || !s.rail().targets().equals(rail.targets()))) {
            out.send(Messages.NAMING_TOPIC, new Naming(rail.targets().getFirst().name()));
        }
        State next = aim(s.withRail(rail), out);
        out.send(Messages.SUGGESTED_TOPIC, said(next));
        return next;
    }

    /**
     * For a move or a copy, ask where it might go and what going there would do: places near the folder shown for the
     * kind it reaches, when that changes, and a plan whenever the reach, the action or the destination does.
     */
    private static State aim(State s, Out out) {
        Plan.Op op = s.rail().applied().act() == null ? null : s.rail().applied().act().placed();
        if (op == null || s.folder().isEmpty() || s.rail().targets().isEmpty()) {
            return s;
        }
        Aim a = s.work().aim();
        Path folder = s.folder().get();
        Kind kind = s.rail().targets().getFirst().kind();
        if (s.rail().targets().stream().allMatch(r -> r.kind() == kind)
                && (!a.kind().equals(Optional.of(kind)) || !a.near().equals(Optional.of(folder)))) {
            a = a.asking(kind, folder);
            out.send(Messages.DESTINING_TOPIC, new Destining(a.asked(), folder, kind));
        }
        if (a.destination().isPresent()) {
            Planning want = new Planning(a.nextPlan(), op, s.rail().targets(), a.destination().get());
            if (!a.asks(want)) {
                a = a.planning(want);
                out.send(Messages.PLANNING_TOPIC, want);
            }
        }
        return s.withWork(s.work().withAim(a));
    }

    /** Places to go: offered for the kind asked, unless a later ask has overtaken them. */
    private static State destinations(State s, Destinations d, Out out) {
        if (d.ticket() != s.work().aim().asked()) {
            return s;
        }
        State next = s.withWork(s.work().withAim(s.work().aim().offering(d.places())));
        out.send(Messages.SUGGESTED_TOPIC, said(next));
        return next;
    }

    /** { Choose folder…} answered: the destination from now on. */
    private static State destine(State s, Destine d, Out out) {
        return suggest(s.withWork(s.work().withAim(s.work().aim().to(d.folder()))), true, out);
    }

    /** What going there would do: the card's button, unless a later plan has been asked for. */
    private static State planned(State s, Planned p, Out out) {
        Aim a = s.work().aim();
        if (a.sent().isEmpty() || a.sent().get().ticket() != p.ticket()) {
            return s;
        }
        State next = s.withWork(s.work().withAim(a.planned(p.plan())));
        out.send(Messages.SUGGESTED_TOPIC, said(next));
        return next;
    }

    /**
     * The rail as it stands: for a move or a copy with its Destination row and the card for going there, and while a
     * run goes, its card saying how far it has got.
     */
    private static Suggested said(State s) {
        Suggested rail = s.rail().worded();
        List<Intents.Candidate> intents = s.proposing() && !rail.empty() ? s.intents() : List.of();
        if (!intents.isEmpty()) {
            List<Chip> chips = new ArrayList<>();
            for (Intents.Candidate c : intents) {
                chips.add(new Chip(c.label(), c.count(), false));
            }
            rail = rail.proposed(s.held().shift() ? "a range" : "a rule", chips);
        }
        Plan.Op op = s.rail().applied().act() == null ? null : s.rail().applied().act().placed();
        if (op != null && !rail.empty()) {
            rail = placed(rail, s.work().aim());
        }
        Suggested worded = rail;
        return s.work().running().map(r -> worded.running(r.said())).orElse(worded);
    }

    /**
     * The Destination row, the destination picked first when it is not one of the places offered, and the card: where
     * they would go, how many would, and what would be left alone and why.
     */
    private static Suggested placed(Suggested rail, Aim a) {
        List<Chip> places = new ArrayList<>();
        Optional<Path> to = a.destination();
        if (to.isPresent() && a.offered().stream().noneMatch(d -> d.path().equals(to.get()))) {
            places.add(new Chip(ListFolder.name(to.get()), 0, true));
        }
        for (Dest d : a.offered()) {
            places.add(new Chip(ListFolder.name(d.path()), d.count(), to.isPresent() && d.path().equals(to.get())));
        }
        Card card = rail.card();
        if (to.isEmpty()) {
            Card waiting = new Card(card.headline(), card.detail() + ". Choose where they go", "", "", false);
            return rail.placed(true, places, waiting);
        }
        if (a.plan().isEmpty()) {
            return rail.placed(true, places, new Card(card.headline(), card.detail(), "", "", false));
        }
        Plan plan = a.plan().get();
        int n = plan.count();
        String verb = plan.op() == Plan.Op.MOVE ? "Move" : "Copy";
        String go = n == 0 ? "" : verb + " " + n + (n == 1 ? " file" : " files") + " to " + ListFolder.name(to.get());
        StringBuilder detail = new StringBuilder(card.detail());
        if (!plan.skipped().isEmpty()) {
            Plan.Skip first = plan.skipped().getFirst();
            detail.append(". ").append(plan.skipped().size()).append(" left alone: ")
                    .append(ListFolder.name(first.path())).append(", ").append(first.reason());
            if (plan.skipped().size() > 1) {
                detail.append(", and others");
            }
        }
        return rail.placed(true, places, new Card(card.headline(), detail.toString(), go, "", false));
    }

    // ------------------------------------------------------------------ carrying it out, and taking it back

    /**
     * The card's button: carry out the plan the card states. One run at a time; a second is refused on the status
     * line rather than queued, because a queued run would act on a selection the person has since moved past.
     */
    private static State carry(State s, Carry c, Out out) {
        if (s.rail().applied().act() == Act.MARK && !s.rail().targets().isEmpty()) {
            return keep(s, out);
        }
        Plan plan = s.rail().plan(names(s));
        boolean placed = s.rail().applied().act() != null && s.rail().applied().act().placed() != null;
        if (plan == null && placed) {
            plan = s.work().aim().plan().orElse(null);
        }
        if (plan == null || plan.count() == 0) {
            return s;
        }
        String words = said(s).card().go();
        return start(s, plan, Way.DO, 0L, words, out);
    }

    /**
     * The rename field submitted: planned here, against the names in the folder shown, and carried out at once, since
     * the Enter that submits a name is the person agreeing to it. A name that will not do is said, and nothing runs.
     */
    private static State rename(State s, Rename r, Out out) {
        if (s.rail().applied().act() != Act.RENAME || s.rail().targets().size() != 1) {
            return s;
        }
        Row row = s.rail().targets().getFirst();
        Plan plan = Plan.rename(row, r.name(), names(s));
        if (plan.count() == 0) {
            refuse(out, "Can't rename " + row.name() + ": " + plan.skipped().getFirst().reason());
            return s;
        }
        return start(s, plan, Way.DO, 0L, "Rename " + row.name() + " to " + r.name().strip(), out);
    }

    /** The names in the folder shown: what a new name, or an archive's, must not be. */
    private static Set<String> names(State s) {
        Set<String> out = new HashSet<>();
        for (Row r : s.rows()) {
            out.add(r.name());
        }
        return out;
    }

    /** Ctrl+Z on entry {@code u.entry()}: run the reverse of what it last did. */
    private static State undo(State s, Undo u, Out out) {
        Entry e = s.work().journal().get(u.entry());
        if (e == null || e.undone()) {
            return s;
        }
        return start(s, e.latest().reverse(), Way.UNDO, u.entry(), e.words(), out);
    }

    /** Ctrl+Y on entry {@code r.entry()}: run the reverse of its undo, which is doing it again. */
    private static State redo(State s, Redo r, Out out) {
        Entry e = s.work().journal().get(r.entry());
        if (e == null || !e.undone()) {
            return s;
        }
        return start(s, e.latest().reverse(), Way.REDO, r.entry(), e.words(), out);
    }

    private static State start(State s, Plan plan, Way way, long entry, String words, Out out) {
        if (s.work().running().isPresent() || s.work().comparing().checking().isPresent()) {
            refuse(out, "Something is already being done: wait for it, or press Stop");
            return s;
        }
        if (s.work().trash().isEmpty()) {
            refuse(out, "Nothing can be done before the session is read");
            return s;
        }
        Running r = new Running(s.work().runs() + 1, entry, way, words, way.going(plan), 0, plan.count());
        out.send(Messages.RUN_TOPIC, new Run(r.run(), plan, s.work().trash().get()));
        State next = s.withWork(s.work().starting(r));
        out.send(Messages.SUGGESTED_TOPIC, said(next));
        return next;
    }

    /** How far the run has got: the card's count. */
    private static State progress(State s, Progress p, Out out) {
        if (!s.work().runs(p.run())) {
            return s;
        }
        State next = s.withWork(s.work().at(p.done()));
        out.send(Messages.SUGGESTED_TOPIC, said(next));
        if (next.work().comparing().offer().isPresent()) {
            offers(next, out);
        }
        return next;
    }

    /**
     * What the run did. A new entry that did anything goes in the journal and the window's history; an undo or a redo
     * changes its entry. The status line says what happened, what could not be done and why, and whether it was
     * stopped; and the folder shown is listed again, so the list says it too.
     */
    private static State ran(State s, Ran r, Out out) {
        if (!s.work().runs(r.run())) {
            return s;
        }
        Running run = s.work().running().get();
        Done d = r.done();
        Work work;
        if (run.way() == Way.DO) {
            long entry = s.work().entries() + 1;
            work = d.count() == 0 ? s.work().idle() : s.work().landed(entry, new Entry(run.words(), d, false));
            if (d.count() > 0) {
                out.send(Messages.RECORDED_TOPIC, new Recorded(entry, run.words()));
            }
        } else {
            work = s.work().landed(run.entry(), new Entry(run.words(), d, run.way() == Way.UNDO));
        }
        StringBuilder words = new StringBuilder(run.way().said(run, d));
        if (d.stopped()) {
            words.append(". Stopped after ").append(d.count()).append(" of ").append(run.total());
        }
        if (!d.failed().isEmpty()) {
            words.append(". ").append(d.failed().size()).append(" could not be: ").append(d.failed().getFirst());
        }
        out.send(Messages.STATUS_TOPIC, new Status(words.toString(), !d.failed().isEmpty()));
        // An offer's run is over: its card says so at once, and again once the lists say what it did.
        State next = s.withWork(work.withComparing(work.comparing().offering(Optional.empty())));
        offers(next, out);
        out.send(Messages.SUGGESTED_TOPIC, said(next));
        // Both lists, in Two Folders: what a run did may have reached either.
        State again = next;
        for (Side side : Side.values()) {
            Optional<Path> folder = again.pane(side).folder();
            if (folder.isPresent() && (side == Side.FROM || again.to().isPresent())) {
                again = again.relisting(side);
                out.send(Messages.LISTING_TOPIC, new Listing(again.pane(side).asked(), folder.get()));
            }
        }
        return again;
    }

    // ------------------------------------------------------------------ the Preview Dock

    /**
     * The dock follows the selection: one file is previewed, asked of {@link ReadPreview} when it becomes the one;
     * none or several are a note. The preview shown stays until the next one lands, so the dock never flashes empty
     * between two files.
     */
    private static State dock(State s, Out out) {
        conflict(s, out);
        Dock d = s.work().dock();
        if (s.selected().size() == 1) {
            Path one = s.selected().iterator().next();
            if (d.path().equals(Optional.of(one))) {
                return s;
            }
            Dock next = new Dock(d.ticket() + 1, Optional.of(one));
            out.send(Messages.PREVIEWING_TOPIC, new Previewing(next.ticket(), one));
            return s.withWork(s.work().withDock(next));
        }
        String note;
        if (s.selected().isEmpty()) {
            note = "Select one file to see it here";
        } else {
            long bytes = 0L;
            for (Row r : s.rows()) {
                bytes += s.selected().contains(r.path()) ? r.size() : 0L;
            }
            note = s.selected().size() + " selected" + (bytes > 0 ? " · " + Bytes.format(bytes) : "");
        }
        out.send(Messages.DOCKED_TOPIC, new Docked(Optional.empty(), note));
        return s.withWork(s.work().withDock(new Dock(d.ticket(), Optional.empty())));
    }

    /** A preview landed: shown, unless the selection has moved on since it was asked. */
    private static State previewed(State s, Previewed p, Out out) {
        if (p.ticket() != s.work().dock().ticket() || s.work().dock().path().isEmpty()) {
            return s;
        }
        out.send(Messages.DOCKED_TOPIC, new Docked(Optional.of(p.preview()), ""));
        return s;
    }

    /**
     * The strip's Open with: the file it previews, handed on. Vexplore has no viewer of its own, so a picture or a
     * video suits Rasterware, and anything else the desktop's own program; the button's menu names either. With no
     * file in the strip (the selection moved on under the pointer) nothing is.
     */
    private static State openWith(State s, OpenWith o, Out out) {
        if (s.work().dock().path().isEmpty()) {
            return s;
        }
        Path file = s.work().dock().path().get();
        Kind kind = s.row(file).map(Row::kind).orElse(Kind.ofExtension(Kind.extension(ListFolder.name(file))));
        boolean rasterware = o.with() == With.RASTERWARE
                || (o.with() == With.SUITED && (kind == Kind.IMAGE || kind == Kind.VIDEO));
        out.send(Messages.HANDING_TOPIC, new Handing(file, rasterware));
        return s;
    }

    /**
     * What a hand-off came to, and what opened it: the desktop's opener standing in for Rasterware is said, since
     * Rasterware was asked.
     */
    private static State handedOff(State s, HandedOff h, Out out) {
        String name = ListFolder.name(h.file());
        if (h.problem() != null) {
            refuse(out, "Can't open " + name + ": " + h.problem());
        } else if (HandedOff.RASTERWARE.equals(h.to())) {
            out.send(Messages.STATUS_TOPIC, new Status("Opened " + name + " in Rasterware", false));
        } else if (h.rasterware()) {
            out.send(Messages.STATUS_TOPIC, new Status("Rasterware isn't installed, so " + name
                    + " opened in the desktop's own program", false));
        } else {
            out.send(Messages.STATUS_TOPIC, new Status("Opened " + name + " in the desktop's own program", false));
        }
        return s;
    }

    // ------------------------------------------------------------------ marks

    /** How many marks are kept; a new one past this drops the oldest. */
    static final int MARKS = 6;

    /**
     * Keep as Mark: what the rail reaches, named for where it is and what it is
     * ({@code Downloads: All videos here · 8}), newest first. A mark of the same files replaces the one before it.
     * Kept across runs, with the folder shown.
     */
    private static State keep(State s, Out out) {
        Rail rail = s.rail();
        Set<Path> paths = new HashSet<>();
        for (Row r : rail.targets()) {
            paths.add(r.path());
        }
        int n = paths.size();
        String what = n == 1 ? rail.targets().getFirst().name()
                : rail.scopes().stream().filter(o -> o.value() == rail.applied().scope()).findFirst()
                        .map(o -> o.label()).orElse("Just these") + " · " + n;
        String name = s.folder().map(ListFolder::name).orElse("") + ": " + what;
        List<Mark> kept = new ArrayList<>();
        kept.add(new Mark(name, paths));
        for (Mark m : s.work().marks()) {
            if (!m.paths().equals(paths) && kept.size() < MARKS) {
                kept.add(m);
            }
        }
        State next = s.withWork(s.work().withMarks(kept));
        out.send(Messages.MARKS_TOPIC, marks(next));
        out.send(Messages.STATUS_TOPIC, new Status("Kept as a mark: " + name, false));
        return remember(next, out);
    }

    /** A mark pressed: its folder shown, with what of it is still there selected. */
    private static State recallMark(State s, RecallMark r, Out out) {
        if (r.index() >= s.work().marks().size()) {
            return s;
        }
        Mark m = s.work().marks().get(r.index());
        Path folder = m.paths().iterator().next().getParent();
        if (folder == null) {
            return s;
        }
        return remember(show(s, folder, m.paths(), out), out);
    }

    /** A mark's × pressed: forgotten, here and in the session. */
    private static State forgetMark(State s, ForgetMark f, Out out) {
        if (f.index() >= s.work().marks().size()) {
            return s;
        }
        List<Mark> kept = new ArrayList<>(s.work().marks());
        Mark gone = kept.remove(f.index());
        State next = s.withWork(s.work().withMarks(kept));
        out.send(Messages.MARKS_TOPIC, marks(next));
        out.send(Messages.STATUS_TOPIC, new Status("Forgot the mark " + gone.name(), false));
        return remember(next, out);
    }

    private static Marks marks(State s) {
        List<String> names = new ArrayList<>(s.work().marks().size());
        for (Mark m : s.work().marks()) {
            names.add(m.name());
        }
        return new Marks(names);
    }

    // ------------------------------------------------------------------ the status line

    /** Say why on the status line, and draw the eye to it. A refusal is not worth a dialog. */
    private static void refuse(Out out, String why) {
        out.send(Messages.STATUS_TOPIC, new Status(why, true));
    }
}
