package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Activated;
import dev.sibarum.vexplore.core.Messages.Chosen;
import dev.sibarum.vexplore.core.Messages.Find;
import dev.sibarum.vexplore.core.Messages.Found;
import dev.sibarum.vexplore.core.Messages.Go;
import dev.sibarum.vexplore.core.Messages.Is;
import dev.sibarum.vexplore.core.Messages.Recall;
import dev.sibarum.vexplore.core.Messages.Recalled;
import dev.sibarum.vexplore.core.Messages.Remember;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Shown;
import dev.sibarum.vexplore.core.Messages.Start;
import dev.sibarum.vexplore.core.Messages.Started;
import dev.sibarum.vexplore.core.Messages.Listed;
import dev.sibarum.vexplore.core.Messages.Listing;
import dev.sibarum.vexplore.core.Messages.Navigate;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.sibarum.vexplore.core.Messages.Summary;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The controller, alone: no window and no jobs. The {@link Explorer} owns an inbox on a tick of this test's, as it will
 * own one on the framework's, and each test publishes what the view or a job would and runs the tick, then reads what
 * the explorer sent. The jobs' replies are published by hand, which is what makes the interesting orders (a remembered
 * folder that has gone, a path with nothing at it) as easy to test as the common one.
 */
class ExplorerTest {

    @TempDir
    Path dir;

    private Fatal previous;
    private Atchung bus;
    private Tick tick;
    /** Everything the explorer sent, in order. */
    private final List<Object> sent = new ArrayList<>();

    @BeforeEach
    void build() {
        previous = Atchung.fatal();
        Atchung.onFatal(Fatal.THROW);
        bus = Atchung.create();
        tick = bus.tick(null);
        new Explorer(tick.inbox("explorer"));
        Inbox heard = tick.inbox("heard");
        for (Topic<?> t : List.of(Messages.LISTING_TOPIC, Messages.SHOWN_TOPIC, Messages.SUMMARY_TOPIC, Messages.START_TOPIC,
                Messages.STATUS_TOPIC, Messages.FIND_TOPIC,
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

    /** Whether the explorer asked for {@code folder}'s rows: what showing a folder starts with. */
    private boolean asks(Path folder) {
        return sent.stream().anyMatch(m -> m instanceof Listing l && l.folder().equals(folder));
    }

    /** The listing last asked for. */
    private Listing asked() {
        return took(Listing.class).getLast();
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
        tell(Messages.FOUND_TOPIC, new Found(folder, Why.RESTORE_FOLDER, Is.FOLDER));
        assertTrue(asks(folder));
        assertEquals(List.of(new Remember(Optional.of(folder))), took(Remember.class));
    }

    @Test
    void aRememberedFolderThatHasGoneIsForgottenWithoutComplaint() {
        Path gone = path("gone");
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.of(gone), List.of(), dir));
        tell(Messages.FOUND_TOPIC, new Found(gone, Why.RESTORE_FOLDER, Is.NOWHERE));
        assertTrue(asks(dir), "the folder it started in");
        assertTrue(took(Status.class).isEmpty(), "the person moved it; nothing to say");
    }

    @Test
    void aPathOnTheCommandLineWinsOverTheRememberedFolder() {
        Path file = path("a.txt");
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.of(path("sub")), List.of(file), dir));
        assertEquals(List.of(new Find(file, Why.ARGUMENT)), took(Find.class));
        tell(Messages.FOUND_TOPIC, new Found(file, Why.ARGUMENT, Is.FILE));
        assertEquals(List.of(dir), took(Listing.class).stream().map(Listing::folder).toList(),
                "a file shows the folder it is in");
    }

    @Test
    void anUnchangedSessionIsNotRememberedTwice() {
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.empty(), List.of(), dir));
        assertEquals(1, took(Remember.class).size());
        tell(Messages.FOUND_TOPIC, new Found(dir, Why.GO, Is.FOLDER));
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
        tell(Messages.FOUND_TOPIC, new Found(folder, Why.GO, Is.FOLDER));
        assertTrue(asks(folder));
        assertEquals(List.of(new Remember(Optional.of(folder))), took(Remember.class));
    }

    @Test
    void nothingAtATypedPathIsRefusedOnTheStatusLine() {
        started();
        tell(Messages.FOUND_TOPIC, new Found(path("gone"), Why.GO, Is.NOWHERE));
        assertFalse(sent.stream().anyMatch(Listing.class::isInstance), "the folder shown stays");
        assertTrue(took(Status.class).getFirst().alert());
    }

    // ------------------------------------------------------------------ the folder shown

    private Row file(String name, long size) {
        return new Row(path(name), name, false, size, Instant.EPOCH, Kind.TEXT);
    }

    private Row folder(String name) {
        return new Row(path(name), name, true, 0L, Instant.EPOCH, Kind.FOLDER);
    }

    /** Show {@code folder} as the list would have it once its listing lands, and forget what that sent. */
    private void showing(Path folder, Row... rows) {
        tell(Messages.NAVIGATE_TOPIC, new Navigate(folder));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), folder, List.of(rows), null));
        sent.clear();
    }

    @Test
    void aListingOvertakenByALaterAskIsDropped() {
        Path a = path("a");
        Path b = path("b");
        tell(Messages.NAVIGATE_TOPIC, new Navigate(a));
        Listing first = asked();
        tell(Messages.NAVIGATE_TOPIC, new Navigate(b));
        Listing second = asked();
        tell(Messages.LISTED_TOPIC, new Listed(first.ticket(), a, List.of(), null));
        assertTrue(took(Shown.class).isEmpty(), "the folder the person left is never shown");
        tell(Messages.LISTED_TOPIC, new Listed(second.ticket(), b, List.of(), null));
        assertEquals(b, took(Shown.class).getFirst().folder());
    }

    @Test
    void aTypedFileIsSelectedWhenItsFolderLands() {
        started();
        Path typed = path("a.txt");
        tell(Messages.FOUND_TOPIC, new Found(typed, Why.GO, Is.FILE));
        Listing l = asked();
        assertEquals(dir, l.folder());
        tell(Messages.LISTED_TOPIC, new Listed(l.ticket(), dir, List.of(file("a.txt", 1)), null));
        assertEquals(Optional.of(typed), took(Shown.class).getFirst().select());
    }

    @Test
    void theStatusLineCountsWhatIsSelected() {
        showing(dir, folder("sub"), file("a.txt", 10), file("b.txt", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Set.of(path("sub"), path("a.txt"))));
        assertEquals(List.of(new Summary(3, 2, 10L)), took(Summary.class));
    }

    @Test
    void activatingAFolderShowsItAndAFileOpensIt() {
        showing(dir, folder("sub"), file("a.txt", 10));
        tell(Messages.ACTIVATED_TOPIC, new Activated(path("sub")));
        assertTrue(asks(path("sub")));
        sent.clear();
        showing(dir, folder("sub"), file("a.txt", 10));
        tell(Messages.ACTIVATED_TOPIC, new Activated(path("a.txt")));
        assertEquals(List.of(new Start(path("a.txt"))), took(Start.class));
    }

    @Test
    void aFolderThatCannotBeReadIsShownEmptyAndSaid() {
        Path locked = path("locked");
        tell(Messages.NAVIGATE_TOPIC, new Navigate(locked));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), locked, List.of(), "access is denied"));
        assertTrue(sent.contains(new Shown(locked, List.of(), Optional.empty())), "the breadcrumb where it was asked");
        assertTrue(took(Status.class).getFirst().alert());
    }

    @Test
    void aFileThatWillNotOpenIsSaid() {
        tell(Messages.STARTED_TOPIC, new Started(path("a.txt"), "no opener"));
        assertTrue(took(Status.class).getFirst().alert());
    }
}
