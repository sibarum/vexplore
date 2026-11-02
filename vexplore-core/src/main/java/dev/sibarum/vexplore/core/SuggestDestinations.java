package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Dest;
import dev.sibarum.vexplore.core.Messages.Destinations;
import dev.sibarum.vexplore.core.Messages.Destining;
import dev.vexelray.framework.api.Job;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where files of a kind probably want to go: 0.1's {@code Destinations}, on the offload lane because it lists
 * several folders.
 *
 * <p>The design's answer to "move these somewhere" is a guess before it is a dialog: the person's own folder for the
 * kind (Pictures for pictures, Videos for videos), then the folders nearby that already hold that kind, scored by how
 * many of it are directly in each. Nearby is the subfolders of the folder shown, its parent and the parent's other
 * subfolders, the places a person tidies into. A place holding none is not a suggestion, so the list is often short
 * and sometimes empty, which is correct; *Choose folder…* is always there.
 */
@Job
public final class SuggestDestinations {

    /** How many places the rail offers. */
    static final int MAX = 3;

    public Destinations run(Destining d) {
        return new Destinations(d.ticket(), places(d.folder(), d.kind(), Path.of(System.getProperty("user.home"))));
    }

    static List<Dest> places(Path here, Kind kind, Path home) {
        if (kind == Kind.FOLDER) {
            return List.of();
        }
        Path shown = here.toAbsolutePath().normalize();
        List<Dest> out = new ArrayList<>();
        String own = kind == Kind.IMAGE ? "Pictures" : kind == Kind.VIDEO ? "Videos" : null;
        Path mine = own == null ? null : home.toAbsolutePath().normalize().resolve(own);
        if (mine != null && Files.isDirectory(mine) && !mine.equals(shown)) {
            out.add(new Dest(mine, "your " + own + " folder", 0));
        }
        Set<Path> candidates = new LinkedHashSet<>(subfolders(shown));
        Path parent = shown.getParent();
        if (parent != null) {
            candidates.add(parent);
            candidates.addAll(subfolders(parent));
        }
        candidates.remove(shown);
        candidates.remove(mine);
        List<Dest> near = new ArrayList<>();
        for (Path c : candidates) {
            int n = count(c, kind);
            if (n > 0) {
                near.add(new Dest(c, "holds " + n + " " + (n == 1 ? kind.singular() : kind.plural()), n));
            }
        }
        near.sort(Comparator.comparingInt(Dest::count).reversed().thenComparing(x -> x.path().toString()));
        out.addAll(near);
        return out.size() > MAX ? List.copyOf(out.subList(0, MAX)) : List.copyOf(out);
    }

    private static List<Path> subfolders(Path dir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> in = Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (Path p : in) {
                out.add(p.toAbsolutePath().normalize());
            }
        } catch (IOException | RuntimeException e) {
            // A folder that cannot be read suggests nothing; it is not this job's to say why.
        }
        return out;
    }

    private static int count(Path dir, Kind kind) {
        int n = 0;
        try (DirectoryStream<Path> in = Files.newDirectoryStream(dir)) {
            for (Path p : in) {
                if (Files.isRegularFile(p) && Kind.ofExtension(Kind.extension(ListFolder.name(p))) == kind) {
                    n++;
                }
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
        return n;
    }
}
