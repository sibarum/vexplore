package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.suggest.Suggestions.Scope;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The state model, which is the part of an application worth testing hardest.
 *
 * <p>No GUI, no window, no device. That is the point of keeping the document a plain value changed through one
 * committer: the interesting behaviour — that a listing which lost a race is dropped, that concurrent edits all
 * land, that a listener sees every version — is testable in milliseconds and needs no machine with a GPU.
 */
class ModelTest {

    private static final Path A = Path.of("A");
    private static final Path B = Path.of("B");

    private static Entry file(String name) {
        return new Entry(Path.of("A", name), false, 1L, Instant.EPOCH);
    }

    @Test
    void startsOnNoFolder() {
        assertEquals(null, new Model().doc().folder());
    }

    @Test
    void navigatingSaysWhetherItMovedAndTheSameFolderTwiceDoesNot() {
        Model model = new Model();
        assertTrue(model.navigate(A));
        assertFalse(model.navigate(A), "already there: nothing to list again");
        assertTrue(model.doc().loading());
    }

    @Test
    void aListingForAFolderYouHaveLeftIsDropped() {
        Model model = new Model();
        model.navigate(A);
        model.navigate(B);
        model.listed(A, List.of(file("stale")));
        assertTrue(model.doc().entries().isEmpty(), "the slow listing of A must not overwrite B");
        assertTrue(model.doc().loading(), "and B is still on its way");

        model.listed(B, List.of(file("fresh")));
        assertEquals(1, model.doc().entries().size());
        assertFalse(model.doc().loading());
    }

    @Test
    void aNewSelectionForgetsWhatWasPickedOnTheRail() {
        Model model = new Model();
        model.navigate(A);
        model.listed(A, List.of(file("a"), file("b")));
        model.select(Set.of(Path.of("A", "a")));
        model.pick(p -> p.with(Scope.SAME_KIND));
        assertEquals(Scope.SAME_KIND, model.doc().pick().scope());

        model.select(Set.of(Path.of("A", "b")));
        assertEquals(Scope.THIS, model.doc().pick().scope(), "the picks answered the old question");
    }

    @Test
    void selectingWhatIsAlreadySelectedChangesNothing() {
        Model model = new Model();
        model.select(Set.of(Path.of("x")));
        long v = model.version();
        model.select(Set.of(Path.of("x")));
        assertEquals(v, model.version() , "a re-announced selection must not redraw the rail");
    }

    @Test
    void concurrentEditsAllLandAndTheListenerEndsOnTheLastOne() throws Exception {
        Model model = new Model();
        java.util.concurrent.atomic.AtomicReference<Doc> lastSeen = new java.util.concurrent.atomic.AtomicReference<>();
        model.onChange(lastSeen::set);
        int threads = 8;
        int each = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            int id = t;
            pool.execute(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = 0; i < each; i++) {
                    model.select(Set.of(Path.of("t" + id, "f" + i)));
                }
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(model.doc(), lastSeen.get(),
                "whatever order the threads delivered in, the listener finishes on the newest document");
    }
}
