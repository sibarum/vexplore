package dev.sibarum.vexplore;

import dev.sibarum.suite.git.Status;
import dev.sibarum.vexplore.files.Destinations.Dest;
import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Preview;
import dev.sibarum.vexplore.ops.Plan;
import dev.sibarum.vexplore.remote.Remotes;
import dev.sibarum.vexplore.suggest.Intents;
import dev.sibarum.vexplore.suggest.Order;
import dev.sibarum.vexplore.suggest.Suggestions;
import dev.sibarum.vexplore.suggest.Suggestions.Act;
import dev.sibarum.vexplore.suggest.Suggestions.Pick;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What Vexplore knows, as one immutable value.
 *
 * @param folder   the folder being shown; null until the first {@link Model#navigate}
 * @param entries  what was listed in it, or empty while {@code loading}
 * @param loading  whether a listing of {@code folder} is still on its way
 * @param selected what the user has selected in the list
 * @param pick     what they have chosen on the Suggestion Rail so far
 * @param preview  what the Preview Dock shows, or null when nothing single is selected or it has not arrived yet
 * @param now      the instant the listing was taken at: what "today" means for everything derived from it
 * @param input    the modifier keys held and the order the list is showing — what the user is in the middle of
 * @param work     destinations, what has been done and can be undone, and the marks kept for later
 * @param git      what git says about {@code folder}: null outside a repository, without git, or until it answers
 */
record Doc(Path folder, List<Entry> entries, boolean loading, Set<Path> selected, Pick pick, Preview preview,
           Instant now, Input input, Work work, Status git) {

    /** The keys held, and the order shown. */
    record Input(boolean shift, boolean control, Order order, boolean connecting) {
        static final Input NONE = new Input(false, false, Order.NEWEST, false);

        Input holding(boolean shift, boolean control) {
            return new Input(shift, control, order, connecting);
        }

        Input sorted(Order o) {
            return new Input(shift, control, o, connecting);
        }

        /** Whether the rail is showing the Connect panel, the person part-way through naming a new host. */
        Input connecting(boolean on) {
            return new Input(shift, control, order, on);
        }
    }

    /** A group of files kept under a name, to be acted on later or compared. */
    record Mark(String name, Set<Path> paths) {
    }

    /**
     * Everything about doing things.
     *
     * @param destination the chosen folder for a move, copy or archive, or null
     * @param destNames   the names in it, so a plan can say what would collide before anything runs
     * @param suggested   places nearby that already hold what is being acted on
     * @param notice      one line saying how the last thing went, or empty
     * @param busy        one line saying what is running now, or empty
     * @param marks       the marks kept so far
     * @param name        the new name typed for a rename, or null until one is
     *
     * <p>What has been done is not kept here: it is the window's undo history ({@link Actor}), the one Ctrl+Z,
     * Ctrl+Y and the Undo button all reach, along with the typing in this window's fields.
     */
    record Work(Path destination, Set<String> destNames, List<Dest> suggested, String notice,
                String busy, List<Mark> marks, String name) {
        static final Work NONE = new Work(null, Set.of(), List.of(), "", "", List.of(), null);

        Work naming(String to) {
            return new Work(destination, destNames, suggested, notice, busy, marks, to);
        }

        Work choosing(Path to, Set<String> names) {
            return new Work(to, names, suggested, notice, busy, marks, name);
        }

        Work suggesting(List<Dest> d) {
            return new Work(destination, destNames, d, notice, busy, marks, name);
        }

        Work noting(String text) {
            return new Work(destination, destNames, suggested, text, busy, marks, name);
        }

        Work running(String text) {
            return new Work(destination, destNames, suggested, notice, text, marks, name);
        }

        /** Something finished: say how it went, and nothing is running any more. */
        Work finished(String text) {
            return new Work(destination, destNames, suggested, text, "", marks, name);
        }

        Work marking(Mark m) {
            List<Mark> out = new ArrayList<>(marks);
            out.add(m);
            return new Work(destination, destNames, suggested, notice, busy, List.copyOf(out), name);
        }

        Work unmarking(String mark) {
            return new Work(destination, destNames, suggested, notice, busy,
                    marks.stream().filter(m -> !m.name().equals(mark)).toList(), name);
        }
    }

    static Doc initial() {
        return new Doc(null, List.of(), false, Set.of(), Pick.NONE, null, Instant.now(), Input.NONE, Work.NONE, null);
    }

    private Doc with(Path folder, List<Entry> entries, boolean loading, Set<Path> selected, Pick pick,
                     Preview preview, Instant now, Input input, Work work, Status git) {
        return new Doc(folder, entries, loading, selected, pick, preview, now, input, work, git);
    }

    Doc navigating(Path to) {
        // Going somewhere ends naming a new host: either that is where it went, or the person went elsewhere instead.
        return with(to, List.of(), true, Set.of(), Pick.NONE, null, now, input.connecting(false),
                work.noting("").naming(null), null);
    }

    Doc listed(List<Entry> listing, Instant at) {
        return with(folder, listing, false, Set.of(), Pick.NONE, null, at, input, work.naming(null), git);
    }

    Doc selecting(Set<Path> paths) {
        // A new selection is a new question: what was picked on the rail was an answer to the old one, and a
        // preview of another file is not a preview of this one. The preview of the same single file stays,
        // because re-selecting it must not make the dock blink. A name typed for a rename was for the old one.
        Preview keep = preview != null && paths.size() == 1 && paths.contains(preview.path()) ? preview : null;
        return with(folder, entries, loading, paths, Pick.NONE, keep, now, input, work.naming(null), git);
    }

    Doc picking(Pick p) {
        return with(folder, entries, loading, selected, p, preview, now, input, work, git);
    }

    Doc previewing(Preview p) {
        return with(folder, entries, loading, selected, pick, p, now, input, work, git);
    }

    Doc inputting(Input i) {
        return with(folder, entries, loading, selected, pick, preview, now, i, work, git);
    }

    Doc working(Work w) {
        return with(folder, entries, loading, selected, pick, preview, now, input, w, git);
    }

    Doc gitting(Status s) {
        return with(folder, entries, loading, selected, pick, preview, now, input, work, s);
    }

    /** Whether the folder shown is on another host, which is what gives the rail its other panel. */
    boolean remote() {
        return Remotes.is(folder);
    }

    /** The one selected path, or null if there are none or several: the only case the dock speaks to. */
    Path single() {
        return selected.size() == 1 ? selected.iterator().next() : null;
    }

    /** The list as the table is showing it. */
    List<Entry> ordered() {
        return input.order().apply(entries);
    }

    /** The rail for this moment. Pure, and cheap enough to ask for on every change. */
    Suggestions.Rail rail() {
        return Suggestions.of(entries, selected, pick, now, ZoneId.systemDefault());
    }

    /** What the held modifier is announcing: ranges for Shift, rules for Control, nothing for neither. */
    List<Intents.Candidate> intent() {
        if (selected.isEmpty()) {
            return List.of();
        }
        if (input.shift()) {
            return Intents.shift(ordered(), selected, ZoneId.systemDefault());
        }
        if (input.control()) {
            return Intents.control(ordered(), selected, now, ZoneId.systemDefault());
        }
        return List.of();
    }

    long selectedBytes() {
        return entries.stream().filter(e -> selected.contains(e.path())).mapToLong(Entry::size).sum();
    }

    /**
     * What the chosen action would do to what the rail reaches, or null when no action is chosen or it is not one
     * that writes (marking). It reads only values, so it can be drawn before anything runs.
     */
    Plan plan() {
        Act act = pick.act();
        if (act == null || act == Act.MARK) {
            return null;
        }
        // On a remote folder: a copy here (a download), a rename, and a delete into the host's own trash. Moving
        // between its folders, and archiving there, are not offered yet.
        if (remote() && act != Act.COPY && act != Act.RENAME && act != Act.DELETE) {
            return null;
        }
        List<Entry> targets = rail().effect().targets();
        List<Path> paths = targets.stream().map(Entry::path).toList();
        List<Long> sizes = targets.stream().map(Entry::size).toList();
        if (act == Act.RENAME) {
            if (targets.size() != 1) {
                // Several at once is renaming by pattern, which is its own piece of work (docs/TODO.md).
                List<Plan.Skip> why = paths.stream().map(p -> new Plan.Skip(p, "one name is changed at a time"))
                        .toList();
                return new Plan(Plan.Kind.RENAME, List.of(), why, 0L, null);
            }
            Set<String> taken = new java.util.HashSet<>();
            for (Entry e : entries) {
                taken.add(e.name());
            }
            return Plan.rename(paths.get(0), sizes.get(0), work.name(), taken);
        }
        return switch (act) {
            case MOVE -> Plan.of(Plan.Kind.MOVE, paths, sizes, work.destination(), work.destNames(), null);
            case COPY -> Plan.of(Plan.Kind.COPY, paths, sizes, work.destination(), work.destNames(), null);
            case DELETE -> Plan.of(Plan.Kind.DELETE, paths, sizes, null, Set.of(), null);
            case ARCHIVE -> {
                Path where = work.destination() != null ? work.destination() : folder;
                yield Plan.of(Plan.Kind.ARCHIVE, paths, sizes, where, Set.of(), zipName(where));
            }
            default -> null;
        };
    }

    private Path zipName(Path where) {
        Set<String> taken = new java.util.HashSet<>();
        for (Entry e : entries) {
            taken.add(e.name());
        }
        String base = "Archive";
        String name = base + ".zip";
        for (int i = 2; where.equals(folder) && taken.contains(name); i++) {
            name = base + "-" + i + ".zip";
        }
        return where.resolve(name);
    }
}
