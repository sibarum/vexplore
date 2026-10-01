package dev.sibarum.vexplore.ops;

import dev.sibarum.vexplore.ops.Operations.Done;
import dev.sibarum.vexplore.ops.Plan.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real files in a temporary folder: every action does what its plan said, and every one can be taken back. */
class OperationsTest {

    private static Path file(Path dir, String name, String text) throws IOException {
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(name), text);
    }

    private static Plan plan(Kind k, List<Path> targets, Path dest, Set<String> existing) throws IOException {
        java.util.ArrayList<Long> sizes = new java.util.ArrayList<>();
        for (Path p : targets) {
            sizes.add(Files.size(p));
        }
        return Plan.of(k, targets, sizes, dest, existing, null);
    }

    @Test
    void movingGoesAndUndoingComesBack(@TempDir Path root) throws IOException {
        Path a = file(root.resolve("in"), "a.txt", "aaa");
        Path b = file(root.resolve("in"), "b.txt", "bb");
        Path out = Files.createDirectories(root.resolve("out"));

        Done d = Operations.execute(plan(Kind.MOVE, List.of(a, b), out, Set.of()), root.resolve("trash"), n -> { });

        assertEquals(2, d.count());
        assertFalse(Files.exists(a));
        assertEquals("aaa", Files.readString(out.resolve("a.txt")));

        assertEquals(List.of(), Operations.undo(d));
        assertEquals("aaa", Files.readString(a));
        assertFalse(Files.exists(out.resolve("a.txt")));
    }

    @Test
    void copyingLeavesTheOriginalsAndUndoRemovesOnlyTheCopies(@TempDir Path root) throws IOException {
        Path a = file(root.resolve("in"), "a.txt", "aaa");
        Path out = Files.createDirectories(root.resolve("out"));
        Done d = Operations.execute(plan(Kind.COPY, List.of(a), out, Set.of()), root.resolve("t"), n -> { });

        assertTrue(Files.exists(a) && Files.exists(out.resolve("a.txt")));
        Operations.undo(d);
        assertTrue(Files.exists(a), "the original is never touched by an undo of a copy");
        assertFalse(Files.exists(out.resolve("a.txt")));
    }

    @Test
    void aFolderIsCopiedWithEverythingInIt(@TempDir Path root) throws IOException {
        Path dir = root.resolve("in/proj");
        file(dir.resolve("sub"), "x.txt", "x");
        file(dir, "y.txt", "y");
        Path out = Files.createDirectories(root.resolve("out"));

        Operations.execute(plan(Kind.COPY, List.of(dir), out, Set.of()), root.resolve("t"), n -> { });

        assertEquals("x", Files.readString(out.resolve("proj/sub/x.txt")));
        assertEquals("y", Files.readString(out.resolve("proj/y.txt")));
    }

    @Test
    void deletingIsRecoverable(@TempDir Path root) throws IOException {
        Path a = file(root.resolve("in"), "a.txt", "keep me");
        Path trash = root.resolve("trash");

        Done d = Operations.execute(plan(Kind.DELETE, List.of(a), null, Set.of()), trash, n -> { });

        assertFalse(Files.exists(a), "it left the folder");
        assertTrue(Files.exists(d.done().get(0).to()), "and is in the trash, not gone");
        assertEquals(List.of(), Operations.undo(d));
        assertEquals("keep me", Files.readString(a));
    }

    @Test
    void twoDeletedFilesWithTheSameNameDoNotCollideInTheTrash(@TempDir Path root) throws IOException {
        Path a = file(root.resolve("one"), "same.txt", "1");
        Path b = file(root.resolve("two"), "same.txt", "2");
        Done d = Operations.execute(plan(Kind.DELETE, List.of(a, b), null, Set.of()), root.resolve("t"), n -> { });
        assertEquals(2, d.count());
        assertEquals(List.of(), Operations.undo(d));
        assertEquals("1", Files.readString(a));
        assertEquals("2", Files.readString(b));
    }

    @Test
    void theNamesThatWouldCollideAreSkippedInThePlanAndNothingIsOverwritten(@TempDir Path root) throws IOException {
        Path a = file(root.resolve("in"), "a.txt", "new");
        Path b = file(root.resolve("in"), "b.txt", "bb");
        Path out = root.resolve("out");
        file(out, "a.txt", "old");

        Plan p = plan(Kind.MOVE, List.of(a, b), out, Set.of("a.txt"));
        assertEquals(1, p.count());
        assertEquals(1, p.skipped().size());
        assertTrue(p.skipped().get(0).reason().contains("already"));

        Operations.execute(p, root.resolve("t"), n -> { });
        assertEquals("old", Files.readString(out.resolve("a.txt")), "the file that was there is untouched");
        assertEquals("new", Files.readString(a), "and the one that was skipped never moved");
    }

    @Test
    void aSurpriseAtExecutionIsReportedAndTheRestCarryOn(@TempDir Path root) throws IOException {
        Path a = file(root.resolve("in"), "a.txt", "a");
        Path b = file(root.resolve("in"), "b.txt", "b");
        Path out = Files.createDirectories(root.resolve("out"));
        Plan p = plan(Kind.MOVE, List.of(a, b), out, Set.of());
        file(out, "a.txt", "appeared after the plan was made");

        Done d = Operations.execute(p, root.resolve("t"), n -> { });

        assertEquals(1, d.count());
        assertEquals(1, d.failed().size());
        assertTrue(Files.exists(a), "the failed one is still where it was");
    }

    @Test
    void aFolderCannotBeMovedIntoItself(@TempDir Path root) throws IOException {
        Path dir = Files.createDirectories(root.resolve("a"));
        Path inside = Files.createDirectories(dir.resolve("b"));
        Plan p = Plan.of(Kind.MOVE, List.of(dir), List.of(0L), inside, Set.of(), null);
        assertEquals(0, p.count());
        assertTrue(p.skipped().get(0).reason().contains("inside"));
    }

    @Test
    void archivingWritesAZipAndUndoRemovesIt(@TempDir Path root) throws IOException {
        Path a = file(root.resolve("in"), "a.txt", "aaa");
        Path d = root.resolve("in/dir");
        file(d, "n.txt", "nested");
        Path zip = root.resolve("in/Archive.zip");
        Plan p = Plan.of(Kind.ARCHIVE, List.of(a, d), List.of(3L, 6L), null, Set.of(), zip);

        Done done = Operations.execute(p, root.resolve("t"), n -> { });

        assertEquals(2, done.count());
        try (ZipFile z = new ZipFile(zip.toFile())) {
            assertTrue(z.getEntry("a.txt") != null);
            assertTrue(z.getEntry("dir/n.txt") != null, "folders keep their structure");
        }
        Operations.undo(done);
        assertFalse(Files.exists(zip));
        assertTrue(Files.exists(a), "archiving never removes what it archived");
    }

    @Test
    void progressIsReportedAfterEachStep(@TempDir Path root) throws IOException {
        Path a = file(root.resolve("in"), "a", "1");
        Path b = file(root.resolve("in"), "b", "2");
        Path out = Files.createDirectories(root.resolve("out"));
        java.util.ArrayList<Integer> seen = new java.util.ArrayList<>();
        Operations.execute(plan(Kind.COPY, List.of(a, b), out, Set.of()), root.resolve("t"), seen::add);
        assertEquals(List.of(1, 2), seen);
    }
}
