package dev.sibarum.vexplore.remote;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A path on a remote host: a POSIX path, {@code /} separated, tied to its {@link RemoteFileSystem}.
 *
 * <p>Two paths are equal when they name the same string on the same host. A path that came out of a listing also
 * carries what the listing said about it ({@link #known}), so reading its attributes straight afterwards costs no
 * round trip: a folder of a thousand files is one listing and not a thousand {@code STAT}s. That is a cache and not
 * part of the value: it is trusted only briefly, and not past a change made to the host from here.
 */
final class RemotePath implements Path {

    /** How long what a listing said about a path is taken as still true. */
    static final long KNOWN_FOR_MS = 5_000;

    private final RemoteFileSystem fs;
    private final String path;
    private final String[] names;

    private final Sftp.Attrs known;
    private final long knownAt;
    private final long knownChanges;

    RemotePath(RemoteFileSystem fs, String path) {
        this(fs, path, null);
    }

    RemotePath(RemoteFileSystem fs, String path, Sftp.Attrs known) {
        this.fs = fs;
        this.path = clean(path);
        this.names = this.path.isEmpty() || this.path.equals("/") ? new String[0]
                : (this.path.startsWith("/") ? this.path.substring(1) : this.path).split("/");
        this.known = known;
        this.knownAt = known == null ? 0L : System.currentTimeMillis();
        this.knownChanges = fs.changes();
    }

    /** What the listing that produced this path said about it, if it was recently enough to trust; else null. */
    Sftp.Attrs known() {
        return known != null && System.currentTimeMillis() - knownAt < KNOWN_FOR_MS && fs.changes() == knownChanges
                ? known : null;
    }

    /** The string the server is sent. Empty is the directory the server started in, which is home. */
    String wire() {
        return path.isEmpty() ? "." : path;
    }

    private static String clean(String p) {
        String s = p.replace('\\', '/').replaceAll("/{2,}", "/");
        if (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    @Override
    public RemoteFileSystem getFileSystem() {
        return fs;
    }

    @Override
    public boolean isAbsolute() {
        return path.startsWith("/");
    }

    @Override
    public Path getRoot() {
        return isAbsolute() ? new RemotePath(fs, "/") : null;
    }

    @Override
    public Path getFileName() {
        return names.length == 0 ? null : new RemotePath(fs, names[names.length - 1]);
    }

    @Override
    public Path getParent() {
        if (names.length == 0 || (names.length == 1 && !isAbsolute())) {
            return null;
        }
        String joined = String.join("/", Arrays.copyOf(names, names.length - 1));
        return new RemotePath(fs, isAbsolute() ? "/" + joined : joined);
    }

    @Override
    public int getNameCount() {
        return names.length;
    }

    @Override
    public Path getName(int index) {
        if (index < 0 || index >= names.length) {
            throw new IllegalArgumentException();
        }
        return new RemotePath(fs, names[index]);
    }

    @Override
    public Path subpath(int begin, int end) {
        if (begin < 0 || end > names.length || begin >= end) {
            throw new IllegalArgumentException();
        }
        return new RemotePath(fs, String.join("/", Arrays.copyOfRange(names, begin, end)));
    }

    @Override
    public boolean startsWith(Path other) {
        if (!(other instanceof RemotePath o) || o.fs != fs || o.isAbsolute() != isAbsolute()
                || o.names.length > names.length) {
            return false;
        }
        for (int i = 0; i < o.names.length; i++) {
            if (!o.names[i].equals(names[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean endsWith(Path other) {
        if (!(other instanceof RemotePath o) || o.fs != fs || (o.isAbsolute() && !o.equals(this))
                || o.names.length > names.length) {
            return false;
        }
        for (int i = 1; i <= o.names.length; i++) {
            if (!o.names[o.names.length - i].equals(names[names.length - i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Path normalize() {
        List<String> out = new ArrayList<>();
        for (String n : names) {
            if (n.equals(".") || n.isEmpty()) {
                continue;
            }
            if (n.equals("..") && !out.isEmpty() && !out.getLast().equals("..")) {
                out.removeLast();
            } else if (!(n.equals("..") && isAbsolute())) {
                out.add(n);
            }
        }
        String joined = String.join("/", out);
        return new RemotePath(fs, isAbsolute() ? "/" + joined : joined);
    }

    @Override
    public Path resolve(Path other) {
        RemotePath o = mine(other);
        if (o.isAbsolute()) {
            return o;
        }
        if (o.path.isEmpty()) {
            return this;
        }
        return new RemotePath(fs, path.isEmpty() ? o.path : path + "/" + o.path);
    }

    @Override
    public Path resolve(String other) {
        return resolve(new RemotePath(fs, other));
    }

    /** {@code name} in this folder, carrying what the listing said about it. */
    RemotePath child(String name, Sftp.Attrs attrs) {
        return new RemotePath(fs, path.isEmpty() ? name : path.equals("/") ? "/" + name : path + "/" + name, attrs);
    }

    @Override
    public Path relativize(Path other) {
        RemotePath o = mine(other);
        if (o.isAbsolute() != isAbsolute()) {
            throw new IllegalArgumentException("one path is absolute and the other is not");
        }
        int common = 0;
        while (common < names.length && common < o.names.length && names[common].equals(o.names[common])) {
            common++;
        }
        List<String> out = new ArrayList<>();
        for (int i = common; i < names.length; i++) {
            out.add("..");
        }
        out.addAll(Arrays.asList(o.names).subList(common, o.names.length));
        return new RemotePath(fs, String.join("/", out));
    }

    @Override
    public URI toUri() {
        Host h = fs.host();
        try {
            return new URI("sftp", h.user(), h.host(), h.port() == 0 ? -1 : h.port(),
                    toAbsolutePath().toString(), null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Relative paths are relative to home, as the server takes them. Home is known once a session has started; before
     * then a relative path is taken from the root, which is a guess, so callers that care resolve with
     * {@link #toRealPath} on the offload lane first.
     */
    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        String home = fs.homeIfKnown();
        return new RemotePath(fs, (home == null ? "" : home) + "/" + path);
    }

    /** Asks the server, so it blocks. */
    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        return new RemotePath(fs, fs.realpath(wire()));
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
        throw new UnsupportedOperationException("a remote folder cannot be watched");
    }

    @Override
    public int compareTo(Path other) {
        return path.compareTo(((RemotePath) other).path);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RemotePath p && p.fs == fs && p.path.equals(path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fs.host(), path);
    }

    @Override
    public String toString() {
        return path;
    }

    private RemotePath mine(Path other) {
        if (!(other instanceof RemotePath o) || o.fs != fs) {
            throw new ProviderMismatchException();
        }
        return o;
    }
}
