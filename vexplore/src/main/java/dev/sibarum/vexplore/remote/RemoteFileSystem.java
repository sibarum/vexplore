package dev.sibarum.vexplore.remote;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Set;

/**
 * One host, as a file system. It owns at most one SFTP session, started the first time anything asks and
 * started again if it dies; nothing connects by being constructed, so naming a host in the tree costs nothing.
 *
 * <p>The last way it failed to connect is kept ({@link #problem}), because a listing that cannot reach the host is,
 * by {@code Folders}' rule, an empty folder, and the person looking at it deserves to know which kind of empty.
 */
final class RemoteFileSystem extends FileSystem {

    /** The work one request does with the session; it may be asked twice, if the first session died. */
    interface Call<T> {
        T on(Sftp sftp) throws IOException;
    }

    private final RemoteProvider provider;
    private final Host host;
    private final Runnable connected;

    private Sftp session;
    private volatile String home;
    private volatile String problem = "";
    /** Counts writes: what a listing said about a path is not trusted past the next change to this host. */
    private final java.util.concurrent.atomic.AtomicLong changes = new java.util.concurrent.atomic.AtomicLong();

    RemoteFileSystem(RemoteProvider provider, Host host, Runnable connected) {
        this.provider = provider;
        this.host = host;
        this.connected = connected;
    }

    Host host() {
        return host;
    }

    /** Whether a session is up now. Asks nothing. */
    synchronized boolean live() {
        return session != null && !session.dead();
    }

    /** Why the last attempt to connect failed, or empty if it did not (or none was made). */
    String problem() {
        return problem;
    }

    /** Home, if a session has said; null before one has. */
    String homeIfKnown() {
        return home;
    }

    String realpath(String path) throws IOException {
        return run(s -> s.realpath(path));
    }

    /**
     * Do {@code call} on the session, starting one if there is none. A session that dies under the call is replaced
     * and the call made once more, since what comes through here only reads (a change goes through {@link #change});
     * a refusal from the server is an answer, not a death, and is not retried.
     */
    <T> T run(Call<T> call) throws IOException {
        Sftp s = session();
        try {
            return call.on(s);
        } catch (Sftp.Refused e) {
            throw e;
        } catch (IOException e) {
            if (!s.dead()) {
                throw e;
            }
            return call.on(session());
        }
    }

    /**
     * Do {@code call}, which changes something on the host, once. Not made again if the session dies under it: a
     * rename that reached the server before the pipe broke would fail the second time and say the wrong thing.
     */
    <T> T change(Call<T> call) throws IOException {
        try {
            return call.on(session());
        } finally {
            changes.incrementAndGet();
        }
    }

    /** How many changes this host has seen from here; a path's cached attributes are good for one value of this. */
    long changes() {
        return changes.get();
    }

    private Sftp session() throws IOException {
        boolean fresh = false;
        Sftp s;
        synchronized (this) {
            if (session == null || session.dead()) {
                try {
                    session = Sftp.start(host.command());
                    home = session.realpath(".");
                    problem = "";
                    fresh = true;
                } catch (IOException e) {
                    session = null;
                    problem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    throw e;
                }
            }
            s = session;
        }
        if (fresh) {
            connected.run();
        }
        return s;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public synchronized void close() throws IOException {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return List.of(new RemotePath(this, "/"));
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return List.of();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return Set.of("basic");
    }

    @Override
    public Path getPath(String first, String... more) {
        StringBuilder s = new StringBuilder(first);
        for (String m : more) {
            if (!m.isEmpty()) {
                s.append(s.isEmpty() ? "" : "/").append(m);
            }
        }
        return new RemotePath(this, s.toString());
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException("a remote folder cannot be watched");
    }

    @Override
    public String toString() {
        return "sftp://" + host.authority();
    }
}
