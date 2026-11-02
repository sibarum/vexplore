package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Place;
import dev.sibarum.vexplore.core.Messages.Places;
import dev.sibarum.vexplore.core.Messages.PlacesWanted;
import dev.vexelray.framework.api.Job;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The top of the tree of places: the person's own folders that exist, then the drives, which is what a file browser
 * opens onto. On the offload lane, since asking whether a folder exists is the disk's question.
 *
 * <p>The folders are the profile's usual names under the home folder. That is Windows' layout, and Linux's when the
 * desktop has not renamed them; 0.1 read the XDG names on Linux through a module per OS, which comes back with
 * packaging (docs/TODO.md).
 */
@Job
public final class ListPlaces {

    static final List<String> USER_FOLDERS = List.of("Desktop", "Documents", "Downloads", "Music", "Pictures",
            "Videos");

    public Places run(PlacesWanted w) {
        List<Place> out = new ArrayList<>();
        Path home = Path.of(System.getProperty("user.home"));
        for (String name : USER_FOLDERS) {
            Path p = home.resolve(name);
            if (Files.isDirectory(p)) {
                out.add(new Place(p, name));
            }
        }
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            out.add(new Place(root, rootName(root)));
        }
        return new Places(out);
    }

    /** A drive by its letter, {@code C:}: its root without the separator. Linux's one root is {@code /}. */
    static String rootName(Path root) {
        String s = root.toString().replaceAll("[\\\\/]+$", "");
        return s.isEmpty() ? root.toString() : s;
    }
}
