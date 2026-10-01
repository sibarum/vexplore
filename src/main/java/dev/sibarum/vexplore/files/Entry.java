package dev.sibarum.vexplore.files;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/**
 * One row of a folder listing, read once. The list draws these and never touches the disk again, so a slow or
 * vanished volume costs a listing and not a frame.
 *
 * @param path     where it is
 * @param folder   whether it is a directory
 * @param size     bytes; zero for a folder, whose size is a question the listing does not ask
 * @param modified last modification
 */
public record Entry(Path path, boolean folder, long size, Instant modified) {

    /** The last path element; a drive root has none, so it is the root itself. */
    public String name() {
        Path n = path.getFileName();
        return n == null ? path.toString() : n.toString();
    }

    /** Lower-case extension without the dot; empty for a folder or a name with none. */
    public String extension() {
        if (folder) {
            return "";
        }
        String n = name();
        int dot = n.lastIndexOf('.');
        return dot <= 0 || dot == n.length() - 1 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The coarse kind, from the extension. */
    public Kind kind() {
        return folder ? Kind.FOLDER : Kind.ofExtension(extension());
    }

    /** What the Type column says: the extension in capitals, or {@code FOLDER}. */
    public String typeLabel() {
        if (folder) {
            return "FOLDER";
        }
        String e = extension();
        return e.isEmpty() ? "FILE" : e.toUpperCase(Locale.ROOT);
    }
}
