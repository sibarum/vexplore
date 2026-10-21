package dev.sibarum.vexplore.spi;

import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * What differs per operating system about a computer's own files, as Vexplore shows them: which folders are the
 * user's, what the top of the file system is and what it is called, and what a file may be named. One
 * {@code vexplore-<os>} module provides it through {@link ServiceLoader}; nothing else in Vexplore asks which OS it is
 * on.
 */
public interface FilePlatform {

    /** A place to show at the top of the navigator: where it is, and what it is called. */
    record Place(Path path, String label) {
    }

    /** The user's own folders under {@code home} that exist — desktop, documents, downloads, music, pictures, videos. */
    List<Place> userFolders(Path home);

    /** The tops of this machine's file systems: drives on Windows; the root and mounted volumes on Linux. */
    List<Place> volumes();

    /** What a path with no file name of its own — a root — is called: {@code C:}, {@code File System}. */
    String rootName(Path root);

    /**
     * Whether a file at {@code from} moved into the folder {@code into} stays on one volume — a rename, which is what a
     * drop does by default — rather than crossing to another, which is a copy. On Windows a volume is a drive; on Linux
     * volumes are mounted inside one tree, so it is the file system the paths are on.
     */
    boolean sameVolume(Path from, Path into);

    /** {@code name} made a valid single file name here, with anything this OS forbids in one replaced. */
    String safeName(String name);

    /** The one platform on the class path. */
    static FilePlatform current() {
        return Found.INSTANCE;
    }

    /** Looked up once, on first use. */
    final class Found {
        private static final FilePlatform INSTANCE = choose(ServiceLoader.load(FilePlatform.class).stream()
                .map(ServiceLoader.Provider::get).toList());

        private Found() {
        }

        static FilePlatform choose(List<FilePlatform> found) {
            if (found.size() != 1) {
                throw new IllegalStateException((found.isEmpty() ? "no" : found.size() + "")
                        + " vexplore OS modules on the class path; the application's OS-activated profile adds one");
            }
            return found.getFirst();
        }
    }
}
