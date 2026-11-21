package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Fate;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Plan.Op;
import dev.sibarum.vexplore.core.Plan.Step;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the queue will make of the disk (docs/shape.md, M9): a pure function, the plans waiting in, a folder as it will
 * be out. The explorer runs it in a step, for each list it shows and for each plan it makes, so that a list shows its
 * folder after the queue, and a plan is checked against that folder rather than the one on the disk now.
 *
 * <p>Each item is applied in order, each step as its {@link Op} says: a step's {@code from} is gone after it when the op
 * {@link Op#leaves leaves} (a move, a delete, a rename), and its {@code to}, when it has one, is there after it. A
 * delete's {@code to} is none, as a plan has it: the run picks the place in the trash. An archive's every step has the
 * one zip as its {@code to}.
 */
public final class Projection {

    private Projection() {
    }

    /**
     * One item, as the projection needs it: its number, which a fate carries (0 for the run in flight), its plan, and
     * the rows it was made from, which say what a file only going to be there will look like.
     */
    public record Item(long number, Plan plan, List<Row> rows) {
        public Item {
            rows = List.copyOf(rows);
        }
    }

    /** A folder after the queue: its rows, the ghosts after the rows there, and each fated row's fate by path. */
    public record Folder(List<Row> rows, Map<Path, Fate> fates) {
        public Folder {
            rows = List.copyOf(rows);
            fates = Map.copyOf(fates);
        }
    }

    /**
     * {@code folder}, whose rows on the disk are {@code rows} in the order shown, as it will be after {@code items}. A
     * row that leaves stays, fated, so the list shows where it goes rather than a gap; a renamed row stands in its
     * place under its new path; a row that arrives is a ghost, after the rows there, in the order it was queued. A
     * ghost is drawn from the row it is made from, so one made from a row not in {@code items}' rows (an undo's) is not
     * drawn, and the folder's names still count it ({@link #names}).
     */
    public static Folder of(List<Item> items, Path folder, List<Row> rows) {
        Path here = normal(folder);
        LinkedHashMap<Path, Row> present = new LinkedHashMap<>();
        for (Row r : rows) {
            present.put(normal(r.path()), r);
        }
        Map<Path, Fate> fates = new HashMap<>();
        for (Item item : items) {
            Plan plan = item.plan();
            Op op = plan.op();
            Map<Path, Row> made = new HashMap<>();
            for (Row r : item.rows()) {
                made.put(normal(r.path()), r);
            }
            boolean zipped = false;
            for (Step s : plan.steps()) {
                Path from = normal(s.from());
                Optional<Path> to = Optional.ofNullable(s.to()).map(Projection::normal);
                boolean fromHere = here.equals(from.getParent()) && present.containsKey(from);
                boolean toHere = to.isPresent() && here.equals(to.get().getParent());
                if (fromHere && op.leaves() && toHere) {
                    Row was = present.get(from);
                    Row now = new Row(to.get(), ListFolder.name(to.get()), was.folder(), was.size(), was.modified(),
                            was.kind());
                    replace(present, from, now);
                    fates.remove(from);
                    fates.put(to.get(), new Fate(Fate.Kind.RENAMED,
                            "was " + was.name(), item.number()));
                    continue;
                }
                if (fromHere) {
                    Fate f = op.leaves()
                            ? to.isEmpty() ? new Fate(Fate.Kind.DELETING, "into the trash", item.number())
                            : new Fate(Fate.Kind.LEAVING, "→ " + ListFolder.name(to.get().getParent()), item.number())
                            : op == Op.ARCHIVE
                            ? new Fate(Fate.Kind.ARCHIVING, "→ " + ListFolder.name(to.orElse(from)), item.number())
                            : new Fate(Fate.Kind.COPYING, "copy → " + ListFolder.name(to.map(Path::getParent)
                            .orElse(from)), item.number());
                    if (op.leaves() || !fates.containsKey(from)) {
                        fates.put(from, f);
                    }
                }
                if (toHere && !present.containsKey(to.get())) {
                    Row source = made.get(from);
                    if (op == Op.ARCHIVE) {
                        if (!zipped) {
                            zipped = true;
                            long bytes = plan.bytes();
                            Instant when = source == null ? Instant.EPOCH : source.modified();
                            String name = ListFolder.name(to.get());
                            present.put(to.get(), new Row(to.get(), name, false, bytes, when,
                                    Kind.ofExtension(Kind.extension(name))));
                            fates.put(to.get(), new Fate(Fate.Kind.ARRIVING, "archive of " + plan.count(),
                                    item.number()));
                        }
                        continue;
                    }
                    if (source == null) {
                        continue;
                    }
                    present.put(to.get(), new Row(to.get(), ListFolder.name(to.get()), source.folder(), source.size(),
                            source.modified(), source.kind()));
                    String whence = from.getParent() == null ? from.toString() : ListFolder.name(from.getParent());
                    fates.put(to.get(), new Fate(Fate.Kind.ARRIVING,
                            (op == Op.COPY ? "copy ← " : "← ") + whence, item.number()));
                }
            }
        }
        return new Folder(new ArrayList<>(present.values()), fates);
    }

    /** {@code value} in {@code key}'s place, under its own key, the order otherwise kept. */
    private static void replace(LinkedHashMap<Path, Row> map, Path key, Row value) {
        List<Map.Entry<Path, Row>> entries = new ArrayList<>(map.entrySet());
        map.clear();
        for (Map.Entry<Path, Row> e : entries) {
            if (e.getKey().equals(key)) {
                map.put(normal(value.path()), value);
            } else {
                map.put(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * The names {@code folder} will hold after {@code items}, from {@code names}, the ones it holds now: what a plan into
     * it is checked against, so nothing is queued to land on a name something waiting will take.
     */
    public static Set<String> names(List<Item> items, Path folder, Collection<String> names) {
        Delta d = delta(items, folder);
        Set<String> out = new HashSet<>(names);
        out.removeAll(d.leaving());
        out.addAll(d.arriving());
        return out;
    }

    /**
     * What {@code items} do to the names in {@code folder}: those that arrive, and those that leave and do not come
     * back. A job that reads the folder's names applies it ({@link PlanAction}).
     */
    public static Delta delta(List<Item> items, Path folder) {
        Path here = normal(folder);
        Set<String> arriving = new HashSet<>();
        Set<String> leaving = new HashSet<>();
        for (Item item : items) {
            Op op = item.plan().op();
            for (Step s : item.plan().steps()) {
                Path from = normal(s.from());
                if (op.leaves() && here.equals(from.getParent())) {
                    String name = ListFolder.name(from);
                    arriving.remove(name);
                    leaving.add(name);
                }
                if (s.to() != null && here.equals(normal(s.to()).getParent())) {
                    String name = ListFolder.name(s.to());
                    leaving.remove(name);
                    arriving.add(name);
                }
            }
        }
        return new Delta(arriving, leaving);
    }

    /** The names a folder gains and loses. */
    public record Delta(Set<String> arriving, Set<String> leaving) {
        public Delta {
            arriving = Set.copyOf(arriving);
            leaving = Set.copyOf(leaving);
        }

        public static final Delta NONE = new Delta(Set.of(), Set.of());
    }

    /**
     * The files {@code items} will take away, each with why, in the words of a plan's {@link Plan.Skip}: a plan made
     * after them leaves these alone, since the file will not be there to act on.
     */
    public static Map<Path, String> claimed(List<Item> items) {
        Map<Path, String> out = new HashMap<>();
        for (Item item : items) {
            Op op = item.plan().op();
            if (!op.leaves()) {
                continue;
            }
            for (Step s : item.plan().steps()) {
                String why = s.to() == null ? "it is queued to go into the trash"
                        : op == Op.RENAME ? "it is queued to be renamed " + ListFolder.name(s.to())
                        : "it is queued to move to " + ListFolder.name(s.to().getParent());
                out.put(normal(s.from()), why);
            }
        }
        return out;
    }

    /** {@code plan} with each step on a file {@code claimed} left alone instead, with its reason. */
    public static Plan claim(Plan plan, Map<Path, String> claimed) {
        if (claimed.isEmpty()) {
            return plan;
        }
        List<Step> steps = new ArrayList<>();
        List<Plan.Skip> skipped = new ArrayList<>(plan.skipped());
        long bytes = plan.bytes();
        for (Step s : plan.steps()) {
            String why = claimed.get(normal(s.from()));
            if (why == null) {
                steps.add(s);
            } else {
                skipped.add(new Plan.Skip(s.from(), why));
            }
        }
        if (steps.size() == plan.steps().size()) {
            return plan;
        }
        return new Plan(plan.op(), steps, skipped, bytes);
    }

    private static Path normal(Path p) {
        return p.toAbsolutePath().normalize();
    }
}
