package dev.sibarum.vexplore.files;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The application's only reader of the file system.
 *
 * <p><b>Every method here blocks and none belongs on the frame loop.</b> A listing may cross a network share or a
 * disk that has gone to sleep; callers run these on {@code Gui.offload()} and land the result through the model.
 * Nothing here throws for a thing the user cannot fix — an unreadable folder is an empty one, because a
 * file browser that stops at the first permission error is a file browser that cannot open {@code C:\}.
 */
public final class Folders {

    private Folders() {
    }

    /** Everything directly in {@code dir}: folders first, then files, each by name. Hidden entries are listed. */
    public static List<Entry> list(Path dir) {
        List<Entry> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                entry(p, out);
            }
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        out.sort(Comparator.comparing(Entry::folder).reversed()
                .thenComparing(e -> e.name().toLowerCase(java.util.Locale.ROOT)));
        return List.copyOf(out);
    }

    /** The subfolders of {@code dir} by name — what the tree expands to. */
    public static List<Path> subfolders(Path dir) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (Path p : stream) {
                out.add(p);
            }
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        out.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)));
        return List.copyOf(out);
    }

    /** {@code path} from the root down, each element a path: {@code C:\}, {@code C:\Users}, {@code C:\Users\me}. */
    public static List<Path> chain(Path path) {
        List<Path> out = new ArrayList<>();
        for (Path p = path.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            out.add(0, p);
        }
        return List.copyOf(out);
    }

    private static void entry(Path p, List<Entry> out) {
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
            out.add(new Entry(p, a.isDirectory(), a.isDirectory() ? 0L : a.size(), a.lastModifiedTime().toInstant()));
        } catch (IOException | RuntimeException e) {
            // Vanished between the listing and the stat, or not ours to read: it is not a row.
            out.add(new Entry(p, false, 0L, Instant.EPOCH));
        }
    }
}
