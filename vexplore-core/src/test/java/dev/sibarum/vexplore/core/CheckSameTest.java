package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Check;
import dev.sibarum.vexplore.core.Messages.Checked;
import dev.sibarum.vexplore.core.Messages.Checking;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Twins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sibarum.atchung.Sender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckSameTest {

    @TempDir
    Path dir;

    private final List<Checking> counted = new ArrayList<>();
    private final CheckSame check = new CheckSame(Sender.collecting(Messages.CHECKING_TOPIC, counted));

    private Twins twins(String name, byte[] here, byte[] there) throws Exception {
        Path a = Files.createDirectories(dir.resolve("a")).resolve(name);
        Path b = Files.createDirectories(dir.resolve("b")).resolve(name);
        Files.write(a, here);
        Files.write(b, there);
        return new Twins(row(a, here.length), row(b, there.length));
    }

    private static Row row(Path p, long size) {
        return new Row(p, p.getFileName().toString(), false, size, Instant.EPOCH, Kind.OTHER);
    }

    /** Bytes enough to span several blocks, with one changed near the end when {@code changed}. */
    private static byte[] big(boolean changed) {
        byte[] b = new byte[(1 << 16) * 3 + 17];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (i * 31);
        }
        if (changed) {
            b[b.length - 5] ^= 1;
        }
        return b;
    }

    @Test
    void pairsAreReadByteForByte() throws Exception {
        Twins same = twins("same.bin", big(false), big(false));
        Twins late = twins("late.bin", big(false), big(true));
        Twins longer = twins("longer.txt", "abc".getBytes(), "abcd".getBytes());
        Twins empty = twins("empty.txt", new byte[0], new byte[0]);
        Checked c = check.run(new Check(7L, List.of(same, late, longer, empty)));
        assertEquals(Set.of(same, empty), c.same());
        assertEquals(Set.of(late, longer), c.differ());
        assertEquals(null, c.problem());
        assertEquals(new Checking(7L, 4, 4), counted.getLast(), "the count, the latest of it");
    }

    @Test
    void aPairThatCannotBeReadIsInNeitherAndSaid() throws Exception {
        Twins gone = new Twins(row(dir.resolve("gone.txt"), 3), row(dir.resolve("also-gone.txt"), 3));
        Twins same = twins("same.txt", "abc".getBytes(), "abc".getBytes());
        Checked c = check.run(new Check(1L, List.of(gone, same)));
        assertEquals(Set.of(same), c.same());
        assertTrue(c.differ().isEmpty());
        assertNotNull(c.problem());
        assertTrue(c.problem().startsWith("gone.txt: "), c.problem());
    }

    @Test
    void aStopEndsItBetweenTwoBlocks() throws Exception {
        Twins same = twins("same.bin", big(false), big(false));
        Thread.currentThread().interrupt();
        try {
            Checked c = check.run(new Check(2L, List.of(same)));
            assertTrue(c.same().isEmpty() && c.differ().isEmpty(), "stopped before it could say");
        } finally {
            Thread.interrupted();
        }
    }
}
