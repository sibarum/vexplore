package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Activated;
import dev.sibarum.vexplore.core.Messages.Carry;
import dev.sibarum.vexplore.core.Messages.Card;
import dev.sibarum.vexplore.core.Messages.Chip;
import dev.sibarum.vexplore.core.Messages.Dest;
import dev.sibarum.vexplore.core.Messages.Destine;
import dev.sibarum.vexplore.core.Messages.Destinations;
import dev.sibarum.vexplore.core.Messages.Destining;
import dev.sibarum.vexplore.core.Messages.Docked;
import dev.sibarum.vexplore.core.Messages.Chosen;
import dev.sibarum.vexplore.core.Messages.Find;
import dev.sibarum.vexplore.core.Messages.ForgetMark;
import dev.sibarum.vexplore.core.Messages.Found;
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
import dev.sibarum.vexplore.core.Messages.OpenIn;
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
import dev.sibarum.vexplore.core.Messages.Slot;
import dev.sibarum.vexplore.core.Messages.Start;
import dev.sibarum.vexplore.core.Messages.Stirred;
import dev.sibarum.vexplore.core.Messages.Started;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.sibarum.vexplore.core.Messages.Suggested;
import dev.sibarum.vexplore.core.Messages.Summary;
import dev.sibarum.vexplore.core.Messages.Undo;
import dev.sibarum.vexplore.core.Messages.Unwatch;
import dev.sibarum.vexplore.core.Messages.Unwatched;
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
import java.util.HashSet;
import java.util.List;
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
 * <p>The disk is the jobs' ({@link ListFolder}, {@link FindPath}, {@link OpenFile}, {@link ReadSession},
 * {@link WriteSession}): a step sends a request and a later step acts on the reply. A slow disk is a slow answer,
 * never a stalled lane. Jobs answer in no order, so a listing carries a ticket, and one overtaken by a later ask is
 * dropped: a slow share answering after the person has moved on never shows the folder they left.
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
        Activated.class, Started.class, Picked.class, Carry.class, Progress.class, Ran.class, Undo.class,
        Redo.class, Destinations.class, Destine.class, Planned.class,
        Rename.class, RecallMark.class, ForgetMark.class, Previewed.class, OpenIn.class, HandedOff.class,
        Menu.class, Stirred.class, Unwatched.class})
@Publishes({Shown.class, Summary.class, Status.class, Find.class, Listing.class, Start.class, Recall.class,
        Remember.class, Suggested.class, Run.class, Recorded.class,
        Destining.class, Planning.class, Naming.class, Marks.class,
        Previewing.class, Docked.class, Handing.class, Watch.class, Unwatch.class})
public final class Explorer {

    /** How many of each message the inbox holds before a sender is refused. A person sends one at a time. */
    static final int CAPACITY = 256;

    // ------------------------------------------------------------------ the state

    /**
     * Everything the explorer knows, in one value.
     *
     * @param folder   the folder shown, or being listed to be shown; none yet
     * @param asked    the ticket of the listing last asked for: a reply with another has been overtaken
     * @param select   the row to select when that listing lands: a file named on the command line or typed
     * @param rows     the folder's rows, as last listed. The order the list shows is the table's.
     * @param today    the day the rows were listed on, which the rail's "today" means
     * @param zone     the zone a row's day is read in
     * @param selected the rows selected, as the list last said
     * @param pick     the rail's Select, Condition and Action so far
     * @param rail     what the rail last offered: a chip pressed is resolved against it
     * @param started  the folder the application started in, shown when there is no other
     * @param armed    whether the restore is done: before then a write would overwrite what is being restored
     * @param last     the session last sent to be remembered, so an unchanged one is not written again
     * @param work     what has been carried out and can be taken back, and the run in flight
     */
    record State(Optional<Path> folder, long asked, Set<Path> select, List<Row> rows, LocalDate today,
                 ZoneId zone, Set<Path> selected, Pick pick, Rail rail, Optional<Path> started, boolean armed,
                 Optional<Remember> last, Work work) {

        State {
            rows = List.copyOf(rows);
            selected = Set.copyOf(selected);
        }

        static State initial() {
            return new State(Optional.empty(), 0L, Set.of(), List.of(), LocalDate.EPOCH, ZoneOffset.UTC,
                    Set.of(), Pick.NONE, Rail.EMPTY, Optional.empty(), false, Optional.empty(), Work.NONE);
        }

        /** Asking for {@code value}'s listing: what was listed before is no longer what is shown. */
        State asking(Path value, Set<Path> then) {
            return new State(Optional.of(value), asked + 1, then, List.of(), today, zone, Set.of(), Pick.NONE,
                    Rail.EMPTY, started, armed, last, work);
        }

        /**
         * The folder shown, asked for again after a run: what is selected is kept, so the rows still there stay
         * selected in the explorer as they do in the list.
         */
        State relisting() {
            return new State(folder, asked + 1, Set.of(), rows, today, zone, selected, Pick.NONE, Rail.EMPTY,
                    started, armed, last, work);
        }

        /**
         * The folder shown, asked for again because it changed on the disk: the selection and the rail's pick are
         * kept, since the person did nothing. A download writing beside a pick half made never undoes it.
         */
        State stirring() {
            return new State(folder, asked + 1, Set.of(), rows, today, zone, selected, pick, rail, started, armed,
                    last, work);
        }

        /**
         * A folder's rows landed: of what was selected, only what is still there. The pick is whatever asking left:
         * none for a new folder or after a run, and the one being made when the folder only changed.
         */
        State withRows(Listed l) {
            Set<Path> still = new HashSet<>();
            for (Row r : l.rows()) {
                if (selected.contains(r.path())) {
                    still.add(r.path());
                }
            }
            return new State(folder, asked, Set.of(), l.rows(), l.today(), l.zone(), still, pick, rail, started,
                    armed, last, work);
        }

        State withSelected(Set<Path> value) {
            return new State(folder, asked, select, rows, today, zone, value, pick, rail, started, armed, last, work);
        }

        State withPick(Pick value) {
            return new State(folder, asked, select, rows, today, zone, selected, value, rail, started, armed, last,
                    work);
        }

        State withRail(Rail value) {
            return new State(folder, asked, select, rows, today, zone, selected, pick, value, started, armed, last,
                    work);
        }

        State withStarted(Path value) {
            return new State(folder, asked, select, rows, today, zone, selected, pick, rail, Optional.of(value), armed,
                    last, work);
        }

        State withArmed() {
            return new State(folder, asked, select, rows, today, zone, selected, pick, rail, started, true, last, work);
        }

        State withLast(Remember value) {
            return new State(folder, asked, select, rows, today, zone, selected, pick, rail, started, armed,
                    Optional.of(value), work);
        }

        State withWork(Work value) {
            return new State(folder, asked, select, rows, today, zone, selected, pick, rail, started, armed, last,
                    value);
        }

        /** The row at {@code path}, or none: one the list showed before this listing, or never. */
        Optional<Row> row(Path path) {
            for (Row r : rows) {
                if (r.path().equals(path)) {
                    return Optional.of(r);
                }
            }
            return Optional.empty();
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
        Step<State, Started> started = b.step("started", Explorer::started);
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
        Step<State, OpenIn> openIn = b.step("openIn", Explorer::openIn);
        Step<State, HandedOff> handedOff = b.step("handedOff", Explorer::handedOff);
        Step<State, Menu> menu = b.step("menu", Explorer::menu);
        Step<State, Stirred> stirred = b.step("stirred", Explorer::stirred);
        Step<State, Unwatched> unwatched = b.step("unwatched", Explorer::unwatched);

        b.on(WindowOpened.TOPIC, begin)
                .on(Messages.RECALLED_TOPIC, recalled)
                .on(Messages.GO_TOPIC, go)
                .on(Messages.FOUND_TOPIC, found)
                .on(Messages.NAVIGATE_TOPIC, navigate)
                .on(Messages.LISTED_TOPIC, listed)
                .on(Messages.CHOSEN_TOPIC, chosen)
                .on(Messages.ACTIVATED_TOPIC, activated)
                .on(Messages.STARTED_TOPIC, started)
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
                .on(Messages.OPEN_IN_TOPIC, openIn)
                .on(Messages.HANDED_OFF_TOPIC, handedOff)
                .on(Messages.MENU_TOPIC, menu)
                .on(Messages.STIRRED_TOPIC, stirred)
                .on(Messages.UNWATCHED_TOPIC, unwatched);
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
        Remember now = new Remember(s.folder(), s.work().marks());
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

    /** The breadcrumb, the tree, Enter on a folder's row, Backspace or Alt+Up: show that folder. */
    private static State navigate(State s, Navigate n, Out out) {
        return remember(show(s, n.folder(), Set.of(), out), out);
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
     * A folder's rows: shown, unless a later ask has overtaken them. A folder that could not be read is shown empty, the
     * breadcrumb where the person asked to be, and the status line says why. By the folder's name: the breadcrumb says
     * where it is, and a status line that holds a whole path runs over the counts (framework-notes FN-6).
     */
    private static State listed(State s, Listed l, Out out) {
        if (l.ticket() != s.asked()) {
            return s;
        }
        if (l.problem() != null) {
            refuse(out, "Can't open " + ListFolder.name(l.folder()) + ": " + l.problem());
        }
        out.send(Messages.SHOWN_TOPIC, new Shown(l.folder(), l.rows(), s.select()));
        State next = s.withRows(l);
        long bytes = 0L;
        for (Row r : l.rows()) {
            bytes += next.selected().contains(r.path()) ? r.size() : 0L;
        }
        out.send(Messages.SUMMARY_TOPIC, new Summary(l.rows().size(), next.selected().size(), bytes));
        return dock(suggest(watch(next, l, out), true, out), out);
    }

    /**
     * Watch the folder whose rows just landed, unless it already is. Asked when they land rather than when they are
     * asked for, so a folder that cannot be read is not watched; and then the one before it, no longer shown, is not.
     */
    private static State watch(State s, Listed l, Out out) {
        Optional<Path> was = s.work().watched().folder();
        if (l.problem() != null) {
            if (was.isEmpty()) {
                return s;
            }
            out.send(Messages.UNWATCH_TOPIC, new Unwatch());
            return s.withWork(s.work().withWatched(Watched.NONE));
        }
        if (was.equals(Optional.of(l.folder()))) {
            return s;
        }
        out.send(Messages.WATCH_TOPIC, new Watch(l.folder()));
        return s.withWork(s.work().withWatched(new Watched(Optional.of(l.folder()))));
    }

    /**
     * The folder shown changed on the disk: list it again, keeping the selection and the pick. Said of a folder no
     * longer shown, or while another is being asked for, it is dropped, as a stale listing is.
     */
    private static State stirred(State s, Stirred m, Out out) {
        if (!s.folder().equals(Optional.of(m.folder())) || !s.work().watched().folder().equals(s.folder())) {
            return s;
        }
        State next = s.stirring();
        out.send(Messages.LISTING_TOPIC, new Listing(next.asked(), m.folder()));
        return next;
    }

    /**
     * The watch ended. On the folder still watched (it was moved or removed), it is no longer: the listing its last
     * {@link Stirred} asked for says so, and a folder shown again is watched again.
     */
    private static State unwatched(State s, Unwatched u, Out out) {
        if (!s.work().watched().folder().equals(Optional.of(u.folder()))) {
            return s;
        }
        return s.withWork(s.work().withWatched(Watched.NONE));
    }

    /**
     * What the list says is selected: counted for the status line, with the selected files' bytes. A path that is not
     * one of this folder's rows (the list had not caught up with a new listing) is not counted.
     */
    private static State chosen(State s, Chosen c, Out out) {
        Set<Path> selected = new HashSet<>();
        long bytes = 0L;
        for (Row r : s.rows()) {
            if (c.paths().contains(r.path())) {
                selected.add(r.path());
                bytes += r.size();
            }
        }
        out.send(Messages.SUMMARY_TOPIC, new Summary(s.rows().size(), selected.size(), bytes));
        return dock(suggest(s.withSelected(selected), out), out);
    }

    /** Enter or a double click on a row: a folder is gone into, and a file opened as the desktop would open it. */
    private static State activated(State s, Activated a, Out out) {
        Optional<Row> row = s.row(a.path());
        if (row.isEmpty()) {
            return s;
        }
        if (row.get().folder()) {
            return remember(show(s, a.path(), Set.of(), out), out);
        }
        out.send(Messages.START_TOPIC, new Start(a.path()));
        return s;
    }

    /** What opening a file came to: said either way, since what opens it is another program and may take a moment. */
    private static State started(State s, Started st, Out out) {
        String name = ListFolder.name(st.file());
        if (st.problem() != null) {
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
    private static State menu(State s, Menu m, Out out) {
        if (Menu.OPEN.equals(m.action())) {
            if (s.selected().size() != 1) {
                refuse(out, "Open is for one item at a time");
                return s;
            }
            return activated(s, new Activated(s.selected().iterator().next()), out);
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
        if (s.work().running().isPresent()) {
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
        State next = s.withWork(work);
        out.send(Messages.SUGGESTED_TOPIC, said(next));
        if (next.folder().isEmpty()) {
            return next;
        }
        State again = next.relisting();
        out.send(Messages.LISTING_TOPIC, new Listing(again.asked(), again.folder().get()));
        return again;
    }

    // ------------------------------------------------------------------ the Preview Dock

    /**
     * The dock follows the selection: one file is previewed, asked of {@link ReadPreview} when it becomes the one;
     * none or several are a note. The preview shown stays until the next one lands, so the dock never flashes empty
     * between two files.
     */
    private static State dock(State s, Out out) {
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
     * The dock's Open in Rasterware: the file it previews, handed on. Vexplore has no viewer of its own, so a picture
     * or a video is viewed in Rasterware. With no file in the dock (the selection moved on under the pointer) nothing is.
     */
    private static State openIn(State s, OpenIn o, Out out) {
        s.work().dock().path().ifPresent(p -> out.send(Messages.HANDING_TOPIC, new Handing(p)));
        return s;
    }

    /** What a hand-off came to, and what opened it: the desktop's opener is said, since Rasterware was asked. */
    private static State handedOff(State s, HandedOff h, Out out) {
        String name = ListFolder.name(h.file());
        if (h.problem() != null) {
            refuse(out, "Can't open " + name + ": " + h.problem());
        } else if (HandedOff.RASTERWARE.equals(h.to())) {
            out.send(Messages.STATUS_TOPIC, new Status("Opened " + name + " in Rasterware", false));
        } else {
            out.send(Messages.STATUS_TOPIC, new Status("Rasterware isn't installed, so " + name
                    + " opened in the desktop's own program", false));
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
