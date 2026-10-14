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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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
 */
final class Actor {

    private final Gui gui;
    private final Model model;
    private final Browser browser;
    private final Dialogs dialogs;
    private final Path trash;

    Actor(Gui gui, Model model, Browser browser, Dialogs dialogs, Path trash) {
        this.gui = gui;
        this.model = model;
        this.browser = browser;
        this.dialogs = dialogs;
        this.trash = trash;
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
            model.work(w -> w.did(done, summary(plan, done)));
            afterChange(doc);
        });
    }

    /** Take the last thing back. */
    void undo() {
        Doc doc = model.doc();
        List<Done> history = doc.work().history();
        if (history.isEmpty() || !doc.work().busy().isEmpty()) {
            return;
        }
        Done last = history.get(history.size() - 1);
        model.work(w -> w.running("Undoing…"));
        offload(() -> {
            List<String> failed = Operations.undo(last);
            String text = failed.isEmpty() ? "Undid: " + describe(last)
                    : "Undid part of: " + describe(last) + " · " + failed.size() + " could not be put back";
            model.work(w -> w.undid(text));
            afterChange(doc);
        });
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
            model.work(w -> w.did(done, capital(describe(done)) + "."));
            browser.reveal(dir);
        });
    }

    /** Ask for a destination with the folder dialog. */
    void choose() {
        Path start = model.doc().work().destination() != null ? model.doc().work().destination()
                : model.doc().remote() ? Startup.downloads() : model.doc().folder();   // the dialog browses this disk
        // The answer arrives on the handler lane, and destination puts its own disk work on the offload lane.
        dialogs.openFolder(start, this::destination, () -> { });
    }

    // ------------------------------------------------------------------ helpers

    private void afterChange(Doc before) {
        Path dest = model.doc().work().destination();
        if (dest != null) {
            destination(dest);
        }
        browser.reload();
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
