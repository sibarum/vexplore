package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Stirred;
import dev.sibarum.vexplore.core.Messages.Unwatched;
import dev.sibarum.vexplore.core.Messages.Watch;
import dev.sibarum.vexplore.core.WatchFolder.Burst;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sibarum.atchung.Atchung;
import sibarum.atchung.Sender;
import sibarum.atchung.Tick;
import sibarum.probe.Cause;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        Atchung bus = Atchung.create();
        Tick tick = bus.tick(null);
        List<Stirred> heard = new CopyOnWriteArrayList<>();
        tick.inbox("heard").subscribe(Messages.STIRRED_TOPIC, heard::add, 64);
        WatchFolder watch = new WatchFolder(Sender.outside(bus, Messages.STIRRED_TOPIC,
                Cause.source("test.watch", Cause.Kind.IO)));
        AtomicReference<Unwatched> ended = new AtomicReference<>();
        Thread run = new Thread(() -> ended.set(watch.run(new Watch(dir))), "test-watch");
        run.start();

        long deadline = System.nanoTime() + 10_000_000_000L;
        for (int i = 0; heard.isEmpty() && System.nanoTime() < deadline; i++) {
            Files.writeString(dir.resolve("landed-" + i + ".txt"), "x");
            Thread.sleep(150L);
            tick.run(System.nanoTime() + 1_000_000L, Tick.ONE_THREAD);
        }
        assertFalse(heard.isEmpty(), "a file landing was said");
        assertEquals(dir, heard.getFirst().folder());

        run.interrupt();
        run.join(5_000L);
        assertFalse(run.isAlive(), "the stop ended the run");
        assertEquals(dir, ended.get().folder());
        assertNull(ended.get().problem(), "stopped is not a problem");
    }

    @Test
    void aFolderThatIsNotThereSaysSo() {
        WatchFolder watch = new WatchFolder(Sender.outside(Atchung.create(), Messages.STIRRED_TOPIC,
                Cause.source("test.watch.gone", Cause.Kind.IO)));
        Unwatched u = watch.run(new Watch(dir.resolve("gone")));
        assertTrue(u.problem() != null && !u.problem().isEmpty(), "said why");
    }
}
