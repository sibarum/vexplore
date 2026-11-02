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
}
