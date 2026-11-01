package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Find;
import dev.sibarum.vexplore.core.Messages.Kind;
import dev.sibarum.vexplore.core.Messages.Why;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(Kind.FOLDER, find.run(new Find(dir, Why.GO)).kind());
        assertEquals(Kind.FILE, find.run(new Find(file, Why.GO)).kind());
        assertEquals(Kind.NOWHERE, find.run(new Find(dir.resolve("new.txt"), Why.GO)).kind());
    }
}
