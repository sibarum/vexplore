package dev.sibarum.vexplore.files;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where files of a kind probably want to go.
 *
 * <p>The design's answer to "move these somewhere" is a guess before it is a dialog: the folder nearby that already
 * holds that kind. Candidates are the subfolders of the folder being shown, its parent, and its siblings — the
 * places a person tidies into — scored by how many files of the wanted kind are directly in each. A place with none
 * is not a suggestion, so the list is often short and sometimes empty, which is correct.
 *
 * <p>Blocking: it lists several folders. Run it on the offload lane.
 */
public final class Destinations {

    private static final int MAX = 3;

    /** A place, and why it was suggested. */
    public record Dest(Path path, String reason, int count) {
    }

    private Destinations() {
    }

    public static List<Dest> suggest(Path here, Kind kind, Set<Path> exclude) {
        if (here == null || kind == null || kind == Kind.FOLDER) {
            return List.of();
        }
        Set<Path> candidates = new HashSet<>(Folders.subfolders(here));
        Path parent = here.getParent();
        if (parent != null) {
            candidates.add(parent);
            candidates.addAll(Folders.subfolders(parent));
        }
        candidates.remove(here);
        candidates.removeAll(exclude);
        List<Dest> out = new ArrayList<>();
        for (Path c : candidates) {
            int n = 0;
            for (Entry e : Folders.list(c)) {
                if (!e.folder() && e.kind() == kind) {
                    n++;
                }
            }
            if (n > 0) {
                out.add(new Dest(c, "holds " + n + " " + (n == 1 ? kind.singular() : kind.plural()), n));
            }
        }
        out.sort(Comparator.comparingInt(Dest::count).reversed().thenComparing(d -> d.path().toString()));
        return out.size() > MAX ? List.copyOf(out.subList(0, MAX)) : List.copyOf(out);
    }

    /** The names directly in {@code dir}: what a plan is checked against for collisions. */
    public static Set<String> names(Path dir) {
        Set<String> out = new HashSet<>();
        for (Entry e : Folders.list(dir)) {
            out.add(e.name());
        }
        return Set.copyOf(out);
    }
}
