package dev.sibarum.vexplore.files;

import dev.sibarum.vexplore.remote.Remotes;
import dev.sibarum.vexplore.spi.FilePlatform;

import java.nio.file.Path;

/**
 * What a path is called, in one place: its file name, or — for a root, which has none — what this OS calls it
 * ({@link FilePlatform#rootName}, {@code C:} or {@code File System}). A server's root keeps its path ({@code /}), which
 * is what it is called there.
 */
public final class Names {

    private Names() {
    }

    public static String of(Path p) {
        Path n = p.getFileName();
        if (n != null) {
            return n.toString();
        }
        return Remotes.is(p) ? p.toString() : FilePlatform.current().rootName(p);
    }
}
