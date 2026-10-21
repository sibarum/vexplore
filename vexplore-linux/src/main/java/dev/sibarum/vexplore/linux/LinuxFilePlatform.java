package dev.sibarum.vexplore.linux;

import dev.sibarum.vexplore.spi.FilePlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Files on Linux: the user's folders as the desktop names them (XDG user directories, which a user may have renamed or
 * a locale translated), the file system's root and the volumes mounted for the user, and Linux's rules for a name.
 */
public final class LinuxFilePlatform implements FilePlatform {

    /** The root, as Linux file managers call it. */
    static final String ROOT_NAME = "File System";

    /** The XDG user directories shown, in order, with their usual folders where the desktop does not say. */
    private static final Map<String, String> XDG = new LinkedHashMap<>();

    static {
        XDG.put("XDG_DESKTOP_DIR", "Desktop");
        XDG.put("XDG_DOCUMENTS_DIR", "Documents");
        XDG.put("XDG_DOWNLOAD_DIR", "Downloads");
        XDG.put("XDG_MUSIC_DIR", "Music");
        XDG.put("XDG_PICTURES_DIR", "Pictures");
        XDG.put("XDG_VIDEOS_DIR", "Videos");
    }

    /** Where volumes are mounted for a person, rather than for the system. */
    private static final List<String> MOUNT_PARENTS = List.of("/media/", "/run/media/", "/mnt/");

    private static final Pattern XDG_LINE = Pattern.compile("^(XDG_\\w+_DIR)=\"(.*)\"\\s*$");

    @Override
    public List<Place> userFolders(Path home) {
        return userFolders(home, home.resolve(".config").resolve("user-dirs.dirs"));
    }

    /** {@link #userFolders(Path)} reading the XDG file at {@code dirsFile}; for tests. */
    static List<Place> userFolders(Path home, Path dirsFile) {
        Map<String, Path> named = new LinkedHashMap<>();
        XDG.forEach((key, folder) -> named.put(key, home.resolve(folder)));
        try {
            for (String line : Files.readAllLines(dirsFile)) {
                Matcher m = XDG_LINE.matcher(line.strip());
                if (m.matches() && named.containsKey(m.group(1))) {
                    String value = m.group(2).replace("$HOME", home.toString());
                    try {
                        named.put(m.group(1), Path.of(value));
                    } catch (InvalidPathException e) {
                        // a line that names no path leaves the usual folder
                    }
                }
            }
        } catch (IOException e) {
            // no user-dirs.dirs: the usual folders
        }
        List<Place> out = new ArrayList<>();
        for (Path p : named.values()) {
            // A user directory the desktop has turned off points at home itself.
            if (!p.equals(home) && Files.isDirectory(p) && p.getFileName() != null) {
                out.add(new Place(p, p.getFileName().toString()));
            }
        }
        return out;
    }

    /** The root, then every file system mounted under {@code /media}, {@code /run/media} or {@code /mnt}. */
    @Override
    public List<Place> volumes() {
        return volumes(Path.of("/proc/self/mounts"));
    }

    /** {@link #volumes()} reading the mount table at {@code mounts}; for tests. */
    static List<Place> volumes(Path mounts) {
        List<Place> out = new ArrayList<>();
        Path root = Path.of("/");
        out.add(new Place(root, ROOT_NAME));
        try {
            for (String line : Files.readAllLines(mounts)) {
                String[] fields = line.split(" ");
                if (fields.length < 2) {
                    continue;
                }
                String point = unescape(fields[1]);
                if (MOUNT_PARENTS.stream().anyMatch(point::startsWith)) {
                    Path p = Path.of(point);
                    if (p.getFileName() != null && out.stream().noneMatch(e -> e.path().equals(p))) {
                        out.add(new Place(p, p.getFileName().toString()));
                    }
                }
            }
        } catch (IOException | InvalidPathException e) {
            // no mount table to read: the root alone
        }
        return out;
    }

    /** The mount table writes a space, a tab, a newline and a backslash as octal escapes ({@code \040}). */
    static String unescape(String field) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c == '\\' && i + 3 < field.length() && field.substring(i + 1, i + 4).matches("[0-7]{3}")) {
                out.append((char) Integer.parseInt(field.substring(i + 1, i + 4), 8));
                i += 3;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * The file system each path is on, found from the nearest part of it that exists (a drop's destination may be a
     * folder still to be made). Where that cannot be known, the same: a move, as within one volume, is what a drop does
     * by default.
     */
    @Override
    public boolean sameVolume(Path from, Path into) {
        try {
            return Files.getFileStore(existing(from)).equals(Files.getFileStore(existing(into)));
        } catch (IOException | SecurityException e) {
            return true;
        }
    }

    private static Path existing(Path p) {
        Path at = p.toAbsolutePath();
        while (at != null && !Files.exists(at)) {
            at = at.getParent();
        }
        return at == null ? Path.of("/") : at;
    }

    /** The only root is {@code /}. */
    @Override
    public String rootName(Path root) {
        return ROOT_NAME;
    }

    /** Linux forbids only {@code /} and NUL in a name, and {@code .} and {@code ..} are not names. */
    @Override
    public String safeName(String name) {
        String s = name.replace('/', '_').replace('\0', '_');
        return s.equals(".") || s.equals("..") || s.isBlank() ? "_" + s : s;
    }
}
