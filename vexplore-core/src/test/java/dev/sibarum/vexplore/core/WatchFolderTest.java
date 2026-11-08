package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Freshness;
import dev.sibarum.vexplore.core.Messages.Stirred;
import dev.sibarum.vexplore.core.Messages.Side;
import dev.sibarum.vexplore.core.Messages.Unwatched;
import dev.sibarum.vexplore.core.Messages.Watch;
import dev.sibarum.vexplore.core.WatchFolder.Arrivals;
import dev.sibarum.vexplore.core.WatchFolder.Burst;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sibarum.atchung.Sender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link WatchFolder}: when a burst is said, by its {@link Burst} alone, and a real folder watched and let go. */
class WatchFolderTest {

    @TempDir
    Path dir;

    @Test
    void aBurstIsSaidOnceItIsQuiet() {
        Burst b = Burst.NONE.stirred(1000L).stirred(1100L);
        assertFalse(b.due(1200L), "still busy");
        assertEquals(200L, b.untilDue(1100L));
        assertTrue(b.due(1300L), "quiet for long enough");
        assertTrue(Burst.NONE.quiet());
        assertFalse(Burst.NONE.due(5000L), "nothing to say");
    }

    @Test
    void aBurstThatNeverGoesQuietIsSaidEachSecond() {
        Burst b = Burst.NONE;
        for (long t = 0L; t < WatchFolder.MOST_MS; t += 100L) {
            b = b.stirred(t);
            assertFalse(b.due(t), "at " + t);
        }
        b = b.stirred(WatchFolder.MOST_MS);
        assertTrue(b.due(WatchFolder.MOST_MS), "a second after it began, though it never went quiet");
        assertEquals(1L, Burst.NONE.stirred(0L).untilDue(5000L), "late already: wait as little as can be");
    }

    @Test
    void aFileArrivingIsSaidAndAStopEndsTheRunQuietly() throws Exception {
        // Sent from the watch's own thread, so into lists that are safe there.
        List<Stirred> heard = new CopyOnWriteArrayList<>();
        List<Freshness> fresh = new CopyOnWriteArrayList<>();
        WatchFolder watch = new WatchFolder(Sender.collecting(Messages.STIRRED_TOPIC, heard),
                Sender.collecting(Messages.FRESHNESS_TOPIC, fresh));
        AtomicReference<Unwatched> ended = new AtomicReference<>();
        Thread run = new Thread(() -> ended.set(watch.run(new Watch(Side.FROM, dir))), "test-watch");
        run.start();

        long deadline = System.nanoTime() + 10_000_000_000L;
        for (int i = 0; (heard.isEmpty() || fresh.isEmpty()) && System.nanoTime() < deadline; i++) {
            Files.writeString(dir.resolve("landed-" + i + ".txt"), "x");
            Thread.sleep(150L);
        }
        assertFalse(heard.isEmpty(), "a file landing was said");
        assertEquals(dir, heard.getFirst().folder());
        assertFalse(fresh.isEmpty(), "and was fresh");
        assertTrue(fresh.getLast().fresh().stream().allMatch(p -> p.getParent().equals(dir)
                && p.getFileName().toString().startsWith("landed-")), fresh.getLast().toString());

        run.interrupt();
        run.join(5_000L);
        assertFalse(run.isAlive(), "the stop ended the run");
        assertEquals(dir, ended.get().folder());
        assertNull(ended.get().problem(), "stopped is not a problem");
    }

    @Test
    void aFolderThatIsNotThereSaysSo() {
        Unwatched u = new WatchFolder(Sender.collecting(Messages.STIRRED_TOPIC, new ArrayList<>()),
                Sender.collecting(Messages.FRESHNESS_TOPIC, new ArrayList<>()))
                .run(new Watch(Side.FROM, dir.resolve("gone")));
        assertTrue(u.problem() != null && !u.problem().isEmpty(), "said why");
    }

    @Test
    void aFileIsFreshUntilItsTimeIsUpOrItLeaves() {
        Path a = dir.resolve("a.txt");
        Path b = dir.resolve("b.txt");
        Arrivals arr = Arrivals.NONE.landed(a, 1_000L).landed(b, 5_000L);
        assertEquals(Long.MAX_VALUE, Arrivals.NONE.untilNext(0L), "nothing to wait for");
        assertEquals(WatchFolder.FRESH_MS - 1_000L, arr.untilNext(2_000L), "a goes first");
        assertEquals(Set.of(a, b), arr.expired(1_000L + WatchFolder.FRESH_MS - 1L).landed().keySet());
        assertEquals(Set.of(b), arr.expired(1_000L + WatchFolder.FRESH_MS).landed().keySet(), "a's time is up");
        assertEquals(Set.of(a), arr.left(b).landed().keySet(), "b left");
        Arrivals again = arr.landed(a, 9_000L);
        assertEquals(Set.of(a), again.expired(5_000L + WatchFolder.FRESH_MS).landed().keySet(),
                "landing again starts its time again");
    }
}
