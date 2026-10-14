package dev.sibarum.vexplore.remote;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The one way the rest of Vexplore reaches a remote host, and the questions it asks about a path that might be one.
 *
 * <p>A remote folder is a {@link Path} like any other, so most of the application never asks. What does ask is the
 * part that would otherwise do the wrong thing: git, which runs locally; the applications a file is opened in, which
 * want a local file and get a downloaded one ({@link #local}); and the Suggestion Rail, which offers what can be done
 * to a server it does not write to.
 */
public final class Remotes {

    private static final RemoteProvider PROVIDER = new RemoteProvider();

    /** {@code [user@]host:path}, scp's way of saying it. A one-letter host is a Windows drive, not a host. */
    private static final Pattern SCP = Pattern.compile("^(?:([^@/:\\s]+)@)?([^@/:\\s]{2,}):(.*)$");

    private Remotes() {
    }

    /** Whether {@code p} is on a remote host. */
    public static boolean is(Path p) {
        return p != null && p.getFileSystem() instanceof RemoteFileSystem;
    }

    /**
     * {@code host}'s home folder, as a path that has not asked the server anything yet: the empty path, which the
     * server takes as the folder it started in. {@link #absolute} turns it into a real one.
     */
    public static Path home(Host host) {
        return PROVIDER.system(host).getPath("");
    }

    /** The host {@code p} is on, or null for a local path. */
    public static Host host(Path p) {
        return is(p) ? ((RemoteFileSystem) p.getFileSystem()).host() : null;
    }

    /**
     * An address as a person or another program would give one: {@code sftp://[user@]host[:port]/path}, or scp's
     * {@code [user@]host:path}. Empty when it is neither, which includes every Windows path.
     */
    public static Optional<Path> parse(String address) {
        if (address == null) {
            return Optional.empty();
        }
        String s = address.strip();
        try {
            if (s.regionMatches(true, 0, "sftp://", 0, 7) || s.regionMatches(true, 0, "ssh://", 0, 6)) {
                URI uri = new URI(s);
                if (uri.getHost() == null) {
                    return Optional.empty();
                }
                return Optional.of(PROVIDER.getPath(uri));
            }
            Matcher m = SCP.matcher(s);
            if (m.matches()) {
                Host h = new Host(m.group(1), m.group(2), 0);
                return Optional.of(PROVIDER.system(h).getPath(m.group(3)));
            }
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** {@code p} with a relative path resolved against home by asking the server. Blocks; a local path is as it was. */
    public static Path absolute(Path p) throws IOException {
        return is(p) && !p.isAbsolute() ? p.toRealPath() : p;
    }

    /**
     * Where a delete on {@code p}'s host puts things: {@code .vexplore-trash} in the user's home there. On the host
     * so that deleting is a rename, quick and undoable, rather than a download of everything deleted. Asks the server
     * where home is, so it blocks.
     */
    public static Path trash(Path p) throws IOException {
        return absolute(home(host(p))).resolve(TRASH);
    }

    /** The name of the trash folder in a remote home. Hidden, as a dot name is on the systems SFTP serves. */
    public static final String TRASH = ".vexplore-trash";

    /** {@code sftp://user@host/path}: what Copy path puts on the clipboard for a remote file. */
    public static String address(Path p) {
        if (!is(p)) {
            return p.toString();
        }
        Host h = host(p);
        return "sftp://" + h.authority() + p.toAbsolutePath();
    }

    /** Whether {@code p}'s host has a session up now. Asks nothing, so it is cheap enough for a tree row. */
    public static boolean connected(Path p) {
        return is(p) && ((RemoteFileSystem) p.getFileSystem()).live();
    }

    /** Why {@code p}'s host could not be reached the last time it was tried; empty if it could, or for a local path. */
    public static String problem(Path p) {
        return is(p) ? ((RemoteFileSystem) p.getFileSystem()).problem() : "";
    }

    /** Run {@code r} whenever any host connects: the tree's cue to offer what is inside it. */
    public static void onConnect(Runnable r) {
        PROVIDER.onConnect(r);
    }

    /** The hosts in the user's {@code ~/.ssh/config}, in the order written. */
    public static List<Host> configured() {
        return Host.configured(Path.of(System.getProperty("user.home"), ".ssh", "config"));
    }

    /**
     * A local copy of {@code p}, for an application that wants a file on this disk: downloaded into
     * {@code ~/.vexplore/remote/<host>/<path>}, and downloaded again only when the remote one has changed size or
     * time. A local path is returned as is. Blocks.
     */
    public static Path local(Path p) throws IOException {
        if (!is(p)) {
            return p;
        }
        Path to = cache(p);
        var remote = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
        if (Files.isRegularFile(to) && Files.size(to) == remote.size()
                && Files.getLastModifiedTime(to).equals(remote.lastModifiedTime())) {
            return to;
        }
        Files.createDirectories(to.getParent());
        Path part = to.resolveSibling(to.getFileName() + ".part");
        try (var in = Files.newInputStream(p)) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.setLastModifiedTime(part, remote.lastModifiedTime());
        return Files.move(part, to, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Where {@link #local} keeps {@code p}: each name made safe for this disk, so the tree stays readable. */
    static Path cache(Path p) {
        Path to = Path.of(System.getProperty("user.home"), ".vexplore", "remote", safe(host(p).authority()));
        for (Path n : p.toAbsolutePath()) {
            to = to.resolve(safe(n.toString()));
        }
        return to;
    }

    private static String safe(String name) {
        String s = name.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "_");
        return s.equals(".") || s.equals("..") || s.isBlank() ? "_" + s : s;
    }
}
