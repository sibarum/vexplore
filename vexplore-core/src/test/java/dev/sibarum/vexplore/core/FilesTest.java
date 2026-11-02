package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Find;
import dev.sibarum.vexplore.core.Messages.Level;
import dev.sibarum.vexplore.core.Messages.LevelWanted;
import dev.sibarum.vexplore.core.Messages.Listed;
import dev.sibarum.vexplore.core.Messages.Listing;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Is;
import dev.sibarum.vexplore.core.Messages.Why;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The jobs, called as the offload lane calls them: a request in, a reply out, against a real folder. What can go wrong
 * is in the reply, never thrown.
 */
class FilesTest {

    @TempDir
    Path dir;

    @Test
    void findingSaysWhatIsThere() throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "a");
        FindPath find = new FindPath();
        assertEquals(Is.FOLDER, find.run(new Find(dir, Why.GO)).is());
        assertEquals(Is.FILE, find.run(new Find(file, Why.GO)).is());
        assertEquals(Is.NOWHERE, find.run(new Find(dir.resolve("new.txt"), Why.GO)).is());
    }

    @Test
    void aListingIsFoldersFirstThenFilesEachByName() throws Exception {
        Files.writeString(dir.resolve("b.TXT"), "bb");
        Files.writeString(dir.resolve("A.mp4"), "a");
        Files.createDirectory(dir.resolve("z"));
        Listed l = new ListFolder().run(new Listing(7, dir));
        assertNull(l.problem());
        assertEquals(7, l.ticket());
        assertEquals(List.of("z", "A.mp4", "b.TXT"), l.rows().stream().map(Row::name).toList());
        Row b = l.rows().getLast();
        assertEquals(2, b.size());
        assertEquals(Kind.TEXT, b.kind());
        assertEquals("TXT", b.typeLabel());
        assertEquals(Kind.FOLDER, l.rows().getFirst().kind());
    }

    @Test
    void aFolderThatCannotBeListedIsAProblemNotAThrow() {
        Listed l = new ListFolder().run(new Listing(1, dir.resolve("gone")));
        assertEquals(List.of(), l.rows());
        assertEquals("it is not there any more", l.problem());
    }

    @Test
    void aLevelIsTheSubfoldersOnly() throws Exception {
        Files.createDirectory(dir.resolve("b"));
        Files.createDirectory(dir.resolve("A"));
        Files.writeString(dir.resolve("c.txt"), "c");
        Level level = new ListLevel().run(new LevelWanted(dir, dir));
        assertEquals(List.of(dir.resolve("A"), dir.resolve("b")), level.folders());
    }

    private Row row(Path p) throws java.io.IOException {
        String name = ListFolder.name(p);
        return new Row(p, name, Files.isDirectory(p), Files.isDirectory(p) ? 0L : Files.size(p), java.time.Instant.EPOCH,
                Files.isDirectory(p) ? Kind.FOLDER : Kind.ofExtension(Kind.extension(name)));
    }

    @Test
    void aPlanLeavesAloneWhatWouldCollideAndSaysWhy() throws Exception {
        Path to = Files.createDirectory(dir.resolve("to"));
        Files.writeString(to.resolve("b.txt"), "there");
        Path a = Files.writeString(dir.resolve("a.txt"), "a");
        Path b = Files.writeString(dir.resolve("b.txt"), "b");
        Messages.Planned p = new PlanAction().run(new Messages.Planning(7, Plan.Op.MOVE,
                List.of(row(a), row(b), row(to)), to));
        assertEquals(7, p.ticket());
        assertEquals(List.of(new Plan.Step(a, to.toAbsolutePath().normalize().resolve("a.txt"))), p.plan().steps());
        assertEquals(List.of("a file of that name is already there", "the destination is inside it"),
                p.plan().skipped().stream().map(Plan.Skip::reason).toList());
    }

    @Test
    void aDestinationThatCannotBeReadPlansNothingAndSaysSo() throws Exception {
        Path a = Files.writeString(dir.resolve("a.txt"), "a");
        Messages.Planned p = new PlanAction().run(new Messages.Planning(1, Plan.Op.COPY, List.of(row(a)),
                dir.resolve("gone")));
        assertEquals(0, p.plan().count());
        assertEquals(1, p.plan().skipped().size());
    }

    @Test
    void placesAreTheOwnFolderThenTheNeighboursHoldingTheKind() throws Exception {
        Path home = Files.createDirectory(dir.resolve("home"));
        Files.createDirectory(home.resolve("Videos"));
        Path here = Files.createDirectory(dir.resolve("here"));
        Path clips = Files.createDirectory(here.resolve("clips"));
        Files.writeString(clips.resolve("x.mp4"), "x");
        Files.writeString(clips.resolve("y.mov"), "y");
        Path notes = Files.createDirectory(dir.resolve("notes"));
        Files.writeString(notes.resolve("z.txt"), "z");
        List<Messages.Dest> places = SuggestDestinations.places(here, Kind.VIDEO, home);
        assertEquals(List.of("Videos", "clips"), places.stream().map(d -> ListFolder.name(d.path())).toList());
        assertEquals("holds 2 videos", places.get(1).reason());
    }

    @Test
    void marksAreKeptAndReadBackAsTheyWere() {
        List<Messages.Mark> marks = List.of(new Messages.Mark("Downloads: All videos here · 2",
                java.util.Set.of(dir.resolve("a.mp4").toAbsolutePath(), dir.resolve("b.mp4").toAbsolutePath())),
                new Messages.Mark("notes", java.util.Set.of(dir.resolve("n.txt").toAbsolutePath())));
        assertEquals(marks, ReadSession.marks(WriteSession.marks(marks)));
        assertEquals(List.of(), ReadSession.marks(""));
    }
}
