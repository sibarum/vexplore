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
}
