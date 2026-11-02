package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Mark;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the explorer has carried out and can take back, and the run in flight: a field of its {@code State}, changed
 * only by its steps.
 *
 * <p>The window's history holds only entry numbers (docs/shape.md, M3); what each entry did is here, in the
 * {@link #journal}, so the decision of what to take back is a step's. Each entry keeps its latest {@link Done}, whose
 * reverse is the next thing to run: an undo after it is done, a redo after it is undone.
 *
 * @param trash   where a delete goes, once the session has said; none before
 * @param runs    the explorer's count of its runs, the last one's number
 * @param running the run in flight, or none: one at a time
 * @param entries the journal's count, the last entry's number
 * @param journal each entry by number
 * @param aim     where a move or a copy would go
 * @param marks   the marks kept, newest first
 * @param dock    the preview asked for the dock
 * @param watched the folder watched, and what landed in it lately
 */
record Work(Optional<Path> trash, long runs, Optional<Running> running, long entries, Map<Long, Entry> journal,
            Aim aim, List<Mark> marks, Dock dock, Watched watched) {

    static final Work NONE = new Work(Optional.empty(), 0L, Optional.empty(), 0L, Map.of(), Aim.NONE, List.of(),
            Dock.NONE, Watched.NONE);

    Work {
        journal = Map.copyOf(journal);
        marks = List.copyOf(marks);
    }

    /**
     * Which way a run goes: a new entry, or one already in the journal taken back or done again. Each says what the
     * card shows while it runs and what the status line says after.
     */
    enum Way {
        DO {
            @Override
            String going(Plan plan) {
                return plan.op().going();
            }

            /** {@code Deleted 4 files into Vexplore's trash}, from the entry's words, or how many of them were. */
            @Override
            String said(Running r, Done d) {
                String what = d.count() == r.total()
                        ? d.op().gone() + r.words().substring(r.words().indexOf(' '))
                        : d.op().gone() + " " + d.count() + " of " + r.total();
                if (d.op() == Plan.Op.ARCHIVE && d.count() > 0) {
                    return what + " into " + ListFolder.name(d.done().getFirst().to());
                }
                return d.op() == Plan.Op.DELETE && d.count() > 0
                        ? what + " into Vexplore's trash. Ctrl+Z puts " + (d.count() == 1 ? "it" : "them") + " back"
                        : what;
            }
        },
        UNDO {
            @Override
            String going(Plan plan) {
                return "Undoing";
            }

            @Override
            String said(Running r, Done d) {
                return "Undone: " + r.words();
            }
        },
        REDO {
            @Override
            String going(Plan plan) {
                return "Redoing";
            }

            @Override
            String said(Running r, Done d) {
                return "Done again: " + r.words();
            }
        };

        abstract String going(Plan plan);

        abstract String said(Running r, Done d);
    }

    /**
     * A run in flight.
     *
     * @param run   its number, which its count and its answer carry
     * @param entry the journal entry it takes back or does again; 0 for a new one
     * @param way   which way it goes
     * @param words what the entry is called ({@code Delete 4 files}): the history's words, and the status line's
     * @param going the card's word while it runs ({@code Deleting})
     * @param done  how many steps are finished
     * @param total how many there are
     */
    record Running(long run, long entry, Way way, String words, String going, int done, int total) {

        Running at(int value) {
            return new Running(run, entry, way, words, going, value, total);
        }

        /** {@code Deleting · 3 of 9}. */
        String said() {
            return going + " · " + done + " of " + total;
        }
    }

    /**
     * One thing done that can be taken back.
     *
     * @param words  what it is called ({@code Delete 4 files})
     * @param latest what the last run of it did, whose reverse is the next
     * @param undone whether it stands taken back
     */
    record Entry(String words, Done latest, boolean undone) {
    }

    Work withTrash(Path value) {
        return new Work(Optional.of(value), runs, running, entries, journal, aim, marks, dock, watched);
    }

    /** Run {@code r} starts; its number is the next. */
    Work starting(Running r) {
        return new Work(trash, r.run(), Optional.of(r), entries, journal, aim, marks, dock, watched);
    }

    Work at(int done) {
        return new Work(trash, runs, running.map(r -> r.at(done)), entries, journal, aim, marks, dock, watched);
    }

    /** The run is over, and entry {@code number} is now {@code entry}. */
    Work landed(long number, Entry entry) {
        Map<Long, Entry> next = new HashMap<>(journal);
        next.put(number, entry);
        return new Work(trash, runs, Optional.empty(), Math.max(entries, number), next, aim, marks, dock, watched);
    }

    /** The run is over, and nothing is recorded: it did nothing. */
    Work idle() {
        return new Work(trash, runs, Optional.empty(), entries, journal, aim, marks, dock, watched);
    }

    Work withAim(Aim value) {
        return new Work(trash, runs, running, entries, journal, value, marks, dock, watched);
    }

    Work withMarks(List<Mark> value) {
        return new Work(trash, runs, running, entries, journal, aim, value, dock, watched);
    }

    Work withDock(Dock value) {
        return new Work(trash, runs, running, entries, journal, aim, marks, value, watched);
    }

    /**
     * The preview asked for the dock: its ticket, and the file it is for, or none when the dock shows a note. An
     * answer with another ticket is for a file no longer the one selected.
     */
    record Dock(long ticket, Optional<Path> path) {

        static final Dock NONE = new Dock(0L, Optional.empty());
    }

    Work withWatched(Watched value) {
        return new Work(trash, runs, running, entries, journal, aim, marks, dock, value);
    }

    /**
     * The folder {@link WatchFolder} was asked to watch, or none. What it says of any other folder is about one no
     * longer shown.
     */
    record Watched(Optional<Path> folder) {

        static final Watched NONE = new Watched(Optional.empty());
    }

    /** Whether run {@code run} is the one in flight. */
    boolean runs(long run) {
        return running.isPresent() && running.get().run() == run;
    }
}
