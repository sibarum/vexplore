package dev.sibarum.vexplore.ops;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * What an action would do, worked out before it does anything.
 *
 * <p>The design's rule is that actions show what they will affect before they run, so a plan is a value that can
 * be drawn: how many files, how many bytes, which ones would be skipped and why. {@link Operations#execute} takes
 * a plan and does exactly its {@link #steps}; nothing is decided at execution time except whether the disk
 * agreed.
 *
 * @param kind     what is being done
 * @param steps    each thing that will happen, in order
 * @param skipped  names that will <em>not</em> be touched, with the reason: they already exist at the destination,
 *                 or the destination is inside what is being moved
 * @param bytes    the size of what will be touched
 * @param archive  for {@link Kind#ARCHIVE}, the zip that will be written; otherwise null
 */
public record Plan(Kind kind, List<Step> steps, List<Skip> skipped, long bytes, Path archive) {

    /** What is done to the files. */
    public enum Kind {
        MOVE, COPY, DELETE, ARCHIVE
    }

    /** One file or folder going from one place to another. For an archive, {@code to} is its name inside the zip. */
    public record Step(Path from, Path to) {
    }

    /** A target left alone, and why. */
    public record Skip(Path path, String reason) {
    }

    public int count() {
        return steps.size();
    }

    /** Paths that exist at the destination — what a plan is checked against. */
    public static Plan of(Kind kind, List<Path> targets, List<Long> sizes, Path destination, Set<String> existing,
                          Path archive) {
        java.util.ArrayList<Step> steps = new java.util.ArrayList<>();
        java.util.ArrayList<Skip> skipped = new java.util.ArrayList<>();
        long bytes = 0L;
        java.util.HashSet<String> taken = new java.util.HashSet<>(existing);
        for (int i = 0; i < targets.size(); i++) {
            Path from = targets.get(i);
            String name = from.getFileName() == null ? from.toString() : from.getFileName().toString();
            if (kind == Kind.DELETE || kind == Kind.ARCHIVE) {
                steps.add(new Step(from, destination == null ? null : destination.resolve(name)));
                bytes += sizes.get(i);
                continue;
            }
            if (destination == null) {
                skipped.add(new Skip(from, "no destination chosen"));
                continue;
            }
            // Compared absolute: a suggested destination is, and the folder being shown may not be.
            Path to = destination.toAbsolutePath().normalize();
            Path whole = from.toAbsolutePath().normalize();
            if (to.startsWith(whole)) {
                skipped.add(new Skip(from, "the destination is inside it"));
                continue;
            }
            if (whole.getParent() != null && whole.getParent().equals(to)) {
                skipped.add(new Skip(from, "already in that folder"));
                continue;
            }
            if (!taken.add(name)) {
                skipped.add(new Skip(from, "a file with that name is already there"));
                continue;
            }
            steps.add(new Step(from, destination.resolve(name)));
            bytes += sizes.get(i);
        }
        return new Plan(kind, List.copyOf(steps), List.copyOf(skipped), bytes, archive);
    }
}
