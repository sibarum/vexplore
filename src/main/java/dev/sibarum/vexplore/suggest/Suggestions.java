package dev.sibarum.vexplore.suggest;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Suggestion Rail's brain, as a pure function: a folder, what is selected and what has been picked in,
 * out come the choices to offer and the effect the current choices would have.
 *
 * <h2>Select, Condition, Action</h2>
 * The three steps of the design's command language. <b>Select</b> widens a selection by a rule the selected file is
 * an example of; <b>Condition</b> narrows the result; <b>Action</b> says what to do to it. The function is
 * deliberately not stateful: it is handed everything, so the rail can be computed ahead of time per folder and
 * thrown away, and a suggestion that would arrive late is one that simply is not asked for.
 *
 * <h2>A suggestion is offered only if it says something new</h2>
 * "All videos here" is not offered when every video here is already selected, and "Videos over 500 MB" only when
 * some but not all of them qualify. A chip that changes nothing is noise in a place whose whole promise is that it
 * can be glanced at.
 *
 * <p>Nothing here touches a file. It reads {@link Entry} values, so it is tested with none.
 */
public final class Suggestions {

    /** Which files the rule reaches. */
    public enum Scope { THIS, SAME_KIND, BIG_OF_KIND, SAME_DAY }

    /** Narrowing applied after the scope. */
    public enum Condition { ALL, MODIFIED_TODAY, LARGER_THAN }

    /** What to do with the result. */
    public enum Act { MOVE, COPY, ARCHIVE, RENAME, MARK, DELETE }

    /**
     * The user's picks so far. The default is what happens if nothing is touched: the selection itself, every
     * file of it, no action chosen — which is why accepting nothing costs nothing.
     */
    public record Pick(Scope scope, Condition condition, Act act) {
        public static final Pick NONE = new Pick(Scope.THIS, Condition.ALL, null);

        public Pick with(Scope s) {
            return new Pick(s, condition, act);
        }

        public Pick with(Condition c) {
            return new Pick(scope, c, act);
        }

        public Pick with(Act a) {
            return new Pick(scope, condition, a);
        }
    }

    /** One chip: what it says, how many files it would reach, and the value it stands for. */
    public record Option<T>(T value, String label, int count) {
    }

    /** What the current picks would do, stated before anything does. */
    public record Effect(List<Entry> targets, long bytes) {
        public int count() {
            return targets.size();
        }
    }

    /** The whole rail for one moment. */
    public record Rail(String basis, List<Option<Scope>> scopes, List<Option<Condition>> conditions,
                       List<Act> acts, Effect effect) {
        /** An empty rail: nothing selected, so nothing worth saying. */
        public static final Rail EMPTY = new Rail("", List.of(), List.of(), List.of(), new Effect(List.of(), 0L));
    }

    /** Sizes a "larger than" chip may name, ascending: round numbers, because 487 MB is not a rule anyone keeps. */
    private static final long[] STEPS = {
            1L << 20, 10L << 20, 100L << 20, 500L << 20, 1L << 30, 5L << 30, 10L << 30
    };

    private Suggestions() {
    }

    /**
     * Work out the rail.
     *
     * @param entries  the folder as listed
     * @param selected the selected paths (paths not in {@code entries} are ignored)
     * @param pick     what the user has chosen so far
     * @param now      the instant "today" is measured from
     * @param zone     the zone "today" is measured in
     */
    public static Rail of(List<Entry> entries, Set<Path> selected, Pick pick, Instant now, ZoneId zone) {
        List<Entry> chosen = entries.stream().filter(e -> selected.contains(e.path())).toList();
        if (chosen.isEmpty()) {
            return Rail.EMPTY;
        }
        List<Entry> files = entries.stream().filter(e -> !e.folder()).toList();
        Entry lead = chosen.get(0);
        LocalDate today = LocalDate.ofInstant(now, zone);

        List<Option<Scope>> scopes = new ArrayList<>();
        scopes.add(new Option<>(Scope.THIS, chosen.size() == 1 ? "Just this file" : "Just these", chosen.size()));

        Kind kind = commonKind(chosen);
        if (kind != null && kind != Kind.FOLDER) {
            List<Entry> ofKind = files.stream().filter(e -> e.kind() == kind).toList();
            if (ofKind.size() > chosen.size()) {
                scopes.add(new Option<>(Scope.SAME_KIND, "All " + kind.plural() + " here", ofKind.size()));
            }
            long step = stepBelow(chosen);
            if (step > 0) {
                long over = ofKind.stream().filter(e -> e.size() > step).count();
                if (over >= 2 && over < ofKind.size()) {
                    scopes.add(new Option<>(Scope.BIG_OF_KIND,
                            capitalise(kind.plural()) + " over " + Bytes.threshold(step), (int) over));
                }
            }
        }
        LocalDate day = LocalDate.ofInstant(lead.modified(), zone);
        if (allOnDay(chosen, day, zone)) {
            long sameDay = entries.stream().filter(e -> !e.folder()
                    && LocalDate.ofInstant(e.modified(), zone).equals(day)).count();
            if (sameDay > chosen.size()) {
                scopes.add(new Option<>(Scope.SAME_DAY,
                        day.equals(today) ? "From today" : "From " + Dates.short_(day), (int) sameDay));
            }
        }

        List<Entry> scoped = scope(pick.scope(), chosen, files, kind, stepBelow(chosen), zone, day);
        if (scoped == null) {
            // A pick that no longer applies (the selection changed under it) falls back to the selection.
            scoped = chosen;
        }

        long threshold = STEPS[0];
        long above = stepBelow(scoped);
        if (above > 0) {
            threshold = above;
        }
        final long limit = threshold;
        List<Option<Condition>> conditions = new ArrayList<>();
        conditions.add(new Option<>(Condition.ALL, "All", scoped.size()));
        long today_ = scoped.stream().filter(e -> LocalDate.ofInstant(e.modified(), zone).equals(today)).count();
        if (today_ > 0 && today_ < scoped.size()) {
            conditions.add(new Option<>(Condition.MODIFIED_TODAY, "If modified today", (int) today_));
        }
        long large = scoped.stream().filter(e -> e.size() > limit).count();
        if (large > 0 && large < scoped.size()) {
            conditions.add(new Option<>(Condition.LARGER_THAN, "If larger than " + Bytes.threshold(limit), (int) large));
        }

        Condition condition = conditions.stream().anyMatch(o -> o.value() == pick.condition())
                ? pick.condition() : Condition.ALL;
        List<Entry> targets = scoped.stream().filter(e -> switch (condition) {
            case ALL -> true;
            case MODIFIED_TODAY -> LocalDate.ofInstant(e.modified(), zone).equals(today);
            case LARGER_THAN -> e.size() > limit;
        }).toList();

        long bytes = targets.stream().mapToLong(Entry::size).sum();
        return new Rail(lead.name(), List.copyOf(scopes), List.copyOf(conditions), List.of(Act.values()),
                new Effect(targets, bytes));
    }

    /** The scope a pick names, resolved to entries; {@code null} if it is not on offer for this selection. */
    private static List<Entry> scope(Scope scope, List<Entry> chosen, List<Entry> files, Kind kind, long step,
                                     ZoneId zone, LocalDate day) {
        return switch (scope) {
            case THIS -> chosen;
            case SAME_KIND -> kind == null ? null : files.stream().filter(e -> e.kind() == kind).toList();
            case BIG_OF_KIND -> kind == null || step <= 0 ? null
                    : files.stream().filter(e -> e.kind() == kind && e.size() > step).toList();
            case SAME_DAY -> files.stream().filter(e -> LocalDate.ofInstant(e.modified(), zone).equals(day))
                    .toList();
        };
    }

    private static Kind commonKind(List<Entry> chosen) {
        Kind k = chosen.get(0).kind();
        return chosen.stream().allMatch(e -> e.kind() == k) ? k : null;
    }

    private static boolean allOnDay(List<Entry> chosen, LocalDate day, ZoneId zone) {
        return chosen.stream().noneMatch(e -> e.folder())
                && chosen.stream().allMatch(e -> LocalDate.ofInstant(e.modified(), zone).equals(day));
    }

    /** The largest round size strictly below the smallest of {@code entries}, or 0 when none is. */
    private static long stepBelow(List<Entry> entries) {
        long smallest = entries.stream().mapToLong(Entry::size).min().orElse(0L);
        long best = 0;
        for (long s : STEPS) {
            if (s < smallest) {
                best = s;
            }
        }
        return best;
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Paths of an effect, for marking rows. */
    public static Set<Path> paths(Effect effect) {
        return effect.targets().stream().map(Entry::path).collect(Collectors.toUnmodifiableSet());
    }
}
