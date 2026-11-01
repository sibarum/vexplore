package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.vexelray.framework.core.PickFile;
import dev.vexelray.framework.core.Picked;
import dev.vexelray.framework.shell.Dialogs;
import dev.vexelray.framework.shell.Shell;
import dev.vexelray.framework.shell.VexelApplication;
import dev.vexelray.gui.core.Cuts;
import dev.vexelray.gui.widget.Modal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sibarum.atchung.Sender;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The whole application, built headless by the generated wiring (no window, no GPU) and driven through the same
 * messages a user's gestures send.
 *
 * <p>{@code VexelApplication.tree} runs the wiring as far as the tree and starts the components, so the explorer and the
 * jobs run on their own lanes here, exactly as in the window. The view runs in frames, and there is no loop to run
 * them, so the test runs them while it waits ({@code VexelApplication.frame}): this thread is the GUI thread. No window
 * opens, so the framework never says {@code WindowOpened} for the main one: no session is restored and no settings are
 * written, and this test leaves the user's own settings alone.
 *
 * <p>The test listens on the bus as one more subscriber, which is how it sees what the explorer told the view. Every
 * part answers in its own time, so the test waits for what it asked for, running frames, rather than sleeping.
 */
class ViewTest {

    @TempDir
    Path dir;

    private Shell shell;
    private VexploreAppWiring wiring;
    /** Every status line the explorer sent, in order. */
    private final List<Object> said = new CopyOnWriteArrayList<>();

    @BeforeEach
    void build() {
        wiring = new VexploreAppWiring();
        shell = VexelApplication.tree(wiring, new String[0]);
        // This test's dialogs in place of the framework's, which would hold every question for a window that never
        // comes. Taken back as late as WINDOW, so handing them over after the tree is built is in time.
        shell.dialogs(new Refusing());
        shell.bus().subscribe(Messages.STATUS_TOPIC, said::add);
        cuts = Cuts.watch(shell.gui());
    }

    /**
     * What changed on screen in one frame while a test ran, which every test here then fails on: nothing this
     * application shows changes in one frame. The framework fades it all (a colour, an opacity, words, an image, a node
     * arriving, leaving, hidden or shown) as long as the view keeps its nodes and changes them. A cut found here means
     * a node was rebuilt where it should have been changed, or something stepped round the framework. Fix that; do not
     * take this watch out. If the framework cannot do it, that is a framework note (docs/framework-notes.md).
     */
    private Cuts cuts;

    /** Dialogs that fail the test on any question, since nothing should be asked, and cancel every file picker. */
    private static final class Refusing implements Dialogs {

        @Override
        public void ask(Modal modal) {
            throw new AssertionError("asked unexpectedly: " + modal.title());
        }

        @Override
        public void pick(PickFile request, Sender<Picked> picked) {
            picked.send(Picked.cancelled(request.id()));
        }
    }

    @AfterEach
    void dispose() {
        List<Cuts.Cut> found = cuts.found();
        cuts.close();
        shell.disposer().close();
        assertEquals(List.of(), found, "changed on screen in one frame; keep the node and change it (see cuts)");
    }

    /**
     * Run frames until {@code test} holds. The view hears only in a frame, and this thread runs them, so it is the GUI
     * thread here as the loop's is in the window: what the test reads of the view is read where the view writes it.
     */
    private void eventually(String what, BooleanSupplier test) throws InterruptedException {
        long until = System.nanoTime() + 10_000_000_000L;
        VexelApplication.frame(shell);
        while (!test.getAsBoolean()) {
            if (System.nanoTime() > until) {
                throw new AssertionError("timed out waiting for " + what);
            }
            Thread.sleep(5);
            VexelApplication.frame(shell);
        }
    }

    private void go(Path path) {
        shell.bus().publish(Messages.GO_TOPIC, new Go(path.toString()));
    }

    private Path shown() {
        return wiring.ui().shown();
    }

    @Test
    void itStartsWithNoFolderShown() {
        assertNull(shown());
    }

    @Test
    void aTypedFolderIsShownInTheBreadcrumb() throws Exception {
        Path sub = Files.createDirectory(dir.resolve("sub")).toAbsolutePath().normalize();
        go(sub);
        eventually("the folder", () -> sub.equals(shown()));
    }

    @Test
    void aTypedFileShowsTheFolderItIsIn() throws Exception {
        Path file = Files.writeString(dir.resolve("a.txt"), "a");
        go(file);
        eventually("the folder", () -> dir.toAbsolutePath().normalize().equals(shown()));
    }

    @Test
    void nothingAtATypedPathIsSaidOnTheStatusLine() throws Exception {
        go(dir.resolve("gone"));
        eventually("the status line", () -> !said.isEmpty());
        assertNull(shown(), "nothing was shown");
    }
}
