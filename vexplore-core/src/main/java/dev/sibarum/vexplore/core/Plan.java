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
     * What is done to the files, and what takes it back. {@code DELETE} moves into Vexplore's trash, so it is a move
     * whose destination the run chooses. {@code REMOVE} and {@code UNARCHIVE} delete outright, and are only ever the
     * undo of a copy or an archive, taking away what Vexplore made. Each knows its undo ({@link #undone}) and whether
     * that goes back along each step ({@link #swaps}), which is all {@link Done#reverse} needs.
     */
    public enum Op {
        MOVE("Moving", "Moved", true) {
            @Override
            public Op undone() {
                return MOVE;
            }
        },
        COPY("Copying", "Copied", false) {
            @Override
            public Op undone() {
                return REMOVE;
            }
        },
        DELETE("Deleting", "Deleted", true) {
            @Override
            public Op undone() {
                return MOVE;
            }
        },
        REMOVE("Removing", "Removed", false) {
            @Override
            public Op undone() {
                return COPY;
            }
        },
        /** Every step's {@code to} is the one zip they all go into. */
        ARCHIVE("Archiving", "Archived", false) {
            @Override
            public Op undone() {
                return UNARCHIVE;
            }
        },
        UNARCHIVE("Removing", "Removed", false) {
            @Override
            public Op undone() {
                return ARCHIVE;
            }
        },
        RENAME("Renaming", "Renamed", true) {
            @Override
            public Op undone() {
                return RENAME;
            }
        };

        private final String going;
        private final String gone;
        private final boolean swaps;

        Op(String going, String gone, boolean swaps) {
            this.going = going;
            this.gone = gone;
            this.swaps = swaps;
        }

        /** What takes this back. */
        public abstract Op undone();

        /** Whether its undo goes back along each step, {@code to} to {@code from}, rather than over the same steps. */
        public boolean swaps() {
            return swaps;
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

    /** The name an archive is given beside its files: {@code Archive.zip}, or the first {@code Archive-n.zip} free. */
    static String archiveName(Set<String> taken) {
        String name = "Archive.zip";
        for (int i = 2; taken.contains(name); i++) {
            name = "Archive-" + i + ".zip";
        }
        return name;
    }

    /** {@code rows} into one new zip beside them, under a name not {@code taken} there. */
    public static Plan archive(List<Row> rows, Set<String> taken) {
        Path zip = rows.getFirst().path().resolveSibling(archiveName(taken));
        List<Step> steps = new ArrayList<>(rows.size());
        long bytes = 0L;
        for (Row r : rows) {
            steps.add(new Step(r.path(), zip));
            bytes += r.size();
        }
        return new Plan(Op.ARCHIVE, steps, List.of(), bytes);
    }

    /**
     * Giving {@code row} the name {@code to}, in the folder it is in, where {@code taken} are the names already: 0.1's
     * checks. A name that is empty, unchanged, taken, or not one name is a plan of nothing that says why.
     */
    public static Plan rename(Row row, String to, Set<String> taken) {
        String name = to == null ? "" : to.strip();
        String why = name.isEmpty() ? "type a new name and press Enter"
                : name.equals(row.name()) ? "that is its name already"
                : !named(name) ? "a name cannot hold / or \\, or be . or .."
                : taken.contains(name) ? "a file of that name is already there"
                : null;
        if (why != null) {
            return new Plan(Op.RENAME, List.of(), List.of(new Skip(row.path(), why)), 0L);
        }
        return new Plan(Op.RENAME, List.of(new Step(row.path(), row.path().resolveSibling(name))), List.of(),
                row.size());
    }

    /** Whether {@code name} is one file name, on any of the systems a folder here can be on. */
    static boolean named(String name) {
        return !name.isBlank() && !name.equals(".") && !name.equals("..")
                && name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
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
