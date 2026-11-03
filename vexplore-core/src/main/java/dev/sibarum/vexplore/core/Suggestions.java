package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Card;
import dev.sibarum.vexplore.core.Messages.Chip;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Suggested;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Suggestion Rail's brain, as a pure function: a folder's rows, what is selected and what has been picked in,
 * out come the chips to offer and the effect the picks would have. 0.1's, over {@link Row} instead of its
 * {@code Entry}, with its switches turned into methods on the enums.
 *
 * <h2>Select, Condition, Action</h2>
 *
 * <p>The three steps of the design's command language. <b>Select</b> widens a selection by a rule the selected file is
 * an example of; <b>Condition</b> narrows the result; <b>Action</b> says what to do to it. It is handed everything, so
 * the explorer runs it in a step whenever the rows, the selection or the pick change, and keeps nothing of it but
 * the answer.
 *
 * <h2>A suggestion is offered only if it says something new</h2>
 *
 * <p>"All videos here" is not offered when every video here is already selected, and "Videos over 500 MB" only when
 * some but not all of them qualify. A chip that changes nothing is noise in a place whose whole promise is that it
 * can be glanced at. Rename is offered only when the effect is one row, since it renames one.
 *
 * <p>Nothing here touches a file, so it is tested with none.
 */
public final class Suggestions {

    /** Which rows the rule reaches. */
    public enum Scope {
        THIS {
            @Override
            List<Row> reach(Basis b) {
                return b.chosen();
            }
        },
        SAME_KIND {
            @Override
            List<Row> reach(Basis b) {
                return b.kind() == null ? null : b.files().stream().filter(e -> e.kind() == b.kind()).toList();
            }
        },
        BIG_OF_KIND {
            @Override
            List<Row> reach(Basis b) {
                return b.kind() == null || b.step() <= 0 ? null
                        : b.files().stream().filter(e -> e.kind() == b.kind() && e.size() > b.step()).toList();
            }
        },
        SAME_DAY {
            @Override
            List<Row> reach(Basis b) {
                return b.files().stream().filter(e -> b.day(e).equals(b.lead())).toList();
            }
        };

        /** The rows this scope reaches from {@code b}; null when it is not on offer for that selection. */
        abstract List<Row> reach(Basis b);
    }

    /** Narrowing applied after the scope. */
    public enum Condition {
        ALL {
            @Override
            boolean admits(Row r, Basis b, long limit) {
                return true;
            }
        },
        MODIFIED_TODAY {
            @Override
            boolean admits(Row r, Basis b, long limit) {
                return b.day(r).equals(b.today());
            }
        },
        LARGER_THAN {
            @Override
            boolean admits(Row r, Basis b, long limit) {
                return r.size() > limit;
            }
        };

        abstract boolean admits(Row r, Basis b, long limit);
    }

    /** What to do with the result: its chip, and the verb the card leads with. */
    public enum Act {
        MOVE("Move to…", "Move") {
            @Override
            public Plan.Op placed() {
                return Plan.Op.MOVE;
            }
        },
        COPY("Copy to…", "Copy") {
            @Override
            public Plan.Op placed() {
                return Plan.Op.COPY;
            }
        },
        ARCHIVE("Archive", "Archive") {
            @Override
            Plan plan(List<Row> targets, Set<String> taken) {
                return Plan.archive(targets, taken);
            }
        },
        /** Planned when its name is typed ({@code Messages.Rename}), so the card asks for the name instead. */
        RENAME("Rename…", "Rename") {
            @Override
            String hint() {
                return "Type its new name and press Enter";
            }
        },
        MARK("Mark for later", "Mark"),
        DELETE("Delete (recoverable)", "Delete") {
            @Override
            Plan plan(List<Row> targets, Set<String> taken) {
                return Plan.delete(targets);
            }
        };

        private final String chip;
        private final String verb;

        Act(String chip, String verb) {
            this.chip = chip;
            this.verb = verb;
        }

        public String chip() {
            return chip;
        }

        public String verb() {
            return verb;
        }

        /**
         * What carrying this out on {@code targets} would do, planned in a step from the folder's rows ({@code taken}
         * are their names), or null for an action planned some other way or not built yet.
         */
        Plan plan(List<Row> targets, Set<String> taken) {
            return null;
        }

        /** What the card adds when this is picked: how to carry it out, when its button cannot. */
        String hint() {
            return "";
        }

        /**
         * What this does to each target once it has a destination, or null for an action that needs none. Its plan is
         * {@link PlanAction}'s, which reads the destination.
         */
        public Plan.Op placed() {
            return null;
        }
    }

    /**
     * The person's picks so far. The default is what happens if nothing is touched: the selection itself, every row of
     * it, no action chosen, which is why accepting nothing costs nothing.
     */
    public record Pick(Scope scope, Condition condition, Act act) {
        public static final Pick NONE = new Pick(Scope.THIS, Condition.ALL, null);

        public Pick with(Scope s) {
            return new Pick(s, condition, act);
        }

        public Pick with(Condition c) {
            return new Pick(scope, c, act);
        }

        /** The action, or none when it is the one already picked: pressing a picked action again puts it down. */
        public Pick with(Act a) {
            return new Pick(scope, condition, a == act ? null : a);
        }
    }

    /** One chip: the value it stands for, what it says, and how many rows it would reach. */
    public record Option<T>(T value, String label, int count) {
    }

    /**
     * The whole rail for one moment: the chips on offer, the picks as they apply to them (a pick no longer on offer
     * has fallen back), and the rows the effect reaches.
     */
    public record Rail(String basis, List<Option<Scope>> scopes, List<Option<Condition>> conditions, List<Act> acts,
                       Pick applied, List<Row> targets) {

        /** Nothing selected, so nothing worth saying. */
        public static final Rail EMPTY = new Rail("", List.of(), List.of(), List.of(), Pick.NONE, List.of());

        public Rail {
            scopes = List.copyOf(scopes);
            conditions = List.copyOf(conditions);
            acts = List.copyOf(acts);
            targets = List.copyOf(targets);
        }

        public long bytes() {
            return targets.stream().mapToLong(Row::size).sum();
        }

        /**
         * What the card's button would carry out, or null when no action is picked or it is not planned in a step;
         * {@code taken} are the names in the folder shown.
         */
        public Plan plan(Set<String> taken) {
            return applied.act() == null || targets.isEmpty() ? null : applied.act().plan(targets, taken);
        }

        /** The rail in words, for the view to show: chips worded and lit, the reach, and the card. */
        public Suggested worded() {
            if (this == EMPTY || scopes.isEmpty()) {
                return Suggested.NONE;
            }
            List<Chip> select = new ArrayList<>(scopes.size());
            for (Option<Scope> o : scopes) {
                select.add(new Chip(o.label(), o.count(), o.value() == applied.scope()));
            }
            List<Chip> condition = new ArrayList<>(conditions.size());
            for (Option<Condition> o : conditions) {
                condition.add(new Chip(o.label(), o.count(), o.value() == applied.condition()));
            }
            List<Chip> action = new ArrayList<>(acts.size());
            for (Act a : acts) {
                action.add(new Chip(a.chip(), 0, a == applied.act()));
            }
            Set<Row> reach = new LinkedHashSet<>(targets);
            return new Suggested(basis, select, condition, action, reach, card(), false, List.of());
        }

        /** {@code Move 9 files · 6.5 GB}, and the first few names. */
        private Card card() {
            int n = targets.size();
            boolean folders = targets.stream().anyMatch(Row::folder);
            String counted = n + (folders ? (n == 1 ? " item" : " items") : (n == 1 ? " file" : " files"));
            long bytes = bytes();
            String done = applied.act() == null ? counted : applied.act().verb() + " " + counted;
            String headline = done + (bytes > 0 ? " · " + Bytes.format(bytes) : "");
            StringBuilder detail = new StringBuilder();
            int named = Math.min(n, NAMED);
            for (int i = 0; i < named; i++) {
                detail.append(i == 0 ? "" : ", ").append(targets.get(i).name());
            }
            if (n > named) {
                detail.append(" and ").append(n - named).append(" more");
            }
            detail.append(n == 1 ? ", marked in the list" : ", all marked in the list");
            if (applied.act() != null && !applied.act().hint().isEmpty()) {
                detail.append(". ").append(applied.act().hint());
            }
            String go = plan(Set.of()) != null ? done : applied.act() == Act.MARK ? "Keep as Mark" : "";
            return new Card(headline, detail.toString(), go, "",
                    applied.act() == Act.RENAME);
        }
    }

    /** How many names the card lists before it counts the rest. */
    private static final int NAMED = 3;

    /** Sizes a "larger than" chip may name, ascending: round numbers, because 487 MB is not a rule anyone keeps. */
    private static final long[] STEPS = {
            1L << 20, 10L << 20, 100L << 20, 500L << 20, 1L << 30, 5L << 30, 10L << 30
    };

    /** What a scope and a condition are worked out from. */
    record Basis(List<Row> chosen, List<Row> files, Kind kind, long step, ZoneId zone, LocalDate lead,
                 LocalDate today) {

        LocalDate day(Row r) {
            return LocalDate.ofInstant(r.modified(), zone);
        }
    }

    private Suggestions() {
    }

    /**
     * Work out the rail.
     *
     * @param rows     the folder as listed
     * @param selected the selected paths (paths not in {@code rows} are ignored)
     * @param pick     what the person has chosen so far
     * @param today    the day "today" means
     * @param zone     the zone a row's day is read in
     */
    public static Rail of(List<Row> rows, Set<Path> selected, Pick pick, LocalDate today, ZoneId zone) {
        List<Row> chosen = rows.stream().filter(e -> selected.contains(e.path())).toList();
        if (chosen.isEmpty()) {
            return Rail.EMPTY;
        }
        List<Row> files = rows.stream().filter(e -> !e.folder()).toList();
        Row lead = chosen.getFirst();
        Kind kind = commonKind(chosen);
        LocalDate day = LocalDate.ofInstant(lead.modified(), zone);
        Basis basis = new Basis(chosen, files, kind, stepBelow(chosen), zone, day, today);

        List<Option<Scope>> scopes = new ArrayList<>();
        scopes.add(new Option<>(Scope.THIS, chosen.size() == 1 ? "Just this file" : "Just these", chosen.size()));
        if (kind != null && kind != Kind.FOLDER) {
            List<Row> ofKind = Scope.SAME_KIND.reach(basis);
            if (ofKind.size() > chosen.size()) {
                scopes.add(new Option<>(Scope.SAME_KIND, "All " + kind.plural() + " here", ofKind.size()));
            }
            if (basis.step() > 0) {
                long over = ofKind.stream().filter(e -> e.size() > basis.step()).count();
                if (over >= 2 && over < ofKind.size()) {
                    scopes.add(new Option<>(Scope.BIG_OF_KIND,
                            capitalise(kind.plural()) + " over " + Bytes.threshold(basis.step()), (int) over));
                }
            }
        }
        if (chosen.stream().noneMatch(Row::folder) && chosen.stream().allMatch(e -> basis.day(e).equals(day))) {
            int sameDay = Scope.SAME_DAY.reach(basis).size();
            if (sameDay > chosen.size()) {
                scopes.add(new Option<>(Scope.SAME_DAY,
                        day.equals(today) ? "From today" : "From " + Dates.short_(day), sameDay));
            }
        }

        // A pick that is not on offer (the selection changed under it) falls back to the selection.
        Scope scope = offered(scopes, pick.scope()) ? pick.scope() : Scope.THIS;
        List<Row> scoped = scope.reach(basis);

        long above = stepBelow(scoped);
        long limit = above > 0 ? above : STEPS[0];
        List<Option<Condition>> conditions = new ArrayList<>();
        conditions.add(new Option<>(Condition.ALL, "Always", scoped.size()));
        long modifiedToday = scoped.stream().filter(e -> Condition.MODIFIED_TODAY.admits(e, basis, limit)).count();
        if (modifiedToday > 0 && modifiedToday < scoped.size()) {
            conditions.add(new Option<>(Condition.MODIFIED_TODAY, "If modified today", (int) modifiedToday));
        }
        long large = scoped.stream().filter(e -> Condition.LARGER_THAN.admits(e, basis, limit)).count();
        if (large > 0 && large < scoped.size()) {
            conditions.add(new Option<>(Condition.LARGER_THAN, "If larger than " + Bytes.threshold(limit),
                    (int) large));
        }
        Condition condition = offered(conditions, pick.condition()) ? pick.condition() : Condition.ALL;
        List<Row> targets = scoped.stream().filter(e -> condition.admits(e, basis, limit)).toList();

        List<Act> acts = new ArrayList<>(List.of(Act.values()));
        if (targets.size() != 1) {
            acts.remove(Act.RENAME);
        }
        Act act = acts.contains(pick.act()) ? pick.act() : null;
        return new Rail(lead.name(), scopes, conditions, acts, new Pick(scope, condition, act), targets);
    }

    private static <T> boolean offered(List<Option<T>> options, T value) {
        return options.stream().anyMatch(o -> o.value() == value);
    }

    private static Kind commonKind(List<Row> chosen) {
        Kind k = chosen.getFirst().kind();
        return chosen.stream().allMatch(e -> e.kind() == k) ? k : null;
    }

    /** The largest round size strictly below the smallest of {@code rows}, or 0 when none is. */
    private static long stepBelow(List<Row> rows) {
        long smallest = rows.stream().mapToLong(Row::size).min().orElse(0L);
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
}
