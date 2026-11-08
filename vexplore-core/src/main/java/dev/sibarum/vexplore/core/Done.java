package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Plan.Op;
import dev.sibarum.vexplore.core.Plan.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * What a run did: the steps that took effect, each with where it really went (a delete's place in the trash
 * included), the ones that did not and why, and whether it was stopped before the end. What the journal keeps, and
 * all an undo needs: {@link #reverse} is the plan that takes it back.
 *
 * @param op      what was done
 * @param done    the steps that took effect, in order
 * @param failed  each step that did not, as {@code name: why}
 * @param bytes   the plan's size, as listed
 * @param stopped whether a Stop ended it before its last step
 */
public record Done(Op op, List<Step> done, List<String> failed, long bytes, boolean stopped) {

    public Done {
        done = List.copyOf(done);
        failed = List.copyOf(failed);
    }

    public int count() {
        return done.size();
    }

    /**
     * The plan that takes this back, last step first, as {@link Op#undone} says: a move, a delete or a rename goes back
     * to where it was; a copy or an archive has what it made removed, and that removal is made again. Undoing an undo
     * is the same rule once more, which is the redo.
     */
    public Plan reverse() {
        List<Step> back = new ArrayList<>(done.size());
        for (int i = done.size() - 1; i >= 0; i--) {
            Step s = done.get(i);
            back.add(op.swaps() ? new Step(s.to(), s.from()) : s);
        }
        return new Plan(op.undone(), back, List.of(), bytes);
    }
}
