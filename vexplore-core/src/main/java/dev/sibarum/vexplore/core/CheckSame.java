package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Check;
import dev.sibarum.vexplore.core.Messages.Checked;
import dev.sibarum.vexplore.core.Messages.Checking;
import dev.sibarum.vexplore.core.Messages.StopCheck;
import dev.sibarum.vexplore.core.Messages.Twins;
import dev.vexelray.framework.api.Source;
import sibarum.atchung.Sender;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads pairs of copies side by side and says which match byte for byte: what the rule needs before a copy is
 * removed as identical (rebuild.md, M6's ruling: content is read only where it changes what an action does).
 *
 * <p>Compared, not hashed (docs/shape.md, M6 slice 4): both copies are on this machine, so reading them together and
 * stopping at the first byte that differs is exact, and two different files are usually told apart in their first
 * block, where a hash of each would read both whole.
 *
 * <p>A {@code @Source} because a pair of videos takes a while and wants a Stop. {@link StopCheck} interrupts the
 * thread, which is read between blocks, so a stopped check still says what it found; the pair it was reading is in
 * neither set.
 */
@Source(stop = StopCheck.class)
public final class CheckSame {

    /** How much of each file is read at a time. */
    private static final int BLOCK = 1 << 16;

    private final Sender<Checking> checking;

    public CheckSame(Sender<Checking> checking) {
        this.checking = checking;
    }

    public Checked run(Check c) {
        Set<Twins> same = new HashSet<>();
        Set<Twins> differ = new HashSet<>();
        String problem = null;
        int done = 0;
        int total = c.twins().size();
        for (Twins t : c.twins()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            try {
                Boolean match = same(t.here().path(), t.there().path());
                if (match == null) {
                    break;
                }
                (match ? same : differ).add(t);
            } catch (IOException | RuntimeException e) {
                if (problem == null) {
                    problem = t.here().name() + ": " + ListFolder.problem(e);
                }
            }
            checking.send(new Checking(c.run(), ++done, total));
        }
        return new Checked(c.run(), same, differ, problem);
    }

    /** Whether {@code a} and {@code b} hold the same bytes; null when stopped before it could say. */
    static Boolean same(Path a, Path b) throws IOException {
        if (Files.size(a) != Files.size(b)) {
            return false;
        }
        byte[] x = new byte[BLOCK];
        byte[] y = new byte[BLOCK];
        try (InputStream in = Files.newInputStream(a); InputStream other = Files.newInputStream(b)) {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }
                int n = in.readNBytes(x, 0, BLOCK);
                int m = other.readNBytes(y, 0, BLOCK);
                if (n != m || Arrays.mismatch(x, 0, n, y, 0, m) >= 0) {
                    return false;
                }
                if (n < BLOCK) {
                    return true;
                }
            }
        }
    }
}
