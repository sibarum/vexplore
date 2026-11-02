package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Plan.Op;
import dev.sibarum.vexplore.core.Plan.Step;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

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
 * <p>Blocking: run by {@link RunAction}, on a source's thread.
 */
public final class Operations {

    private Operations() {
    }

    /**
     * Do the plan. A delete goes into a new folder of {@code trash}, named by the time; {@code progress} hears how many
     * steps are finished, after each one.
     */
    public static Done execute(Plan plan, Path trash, IntConsumer progress) {
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
                carry(plan.op(), s.from(), to);
                done.add(new Step(s.from(), to));
            } catch (IOException | RuntimeException e) {
                failed.add(ListFolder.name(s.from()) + ": " + reason(e));
            }
            progress.accept(++n);
        }
        return new Done(plan.op(), done, failed, plan.bytes(), stopped);
    }

    /** One step. A remove takes away {@code to}, the copy; everything else puts {@code from} at {@code to}. */
    private static void carry(Op op, Path from, Path to) throws IOException {
        if (op == Op.REMOVE) {
            deleteTree(to);
        } else if (op == Op.COPY) {
            copyTree(from, to);
        } else {
            moveTree(from, to);
        }
    }

    // ------------------------------------------------------------------ pieces

    static void moveTree(Path from, Path to) throws IOException {
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
            copyTree(from, to);
            deleteTree(from);
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
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
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
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
