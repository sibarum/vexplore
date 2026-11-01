package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Find;
import dev.sibarum.vexplore.core.Messages.Found;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.sibarum.vexplore.core.Messages.Kind;
import dev.sibarum.vexplore.core.Messages.Recall;
import dev.sibarum.vexplore.core.Messages.Recalled;
import dev.sibarum.vexplore.core.Messages.Remember;
import dev.sibarum.vexplore.core.Messages.ShowFolder;
import dev.sibarum.vexplore.core.Messages.Status;
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
import java.util.Optional;

/**
 * The controller: every decision the application makes, as declared steps on one state, reached only by messages.
 * What the template's editor became once its files and tabs were gone: which folder is shown, what the
 * path bar names, and the folder brought back from the last session.
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
 * <p>The disk is the jobs' ({@link FindPath}, {@link ReadSession}, {@link WriteSession}): a step sends a request and a
 * later step acts on the reply. A slow disk is a slow answer, never a stalled lane.
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
@Hears({WindowOpened.class, Recalled.class, Go.class, Found.class})
@Publishes({ShowFolder.class, Status.class, Find.class, Recall.class, Remember.class})
public final class Explorer {

    /** How many of each message the inbox holds before a sender is refused. A person sends one at a time. */
    static final int CAPACITY = 256;

    // ------------------------------------------------------------------ the state

    /**
     * Everything the explorer knows, in one value.
     *
     * @param folder  the folder the navigator shows, or none yet
     * @param started the folder the application started in, shown when there is no other
     * @param armed   whether the restore is done: before then a write would overwrite what is being restored
     * @param last    the session last sent to be remembered, so an unchanged one is not written again
     */
    record State(Optional<Path> folder, Optional<Path> started, boolean armed, Optional<Remember> last) {

        static State initial() {
            return new State(Optional.empty(), Optional.empty(), false, Optional.empty());
        }

        State withFolder(Path value) {
            return new State(Optional.of(value), started, armed, last);
        }

        State withStarted(Path value) {
            return new State(folder, Optional.of(value), armed, last);
        }

        State withArmed() {
            return new State(folder, started, true, last);
        }

        State withLast(Remember value) {
            return new State(folder, started, armed, Optional.of(value));
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

        b.on(WindowOpened.TOPIC, begin)
                .on(Messages.RECALLED_TOPIC, recalled)
                .on(Messages.GO_TOPIC, go)
                .on(Messages.FOUND_TOPIC, found);
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
        return remember(showFolder(next, r.started(), out).withArmed(), out);
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

    // ------------------------------------------------------------------ the path bar

    /** What the path bar was given. A relative path is relative to the folder shown. */
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
     * it is in. A remembered folder that has gone is forgotten, and the folder the application started in is shown.
     */
    private static State found(State s, Found f, Out out) {
        Path shown = f.kind() == Kind.FOLDER ? f.path() : f.kind() == Kind.FILE ? f.path().getParent() : null;
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
        State next = showFolder(s, shown, out);
        return f.why() == Why.GO ? remember(next, out) : remember(next.withArmed(), out);
    }

    private static State showFolder(State s, Path folder, Out out) {
        out.send(Messages.SHOW_FOLDER_TOPIC, new ShowFolder(folder));
        return s.withFolder(folder);
    }

    // ------------------------------------------------------------------ the status line

    /** Say why on the status line, and draw the eye to it. A refusal is not worth a dialog. */
    private static void refuse(Out out, String why) {
        out.send(Messages.STATUS_TOPIC, new Status(why, true));
    }
}
