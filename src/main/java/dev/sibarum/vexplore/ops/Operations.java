package dev.sibarum.vexplore.ops;

import dev.sibarum.vexplore.ops.Plan.Kind;
import dev.sibarum.vexplore.ops.Plan.Step;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Doing a {@link Plan}, and undoing it.
 *
 * <p><b>Recoverable by default.</b> The design says destructive actions are recoverable, so there is no delete
 * here: {@link Kind#DELETE} moves into a trash folder this application owns, and {@link #undo} moves it back.
 * (The operating system's recycle bin is not used because the only route to it from Java goes through AWT, which
 * nothing in this stack links; a trash of our own is also the same on every OS.) A move undoes by moving back, a
 * copy by removing the copies it made, an archive by removing the zip.
 *
 * <p>Nothing is overwritten. A step whose destination appeared since the plan was made is recorded as failed and
 * the rest carry on, so one surprise never turns nine files into a half-done operation with no account of it.
 * Blocking: run on the offload lane.
 */
public final class Operations {

    private Operations() {
    }

    /**
     * What happened: the steps that took effect, and the ones that did not and why. {@code trash} is where a
     * delete put things, kept so the undo does not have to guess.
     */
    public record Done(Kind kind, List<Step> done, List<String> failed, Path archive, long bytes) {
        public int count() {
            return done.size();
        }
    }

    /** Do the plan. {@code progress} hears the number of steps finished, after each one. */
    public static Done execute(Plan plan, Path trash, IntConsumer progress) {
        List<Step> done = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        if (plan.kind() == Kind.ARCHIVE) {
            return archive(plan, failed, progress);
        }
        Path bin = null;
        if (plan.kind() == Kind.DELETE) {
            bin = trash.resolve(Long.toString(System.currentTimeMillis()));
        }
        int n = 0;
        for (Step s : plan.steps()) {
            Path to = plan.kind() == Kind.DELETE ? uniqueIn(bin, s.from()) : s.to();
            try {
                if (plan.kind() == Kind.DELETE) {
                    Files.createDirectories(bin);
                }
                switch (plan.kind()) {
                    case COPY -> copyTree(s.from(), to);
                    case MOVE, DELETE -> moveTree(s.from(), to);
                    case ARCHIVE -> throw new IllegalStateException();
                }
                done.add(new Step(s.from(), to));
            } catch (IOException | RuntimeException e) {
                failed.add(name(s.from()) + ": " + reason(e));
            }
            progress.accept(++n);
        }
        return new Done(plan.kind(), List.copyOf(done), List.copyOf(failed), null, plan.bytes());
    }

    /** Put things back. Returns the failures; an empty list is a clean undo. */
    public static List<String> undo(Done d) {
        List<String> failed = new ArrayList<>();
        if (d.kind() == Kind.ARCHIVE) {
            try {
                Files.deleteIfExists(d.archive());
            } catch (IOException e) {
                failed.add(name(d.archive()) + ": " + reason(e));
            }
            return failed;
        }
        for (int i = d.done().size() - 1; i >= 0; i--) {
            Step s = d.done().get(i);
            try {
                if (d.kind() == Kind.COPY) {
                    deleteTree(s.to());
                } else {
                    if (Files.exists(s.from())) {
                        failed.add(name(s.from()) + ": something is already at its old place");
                        continue;
                    }
                    moveTree(s.to(), s.from());
                }
            } catch (IOException | RuntimeException e) {
                failed.add(name(s.to()) + ": " + reason(e));
            }
        }
        return failed;
    }

    // ------------------------------------------------------------------ pieces

    private static Done archive(Plan plan, List<String> failed, IntConsumer progress) {
        List<Step> done = new ArrayList<>();
        Path zip = plan.archive();
        if (Files.exists(zip)) {
            failed.add(name(zip) + ": already exists");
            return new Done(Kind.ARCHIVE, List.of(), List.copyOf(failed), zip, 0L);
        }
        int n = 0;
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(out)) {
            for (Step s : plan.steps()) {
                try {
                    addToZip(z, s.from(), name(s.from()));
                    done.add(s);
                } catch (IOException e) {
                    failed.add(name(s.from()) + ": " + reason(e));
                }
                progress.accept(++n);
            }
        } catch (IOException e) {
            failed.add(name(zip) + ": " + reason(e));
        }
        return new Done(Kind.ARCHIVE, List.copyOf(done), List.copyOf(failed), zip, plan.bytes());
    }

    private static void addToZip(ZipOutputStream z, Path source, String entry) throws IOException {
        if (Files.isDirectory(source)) {
            try (var walk = Files.walk(source)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    String rel = entry + "/" + source.relativize(p).toString().replace('\\', '/');
                    if (Files.isDirectory(p)) {
                        z.putNextEntry(new ZipEntry(rel.endsWith("/") ? rel : rel + "/"));
                        z.closeEntry();
                    } else {
                        z.putNextEntry(new ZipEntry(rel));
                        Files.copy(p, z);
                        z.closeEntry();
                    }
                }
            }
        } else {
            z.putNextEntry(new ZipEntry(entry));
            Files.copy(source, z);
            z.closeEntry();
        }
    }

    private static void moveTree(Path from, Path to) throws IOException {
        if (Files.exists(to)) {
            throw new IOException("already exists");
        }
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            // Another volume, or a folder the OS will not rename in one go: copy, then remove what was copied.
            copyTree(from, to);
            deleteTree(from);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (Files.exists(to)) {
            throw new IOException("already exists");
        }
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path p) throws IOException {
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

    private static Path uniqueIn(Path bin, Path from) {
        Path to = bin.resolve(name(from));
        for (int i = 2; Files.exists(to); i++) {
            to = bin.resolve(i + "-" + name(from));
        }
        return to;
    }

    private static String name(Path p) {
        return p.getFileName() == null ? p.toString() : p.getFileName().toString();
    }

    private static String reason(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
