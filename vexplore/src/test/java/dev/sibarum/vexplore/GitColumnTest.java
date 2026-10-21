package dev.sibarum.vexplore;

import dev.sibarum.suite.git.Status;
import dev.sibarum.suite.git.Status.Change;
import dev.sibarum.suite.git.Status.State;
import dev.sibarum.vexplore.files.Entry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitColumnTest {

    private static final Path ROOT = Path.of("C:/repo").toAbsolutePath();

    private static Status status(Change... changes) {
        return new Status(ROOT, new Status.Branch("main", "abc1234", null, 0, 0), List.of(changes));
    }

    private static Entry file(String rel) {
        return new Entry(ROOT.resolve(rel), false, 1, Instant.EPOCH);
    }

    private static Entry folder(String rel) {
        return new Entry(ROOT.resolve(rel), true, 0, Instant.EPOCH);
    }

    @Test
    void outsideARepositoryEveryRowIsBlank() {
        assertEquals(GitColumn.Mark.NONE, GitColumn.of(null, file("a.txt")));
    }

    @Test
    void aFileSaysWhatItIs() {
        Status s = status(new Change("a.txt", null, State.MODIFIED), new Change("b.txt", null, State.STAGED_AND_MODIFIED));
        assertEquals(new GitColumn.Mark("modified", GitColumn.Tone.CHANGED), GitColumn.of(s, file("a.txt")));
        assertEquals("part-staged", GitColumn.of(s, file("b.txt")).words());
        assertEquals(GitColumn.Mark.NONE, GitColumn.of(s, file("clean.txt")));
    }

    @Test
    void aFolderGivesTheCountThatMostWantsAttention() {
        Status s = status(new Change("src/a.txt", null, State.MODIFIED),
                new Change("src/deep/b.txt", null, State.STAGED),
                new Change("src/new/", null, State.UNTRACKED),
                new Change("lib/c.txt", null, State.CONFLICTED),
                new Change("lib/d.txt", null, State.MODIFIED),
                new Change("docs/n.md", null, State.UNTRACKED));
        assertEquals(new GitColumn.Mark("2 changed", GitColumn.Tone.QUIET), GitColumn.of(s, folder("src")));
        assertEquals(new GitColumn.Mark("1 conflict", GitColumn.Tone.ALARM), GitColumn.of(s, folder("lib")));
        assertEquals("1 untracked", GitColumn.of(s, folder("docs")).words());
        assertEquals(GitColumn.Mark.NONE, GitColumn.of(s, folder("clean")));
    }

    @Test
    void aFolderThatIsWhollyUntrackedOrIgnoredSaysSo() {
        Status s = status(new Change("src/new/", null, State.UNTRACKED), new Change("target/", null, State.IGNORED));
        assertEquals("untracked", GitColumn.of(s, folder("src/new")).words());
        assertEquals(new GitColumn.Mark("ignored", GitColumn.Tone.FAINT), GitColumn.of(s, folder("target")));
        assertEquals("ignored", GitColumn.of(s, file("target/classes/A.class")).words());
    }
}
