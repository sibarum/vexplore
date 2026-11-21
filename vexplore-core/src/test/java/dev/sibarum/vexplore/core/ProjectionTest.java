package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Fate;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Plan.Op;
import dev.sibarum.vexplore.core.Plan.Step;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the queue will make of a folder: {@link Projection}, a pure function, so no disk. */
class ProjectionTest {

    private static final Path HERE = Path.of("/tmp/here").toAbsolutePath();
    private static final Path THERE = Path.of("/tmp/there").toAbsolutePath();

    private static Row row(Path folder, String name, long size) {
        return new Row(folder.resolve(name), name, false, size, Instant.EPOCH, Kind.ofExtension(Kind.extension(name)));
    }

    private static Projection.Item item(long n, Op op, List<Row> rows, Path... tos) {
        List<Step> steps = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            steps.add(new Step(rows.get(i).path(), tos.length == 0 ? null : tos[Math.min(i, tos.length - 1)]));
        }
        return new Projection.Item(n, new Plan(op, steps, List.of(), 0L), rows);
    }

    @Test
    void aMoveLeavesItsRowFatedHereAndAGhostThere() {
        Row a = row(HERE, "a.txt", 10);
        Row b = row(HERE, "b.txt", 20);
        List<Projection.Item> q = List.of(item(3, Op.MOVE, List.of(a), THERE.resolve("a.txt")));

        Projection.Folder here = Projection.of(q, HERE, List.of(a, b));
        assertEquals(List.of(a, b), here.rows(), "a row that leaves stays, so nothing jumps");
        assertEquals(new Fate(Fate.Kind.LEAVING, "→ there", 3), here.fates().get(a.path()));
        assertNull(here.fates().get(b.path()));

        Projection.Folder there = Projection.of(q, THERE, List.of());
        assertEquals(1, there.rows().size());
        Row ghost = there.rows().getFirst();
        assertEquals(THERE.resolve("a.txt"), ghost.path());
        assertEquals(10L, ghost.size(), "drawn from the row it is made from");
        assertEquals(new Fate(Fate.Kind.ARRIVING, "← here", 3), there.fates().get(ghost.path()));
    }

    @Test
    void aDeleteIsIntoTheTrash() {
        Row a = row(HERE, "a.txt", 10);
        Projection.Folder f = Projection.of(List.of(item(1, Op.DELETE, List.of(a))), HERE, List.of(a));
        assertEquals(Fate.Kind.DELETING, f.fates().get(a.path()).kind());
    }

    @Test
    void aRenamedRowStandsInItsPlaceUnderItsNewName() {
        Row a = row(HERE, "a.txt", 10);
        Row b = row(HERE, "b.txt", 20);
        Row c = row(HERE, "c.txt", 30);
        Projection.Folder f = Projection.of(List.of(item(2, Op.RENAME, List.of(b), HERE.resolve("z.txt"))), HERE,
                List.of(a, b, c));
        assertEquals(List.of("a.txt", "z.txt", "c.txt"), f.rows().stream().map(Row::name).toList());
        assertEquals(new Fate(Fate.Kind.RENAMED, "was b.txt", 2), f.fates().get(HERE.resolve("z.txt")));
        assertFalse(f.fates().containsKey(b.path()));
    }

    @Test
    void aCopyStaysAndAnArchiveIsOneGhost() {
        Row a = row(HERE, "a.txt", 10);
        Row b = row(HERE, "b.txt", 20);
        Path zip = HERE.resolve("Archive.zip");
        Projection.Item archive = new Projection.Item(4, new Plan(Op.ARCHIVE,
                List.of(new Step(a.path(), zip), new Step(b.path(), zip)), List.of(), 30L), List.of(a, b));
        Projection.Folder f = Projection.of(List.of(archive), HERE, List.of(a, b));
        assertEquals(3, f.rows().size(), "one zip, however many go into it");
        assertEquals(Fate.Kind.ARCHIVING, f.fates().get(a.path()).kind());
        assertEquals(new Fate(Fate.Kind.ARRIVING, "archive of 2", 4), f.fates().get(zip));
        assertEquals(30L, f.rows().getLast().size());

        Projection.Folder copied = Projection.of(List.of(item(5, Op.COPY, List.of(a), THERE.resolve("a.txt"))),
                HERE, List.of(a));
        assertEquals(new Fate(Fate.Kind.COPYING, "copy → there", 5), copied.fates().get(a.path()));
    }

    @Test
    void namesAreTheFoldersOnceTheQueueIsDone() {
        Row a = row(HERE, "a.txt", 10);
        Row x = row(THERE, "x.txt", 10);
        List<Projection.Item> q = List.of(item(1, Op.MOVE, List.of(a), THERE.resolve("a.txt")),
                item(2, Op.DELETE, List.of(x)));
        assertEquals(Set.of("a.txt", "y.txt"), Projection.names(q, THERE, Set.of("x.txt", "y.txt")));
        assertEquals(Set.of("b.txt"), Projection.names(q, HERE, Set.of("a.txt", "b.txt")));
    }

    @Test
    void aFileSomethingWaitingTakesAwayIsLeftAlone() {
        Row a = row(HERE, "a.txt", 10);
        Row b = row(HERE, "b.txt", 20);
        List<Projection.Item> q = List.of(item(1, Op.DELETE, List.of(a)));
        Plan copy = new Plan(Op.COPY, List.of(new Step(a.path(), THERE.resolve("a.txt")),
                new Step(b.path(), THERE.resolve("b.txt"))), List.of(), 30L);
        Plan claimed = Projection.claim(copy, Projection.claimed(q));
        assertEquals(List.of(b.path()), claimed.steps().stream().map(Step::from).toList());
        assertEquals(new Plan.Skip(a.path(), "it is queued to go into the trash"), claimed.skipped().getFirst());
        assertTrue(Projection.claimed(List.of(item(1, Op.COPY, List.of(a), THERE.resolve("a.txt")))).isEmpty(),
                "a copy takes nothing away");
    }
}
