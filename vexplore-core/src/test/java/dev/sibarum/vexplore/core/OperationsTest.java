package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Plan.Op;
import dev.sibarum.vexplore.core.Plan.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plans carried out against a real temporary folder, and taken back by their reverse: what the run does, with no lane.
 */
class OperationsTest {

    @TempDir
    Path dir;

    private Path file(String name, String text) throws IOException {
        return Files.writeString(dir.resolve(name), text);
    }

    private static Row row(Path p) throws IOException {
        return new Row(p, ListFolder.name(p), false, Files.size(p), Instant.EPOCH, Kind.TEXT);
    }

    private Path trash() {
        return dir.resolve("trash");
    }

    @Test
    void aDeleteGoesIntoTheTrashAndItsReverseBringsItBack() throws IOException {
        Path a = file("a.txt", "a");
        Path b = file("b.txt", "bb");
        List<Integer> counts = new ArrayList<>();
        Done gone = Operations.execute(Plan.delete(List.of(row(a), row(b))), trash(), counts::add);
        assertEquals(2, gone.count());
        assertEquals(List.of(1, 2), counts);
        assertFalse(Files.exists(a));
        assertTrue(gone.done().getFirst().to().startsWith(trash()), "kept, not deleted");

        Done back = Operations.execute(gone.reverse(), trash(), n -> { });
        assertEquals(List.of(), back.failed());
        assertEquals("a", Files.readString(a));
        assertEquals("bb", Files.readString(b));

        Done again = Operations.execute(back.reverse(), trash(), n -> { });
        assertEquals(2, again.count(), "the reverse of the undo is the redo");
        assertFalse(Files.exists(a));
    }

    @Test
    void twoFilesOfOneNameGetTwoPlacesInTheTrash() throws IOException {
        Path sub = Files.createDirectory(dir.resolve("sub"));
        Path a = file("a.txt", "1");
        Path other = Files.writeString(sub.resolve("a.txt"), "2");
        Done gone = Operations.execute(Plan.delete(List.of(row(a), row(other))), trash(), n -> { });
        assertEquals(2, gone.count());
        assertTrue(gone.done().get(1).to().getFileName().toString().startsWith("2-"));
    }

    @Test
    void anUndoNeverOverwritesWhatHasTakenTheOldPlace() throws IOException {
        Path a = file("a.txt", "old");
        Path b = file("b.txt", "b");
        Done gone = Operations.execute(Plan.delete(List.of(row(a), row(b))), trash(), n -> { });
        file("a.txt", "new");
        Done back = Operations.execute(gone.reverse(), trash(), n -> { });
        assertEquals(1, back.failed().size(), "a.txt is taken; the rest carries on");
        assertEquals("new", Files.readString(a));
        assertEquals("b", Files.readString(b));
    }

    @Test
    void aCopyIsTakenBackByRemovingWhatItMade() throws IOException {
        Path a = file("a.txt", "a");
        Path to = dir.resolve("copy.txt");
        Done made = Operations.execute(new Plan(Op.COPY, List.of(new Step(a, to)), List.of(), 1), trash(), n -> { });
        assertEquals("a", Files.readString(to));
        Done removed = Operations.execute(made.reverse(), trash(), n -> { });
        assertEquals(Op.REMOVE, removed.op());
        assertFalse(Files.exists(to));
        assertTrue(Files.exists(a), "the original is never touched");
    }

    @Test
    void aStoppedRunEndsBetweenStepsAndSaysSo() throws IOException {
        Path a = file("a.txt", "a");
        Path b = file("b.txt", "b");
        Thread.currentThread().interrupt();
        try {
            Done d = Operations.execute(Plan.delete(List.of(row(a), row(b))), trash(), n -> { });
            assertTrue(d.stopped());
            assertEquals(0, d.count());
            assertTrue(Files.exists(a));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void anArchiveIsOneNewZipBesideItsFilesAndItsUndoRemovesIt() throws IOException {
        Path a = file("a.txt", "a");
        Path b = file("b.txt", "b");
        Plan plan = Plan.archive(List.of(row(a), row(b)), java.util.Set.of("a.txt", "b.txt", "Archive.zip"));
        Path zip = dir.resolve("Archive-2.zip");
        assertEquals(zip, plan.steps().getFirst().to(), "a name not taken");
        Done made = Operations.execute(plan, trash(), n -> { });
        assertEquals(2, made.count());
        try (var z = new java.util.zip.ZipFile(zip.toFile())) {
            assertEquals(2, z.size());
        }
        assertTrue(Files.exists(a), "the files stay where they are");

        Done gone = Operations.execute(made.reverse(), trash(), n -> { });
        assertEquals(List.of(), gone.failed());
        assertFalse(Files.exists(zip));
        Operations.execute(gone.reverse(), trash(), n -> { });
        assertTrue(Files.exists(zip), "and the redo makes it again");
    }

    @Test
    void anArchiveNeverWritesOverAZipAlreadyThere() throws IOException {
        Path a = file("a.txt", "a");
        Files.writeString(dir.resolve("Archive.zip"), "not mine");
        Done d = Operations.execute(Plan.archive(List.of(row(a)), java.util.Set.of()), trash(), n -> { });
        assertEquals(0, d.count());
        assertEquals("not mine", Files.readString(dir.resolve("Archive.zip")));
    }

    @Test
    void keepNewerTrashesTheOlderMovesTheNewerIntoItsPlaceAndOneUndoTakesItBack() throws IOException {
        Path here = Files.createDirectories(dir.resolve("here"));
        Path there = Files.createDirectories(dir.resolve("there"));
        Path newer = Files.writeString(here.resolve("kiln.mp4"), "new");
        Path older = Files.writeString(there.resolve("kiln.mp4"), "old");
        Done d = Operations.execute(Plan.keepNewer(row(newer), row(older), there), trash(), n -> { });
        assertEquals(List.of(), d.failed());
        assertEquals("new", Files.readString(older));
        assertFalse(Files.exists(newer));
        assertTrue(d.done().getFirst().to().startsWith(trash()), "the older is kept in the trash");

        Done back = Operations.execute(d.reverse(), trash(), n -> { });
        assertEquals(List.of(), back.failed());
        assertEquals("new", Files.readString(newer));
        assertEquals("old", Files.readString(older));
    }

    @Test
    void keepNewerLeavesANewerCopyAlreadyThereAndTrashesOnlyTheOlder() throws IOException {
        Path here = Files.createDirectories(dir.resolve("here"));
        Path there = Files.createDirectories(dir.resolve("there"));
        Path older = Files.writeString(here.resolve("kiln.mp4"), "old");
        Path newer = Files.writeString(there.resolve("kiln.mp4"), "new");
        Plan p = Plan.keepNewer(row(newer), row(older), there);
        assertEquals(1, p.count());
        Operations.execute(p, trash(), n -> { });
        assertFalse(Files.exists(older));
        assertEquals("new", Files.readString(newer));
    }

    @Test
    void keepBothMovesTheCopyInUnderTheFirstFreeNumber() throws IOException {
        Path here = Files.createDirectories(dir.resolve("here"));
        Path there = Files.createDirectories(dir.resolve("there"));
        Path mine = Files.writeString(here.resolve("kiln.mp4"), "mine");
        Files.writeString(there.resolve("kiln.mp4"), "theirs");
        Plan p = Plan.aside(row(mine), there, java.util.Set.of("kiln.mp4", "kiln (2).mp4"));
        assertEquals(there.toAbsolutePath().resolve("kiln (3).mp4"), p.steps().getFirst().to());
        Done d = Operations.execute(p, trash(), n -> { });
        assertEquals("mine", Files.readString(there.resolve("kiln (3).mp4")));
        Operations.execute(d.reverse(), trash(), n -> { });
        assertEquals("mine", Files.readString(mine));

        assertEquals("notes (2)", Plan.asideName("notes", false, java.util.Set.of("notes")));
        assertEquals("a.b (2)", Plan.asideName("a.b", true, java.util.Set.of("a.b")));
    }

    @Test
    void aRenameIsUndoneByRenamingBackAndRefusesANameThatWillNotDo() throws IOException {
        Path a = file("a.txt", "a");
        Done d = Operations.execute(Plan.rename(row(a), "b.txt", java.util.Set.of("a.txt")), trash(), n -> { });
        assertEquals("a", Files.readString(dir.resolve("b.txt")));
        Operations.execute(d.reverse(), trash(), n -> { });
        assertEquals("a", Files.readString(a));

        assertEquals("a file of that name is already there",
                Plan.rename(row(a), "c.txt", java.util.Set.of("c.txt")).skipped().getFirst().reason());
        assertEquals("that is its name already",
                Plan.rename(row(a), " a.txt ", java.util.Set.of()).skipped().getFirst().reason());
        assertEquals(0, Plan.rename(row(a), "x/y", java.util.Set.of()).count());
    }
}
