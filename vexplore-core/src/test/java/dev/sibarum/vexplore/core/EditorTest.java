package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Find;
import dev.sibarum.vexplore.core.Messages.Found;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.sibarum.vexplore.core.Messages.Kind;
import dev.sibarum.vexplore.core.Messages.Recall;
import dev.sibarum.vexplore.core.Messages.Recalled;
import dev.sibarum.vexplore.core.Messages.Remember;
import dev.sibarum.vexplore.core.Messages.ShowFolder;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.sibarum.vexplore.core.Messages.Why;
import dev.vexelray.framework.core.CloseRequested;
import dev.vexelray.framework.core.WindowOpened;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sibarum.atchung.Atchung;
import sibarum.atchung.Fatal;
import sibarum.atchung.Inbox;
import sibarum.atchung.Tick;
import sibarum.atchung.Topic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The controller, alone: no window and no jobs. The {@link Editor} owns an inbox on a tick of this test's, as it will
 * own one on the framework's, and each test publishes what the view or a job would and runs the tick, then reads what
 * the editor sent. The jobs' replies are published by hand, which is what makes the interesting orders (a remembered
 * folder that has gone, a path with nothing at it) as easy to test as the common one.
 */
class EditorTest {

    @TempDir
    Path dir;

    private Fatal previous;
    private Atchung bus;
    private Tick tick;
    /** Everything the editor sent, in order. */
    private final List<Object> sent = new ArrayList<>();

    @BeforeEach
    void build() {
        previous = Atchung.fatal();
        Atchung.onFatal(Fatal.THROW);
        bus = Atchung.create();
        tick = bus.tick(null);
        new Editor(tick.inbox("editor"));
        Inbox heard = tick.inbox("heard");
        for (Topic<?> t : List.of(Messages.SHOW_FOLDER_TOPIC, Messages.STATUS_TOPIC, Messages.FIND_TOPIC,
                Messages.RECALL_TOPIC, Messages.REMEMBER_TOPIC)) {
            listen(heard, t);
        }
    }

    @AfterEach
    void restoreTheFatalPolicy() {
        Atchung.onFatal(previous);
    }

    private <T> void listen(Inbox heard, Topic<T> topic) {
        heard.subscribe(topic, sent::add, 256);
    }

    /** Publish {@code message} as its sender would, and run the tick until nothing is left to deliver. */
    private <T> void tell(Topic<T> topic, T message) {
        bus.publish(topic, message);
        tick.run(System.nanoTime() + 60_000_000_000L);
    }

    /** What was sent of one kind, in order, and forget everything sent so far. */
    private <T> List<T> took(Class<T> kind) {
        List<T> out = sent.stream().filter(kind::isInstance).map(kind::cast).toList();
        sent.clear();
        return out;
    }

    private Path path(String name) {
        return dir.resolve(name).toAbsolutePath().normalize();
    }

    /** A session with nothing to bring back: the folder the application started in is shown. */
    private void started() {
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.empty(), List.of(), dir));
        sent.clear();
    }

    // ------------------------------------------------------------------ the session

    @Test
    void onlyTheMainWindowOpeningBringsTheSessionBack() {
        tell(WindowOpened.TOPIC, new WindowOpened("about"));
        assertTrue(took(Recall.class).isEmpty(), "another window opening brought the session back");
        tell(WindowOpened.TOPIC, new WindowOpened(CloseRequested.MAIN));
        assertEquals(List.of(new Recall()), took(Recall.class));
    }

    @Test
    void theRememberedFolderIsLookedAtThenShownThenRemembered() {
        Path folder = path("sub");
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.of(folder), List.of(), dir));
        assertEquals(List.of(new Find(folder, Why.RESTORE_FOLDER)), took(Find.class));
        tell(Messages.FOUND_TOPIC, new Found(folder, Why.RESTORE_FOLDER, Kind.FOLDER));
        assertTrue(sent.contains(new ShowFolder(folder)));
        assertEquals(List.of(new Remember(Optional.of(folder))), took(Remember.class));
    }

    @Test
    void aRememberedFolderThatHasGoneIsForgottenWithoutComplaint() {
        Path gone = path("gone");
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.of(gone), List.of(), dir));
        tell(Messages.FOUND_TOPIC, new Found(gone, Why.RESTORE_FOLDER, Kind.NOWHERE));
        assertTrue(sent.contains(new ShowFolder(dir)), "the folder it started in");
        assertTrue(took(Status.class).isEmpty(), "the person moved it; nothing to say");
    }

    @Test
    void aPathOnTheCommandLineWinsOverTheRememberedFolder() {
        Path file = path("a.txt");
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.of(path("sub")), List.of(file), dir));
        assertEquals(List.of(new Find(file, Why.ARGUMENT)), took(Find.class));
        tell(Messages.FOUND_TOPIC, new Found(file, Why.ARGUMENT, Kind.FILE));
        assertEquals(List.of(new ShowFolder(dir)), took(ShowFolder.class), "a file shows the folder it is in");
    }

    @Test
    void anUnchangedSessionIsNotRememberedTwice() {
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.empty(), List.of(), dir));
        assertEquals(1, took(Remember.class).size());
        tell(Messages.FOUND_TOPIC, new Found(dir, Why.GO, Kind.FOLDER));
        assertTrue(took(Remember.class).isEmpty(), "the same folder again is the same session");
    }

    // ------------------------------------------------------------------ the path bar

    @Test
    void aRelativePathIsRelativeToTheFolderShown() {
        started();
        tell(Messages.GO_TOPIC, new Go("sub"));
        assertEquals(List.of(new Find(path("sub"), Why.GO)), took(Find.class));
    }

    @Test
    void aTypedFolderIsShownAndRemembered() {
        started();
        Path folder = path("sub");
        tell(Messages.FOUND_TOPIC, new Found(folder, Why.GO, Kind.FOLDER));
        assertTrue(sent.contains(new ShowFolder(folder)));
        assertEquals(List.of(new Remember(Optional.of(folder))), took(Remember.class));
    }

    @Test
    void nothingAtATypedPathIsRefusedOnTheStatusLine() {
        started();
        tell(Messages.FOUND_TOPIC, new Found(path("gone"), Why.GO, Kind.NOWHERE));
        assertFalse(sent.stream().anyMatch(ShowFolder.class::isInstance), "the folder shown stays");
        assertTrue(took(Status.class).getFirst().alert());
    }
}
