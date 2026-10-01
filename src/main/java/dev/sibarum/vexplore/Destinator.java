package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Destinations;
import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;
import dev.sibarum.vexplore.suggest.Suggestions.Act;
import dev.vexelray.gui.core.Gui;

import java.nio.file.Path;
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

    Destinator(Gui gui, Model model) {
        this.gui = gui;
        this.model = model;
        model.onChange(this::follow);
    }

    private void follow(Doc doc) {
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
            var found = Destinations.suggest(mine.folder(), mine.kind(), Set.of());
            synchronized (this) {
                if (!Objects.equals(mine, asked)) {
                    return;
                }
            }
            model.work(w -> w.suggesting(found));
        };
        if (Startup.synchronous()) {
            work.run();
        } else {
            gui.offload().execute(work);
        }
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
