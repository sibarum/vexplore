package dev.sibarum.vexplore.windows;

import dev.sibarum.vexplore.spi.FilePlatform;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Files on Windows: the profile's folders, a place per drive ({@code C:}), and Windows' rules for a file's name. */
public final class WindowsFilePlatform implements FilePlatform {

    private static final List<String> USER_FOLDERS = List.of("Desktop", "Documents", "Downloads", "Music", "Pictures",
            "Videos");

    @Override
    public List<Place> userFolders(Path home) {
        List<Place> out = new ArrayList<>();
        for (String name : USER_FOLDERS) {
            Path p = home.resolve(name);
            if (Files.isDirectory(p)) {
                out.add(new Place(p, name));
            }
        }
        return out;
    }

    @Override
    public List<Place> volumes() {
        List<Place> out = new ArrayList<>();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            out.add(new Place(root, rootName(root)));
        }
        return out;
    }

    /** A drive by its letter, {@code C:}: its root's path without the separator. */
    @Override
    public String rootName(Path root) {
        return root.toString().replaceAll("[\\\\/]+$", "");
    }

    /** A drive is a volume: the same drive letter is the same volume. */
    @Override
    public boolean sameVolume(Path from, Path into) {
        return Objects.equals(from.getRoot(), into.getRoot());
    }

    /** Windows forbids {@code <>:"/\|?*} and control characters in a name, and {@code .} and {@code ..} are not names. */
    @Override
    public String safeName(String name) {
        String s = name.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "_");
        return s.equals(".") || s.equals("..") || s.isBlank() ? "_" + s : s;
    }
}
