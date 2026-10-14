package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Destinations;
import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;
import dev.sibarum.vexplore.remote.Remotes;
import dev.sibarum.vexplore.suggest.Suggestions.Act;
import dev.vexelray.gui.core.Gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps the rail's suggested destinations matching what it is about to act on.
 *
 * <p>Listing the neighbouring folders is disk work, so it runs on the offload lane and lands through the model,
 * which keeps it only if the question is still the one that was asked. It asks once per
 * <em>(folder, kind)</em> and only while an action that needs a destination is chosen — the rail's promise is that
 * a suggestion is ready, not that every possible one was worked out for every selection.
 */
final class Destinator {

    private record Question(Path folder, Kind kind) {
    }

    private final Gui gui;
    private final Model model;
    private Question asked;
    /** The remote folder most recently shown, once listed: offered as a place to upload to. */
    private volatile Path lastRemote;

    Destinator(Gui gui, Model model) {
        this.gui = gui;
        this.model = model;
        model.onChange(this::follow);
    }

    private void follow(Doc doc) {
        if (doc.remote() && !doc.loading()) {
            lastRemote = doc.folder();
        }
        Act act = doc.pick().act();
        Question q = null;
        if ((act == Act.MOVE || act == Act.COPY) && doc.folder() != null) {
            Kind kind = dominant(doc);
            if (kind != null) {
                q = new Question(doc.folder(), kind);
            }
        }
        synchronized (this) {
            if (Objects.equals(q, asked)) {
                return;
            }
            asked = q;
        }
        if (q == null) {
            return;
        }
        Question mine = q;
        Runnable work = () -> {
            var found = new java.util.ArrayList<Destinations.Dest>();
            if (Remotes.is(mine.folder())) {
                found.addAll(here(mine.kind()));
            } else {
                Destinations.Dest guess = Destinations.home(mine.folder(), Startup.home(), mine.kind());
                var near = Destinations.suggest(mine.folder(), mine.kind(),
                        guess == null ? Set.of() : Set.of(guess.path()));
                if (guess != null) {
                    found.add(guess);
                }
                found.addAll(near);
                // The remote folder last shown is where an upload most likely goes. It takes the third place the
                // rail has room for, and is offered only while its host is still connected, so offering it costs
                // no connection.
                Path there = lastRemote;
                if (there != null && Remotes.connected(there)) {
                    while (found.size() >= 3) {
                        found.removeLast();
                    }
                    found.add(new Destinations.Dest(there, "on " + Remotes.host(there).label()
                            + ", where you were", 0));
                }
            }
            synchronized (this) {
                if (!Objects.equals(mine, asked)) {
                    return;
                }
            }
            model.work(w -> w.suggesting(List.copyOf(found)));
        };
        if (Startup.synchronous()) {
            work.run();
        } else {
            gui.offload().execute(work);
        }
    }

    /**
     * Where a download from a remote folder goes: somewhere on this computer, never a neighbour on the server, which
     * is not written to. The person's folder for the kind, then Downloads, then the Desktop.
     */
    private static List<Destinations.Dest> here(Kind kind) {
        var out = new java.util.ArrayList<Destinations.Dest>();
        Destinations.Dest guess = Destinations.home(null, Startup.home(), kind);
        if (guess != null) {
            out.add(guess);
        }
        Path downloads = Startup.downloads();
        out.add(new Destinations.Dest(downloads, downloads.equals(Startup.home()) ? "your home folder"
                : "your Downloads folder", 0));
        Path desktop = Startup.home().resolve("Desktop");
        if (java.nio.file.Files.isDirectory(desktop)) {
            out.add(new Destinations.Dest(desktop, "your Desktop", 0));
        }
        return List.copyOf(out);
    }

    /** The kind most of what the rail reaches is, or null for nothing. */
    private static Kind dominant(Doc doc) {
        var targets = doc.rail().effect().targets();
        if (targets.isEmpty()) {
            return null;
        }
        java.util.EnumMap<Kind, Integer> n = new java.util.EnumMap<>(Kind.class);
        for (Entry e : targets) {
            n.merge(e.kind(), 1, Integer::sum);
        }
        return n.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey)
                .orElse(null);
    }
}
