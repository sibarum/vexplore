package dev.sibarum.vexplore.suggest;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * What a held modifier key is announcing, answered before the gesture is finished.
 *
 * <p>Shift and Control keep their ordinary meaning; this only <em>listens</em>. Shift means a range, so the answer
 * is where the range probably ends, in the order the list is showing. Control means one at a time, each pick an
 * example of a rule, so the answer is the rule. Accepting one replaces the selection with its paths, which is the
 * Select step made without naming it; ignoring all of them costs nothing, because nothing here changes anything.
 *
 * <p>Pure, like {@link Suggestions}: it reads {@link Entry} values and the order they are displayed in, so it is
 * tested with no window and can be computed ahead of time for a folder.
 */
public final class Intents {

    /** One thing a modifier might mean: what it says, and the selection it would produce. */
    public record Candidate(String label, Set<Path> paths) {
        public int count() {
            return paths.size();
        }
    }

    private static final int MAX = 4;
    private static final int MIN_PREFIX = 3;

    private Intents() {
    }

    // ------------------------------------------------------------------ Shift

    /**
     * Where a range from the selection plausibly ends. {@code ordered} is the list as displayed, so "down" is the
     * way the eye goes, whatever the sort.
     */
    public static List<Candidate> shift(List<Entry> ordered, Set<Path> selected, ZoneId zone) {
        int lo = Integer.MAX_VALUE;
        int hi = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (selected.contains(ordered.get(i).path())) {
                lo = Math.min(lo, i);
                hi = Math.max(hi, i);
            }
        }
        if (hi < 0) {
            return List.of();
        }
        List<Candidate> out = new ArrayList<>();
        Entry lead = ordered.get(hi);
        Entry first = ordered.get(lo);
        String prefix = commonPrefix(selectedEntries(ordered, selected));

        List<Run> runs = new ArrayList<>();
        if (!lead.folder()) {
            runs.add(new Run(lead.kind().singular() + " run", (a, b) -> a.kind() == b.kind() && !b.folder()));
            runs.add(new Run("same-day run", (a, b) -> !b.folder() && sameDay(a, b, zone)));
        }
        if (prefix.length() >= MIN_PREFIX) {
            runs.add(new Run("“" + prefix + "” run", (a, b) -> b.name().startsWith(prefix)));
        }
        for (Run run : runs) {
            int down = hi;
            while (down + 1 < ordered.size() && run.same().test(lead, ordered.get(down + 1))) {
                down++;
            }
            int up = lo;
            while (up - 1 >= 0 && run.same().test(first, ordered.get(up - 1))) {
                up--;
            }
            if (down > hi) {
                add(out, "Down to the end of this " + run.name(), range(ordered, lo, down));
            }
            if (up < lo) {
                add(out, "Up to the start of this " + run.name(), range(ordered, up, hi));
            }
            if (down > hi && up < lo) {
                add(out, "The whole " + run.name(), range(ordered, up, down));
            }
        }
        // Whatever else, everything from here to the bottom is a range somebody wants: the cheapest one to state.
        if (hi < ordered.size() - 1) {
            add(out, "To the end of the list", range(ordered, lo, ordered.size() - 1));
        }
        return out.size() > MAX ? List.copyOf(out.subList(0, MAX)) : List.copyOf(out);
    }

    // ------------------------------------------------------------------ Control

    /**
     * The rule the selected files are examples of: every rule that all of them satisfy and that reaches further
     * than they do.
     */
    public static List<Candidate> control(List<Entry> ordered, Set<Path> selected, java.time.Instant now,
                                          ZoneId zone) {
        List<Entry> chosen = selectedEntries(ordered, selected);
        if (chosen.isEmpty() || chosen.stream().anyMatch(Entry::folder)) {
            return List.of();
        }
        List<Entry> files = ordered.stream().filter(e -> !e.folder()).toList();
        Entry a = chosen.get(0);
        List<Candidate> out = new ArrayList<>();

        if (allSame(chosen, (x, y) -> x.kind() == y.kind()) && a.kind() != Kind.OTHER) {
            add(out, "All " + a.kind().plural() + " here", match(files, e -> e.kind() == a.kind()));
        }
        if (!a.extension().isEmpty() && allSame(chosen, (x, y) -> x.extension().equals(y.extension()))) {
            add(out, "Every ." + a.extension() + " file", match(files, e -> e.extension().equals(a.extension())));
        }
        if (allSame(chosen, (x, y) -> sameDay(x, y, zone))) {
            LocalDate day = LocalDate.ofInstant(a.modified(), zone);
            add(out, "Everything from " + Dates.dayWords(day, LocalDate.ofInstant(now, zone)),
                    match(files, e -> LocalDate.ofInstant(e.modified(), zone).equals(day)));
        }
        String prefix = commonPrefix(chosen);
        if (prefix.length() >= MIN_PREFIX) {
            add(out, "Names starting “" + prefix + "”", match(files, e -> e.name().startsWith(prefix)));
        }
        // A rule that reaches no further than what is already selected says nothing.
        out.removeIf(c -> c.paths().equals(selected) || c.paths().size() <= selected.size());
        return out.size() > MAX ? List.copyOf(out.subList(0, MAX)) : List.copyOf(out);
    }

    // ------------------------------------------------------------------ helpers

    private record Run(String name, BiPredicate<Entry, Entry> same) {
    }

    private static List<Entry> selectedEntries(List<Entry> ordered, Set<Path> selected) {
        return ordered.stream().filter(e -> selected.contains(e.path())).toList();
    }

    private static Set<Path> range(List<Entry> ordered, int from, int to) {
        Set<Path> out = new LinkedHashSet<>();
        for (int i = from; i <= to; i++) {
            out.add(ordered.get(i).path());
        }
        return out;
    }

    private static Set<Path> match(List<Entry> files, java.util.function.Predicate<Entry> p) {
        Set<Path> out = new LinkedHashSet<>();
        for (Entry e : files) {
            if (p.test(e)) {
                out.add(e.path());
            }
        }
        return out;
    }

    /** Add unless the same selection is already offered under another name: two chips for one result is noise. */
    private static void add(List<Candidate> out, String label, Set<Path> paths) {
        for (Candidate c : out) {
            if (c.paths().equals(paths)) {
                return;
            }
        }
        out.add(new Candidate(label, paths));
    }

    private static boolean allSame(List<Entry> es, BiPredicate<Entry, Entry> same) {
        Entry a = es.get(0);
        return es.stream().allMatch(e -> same.test(a, e));
    }

    private static boolean sameDay(Entry a, Entry b, ZoneId zone) {
        return LocalDate.ofInstant(a.modified(), zone).equals(LocalDate.ofInstant(b.modified(), zone));
    }

    /**
     * The longest shared start of the names, cut back to the last separator so it is a word and not half of one:
     * {@code lecture-06} and {@code lecture-07} share {@code lecture-}, not {@code lecture-0}.
     */
    static String commonPrefix(List<Entry> es) {
        if (es.isEmpty()) {
            return "";
        }
        String p = es.get(0).name();
        for (Entry e : es) {
            String n = e.name();
            int i = 0;
            while (i < p.length() && i < n.length() && p.charAt(i) == n.charAt(i)) {
                i++;
            }
            p = p.substring(0, i);
        }
        if (es.size() == 1) {
            // One example has no shared prefix with anything; the first word of it is the best guess at one.
            String n = es.get(0).name();
            int cut = firstSeparator(n);
            return cut < 0 ? "" : n.substring(0, cut + 1);
        }
        int cut = lastSeparator(p);
        return cut < 0 ? "" : p.substring(0, cut + 1);
    }

    private static int firstSeparator(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (isSeparator(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int lastSeparator(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (isSeparator(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSeparator(char c) {
        return c == '-' || c == '_' || c == ' ' || c == '.';
    }
}
