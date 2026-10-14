package dev.sibarum.vexplore.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * {@code sftp:} as a {@code java.nio.file} provider.
 *
 * <p>This is what lets the rest of Vexplore stay as it is: {@code Folders} lists, the Preview Dock reads bytes,
 * {@code Files.copy} downloads and {@code walkFileTree} walks, all through {@code Files}, without knowing the disk is
 * somewhere else. Writing goes the same way: a copy to a remote folder is {@code Files.copy} into an output stream,
 * a move on one host is {@code RENAME}, and every refusal is the exception {@code Files} would throw for the disk
 * (a name already there, a folder not empty, no permission), so {@code Operations} needs no remote case of its own.
 *
 * <p>Not installed for {@code Path.of(URI)}: the native image does not look for providers, and {@link Remotes} is the
 * one way in.
 */
final class RemoteProvider extends FileSystemProvider {

    private final Map<String, RemoteFileSystem> systems = new ConcurrentHashMap<>();
    private final List<Runnable> connected = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** The host's file system, made the first time it is asked for. */
    RemoteFileSystem system(Host host) {
        return systems.computeIfAbsent(host.authority(), a -> new RemoteFileSystem(this, host, () -> {
            for (Runnable r : connected) {
                r.run();
            }
        }));
    }

    void onConnect(Runnable r) {
        connected.add(r);
    }

    @Override
    public String getScheme() {
        return "sftp";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
        Host h = host(uri);
        if (systems.containsKey(h.authority())) {
            throw new FileSystemAlreadyExistsException(h.authority());
        }
        return system(h);
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        return system(host(uri));
    }

    @Override
    public Path getPath(URI uri) {
        String p = uri.getPath();
        return system(host(uri)).getPath(p == null || p.isEmpty() ? "" : p);
    }

    static Host host(URI uri) {
        return new Host(uri.getUserInfo(), uri.getHost(), Math.max(0, uri.getPort()));
    }

    // ------------------------------------------------------------------ reading

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        RemotePath d = remote(dir);
        List<Sftp.Item> items = d.getFileSystem().run(s -> s.list(d.wire()));
        List<Path> out = new ArrayList<>(items.size());
        for (Sftp.Item i : items) {
            RemotePath p = d.child(i.name(), i.attrs());
            if (filter == null || filter.accept(p)) {
                out.add(p);
            }
        }
        return new DirectoryStream<>() {
            @Override
            public Iterator<Path> iterator() {
                return out.iterator();
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        for (OpenOption o : options) {
            if (o != StandardOpenOption.READ) {
                throw new IllegalArgumentException(o + " on a stream that reads");
            }
        }
        return new Reader(remote(path), 0L);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs) throws IOException {
        for (OpenOption o : options) {
            if (o != StandardOpenOption.READ) {
                throw new UnsupportedOperationException("a remote file is written through newOutputStream");
            }
        }
        RemotePath p = remote(path);
        long size = stat(p, true).size();
        return new SeekableByteChannel() {
            private long position;
            private boolean open = true;
            /** Read on from where the last read stopped; a seek elsewhere starts another. */
            private Reader reader;
            private long readerAt = -1;

            @Override
            public int read(ByteBuffer dst) throws IOException {
                if (position >= size) {
                    return -1;
                }
                if (reader == null || readerAt != position) {
                    if (reader != null) {
                        reader.close();
                    }
                    reader = new Reader(p, position);
                }
                byte[] b = new byte[Math.min(dst.remaining(), Sftp.CHUNK)];
                int n = reader.read(b, 0, b.length);
                if (n <= 0) {
                    return -1;
                }
                dst.put(b, 0, n);
                position += n;
                readerAt = position;
                return n;
            }

            @Override
            public int write(ByteBuffer src) {
                throw new NonWritableChannelException();
            }

            @Override
            public long position() {
                return position;
            }

            @Override
            public SeekableByteChannel position(long to) {
                position = to;
                return this;
            }

            @Override
            public long size() {
                return size;
            }

            @Override
            public SeekableByteChannel truncate(long to) {
                throw new NonWritableChannelException();
            }

            @Override
            public boolean isOpen() {
                return open;
            }

            @Override
            public void close() {
                open = false;
                if (reader != null) {
                    reader.close();
                }
            }
        };
    }

    /**
     * A remote file as a stream, read in {@link Sftp#CHUNK}s. The handle is opened on the first read, so a stream
     * nobody reads costs nothing; it lives on the session it was opened on, and a session that dies ends the read.
     */
    private static final class Reader extends InputStream {
        private final RemotePath path;
        private Sftp session;
        private byte[] handle;
        private long offset;
        private byte[] buf = new byte[0];
        private int at;
        private boolean end;

        Reader(RemotePath path, long offset) {
            this.path = path;
            this.offset = offset;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (at == buf.length && !fill()) {
                return -1;
            }
            int n = Math.min(len, buf.length - at);
            System.arraycopy(buf, at, b, off, n);
            at += n;
            return n;
        }

        private boolean fill() throws IOException {
            if (end) {
                return false;
            }
            if (handle == null) {
                RemoteFileSystem fs = path.getFileSystem();
                handle = fs.run(s -> {
                    session = s;
                    return s.open(path.wire());
                });
            }
            byte[] got = session.read(handle, offset, Sftp.CHUNK);
            if (got == null || got.length == 0) {
                end = true;
                return false;
            }
            offset += got.length;
            buf = got;
            at = 0;
            return true;
        }

        @Override
        public void close() {
            if (handle != null && session != null && !session.dead()) {
                session.release(handle);
            }
            handle = null;
            end = true;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        if (!type.isAssignableFrom(Attributes.class)) {
            throw new UnsupportedOperationException(type.getSimpleName() + " on a remote file");
        }
        boolean follow = Arrays.stream(options).noneMatch(o -> o == LinkOption.NOFOLLOW_LINKS);
        return (A) new Attributes(stat(remote(path), follow));
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
            throws IOException {
        BasicFileAttributes a = readAttributes(path, BasicFileAttributes.class, options);
        Map<String, Object> all = new HashMap<>();
        all.put("size", a.size());
        all.put("lastModifiedTime", a.lastModifiedTime());
        all.put("lastAccessTime", a.lastAccessTime());
        all.put("creationTime", a.creationTime());
        all.put("isDirectory", a.isDirectory());
        all.put("isRegularFile", a.isRegularFile());
        all.put("isSymbolicLink", a.isSymbolicLink());
        all.put("isOther", a.isOther());
        all.put("fileKey", null);
        String names = attributes.startsWith("basic:") ? attributes.substring(6) : attributes;
        if (names.equals("*")) {
            return all;
        }
        Map<String, Object> out = new HashMap<>();
        for (String n : names.split(",")) {
            if (all.containsKey(n)) {
                out.put(n, all.get(n));
            }
        }
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        if (type != BasicFileAttributeView.class) {
            return null;
        }
        return (V) new BasicFileAttributeView() {
            @Override
            public String name() {
                return "basic";
            }

            @Override
            public BasicFileAttributes readAttributes() throws IOException {
                return RemoteProvider.this.readAttributes(path, BasicFileAttributes.class, options);
            }

            @Override
            public void setTimes(FileTime modified, FileTime access, FileTime create) throws IOException {
                if (modified != null) {
                    RemoteProvider.setTimes(remote(path), modified);
                }
            }
        };
    }

    /**
     * The attributes, from what the listing said when that is recent and not a link's own, and otherwise from the
     * server. A path that does not exist is {@link NoSuchFileException}, as {@code Files} expects.
     */
    private static Sftp.Attrs stat(RemotePath p, boolean follow) throws IOException {
        Sftp.Attrs known = p.known();
        if (known != null && !(follow && known.link())) {
            return known;
        }
        try {
            return p.getFileSystem().run(s -> follow ? s.stat(p.wire()) : s.lstat(p.wire()));
        } catch (Sftp.Refused e) {
            if (e.code() == Sftp.NO_SUCH_FILE) {
                throw new NoSuchFileException(p.toString());
            }
            if (e.code() == Sftp.PERMISSION_DENIED) {
                throw new AccessDeniedException(p.toString());
            }
            throw e;
        }
    }

    private record Attributes(Sftp.Attrs a) implements BasicFileAttributes {
        @Override
        public FileTime lastModifiedTime() {
            return FileTime.from(a.modified(), TimeUnit.SECONDS);
        }

        @Override
        public FileTime lastAccessTime() {
            return lastModifiedTime();
        }

        @Override
        public FileTime creationTime() {
            return lastModifiedTime();
        }

        @Override
        public boolean isRegularFile() {
            return a.regular() || a.permissions() == 0;
        }

        @Override
        public boolean isDirectory() {
            return a.directory();
        }

        @Override
        public boolean isSymbolicLink() {
            return a.link();
        }

        @Override
        public boolean isOther() {
            return !isRegularFile() && !isDirectory() && !isSymbolicLink();
        }

        @Override
        public long size() {
            return Math.max(0L, a.size());
        }

        @Override
        public Object fileKey() {
            return null;
        }
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        stat(remote(path), true);
    }

    @Override
    public boolean isSameFile(Path a, Path b) {
        return a.equals(b);
    }

    @Override
    public boolean isHidden(Path path) {
        Path n = path.getFileName();
        return n != null && n.toString().startsWith(".");
    }

    // ------------------------------------------------------------------ writing

    /**
     * A remote file being written, in {@link Sftp#CHUNK}s. Closing it is what says whether the server kept it: a
     * full disk shows there, so a copy that "finished" without a clean close has failed.
     */
    private static final class Writer extends OutputStream {
        private final RemotePath path;
        private final Sftp session;
        private final byte[] handle;
        private final byte[] buf = new byte[Sftp.CHUNK];
        private int used;
        private long offset;
        private boolean closed;

        Writer(RemotePath path, int flags) throws IOException {
            this.path = path;
            Sftp[] s = new Sftp[1];
            this.handle = path.getFileSystem().change(x -> {
                s[0] = x;
                return x.open(path.wire(), flags);
            });
            this.session = s[0];
        }

        @Override
        public void write(int b) throws IOException {
            if (used == buf.length) {
                flush();
            }
            buf[used++] = (byte) b;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (used == buf.length) {
                    flush();
                }
                int n = Math.min(len, buf.length - used);
                System.arraycopy(b, off, buf, used, n);
                used += n;
                off += n;
                len -= n;
            }
        }

        @Override
        public void flush() throws IOException {
            if (used > 0) {
                session.write(handle, offset, buf, 0, used);
                offset += used;
                used = 0;
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                flush();
            } catch (IOException e) {
                session.release(handle);
                throw e;
            }
            session.finish(handle);
            path.getFileSystem().change(x -> null);   // the file's size and time have changed since anything listed it
        }
    }

    /**
     * Without options, as {@code Files} means it: create or truncate. {@code CREATE_NEW} is what a copy asks for, and
     * a name already there is {@link FileAlreadyExistsException}, so nothing is overwritten by accident.
     */
    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        RemotePath p = remote(path);
        Set<OpenOption> o = Set.of(options);
        if (o.contains(StandardOpenOption.APPEND) || o.contains(StandardOpenOption.READ)) {
            throw new UnsupportedOperationException("appending to or reading from a remote file being written");
        }
        int flags = Sftp.OPEN_WRITE;
        if (o.isEmpty()) {
            flags |= Sftp.OPEN_CREATE | Sftp.OPEN_TRUNCATE;
        } else {
            if (o.contains(StandardOpenOption.CREATE_NEW)) {
                flags |= Sftp.OPEN_CREATE | Sftp.OPEN_EXCLUSIVE;
            } else if (o.contains(StandardOpenOption.CREATE)) {
                flags |= Sftp.OPEN_CREATE;
            }
            if (o.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
                flags |= Sftp.OPEN_TRUNCATE;
            }
        }
        try {
            return new Writer(p, flags);
        } catch (Sftp.Refused e) {
            if ((flags & Sftp.OPEN_EXCLUSIVE) != 0 && exists(p)) {
                throw new FileAlreadyExistsException(p.toString());
            }
            throw refusal(e, p);
        }
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        RemotePath d = remote(dir);
        try {
            d.getFileSystem().change(s -> {
                s.mkdir(d.wire());
                return null;
            });
        } catch (Sftp.Refused e) {
            if (exists(d)) {
                throw new FileAlreadyExistsException(d.toString());
            }
            throw refusal(e, d);
        }
    }

    /** A file, a link or an empty folder; a folder with anything in it is refused, as {@code Files.delete} promises. */
    @Override
    public void delete(Path path) throws IOException {
        RemotePath p = remote(path);
        boolean folder = stat(p, false).directory();
        try {
            p.getFileSystem().change(s -> {
                if (folder) {
                    s.rmdir(p.wire());
                } else {
                    s.remove(p.wire());
                }
                return null;
            });
        } catch (Sftp.Refused e) {
            if (folder && e.code() != Sftp.NO_SUCH_FILE && e.code() != Sftp.PERMISSION_DENIED) {
                throw new DirectoryNotEmptyException(p.toString());
            }
            throw refusal(e, p);
        }
    }

    /**
     * Copy one file, or make one folder, as {@code Files.copy} does: through this computer, since SFTP version 3 has
     * no copy of its own. Between two hosts too, which is why either side may be any remote path.
     */
    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        RemotePath from = remote(source);
        RemotePath to = remote(target);
        Set<CopyOption> o = Set.of(options);
        Sftp.Attrs a = stat(from, true);
        if (exists(to)) {
            if (!o.contains(StandardCopyOption.REPLACE_EXISTING)) {
                throw new FileAlreadyExistsException(to.toString());
            }
            delete(to);
        }
        if (a.directory()) {
            createDirectory(to);
        } else {
            try (InputStream in = newInputStream(from); OutputStream out = newOutputStream(to,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                in.transferTo(out);
            }
        }
        if (o.contains(StandardCopyOption.COPY_ATTRIBUTES)) {
            setTimes(to, FileTime.from(a.modified(), TimeUnit.SECONDS));
        }
    }

    /**
     * On one host, {@code RENAME}: atomic, and refused rather than overwriting. Between hosts, a copy and then a
     * delete, which is not atomic and says so when atomic is asked for.
     */
    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        RemotePath from = remote(source);
        RemotePath to = remote(target);
        Set<CopyOption> o = Set.of(options);
        if (from.getFileSystem() != to.getFileSystem()) {
            if (o.contains(StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "another host");
            }
            if (stat(from, false).directory()) {
                throw new IOException("a folder moves between hosts by copying what is in it");
            }
            copy(from, to, StandardCopyOption.COPY_ATTRIBUTES);
            delete(from);
            return;
        }
        if (exists(to)) {
            if (!o.contains(StandardCopyOption.REPLACE_EXISTING) || o.contains(StandardCopyOption.ATOMIC_MOVE)) {
                throw new FileAlreadyExistsException(to.toString());
            }
            delete(to);
        }
        try {
            from.getFileSystem().change(s -> {
                s.rename(from.wire(), to.wire());
                return null;
            });
        } catch (Sftp.Refused e) {
            throw refusal(e, from);
        }
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        String name = attribute.startsWith("basic:") ? attribute.substring(6) : attribute;
        if (!name.equals("lastModifiedTime") || !(value instanceof FileTime t)) {
            throw new UnsupportedOperationException(attribute + " on a remote file");
        }
        setTimes(remote(path), t);
    }

    /** Both times to {@code modified}: SFTP sets the two together, and nothing here reads the access time. */
    private static void setTimes(RemotePath p, FileTime modified) throws IOException {
        long seconds = modified.to(TimeUnit.SECONDS);
        try {
            p.getFileSystem().change(s -> {
                s.times(p.wire(), seconds, seconds);
                return null;
            });
        } catch (Sftp.Refused e) {
            throw refusal(e, p);
        }
    }

    private static boolean exists(RemotePath p) {
        try {
            stat(p, false);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** The server's no, as the exception {@code Files} would have thrown for the same no on a disk. */
    private static IOException refusal(Sftp.Refused e, RemotePath p) {
        return switch (e.code()) {
            case Sftp.NO_SUCH_FILE -> new NoSuchFileException(p.toString());
            case Sftp.PERMISSION_DENIED -> new AccessDeniedException(p.toString(), null, e.getMessage());
            default -> new IOException(p + ": " + e.getMessage(), e);
        };
    }

    @Override
    public FileStore getFileStore(Path path) {
        throw new UnsupportedOperationException("a remote folder has no file store here");
    }

    private static RemotePath remote(Path p) {
        if (!(p instanceof RemotePath r)) {
            throw new ProviderMismatchException();
        }
        return r;
    }
}
