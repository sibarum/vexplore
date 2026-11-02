package dev.sibarum.vexplore.core;

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
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
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
 */
@Source(stop = Unwatch.class)
public final class WatchFolder {

    /** How long a folder is quiet before what changed in it is said. */
    static final long QUIET_MS = 200L;

    /** The longest a change waits to be said while the folder never goes quiet. */
    static final long MOST_MS = 1000L;

    private final Sender<Stirred> stirred;

    public WatchFolder(Sender<Stirred> stirred) {
        this.stirred = stirred;
    }

    public Unwatched run(Watch w) {
        Path folder = w.folder();
        try (WatchService service = folder.getFileSystem().newWatchService()) {
            folder.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            Burst burst = Burst.NONE;
            while (true) {
                WatchKey key = burst.quiet() ? service.take()
                        : service.poll(burst.untilDue(now()), TimeUnit.MILLISECONDS);
                if (key != null) {
                    key.pollEvents();
                    burst = burst.stirred(now());
                    if (!key.reset()) {
                        stirred.send(new Stirred(folder));
                        return new Unwatched(folder, "it was moved or removed");
                    }
                }
                if (burst.due(now())) {
                    stirred.send(new Stirred(folder));
                    burst = Burst.NONE;
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
}
