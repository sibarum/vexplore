package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Activated;
import dev.sibarum.vexplore.core.Messages.Chosen;
import dev.sibarum.vexplore.core.Messages.Find;
import dev.sibarum.vexplore.core.Messages.Found;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.sibarum.vexplore.core.Messages.Is;
import dev.sibarum.vexplore.core.Messages.Listed;
import dev.sibarum.vexplore.core.Messages.Listing;
import dev.sibarum.vexplore.core.Messages.Navigate;
import dev.sibarum.vexplore.core.Messages.Recall;
import dev.sibarum.vexplore.core.Messages.Recalled;
import dev.sibarum.vexplore.core.Messages.Remember;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Shown;
import dev.sibarum.vexplore.core.Messages.Start;
import dev.sibarum.vexplore.core.Messages.Started;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.sibarum.vexplore.core.Messages.Summary;
import dev.sibarum.vexplore.core.Messages.Why;
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
        Activated.class, Started.class})
@Publishes({Shown.class, Summary.class, Status.class, Find.class, Listing.class, Start.class, Recall.class,
        Remember.class})
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
     * @param selected the rows selected, as the list last said
     * @param started  the folder the application started in, shown when there is no other
     * @param armed    whether the restore is done: before then a write would overwrite what is being restored
     * @param last     the session last sent to be remembered, so an unchanged one is not written again
     */
    record State(Optional<Path> folder, long asked, Optional<Path> select, List<Row> rows, Set<Path> selected,
                 Optional<Path> started, boolean armed, Optional<Remember> last) {

        State {
            rows = List.copyOf(rows);
            selected = Set.copyOf(selected);
        }

        static State initial() {
            return new State(Optional.empty(), 0L, Optional.empty(), List.of(), Set.of(), Optional.empty(), false,
                    Optional.empty());
        }

        /** Asking for {@code value}'s listing: what was listed before is no longer what is shown. */
        State asking(Path value, Optional<Path> then) {
            return new State(Optional.of(value), asked + 1, then, List.of(), Set.of(), started, armed, last);
        }

        State withRows(List<Row> value) {
            return new State(folder, asked, Optional.empty(), value, Set.of(), started, armed, last);
        }

        State withSelected(Set<Path> value) {
            return new State(folder, asked, select, rows, value, started, armed, last);
        }

        State withStarted(Path value) {
            return new State(folder, asked, select, rows, selected, Optional.of(value), armed, last);
        }

        State withArmed() {
            return new State(folder, asked, select, rows, selected, started, true, last);
        }

        State withLast(Remember value) {
            return new State(folder, asked, select, rows, selected, started, armed, Optional.of(value));
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

        b.on(WindowOpened.TOPIC, begin)
                .on(Messages.RECALLED_TOPIC, recalled)
                .on(Messages.GO_TOPIC, go)
                .on(Messages.FOUND_TOPIC, found)
                .on(Messages.NAVIGATE_TOPIC, navigate)
                .on(Messages.LISTED_TOPIC, listed)
                .on(Messages.CHOSEN_TOPIC, chosen)
                .on(Messages.ACTIVATED_TOPIC, activated)
                .on(Messages.STARTED_TOPIC, started);
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
        State next = s.withStarted(r.started());
        if (!r.arguments().isEmpty()) {
            out.send(Messages.FIND_TOPIC, new Find(r.arguments().getFirst(), Why.ARGUMENT));
            return next;
        }
        if (r.folder().isPresent()) {
            out.send(Messages.FIND_TOPIC, new Find(r.folder().get(), Why.RESTORE_FOLDER));
            return next;
        }
        return remember(show(next, r.started(), Optional.empty(), out).withArmed(), out);
    }

    /**
     * Send the session to be remembered if it differs from what was last sent. Not before the restore is done, which
     * would overwrite what is being brought back.
     */
    private static State remember(State s, Out out) {
        if (!s.armed()) {
            return s;
        }
        Remember now = new Remember(s.folder());
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
        Optional<Path> select = f.is() == Is.FILE ? Optional.of(f.path()) : Optional.empty();
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
        return remember(show(s, n.folder(), Optional.empty(), out), out);
    }

    /**
     * Ask for {@code folder}'s rows, to select {@code select} among them when they land. Nothing is shown until they do,
     * so the breadcrumb and the list never disagree about which folder this is.
     */
    private static State show(State s, Path folder, Optional<Path> select, Out out) {
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
        out.send(Messages.SUMMARY_TOPIC, new Summary(l.rows().size(), 0, 0L));
        return s.withRows(l.rows());
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
        return s.withSelected(selected);
    }

    /** Enter or a double click on a row: a folder is gone into, and a file opened as the desktop would open it. */
    private static State activated(State s, Activated a, Out out) {
        Optional<Row> row = s.row(a.path());
        if (row.isEmpty()) {
            return s;
        }
        if (row.get().folder()) {
            return remember(show(s, a.path(), Optional.empty(), out), out);
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

    // ------------------------------------------------------------------ the status line

    /** Say why on the status line, and draw the eye to it. A refusal is not worth a dialog. */
    private static void refuse(Out out, String why) {
        out.send(Messages.STATUS_TOPIC, new Status(why, true));
    }
}
