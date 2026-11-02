package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Row;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What an action would do, worked out before it does anything: 0.1's, with its kind renamed {@link Op}, since a
 * {@link Kind} here is what a file is.
 *
 * <p>The design's rule is that actions show what they will affect before they run, so a plan is a value the card can
 * state: how many, how many bytes, which would be left alone and why. {@link Operations#execute} takes a plan and does
 * exactly its steps; nothing is decided while it runs except whether the disk agreed.
 *
 * <p><b>An undo is a plan too.</b> What was done ({@link Done}) reverses into a plan of its own ({@link Done#reverse}),
 * carried out by the same run with the same count and Stop, so an undo that meets a surprise says so as a run does,
 * and overwrites nothing.
 *
 * @param op      what is done to each step
 * @param steps   each thing that will happen, in order
 * @param skipped what will <em>not</em> be touched, and why
 * @param bytes   the size of what will be touched, as listed
 */
public record Plan(Op op, List<Step> steps, List<Skip> skipped, long bytes) {

    public Plan {
        steps = List.copyOf(steps);
        skipped = List.copyOf(skipped);
    }

    /**
     * What is done to the files. {@code DELETE} moves into Vexplore's trash, so it is a move whose destination the run
     * chooses; {@code REMOVE} deletes outright, and is only ever the undo of a copy, taking away what Vexplore made.
     */
    public enum Op {
        MOVE("Moving", "Moved"),
        COPY("Copying", "Copied"),
        DELETE("Deleting", "Deleted"),
        REMOVE("Removing", "Removed");

        private final String going;
        private final String gone;

        Op(String going, String gone) {
            this.going = going;
            this.gone = gone;
        }

        /** The card's word while it runs: {@code Deleting}. */
        public String going() {
            return going;
        }

        /** The status line's word once it has: {@code Deleted}. */
        public String gone() {
            return gone;
        }
    }

    /**
     * One file or folder and where it goes. For a delete that is still a plan, {@code to} is null: the run picks the
     * place in the trash. For a remove, {@code to} is what is removed and {@code from} the original it was copied from.
     */
    public record Step(Path from, Path to) {
    }

    /** A target left alone, and why. */
    public record Skip(Path path, String reason) {
    }

    public int count() {
        return steps.size();
    }

    /**
     * A move or a copy of {@code rows} into {@code destination}, which holds {@code taken} already: 0.1's checks, each a
     * skip with its reason rather than a step that would fail. Nothing goes over a name already there, into itself,
     * or into the folder it is already in; and two rows of one name cannot both arrive.
     */
    public static Plan to(Op op, List<Row> rows, Path destination, Set<String> taken) {
        List<Step> steps = new ArrayList<>();
        List<Skip> skipped = new ArrayList<>();
        Set<String> names = new HashSet<>(taken);
        Path to = destination.toAbsolutePath().normalize();
        long bytes = 0L;
        for (Row r : rows) {
            Path whole = r.path().toAbsolutePath().normalize();
            String why = to.startsWith(whole) ? "the destination is inside it"
                    : to.equals(whole.getParent()) ? "it is already there"
                    : !names.add(r.name()) ? "a file of that name is already there"
                    : null;
            if (why != null) {
                skipped.add(new Skip(r.path(), why));
                continue;
            }
            steps.add(new Step(r.path(), to.resolve(r.name())));
            bytes += r.size();
        }
        return new Plan(op, steps, skipped, bytes);
    }

    /** Into the trash: every row, the run choosing where in it. */
    public static Plan delete(List<Row> rows) {
        List<Step> steps = new ArrayList<>(rows.size());
        long bytes = 0L;
        for (Row r : rows) {
            steps.add(new Step(r.path(), null));
            bytes += r.size();
        }
        return new Plan(Op.DELETE, steps, List.of(), bytes);
    }
}
