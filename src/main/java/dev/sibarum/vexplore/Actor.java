package dev.sibarum.vexplore;

import dev.sibarum.vexplore.Doc.Mark;
import dev.sibarum.vexplore.files.Destinations;
import dev.sibarum.vexplore.ops.Operations;
import dev.sibarum.vexplore.ops.Operations.Done;
import dev.sibarum.vexplore.ops.Plan;
import dev.sibarum.vexplore.remote.Remotes;
import dev.sibarum.vexplore.suggest.Bytes;
import dev.vexelray.framework.shell.Dialogs;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.edit.Change;
import dev.vexelray.gui.core.edit.History;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Doing things: the one place a button becomes a change to the disk.
 *
 * <p>Every method here is called from a handler and returns at once. The work runs on {@link Gui#offload()}, says
 * what it is doing through the model's {@code busy} line, and lands its result the same way, so the window never
 * waits on a disk and the rail is always showing what is true. Nothing here decides <em>what</em> will happen: that
 * is {@link Doc#plan()}, a value the rail has already drawn, and {@link Operations#execute} does exactly that.
 *
 * <p>One thing runs at a time. A second press while something is running is ignored, because two operations on
 * overlapping files are a question nobody asked.
 *
 * <p>What it does is recorded in the window's undo history, so Ctrl+Z, Ctrl+Y and the rail's Undo button take
 * file operations back and forth in the same order as everything else done in the window, typing included.
 */
final class Actor {

    private final Gui gui;
    private final Model model;
    private final Browser browser;
    private final Dialogs dialogs;
    private final Path trash;
    /** The window's undo history: what has been done to the disk is undone there, with everything else. */
    private final History history;
    /** What else is listed again after a change: the second folder, when one is shown. */
    private final List<Runnable> changed = new java.util.concurrent.CopyOnWriteArrayList<>();

    Actor(Gui gui, Model model, Browser browser, Dialogs dialogs, Path trash) {
        this.gui = gui;
        this.model = model;
        this.browser = browser;
        this.dialogs = dialogs;
        this.trash = trash;
        History bound = gui.history();
        if (bound == null) {
            // A tree the framework did not build has no window history; this one becomes it.
            bound = new History();
            gui.history(bound);
        }
        if (gui.dropHistory() == null) {
            gui.dropHistory(bound);   // a drop is a file operation, undone with the rest
        }
        this.history = bound;
    }

    /** The history Ctrl+Z, Ctrl+Y and the rail's Undo button reach. Undo through {@code gui.undo}, never directly. */
    History history() {
        return history;
    }

    /** Do what the rail says it will do. */
    void run() {
        Doc doc = model.doc();
        Plan plan = doc.plan();
        if (plan == null || plan.count() == 0 || !doc.work().busy().isEmpty()) {
            return;
        }
        Plan.Step first = plan.steps().get(0);
        String verb = switch (plan.kind()) {
            case MOVE -> "Moving";
            case COPY -> Remotes.is(first.from()) ? "Downloading" : Remotes.is(first.to()) ? "Uploading" : "Copying";
            case DELETE -> "Deleting";
            case ARCHIVE -> "Archiving";
            case RENAME -> "Renaming";
            case NEW_FOLDER -> "Making";
        };
        model.work(w -> w.running(verb + " " + plan.count() + (plan.count() == 1 ? " file" : " files") + "…"));
        offload(() -> {
            // A remote file is deleted into a trash on its own host: a rename there, not a download here.
            Path bin = trash;
            if (plan.kind() == Plan.Kind.DELETE && Remotes.is(first.from())) {
                try {
                    bin = Remotes.trash(first.from());
                } catch (java.io.IOException e) {
                    model.work(w -> w.running("").noting("Nothing was deleted: " + Remotes.host(first.from()).label()
                            + " did not say where its trash would be (" + e.getMessage() + ")."));
                    return;
                }
            }
            int[] last = {0};
            Done done = Operations.execute(plan, bin, n -> {
                // A busy line per file would be a version per file; once every few is as live as it needs to be.
                if (n - last[0] >= Math.max(1, plan.count() / 20) || n == plan.count()) {
                    last[0] = n;
                    model.work(w -> w.running(verb + " " + n + " of " + plan.count() + "…"));
                }
            });
            if (done.count() > 0) {
                history.record(new Undo(new AtomicReference<>(done)));   // before the notice, which draws the button
            }
            model.work(w -> w.finished(summary(plan, done)));
            afterChange();
        });
    }

    /**
     * Moving or copying {@code held} into {@code into}, as a drop makes it: a change for the drop history to perform,
     * so a drop is undone by Ctrl+Z exactly as the rail's button is.
     *
     * <p>Planned when it runs, not when it is offered: a drop target is asked every frame on the GUI thread and may
     * not read a disk, so what is already at the destination is learnt here, off the frame loop, and left alone as
     * the rail's plan would leave it. The notice line says how it went, skipped files included. Refused while
     * anything else is running, like every other operation.
     */
    Change dropping(Plan.Kind kind, List<dev.sibarum.vexplore.files.Entry> held, Path into) {
        List<Path> from = held.stream().map(dev.sibarum.vexplore.files.Entry::path).toList();
        List<Long> sizes = held.stream().map(dev.sibarum.vexplore.files.Entry::size).toList();
        return new Change() {
            @Override
            public Check check() {
                return idle() ? Check.OK : Check.REFUSED;
            }

            @Override
            public Change apply() {
                AtomicReference<Done> done = new AtomicReference<>(
                        new Done(kind, List.of(), List.of(), null, 0L));
                String verb = kind == Plan.Kind.MOVE ? "Moving" : "Copying";
                model.work(w -> w.running(verb + " " + from.size() + (from.size() == 1 ? " file" : " files") + "…"));
                offload(() -> {
                    Plan plan = Plan.of(kind, from, sizes, into, Destinations.names(into), null);
                    Done d = plan.count() == 0 ? new Done(kind, List.of(), List.of(), null, 0L)
                            : Operations.execute(plan, trash, n -> { });
                    done.set(d);
                    String text = plan.count() == 0
                            ? "Nothing " + (kind == Plan.Kind.MOVE ? "moved" : "copied") + ": "
                              + (plan.skipped().isEmpty() ? "nothing to do" : plan.skipped().getFirst().reason()) + "."
                            : summary(plan, d);
                    model.work(w -> w.finished(text));
                    afterChange();
                });
                return new Undo(done);
            }
        };
    }

    /** Run {@code r} after anything here changes the disk, as well as listing the folder shown again. */
    void onChanged(Runnable r) {
        changed.add(r);
    }

    /** Whether nothing is running: one thing at a time, undo and redo included. */
    private boolean idle() {
        return model.doc().work().busy().isEmpty();
    }

    /**
     * The way back from what was done to the disk, as an entry in the window's history.
     *
     * <p><b>Applying it only starts the work.</b> An entry applies under the history's lock, and putting back a
     * thousand files is not a quick step, so the disk is touched on the offload lane exactly as it was before this
     * was an entry, with the busy line saying so; the redo is handed back at once. It names no target: what it
     * changes is files, not a control, and the busy line and the notice are where the user watches it happen.
     *
     * <p>Refused while anything is running, so a Ctrl+Z pressed mid-copy is told so rather than ignored. The
     * {@link Done} is a reference because an undo made by a redo still running learns what was redone only when it
     * finishes — and it cannot be applied before then, since that redo is running.
     */
    private final class Undo implements Change {

        private final AtomicReference<Done> done;

        Undo(AtomicReference<Done> done) {
            this.done = done;
        }

        @Override
        public Check check() {
            return idle() ? Check.OK : Check.REFUSED;
        }

        @Override
        public Change apply() {
            Done d = done.get();
            model.work(w -> w.running("Undoing…"));
            offload(() -> {
                List<String> failed = Operations.undo(d);
                String text = failed.isEmpty() ? "Undid: " + describe(d)
                        : "Undid part of: " + describe(d) + " · " + failed.size() + " could not be put back";
                model.work(w -> w.finished(text));
                afterChange();
            });
            return new Redo(d);
        }
    }

    /** Doing again what an {@link Undo} took back, to the same places, never over anything ({@link Operations#redo}). */
    private final class Redo implements Change {

        private final Done done;

        Redo(Done done) {
            this.done = done;
        }

        @Override
        public Check check() {
            return idle() ? Check.OK : Check.REFUSED;
        }

        @Override
        public Change apply() {
            AtomicReference<Done> again = new AtomicReference<>(done);
            model.work(w -> w.running("Redoing…"));
            offload(() -> {
                Done d = Operations.redo(done);
                again.set(d);
                String text = d.failed().isEmpty() ? "Redid: " + describe(d)
                        : "Redid part of: " + describe(d) + " · " + d.failed().size() + " could not be done again";
                model.work(w -> w.finished(text));
                afterChange();
            });
            return new Undo(again);
        }
    }

    /** Keep what the rail reaches as a mark: a group to come back to. */
    void mark() {
        Doc doc = model.doc();
        var effect = doc.rail().effect();
        if (effect.count() == 0) {
            return;
        }
        Set<Path> paths = dev.sibarum.vexplore.suggest.Suggestions.paths(effect);
        String name = "Mark " + (doc.work().marks().size() + 1) + " · " + effect.count()
                + (effect.count() == 1 ? " file" : " files");
        model.work(w -> w.marking(new Mark(name, paths)).noting("Kept " + name + "."));
    }

    void unmark(String name) {
        model.work(w -> w.unmarking(name));
    }

    /** Select what a mark holds, as far as it is in this folder. */
    void recall(Mark m) {
        Set<Path> here = new java.util.HashSet<>();
        for (var e : model.doc().entries()) {
            if (m.paths().contains(e.path())) {
                here.add(e.path());
            }
        }
        model.select(here);
    }

    /** Use {@code folder} as the destination, and learn what is in it. */
    void destination(Path folder) {
        model.work(w -> w.choosing(folder, Set.of()));
        offload(() -> {
            Set<String> names = Destinations.names(folder);
            model.work(w -> folder.equals(w.destination()) ? w.choosing(folder, names) : w);
        });
    }

    /**
     * Make the empty folder {@code name} in the folder shown, and select it once the listing is back. Not planned
     * first: what it will do is the name just typed, it touches nothing that exists, and Ctrl+Z takes it back while it
     * is still empty.
     */
    void makeFolder(String name) {
        Doc doc = model.doc();
        if (doc.folder() == null || !doc.work().busy().isEmpty()) {
            return;
        }
        String n = name == null ? "" : name.strip();
        if (!Plan.named(n)) {
            model.work(w -> w.noting("Not a folder name: " + (n.isEmpty() ? "it is empty" : n) + "."));
            return;
        }
        Path dir = doc.folder().resolve(n);
        model.work(w -> w.running("Making " + n + "…"));
        offload(() -> {
            Done done = Operations.makeFolder(dir);
            if (done.count() == 0) {
                model.work(w -> w.running("").noting("No folder made: " + done.failed().get(0) + "."));
                return;
            }
            history.record(new Undo(new AtomicReference<>(done)));
            model.work(w -> w.finished(capital(describe(done)) + "."));
            browser.reveal(dir);
        });
    }

    /**
     * Ask for a folder to open with the folder dialog, starting at {@code start}, and hand the answer to
     * {@code then}. The dialog browses this disk, so it starts in Downloads when {@code start} is remote or absent.
     */
    void askFolder(Path start, java.util.function.Consumer<Path> then) {
        Path from = start == null || Remotes.is(start) ? Startup.downloads() : start;
        dialogs.openFolder(from, then, () -> { });
    }

    /** Ask for a destination with the folder dialog. */
    void choose() {
        Path start = model.doc().work().destination() != null ? model.doc().work().destination()
                : model.doc().remote() ? Startup.downloads() : model.doc().folder();   // the dialog browses this disk
        // The answer arrives on the handler lane, and destination puts its own disk work on the offload lane.
        dialogs.openFolder(start, this::destination, () -> { });
    }

    // ------------------------------------------------------------------ helpers

    private void afterChange() {
        Path dest = model.doc().work().destination();
        if (dest != null) {
            destination(dest);
        }
        browser.reload();
        changed.forEach(Runnable::run);
    }

    private void offload(Runnable r) {
        if (Startup.synchronous()) {
            r.run();
        } else {
            gui.offload().execute(r);
        }
    }

    private static String summary(Plan plan, Done done) {
        String what = capital(describe(done));
        if (done.failed().isEmpty() && plan.skipped().isEmpty()) {
            return what + ".";
        }
        int skipped = plan.skipped().size() + done.failed().size();
        return what + " · " + skipped + " left alone.";
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String describe(Done d) {
        String n = d.count() + (d.count() == 1 ? " file" : " files");
        return switch (d.kind()) {
            case MOVE -> "moved " + n + " (" + Bytes.format(d.bytes()) + ")";
            case COPY -> (d.done().isEmpty() ? "copied "
                    : Remotes.is(d.done().get(0).from()) ? "downloaded "
                    : Remotes.is(d.done().get(0).to()) ? "uploaded " : "copied ")
                    + n + " (" + Bytes.format(d.bytes()) + ")";
            case DELETE -> "deleted " + n + (!d.done().isEmpty() && Remotes.is(d.done().get(0).from())
                    ? " (kept in " + Remotes.TRASH + " on " + Remotes.host(d.done().get(0).from()).label() + ")"
                    : " (kept in the trash)");
            case ARCHIVE -> "archived " + n + " into " + d.archive().getFileName();
            case RENAME -> d.done().isEmpty() ? "renamed nothing"
                    : "renamed " + name(d.done().get(0).from()) + " to " + name(d.done().get(0).to());
            case NEW_FOLDER -> d.done().isEmpty() ? "made no folder" : "made the folder " + name(d.done().get(0).to());
        };
    }

    private static String name(Path p) {
        return p.getFileName() == null ? p.toString() : p.getFileName().toString();
    }
}
