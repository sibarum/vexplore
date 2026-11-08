package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Twins;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Two listings compared, row by row, by name: each row's {@link Relation} to the other side. Pure, so the explorer runs
 * it in a step, and one pass over each side with a map by name, so fifty thousand rows a side cost a few milliseconds.
 * Names match exactly, as {@link Plan} matches them for a collision.
 */
public final class Compare {

    /**
     * Within this, two modified times are the same time: FAT keeps a time to two seconds, so a file copied to a stick
     * and back would otherwise be only probably identical to itself.
     */
    static final Duration SAME_TIME = Duration.ofSeconds(2);

    private Compare() {
    }

    /** Each of {@code here}'s rows, by path, against {@code there}'s, from the listings alone. */
    public static Map<Path, Relation> of(List<Row> here, List<Row> there) {
        return of(here, there, Map.of());
    }

    /**
     * Each of {@code here}'s rows, by path, against {@code there}'s, and against what reading them found: a pair in
     * {@code checked} matched byte for byte ({@code true}) or did not, whichever side it was read from. A pair read
     * the same is identical whatever its times say; one read different is different whatever its sizes and times say,
     * and still says which is newer.
     */
    public static Map<Path, Relation> of(List<Row> here, List<Row> there, Map<Twins, Boolean> checked) {
        Map<String, Row> byName = new HashMap<>(there.size() * 2);
        for (Row r : there) {
            byName.put(r.name(), r);
        }
        Map<Path, Relation> out = new HashMap<>(here.size() * 2);
        for (Row r : here) {
            Row other = byName.get(r.name());
            Boolean read = other == null || checked.isEmpty() ? null
                    : checked.getOrDefault(new Twins(r, other), checked.get(new Twins(other, r)));
            out.put(r.path(), read == null ? relation(r, other) : read ? Relation.IDENTICAL : differing(r, other));
        }
        return out;
    }

    /** Two files read and found to differ: which is newer, or only different when their times agree. */
    private static Relation differing(Row row, Row other) {
        boolean sameTime = Duration.between(row.modified(), other.modified()).abs().compareTo(SAME_TIME) < 0;
        return sameTime ? Relation.DIFFERENT
                : row.modified().isAfter(other.modified()) ? Relation.NEWER : Relation.OLDER;
    }

    /** {@code row} against the row of its name on the other side, or none. */
    static Relation relation(Row row, Row other) {
        if (other == null) {
            return Relation.ONLY_HERE;
        }
        if (row.folder() || other.folder()) {
            return row.folder() && other.folder() ? Relation.IN_BOTH : Relation.DIFFERENT;
        }
        boolean sameTime = Duration.between(row.modified(), other.modified()).abs().compareTo(SAME_TIME) < 0;
        if (row.size() == other.size()) {
            return sameTime ? Relation.IDENTICAL : Relation.PROBABLY_IDENTICAL;
        }
        if (sameTime) {
            return Relation.DIFFERENT;
        }
        return row.modified().isAfter(other.modified()) ? Relation.NEWER : Relation.OLDER;
    }
}
