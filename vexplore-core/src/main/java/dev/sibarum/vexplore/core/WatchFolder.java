package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Freshness;
import dev.sibarum.vexplore.core.Messages.Stirred;
import dev.sibarum.vexplore.core.Messages.Unwatch;
import dev.sibarum.vexplore.core.Messages.Unwatched;
import dev.sibarum.vexplore.core.Messages.Watch;
import dev.vexelray.framework.api.Source;
import sibarum.atchung.Sender;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Watches the folder shown, on a thread of its own, and says when something in it changed, so the list follows the
 * disk: a file another program saves, removes or is still writing shows without navigating away and back.
 *
 * <p>A {@code @Source} because it waits on the disk for as long as the folder is shown. Its topic is unkeyed, so a
 * {@link Watch} for the next folder stops the run on the last; {@link Unwatch} stops it with no other.
 *
 * <p>A burst is said once ({@link Burst}): a copy of a hundred files, or a download writing for a minute, is a
 * {@link Stirred} when it has been quiet for {@link #QUIET_MS}, and at most one each {@link #MOST_MS} while it goes on,
 * so the explorer lists the folder again at most once a second.
 *
 * <p>It also keeps what landed ({@link Arrivals}): a file created in the folder, or renamed or moved into it, is fresh
 * for {@link #FRESH_MS}, and the set is said again ({@link Freshness}) whenever it grows or something leaves it. Time
 * is kept here because a step cannot read the clock: the explorer holds the set it was last told and never compares
 * times (docs/shape.md, M5).
 */
@Source(stop = Unwatch.class)
public final class WatchFolder {

    /** How long a folder is quiet before what changed in it is said. */
    static final long QUIET_MS = 200L;

    /** The longest a change waits to be said while the folder never goes quiet. */
    static final long MOST_MS = 1000L;

    /** How long a file that landed is tagged new: long enough to look round from the program that saved it. */
    static final long FRESH_MS = 20_000L;

    private final Sender<Stirred> stirred;
    private final Sender<Freshness> freshness;

    public WatchFolder(Sender<Stirred> stirred, Sender<Freshness> freshness) {
        this.stirred = stirred;
        this.freshness = freshness;
    }

    public Unwatched run(Watch w) {
        Path folder = w.folder();
        try (WatchService service = folder.getFileSystem().newWatchService()) {
            folder.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            Burst burst = Burst.NONE;
            Arrivals arrivals = Arrivals.NONE;
            while (true) {
                long now = now();
                long wait = Math.min(burst.quiet() ? Long.MAX_VALUE : burst.untilDue(now), arrivals.untilNext(now));
                WatchKey key = wait == Long.MAX_VALUE ? service.take() : service.poll(wait, TimeUnit.MILLISECONDS);
                Arrivals was = arrivals;
                if (key != null) {
                    long at = now();
                    for (WatchEvent<?> e : key.pollEvents()) {
                        if (e.context() instanceof Path name) {
                            arrivals = e.kind() == StandardWatchEventKinds.ENTRY_CREATE
                                    ? arrivals.landed(folder.resolve(name), at)
                                    : e.kind() == StandardWatchEventKinds.ENTRY_DELETE
                                    ? arrivals.left(folder.resolve(name)) : arrivals;
                        }
                    }
                    burst = burst.stirred(at);
                    if (!key.reset()) {
                        stirred.send(new Stirred(folder));
                        return new Unwatched(folder, "it was moved or removed");
                    }
                }
                if (burst.due(now())) {
                    stirred.send(new Stirred(folder));
                    burst = Burst.NONE;
                }
                arrivals = arrivals.expired(now());
                if (!arrivals.landed().keySet().equals(was.landed().keySet())) {
                    freshness.send(new Freshness(folder, arrivals.landed().keySet()));
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException stopped) {
            return new Unwatched(folder, null);
        } catch (IOException | RuntimeException e) {
            return new Unwatched(folder, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    /**
     * The changes not yet said: when the first and the last came, in milliseconds, or none. A value, so the timing is
     * tested without a disk.
     */
    record Burst(long first, long last) {

        static final Burst NONE = new Burst(-1L, -1L);

        /** Whether there is nothing to say. */
        boolean quiet() {
            return first < 0;
        }

        /** Something changed at {@code now}. */
        Burst stirred(long now) {
            return new Burst(quiet() ? now : first, now);
        }

        /** Whether it is time to say it: quiet for long enough, or waiting too long. */
        boolean due(long now) {
            return !quiet() && (now - last >= QUIET_MS || now - first >= MOST_MS);
        }

        /** How long to wait for the next change before it is due, at least a millisecond. */
        long untilDue(long now) {
            return Math.max(1L, Math.min(last + QUIET_MS, first + MOST_MS) - now);
        }
    }

    /**
     * What landed in the folder and when, in milliseconds: a file created, or renamed or moved in, until
     * {@link #FRESH_MS} has passed or it leaves. A value, so the window is tested without a clock.
     */
    record Arrivals(Map<Path, Long> landed) {

        static final Arrivals NONE = new Arrivals(Map.of());

        Arrivals {
            landed = Map.copyOf(landed);
        }

        /** {@code path} landed at {@code now}: fresh from then, even if it had landed before. */
        Arrivals landed(Path path, long now) {
            Map<Path, Long> next = new HashMap<>(landed);
            next.put(path, now);
            return new Arrivals(next);
        }

        /** {@code path} left the folder: it is fresh no longer. */
        Arrivals left(Path path) {
            if (!landed.containsKey(path)) {
                return this;
            }
            Map<Path, Long> next = new HashMap<>(landed);
            next.remove(path);
            return new Arrivals(next);
        }

        /** Without what has been fresh for {@link #FRESH_MS} by {@code now}. */
        Arrivals expired(long now) {
            Map<Path, Long> next = new HashMap<>(landed);
            next.values().removeIf(at -> now - at >= FRESH_MS);
            return next.size() == landed.size() ? this : new Arrivals(next);
        }

        /** How long until the next one stops being fresh, at least a millisecond, or {@code Long.MAX_VALUE}. */
        long untilNext(long now) {
            long next = Long.MAX_VALUE;
            for (long at : landed.values()) {
                next = Math.min(next, Math.max(1L, at + FRESH_MS - now));
            }
            return next;
        }
    }
}
