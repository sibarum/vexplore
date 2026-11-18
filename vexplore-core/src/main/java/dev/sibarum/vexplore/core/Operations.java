package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Plan.Op;
import dev.sibarum.vexplore.core.Plan.Step;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Carrying out a {@link Plan}: 0.1's, with its undo turned into a plan of its own ({@link Done#reverse}).
 *
 * <p><b>Recoverable by default.</b> The design says destructive actions are recoverable, so a delete moves into a
 * trash this application owns and its undo moves it back. The system's recycle bin is out of reach: the only route to
 * it from Java goes through AWT, which nothing in this stack links, and a trash of its own is the same on every
 * system. Only {@link Op#REMOVE} deletes outright, and it is only ever the undo of a copy.
 *
 * <p><b>Nothing is overwritten.</b> A step whose destination is taken, or appeared since the plan was made, is
 * recorded as failed and the rest carry on, so one surprise never turns nine files into a half-done operation with no
 * account of it.
 *
 * <p><b>A Stop ends it between steps.</b> An interrupt of the running thread is read before each step, and what was
 * done by then is the answer, so a stopped run can still be undone. A step under way finishes.
 *
 * <p><b>It is measured in bytes.</b> A listed folder has no size, so a run first walks what its steps will touch and
 * adds up their files ({@link Meter} hears a size below 0 until then; a Stop while counting does nothing), then says
 * how many bytes are carried as it goes: after each file of a copy or an archive, and every {@link #CHUNK} of a large
 * one. A rename on one volume carries nothing byte by byte, so its step's bytes all count when it is done.
 *
 * <p>Blocking: run by {@link RunAction}, on a source's thread.
 */
public final class Operations {

    /** A file this large or larger is copied a chunk at a time, so the bar moves while it goes. */
    static final long CHUNK = 8L << 20;

    private Operations() {
    }

    /** Hears how far a run has got. */
    @FunctionalInterface
    public interface Meter {

        /**
         * {@code steps} of the plan are finished and {@code bytes} of {@code size} carried; a {@code size} below 0 is
         * still being counted.
         */
        void at(int steps, long bytes, long size);
    }

    /**
     * Do the plan. A delete goes into a new folder of {@code trash}, named by the time; {@code meter} hears the size once
     * it is counted, and how far the run has got as it goes.
     */
    public static Done execute(Plan plan, Path trash, Meter meter) {
        meter.at(0, 0L, -1L);
        long[] through = through(plan);
        if (through == null) {
            return new Done(plan.op(), List.of(), List.of(), plan.bytes(), true);
        }
        Gauge gauge = new Gauge(meter, through.length == 0 ? 0L : through[through.length - 1]);
        if (plan.op() == Op.ARCHIVE) {
            return zip(plan, gauge, through);
        }
        List<Step> done = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Path bin = trash.resolve(Long.toString(System.currentTimeMillis()));
        int n = 0;
        boolean stopped = false;
        for (Step s : plan.steps()) {
            if (Thread.currentThread().isInterrupted()) {
                stopped = true;
                break;
            }
            try {
                Path to = s.to();
                if (to == null) {
                    Files.createDirectories(bin);
                    to = uniqueIn(bin, s.from());
                }
                carry(plan.op(), s.from(), to, gauge);
                done.add(new Step(s.from(), to));
            } catch (IOException | RuntimeException e) {
                failed.add(ListFolder.name(s.from()) + ": " + reason(e));
            }
            n++;
            gauge.step(n, through[n - 1]);
        }
        return new Done(plan.op(), done, failed, plan.bytes(), stopped);
    }

    /**
     * The bytes the plan's steps touch, as a running total: {@code through[i]} is the size of steps 0 to i. What a step
     * cannot read counts as nothing. Null when a Stop came while counting.
     */
    private static long[] through(Plan plan) {
        long[] through = new long[plan.steps().size()];
        long total = 0L;
        for (int i = 0; i < through.length; i++) {
            Step s = plan.steps().get(i);
            Path touched = plan.op() == Op.REMOVE || plan.op() == Op.UNARCHIVE ? s.to() : s.from();
            long size = size(touched);
            if (size < 0L) {
                return null;
            }
            total += size;
            through[i] = total;
        }
        return through;
    }

    /** The size of the files under {@code p}, or of {@code p}; 0 when it is not there; -1 when a Stop came. */
    static long size(Path p) {
        if (Thread.currentThread().isInterrupted()) {
            return -1L;
        }
        long[] sum = {0L};
        try {
            Files.walkFileTree(p, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                    sum[0] += a.size();
                    return Thread.currentThread().isInterrupted() ? FileVisitResult.TERMINATE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            return sum[0];
        }
        return Thread.currentThread().isInterrupted() ? -1L : sum[0];
    }

    /**
     * How far a run has got, said to its {@link Meter}: at the end of each step, and as bytes are carried, each time
     * they have moved on a thousandth of the whole since last said.
     */
    private static final class Gauge {

        private final Meter meter;
        private final long size;
        private final long every;
        private int steps;
        private long bytes;
        private long said;

        Gauge(Meter meter, long size) {
            this.meter = meter;
            this.size = size;
            this.every = Math.max(1L, size / 1000L);
            meter.at(0, 0L, size);
        }

        void carried(long n) {
            bytes += n;
            if (bytes - said >= every) {
                said = bytes;
                meter.at(steps, bytes, size);
            }
        }

        /** Step {@code n} is over: the bytes are those of the steps through it, whatever it carried. */
        void step(int n, long through) {
            steps = n;
            bytes = through;
            said = through;
            meter.at(n, through, size);
        }
    }

    /**
     * One step. A remove or an unarchive takes away {@code to}, the copy or the zip (the zip once: the steps after the
     * first find it gone); everything else puts {@code from} at {@code to}.
     */
    private static void carry(Op op, Path from, Path to, Gauge gauge) throws IOException {
        if (op == Op.REMOVE || op == Op.UNARCHIVE) {
            deleteTree(to);
        } else if (op == Op.COPY) {
            copyTree(from, to, gauge);
        } else {
            moveTree(from, to, gauge);
        }
    }

    /**
     * Every step's source into the one new zip the steps name: 0.1's. Never over a zip already there; a source that
     * cannot be read is left out and said, and a Stop ends it between files, the zip holding what went in by then.
     */
    private static Done zip(Plan plan, Gauge gauge, long[] through) {
        List<Step> done = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        if (plan.steps().isEmpty()) {
            return new Done(Op.ARCHIVE, done, failed, 0L, false);
        }
        Path zip = plan.steps().getFirst().to();
        if (Files.exists(zip)) {
            failed.add(ListFolder.name(zip) + ": something is already at " + zip);
            return new Done(Op.ARCHIVE, done, failed, plan.bytes(), false);
        }
        int n = 0;
        boolean stopped = false;
        try (OutputStream out = Files.newOutputStream(zip, StandardOpenOption.CREATE_NEW);
             ZipOutputStream z = new ZipOutputStream(out)) {
            for (Step s : plan.steps()) {
                if (Thread.currentThread().isInterrupted()) {
                    stopped = true;
                    break;
                }
                try {
                    addToZip(z, s.from(), ListFolder.name(s.from()), gauge);
                    done.add(s);
                } catch (IOException | RuntimeException e) {
                    failed.add(ListFolder.name(s.from()) + ": " + reason(e));
                }
                n++;
                gauge.step(n, through[n - 1]);
            }
        } catch (IOException | RuntimeException e) {
            failed.add(ListFolder.name(zip) + ": " + reason(e));
        }
        return new Done(Op.ARCHIVE, done, failed, plan.bytes(), stopped);
    }

    private static void addToZip(ZipOutputStream z, Path source, String entry, Gauge gauge) throws IOException {
        if (!Files.isDirectory(source)) {
            z.putNextEntry(new ZipEntry(entry));
            pour(source, z, gauge);
            z.closeEntry();
            return;
        }
        try (var walk = Files.walk(source)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                String rel = entry + "/" + source.relativize(p).toString().replace('\\', '/');
                if (Files.isDirectory(p)) {
                    z.putNextEntry(new ZipEntry(rel.endsWith("/") ? rel : rel + "/"));
                } else {
                    z.putNextEntry(new ZipEntry(rel));
                    pour(p, z, gauge);
                }
                z.closeEntry();
            }
        }
    }

    /** {@code file} into {@code out}, telling {@code gauge} as it goes. */
    private static void pour(Path file, OutputStream out, Gauge gauge) throws IOException {
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(file)) {
            for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
                out.write(buffer, 0, n);
                gauge.carried(n);
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    private static void moveTree(Path from, Path to, Gauge gauge) throws IOException {
        if (Files.exists(to)) {
            throw new IOException("something is already at " + to);
        }
        if (!Files.exists(from)) {
            throw new IOException("it is no longer there");
        }
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            // Another volume, or a folder the system will not rename in one go: copy, then remove what was copied.
            copyTree(from, to, gauge);
            deleteTree(from);
        }
    }

    private static void copyTree(Path from, Path to, Gauge gauge) throws IOException {
        if (Files.exists(to)) {
            throw new IOException("something is already at " + to);
        }
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                copyFile(file, to.resolve(from.relativize(file).toString()), a, gauge);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * One file, with its times. A small one in one go, as the system copies; a large one {@link #CHUNK} at a time, so
     * the gauge moves while it goes. A large copy that fails takes its half-written file away with it.
     */
    private static void copyFile(Path file, Path to, BasicFileAttributes a, Gauge gauge) throws IOException {
        if (a.size() < CHUNK) {
            Files.copy(file, to, StandardCopyOption.COPY_ATTRIBUTES);
            gauge.carried(a.size());
            return;
        }
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(to, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            long size = in.size();
            long at = 0L;
            while (at < size) {
                long n = in.transferTo(at, Math.min(CHUNK, size - at), out);
                if (n <= 0L) {
                    break;                     // it shrank while being read: what there was is copied
                }
                at += n;
                gauge.carried(n);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(to);
            throw e;
        }
        Files.getFileAttributeView(to, BasicFileAttributeView.class)
                .setTimes(a.lastModifiedTime(), a.lastAccessTime(), a.creationTime());
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        Files.walkFileTree(p, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** A place in the trash's folder for {@code from}: its own name, or {@code 2-name} when two share one. */
    private static Path uniqueIn(Path bin, Path from) {
        String name = ListFolder.name(from);
        Path to = bin.resolve(name);
        for (int i = 2; Files.exists(to); i++) {
            to = bin.resolve(i + "-" + name);
        }
        return to;
    }

    private static String reason(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
