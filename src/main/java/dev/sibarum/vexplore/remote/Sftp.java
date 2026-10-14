package dev.sibarum.vexplore.remote;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The SFTP protocol, version 3, spoken over a pair of streams: the client half of what OpenSSH's {@code sftp} is.
 *
 * <p><b>Vexplore does not do SSH itself.</b> It starts the user's own {@code ssh} with {@code -s sftp} and talks to
 * the subsystem over its standard input and output, the way {@code Git} runs the {@code git} executable. So
 * authentication is whatever already works at a prompt: {@code ~/.ssh/config}, the agent, keys, known hosts, jump
 * hosts. {@code BatchMode=yes} means nothing ever asks for a password; a host that would ask is a host that refuses,
 * and the refusal's words (ssh's standard error) are what {@link #start} throws.
 *
 * <p>What is spoken is what a file browser needs: {@code REALPATH}, {@code STAT}, {@code LSTAT}, a directory's names
 * and a file's bytes, and to change things, writing a file, {@code MKDIR}, {@code RENAME}, {@code REMOVE},
 * {@code RMDIR} and setting times. One request is outstanding at a time, so every method is synchronized; the
 * callers are the offload lane, where blocking is allowed. A broken pipe marks the session {@link #dead}, and the next caller starts another.
 */
public final class Sftp implements Closeable {

    private static final int INIT = 1;
    private static final int VERSION = 2;
    private static final int OPEN = 3;
    private static final int CLOSE = 4;
    private static final int READ = 5;
    private static final int WRITE = 6;
    private static final int LSTAT = 7;
    private static final int SETSTAT = 9;
    private static final int OPENDIR = 11;
    private static final int READDIR = 12;
    private static final int REMOVE = 13;
    private static final int MKDIR = 14;
    private static final int RMDIR = 15;
    private static final int REALPATH = 16;
    private static final int STAT = 17;
    private static final int RENAME = 18;
    private static final int STATUS = 101;
    private static final int HANDLE = 102;
    private static final int DATA = 103;
    private static final int NAME = 104;
    private static final int ATTRS = 105;

    static final int OK = 0;
    static final int EOF = 1;
    static final int NO_SUCH_FILE = 2;
    static final int PERMISSION_DENIED = 3;

    /** {@code SSH_FXF_*}: how {@link #open(String, int)} opens a file. */
    static final int OPEN_READ = 0x01;
    static final int OPEN_WRITE = 0x02;
    static final int OPEN_CREATE = 0x08;
    static final int OPEN_TRUNCATE = 0x10;
    static final int OPEN_EXCLUSIVE = 0x20;

    private static final int FLAG_SIZE = 0x1;
    private static final int FLAG_UIDGID = 0x2;
    private static final int FLAG_PERMISSIONS = 0x4;
    private static final int FLAG_ACMODTIME = 0x8;
    private static final int FLAG_EXTENDED = 0x80000000;

    /** The most one READ asks for. OpenSSH serves more, but 32 KiB is what every server must. */
    static final int CHUNK = 32 * 1024;

    /** A reply larger than this is a stream out of step, not a reply. */
    private static final int MAX_PACKET = 1 << 24;

    /**
     * What a file is, as far as SFTP says.
     *
     * @param size        bytes, or -1 when the server did not say
     * @param permissions the mode bits including the file type, or 0 when the server did not say
     * @param modified    seconds since the epoch, or 0 when the server did not say
     */
    public record Attrs(long size, int permissions, long modified) {

        public boolean directory() {
            return (permissions & 0xF000) == 0x4000;
        }

        public boolean link() {
            return (permissions & 0xF000) == 0xA000;
        }

        public boolean regular() {
            return (permissions & 0xF000) == 0x8000;
        }
    }

    /** One name in a directory, with its attributes as the listing gave them (a link's are the link's own). */
    public record Item(String name, Attrs attrs) {
    }

    /** The server said no, with a status code and its own words. */
    public static final class Refused extends IOException {
        private final int code;

        Refused(int code, String message) {
            super(message == null || message.isBlank() ? "status " + code : message);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    private final DataInputStream in;
    private final DataOutputStream out;
    private final Closeable owner;
    private int nextId = 1;
    private volatile boolean dead;

    private Sftp(InputStream in, OutputStream out, Closeable owner) {
        this.in = new DataInputStream(in);
        this.out = new DataOutputStream(out);
        this.owner = owner;
    }

    /** Speak SFTP over {@code in} and {@code out}, which belong to {@code owner}; closing this closes it. */
    public static Sftp over(InputStream in, OutputStream out, Closeable owner) throws IOException {
        Sftp s = new Sftp(in, out, owner);
        s.handshake();
        return s;
    }

    /**
     * Start {@code command}, which must leave an SFTP server on the other end of its standard streams, and shake
     * hands. If it dies first, the exception says what it printed: "Permission denied (publickey)." is the
     * answer a person can act on.
     */
    public static Sftp start(List<String> command) throws IOException {
        Process p = new ProcessBuilder(command).redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE).start();
        StringBuilder err = new StringBuilder();
        Thread drain = Thread.ofVirtual().start(() -> {
            try (InputStream e = p.getErrorStream()) {
                byte[] buf = new byte[1024];
                for (int n; (n = e.read(buf)) > 0; ) {
                    synchronized (err) {
                        if (err.length() < 4096) {
                            err.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (IOException ignored) {
                // The process went away; whatever it said is in err already.
            }
        });
        try {
            return over(p.getInputStream(), p.getOutputStream(), () -> {
                p.destroy();
                drain.interrupt();
            });
        } catch (IOException e) {
            p.destroy();
            try {
                p.waitFor(2, TimeUnit.SECONDS);
                drain.join(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            String said;
            synchronized (err) {
                said = err.toString().strip();
            }
            throw new IOException(said.isEmpty() ? "the connection closed before SFTP started" : lastLine(said), e);
        }
    }

    /** Whether the stream has failed; a dead session answers nothing and is replaced. */
    public boolean dead() {
        return dead;
    }

    // ------------------------------------------------------------------ requests

    /** The absolute, canonical form of {@code path}; {@code "."} is the directory the server started in. */
    public synchronized String realpath(String path) throws IOException {
        ByteBuffer r = call(REALPATH, b -> string(b, path), NAME);
        int count = r.getInt();
        if (count < 1) {
            throw new IOException("no name for " + path);
        }
        return string(r);
    }

    /** {@code path}'s attributes, following a link. */
    public synchronized Attrs stat(String path) throws IOException {
        return attrs(call(STAT, b -> string(b, path), ATTRS));
    }

    /** {@code path}'s attributes, a link's own. */
    public synchronized Attrs lstat(String path) throws IOException {
        return attrs(call(LSTAT, b -> string(b, path), ATTRS));
    }

    /** Everything directly in {@code dir}, without {@code .} and {@code ..}, in the server's order. */
    public synchronized List<Item> list(String dir) throws IOException {
        byte[] handle = handle(call(OPENDIR, b -> string(b, dir), HANDLE));
        List<Item> out = new ArrayList<>();
        try {
            while (true) {
                ByteBuffer r;
                try {
                    r = call(READDIR, b -> bytes(b, handle), NAME);
                } catch (Refused e) {
                    if (e.code() == EOF) {
                        break;
                    }
                    throw e;
                }
                int count = r.getInt();
                for (int i = 0; i < count; i++) {
                    String name = string(r);
                    string(r);   // the long name, ls -l style: nothing here reads it
                    Attrs a = attrs(r);
                    if (!name.equals(".") && !name.equals("..")) {
                        out.add(new Item(name, a));
                    }
                }
            }
        } finally {
            release(handle);
        }
        return out;
    }

    /** Open {@code path} for reading. Close the handle with {@link #release}. */
    public synchronized byte[] open(String path) throws IOException {
        return open(path, OPEN_READ);
    }

    /**
     * Open {@code path} with {@code flags} ({@link #OPEN_WRITE} and the rest). A handle that was written through is
     * closed with {@link #finish}, which says whether the server kept what was written.
     */
    public synchronized byte[] open(String path, int flags) throws IOException {
        return handle(call(OPEN, b -> {
            string(b, path);
            b.writeInt(flags);
            b.writeInt(0);   // no attributes: the server's default mode, as a new file from a shell would get
        }, HANDLE));
    }

    /** Write {@code length} bytes of {@code data} from {@code from} into an open file at {@code offset}. */
    public synchronized void write(byte[] handle, long offset, byte[] data, int from, int length) throws IOException {
        call(WRITE, b -> {
            bytes(b, handle);
            b.writeLong(offset);
            b.writeInt(length);
            b.write(data, from, length);
        }, STATUS);
    }

    /** Close a handle written through, and fail if the server could not keep what was written. */
    public synchronized void finish(byte[] handle) throws IOException {
        call(CLOSE, b -> bytes(b, handle), STATUS);
    }

    /** Remove the file {@code path}. */
    public synchronized void remove(String path) throws IOException {
        call(REMOVE, b -> string(b, path), STATUS);
    }

    /** Make the folder {@code path}; its parent must exist. */
    public synchronized void mkdir(String path) throws IOException {
        call(MKDIR, b -> {
            string(b, path);
            b.writeInt(0);
        }, STATUS);
    }

    /** Remove the empty folder {@code path}. */
    public synchronized void rmdir(String path) throws IOException {
        call(RMDIR, b -> string(b, path), STATUS);
    }

    /** Rename {@code from} to {@code to}. Version 3 refuses when {@code to} exists, so nothing is overwritten. */
    public synchronized void rename(String from, String to) throws IOException {
        call(RENAME, b -> {
            string(b, from);
            string(b, to);
        }, STATUS);
    }

    /** Set {@code path}'s access and modification times, in seconds since the epoch. */
    public synchronized void times(String path, long accessed, long modified) throws IOException {
        call(SETSTAT, b -> {
            string(b, path);
            b.writeInt(FLAG_ACMODTIME);
            b.writeInt((int) accessed);
            b.writeInt((int) modified);
        }, STATUS);
    }

    /**
     * Up to {@code length} bytes of an open file from {@code offset}, or null at the end. Fewer than asked is not
     * the end: a server may cap a read, and the caller asks again from where this one stopped.
     */
    public synchronized byte[] read(byte[] handle, long offset, int length) throws IOException {
        try {
            ByteBuffer r = call(READ, b -> {
                bytes(b, handle);
                b.writeLong(offset);
                b.writeInt(length);
            }, DATA);
            byte[] data = new byte[r.getInt()];
            r.get(data);
            return data;
        } catch (Refused e) {
            if (e.code() == EOF) {
                return null;
            }
            throw e;
        }
    }

    /** Let go of a handle. Quietly: a handle that cannot be closed is one the server forgets when the session ends. */
    public synchronized void release(byte[] handle) {
        try {
            call(CLOSE, b -> bytes(b, handle), STATUS);
        } catch (IOException ignored) {
            // See above.
        }
    }

    @Override
    public void close() throws IOException {
        dead = true;
        owner.close();
    }

    // ------------------------------------------------------------------ the wire

    private interface Body {
        void write(DataOutputStream b) throws IOException;
    }

    private void handshake() throws IOException {
        try {
            out.writeInt(5);
            out.writeByte(INIT);
            out.writeInt(3);
            out.flush();
            int length = in.readInt();
            if (length < 5 || length > MAX_PACKET) {
                throw new IOException("not an SFTP server (a reply of " + length + " bytes)");
            }
            int type = in.readUnsignedByte();
            int version = in.readInt();
            in.skipNBytes(length - 5);   // extensions, which nothing here uses
            if (type != VERSION || version < 3) {
                throw new IOException("not an SFTP version 3 server");
            }
        } catch (IOException e) {
            dead = true;
            throw e;
        }
    }

    /**
     * Send one request and read its reply. The reply is {@code expect}'s payload after the id; a STATUS in its place
     * is thrown as {@link Refused}, except an OK when a STATUS was what was expected.
     */
    private ByteBuffer call(int type, Body body, int expect) throws IOException {
        if (dead) {
            throw new IOException("the connection is closed");
        }
        int id = nextId++;
        try {
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            DataOutputStream b = new DataOutputStream(packet);
            b.writeByte(type);
            b.writeInt(id);
            body.write(b);
            out.writeInt(packet.size());
            packet.writeTo(out);
            out.flush();

            int length = in.readInt();
            if (length < 5 || length > MAX_PACKET) {
                throw new IOException("a reply of " + length + " bytes");
            }
            byte[] reply = new byte[length];
            in.readFully(reply);
            ByteBuffer r = ByteBuffer.wrap(reply);
            int got = r.get() & 0xFF;
            int replyId = r.getInt();
            if (replyId != id) {
                throw new IOException("a reply to request " + replyId + " while waiting for " + id);
            }
            if (got == STATUS) {
                int code = r.getInt();
                String message = r.remaining() >= 4 ? string(r) : "";
                if (code == OK && expect == STATUS) {
                    return r;
                }
                throw new Refused(code, message);
            }
            if (got != expect) {
                throw new IOException("reply type " + got + " where " + expect + " was expected");
            }
            return r;
        } catch (Refused e) {
            throw e;
        } catch (EOFException e) {
            dead = true;
            throw new IOException("the connection closed", e);
        } catch (IOException | RuntimeException e) {
            // Anything else leaves the stream at an unknown place in a packet: nothing after it can be trusted.
            dead = true;
            throw e instanceof IOException io ? io : new IOException(e);
        }
    }

    private static Attrs attrs(ByteBuffer r) {
        int flags = r.getInt();
        long size = (flags & FLAG_SIZE) != 0 ? r.getLong() : -1L;
        if ((flags & FLAG_UIDGID) != 0) {
            r.getInt();
            r.getInt();
        }
        int permissions = (flags & FLAG_PERMISSIONS) != 0 ? r.getInt() : 0;
        long modified = 0L;
        if ((flags & FLAG_ACMODTIME) != 0) {
            r.getInt();   // access time
            modified = Integer.toUnsignedLong(r.getInt());
        }
        if ((flags & FLAG_EXTENDED) != 0) {
            int n = r.getInt();
            for (int i = 0; i < n; i++) {
                string(r);
                string(r);
            }
        }
        return new Attrs(size, permissions, modified);
    }

    private static byte[] handle(ByteBuffer r) {
        byte[] h = new byte[r.getInt()];
        r.get(h);
        return h;
    }

    private static String string(ByteBuffer r) {
        byte[] s = new byte[r.getInt()];
        r.get(s);
        return new String(s, StandardCharsets.UTF_8);
    }

    private static void string(DataOutputStream b, String s) throws IOException {
        bytes(b, s.getBytes(StandardCharsets.UTF_8));
    }

    private static void bytes(DataOutputStream b, byte[] s) throws IOException {
        b.writeInt(s.length);
        b.write(s);
    }

    private static String lastLine(String s) {
        String[] lines = s.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) {
                return lines[i].strip();
            }
        }
        return s;
    }
}
