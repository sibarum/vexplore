package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Preview;
import dev.sibarum.vexplore.suggest.Suggestions.Pick;
import sibarum.atchung.Committer;
import sibarum.atchung.State;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The one authoritative state, and the only way to change it. Every edit is a function of the current value,
 * committed through atchung's {@code State}, so concurrent edits are put in an order and none is lost.
 *
 * <p>The named edits below are the whole vocabulary: a control says what happened ({@link #navigate},
 * {@link #select}) and never reaches into the document. {@link #listed} is the one that arrives from elsewhere —
 * a listing finishing on the offload lane — and it carries the folder it was taken of, so a listing that lost a
 * race with a later navigation is dropped instead of drawn.
 */
final class Model {

    private final State<Doc> state;

    private final Committer<Doc, UnaryOperator<Doc>> edit;

    Model() {
        State.Builder<Doc> builder = State.of(Doc.initial());
        // Declared before build, held as a handle: State refuses a committer it was not built with, which is
        // what stops an unrelated component minting its own way to write this.
        this.edit = builder.mutation("doc.edit", (current, change) -> change.apply(current));
        this.state = builder.build();
    }

    Doc doc() {
        return state.value();
    }

    void change(UnaryOperator<Doc> change) {
        state.commit(edit, change);
    }

    /** Go to a folder. Returns false, and changes nothing, if that is where the model already is. */
    boolean navigate(Path to) {
        boolean[] moved = {false};
        change(d -> {
            moved[0] = !Objects.equals(d.folder(), to);
            return moved[0] ? d.navigating(to) : d;
        });
        return moved[0];
    }

    /** A listing arrived. Dropped unless {@code folder} is still the one being shown. */
    void listed(Path folder, List<Entry> entries) {
        Instant at = Instant.now();
        change(d -> Objects.equals(d.folder(), folder) ? d.listed(entries, at) : d);
    }

    void select(Set<Path> paths) {
        // Checked before the commit, not inside it: State counts a commit that returned the same value as a new
        // version, so a re-announced selection would wake every listener to redraw what has not changed.
        if (doc().selected().equals(paths)) {
            return;
        }
        change(d -> d.selecting(paths));
    }

    /** A preview arrived. Dropped unless {@code path} is still the one thing selected. */
    void previewed(Path path, Preview preview) {
        change(d -> Objects.equals(d.single(), path) ? d.previewing(preview) : d);
    }

    void pick(UnaryOperator<Pick> change) {
        change(d -> d.picking(change.apply(d.pick())));
    }

    /** The modifier keys held changed. A no-op, without a version, if they are as they were. */
    void holding(boolean shift, boolean control) {
        Doc.Input now = doc().input();
        if (now.shift() == shift && now.control() == control) {
            return;
        }
        change(d -> d.inputting(d.input().holding(shift, control)));
    }

    /** The list's order changed. */
    void sorted(dev.sibarum.vexplore.suggest.Order order) {
        if (doc().input().order().equals(order)) {
            return;
        }
        change(d -> d.inputting(d.input().sorted(order)));
    }

    /** Change what is known about doing things. */
    void work(UnaryOperator<Doc.Work> change) {
        change(d -> d.working(change.apply(d.work())));
    }

    /**
     * Tell {@code listener} about each new document, <b>in order, one at a time, and never an older one after a
     * newer one</b>.
     *
     * <p>{@code State.onCommit} fires on the committing thread after the compare-and-set, and handlers commit from a
     * pool, so two commits can deliver as version 6 then 5, or at the same instant. A listener that draws the whole
     * document, as {@code Ui.show} does, would then finish on the stale one and stay wrong until the next edit. The
     * document is the truth and only the newest matters, so an older one that arrives late is dropped.
     */
    void onChange(Consumer<Doc> listener) {
        Object gate = new Object();
        long[] last = {0L};
        state.onCommit(v -> {
            synchronized (gate) {
                if (v.version() <= last[0]) {
                    return;
                }
                last[0] = v.version();
                listener.accept(v.value());
            }
        });
    }

    long version() {
        return state.version();
    }
}
