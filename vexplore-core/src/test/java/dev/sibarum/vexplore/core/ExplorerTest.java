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
import dev.sibarum.vexplore.core.Messages.Side;
import dev.sibarum.vexplore.core.Messages.Start;
import dev.sibarum.vexplore.core.Messages.Started;
import dev.sibarum.vexplore.core.Messages.Listed;
import dev.sibarum.vexplore.core.Messages.Listing;
import dev.sibarum.vexplore.core.Messages.Navigate;
import dev.sibarum.vexplore.core.Messages.Status;
import dev.sibarum.vexplore.core.Messages.Summary;
import dev.sibarum.vexplore.core.Messages.Picked;
import dev.sibarum.vexplore.core.Messages.Slot;
import dev.sibarum.vexplore.core.Messages.Suggested;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    /** The day every listing here says it was listed on. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

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
        for (Topic<?> t : List.of(Messages.LISTING_TOPIC, Messages.SHOWN_TOPIC, Messages.SUMMARY_TOPIC,
                Messages.START_TOPIC, Messages.STATUS_TOPIC, Messages.FIND_TOPIC, Messages.RECALL_TOPIC,
                Messages.REMEMBER_TOPIC, Messages.SUGGESTED_TOPIC, Messages.RUN_TOPIC, Messages.RECORDED_TOPIC,
                Messages.DESTINING_TOPIC, Messages.PLANNING_TOPIC, Messages.NAMING_TOPIC,
                Messages.MARKS_TOPIC, Messages.PREVIEWING_TOPIC, Messages.DOCKED_TOPIC,
                Messages.HANDING_TOPIC, Messages.WATCH_TOPIC, Messages.UNWATCH_TOPIC,
                Messages.FRESH_TOPIC, Messages.PAIR_TOPIC)) {
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

    /** What was sent of one kind, in order, forgetting nothing. */
    private <T> List<T> seen(Class<T> kind) {
        return sent.stream().filter(kind::isInstance).map(kind::cast).toList();
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
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.empty(), List.of(), dir, dir.resolve("trash"), List.of()));
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
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.of(folder), List.of(), dir, dir.resolve("trash"),
                List.of()));
        assertEquals(List.of(new Find(folder, Why.RESTORE_FOLDER)), took(Find.class));
        tell(Messages.FOUND_TOPIC, new Found(folder, Why.RESTORE_FOLDER, Is.FOLDER));
        assertTrue(asks(folder));
        assertEquals(List.of(new Remember(Optional.of(folder), List.of())), took(Remember.class));
    }

    @Test
    void aRememberedFolderThatHasGoneIsForgottenWithoutComplaint() {
        Path gone = path("gone");
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.of(gone), List.of(), dir, dir.resolve("trash"), List.of()));
        tell(Messages.FOUND_TOPIC, new Found(gone, Why.RESTORE_FOLDER, Is.NOWHERE));
        assertTrue(asks(dir), "the folder it started in");
        assertTrue(took(Status.class).isEmpty(), "the person moved it; nothing to say");
    }

    @Test
    void aPathOnTheCommandLineWinsOverTheRememberedFolder() {
        Path file = path("a.txt");
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.of(path("sub")), List.of(file), dir,
                dir.resolve("trash"), List.of()));
        assertEquals(List.of(new Find(file, Why.ARGUMENT)), took(Find.class));
        tell(Messages.FOUND_TOPIC, new Found(file, Why.ARGUMENT, Is.FILE));
        assertEquals(List.of(dir), took(Listing.class).stream().map(Listing::folder).toList(),
                "a file shows the folder it is in");
    }

    @Test
    void anUnchangedSessionIsNotRememberedTwice() {
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.empty(), List.of(), dir, dir.resolve("trash"), List.of()));
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
        assertEquals(List.of(new Remember(Optional.of(folder), List.of())), took(Remember.class));
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
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), folder, List.of(rows), null, TODAY, ZoneOffset.UTC));
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
        tell(Messages.LISTED_TOPIC, new Listed(first.ticket(), a, List.of(), null, TODAY, ZoneOffset.UTC));
        assertTrue(took(Shown.class).isEmpty(), "the folder the person left is never shown");
        tell(Messages.LISTED_TOPIC, new Listed(second.ticket(), b, List.of(), null, TODAY, ZoneOffset.UTC));
        assertEquals(b, took(Shown.class).getFirst().folder());
    }

    @Test
    void aTypedFileIsSelectedWhenItsFolderLands() {
        started();
        Path typed = path("a.txt");
        tell(Messages.FOUND_TOPIC, new Found(typed, Why.GO, Is.FILE));
        Listing l = asked();
        assertEquals(dir, l.folder());
        tell(Messages.LISTED_TOPIC, new Listed(l.ticket(), dir, List.of(file("a.txt", 1)), null, TODAY, ZoneOffset.UTC));
        assertEquals(Set.of(typed), took(Shown.class).getFirst().select());
    }

    @Test
    void theStatusLineCountsWhatIsSelected() {
        showing(dir, folder("sub"), file("a.txt", 10), file("b.txt", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("sub"), path("a.txt"))));
        assertEquals(List.of(new Summary(3, Optional.empty(), 2, 10L)), took(Summary.class));
    }

    @Test
    void activatingAFolderShowsItAndAFileOpensIt() {
        showing(dir, folder("sub"), file("a.txt", 10));
        tell(Messages.ACTIVATED_TOPIC, new Activated(Side.FROM, path("sub")));
        assertTrue(asks(path("sub")));
        sent.clear();
        showing(dir, folder("sub"), file("a.txt", 10));
        tell(Messages.ACTIVATED_TOPIC, new Activated(Side.FROM, path("a.txt")));
        assertEquals(List.of(new Start(path("a.txt"))), took(Start.class));
    }

    @Test
    void aFolderThatCannotBeReadIsShownEmptyAndSaid() {
        Path locked = path("locked");
        tell(Messages.NAVIGATE_TOPIC, new Navigate(locked));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), locked, List.of(), "access is denied", TODAY,
                ZoneOffset.UTC));
        assertTrue(sent.contains(new Shown(Side.FROM, locked, List.of(), Set.of())), "the breadcrumb where it was asked");
        assertTrue(took(Status.class).getFirst().alert());
    }

    @Test
    void theMenusOpenIsEntersOnTheOneSelected() {
        showing(dir, folder("sub"), file("a.txt", 10), file("b.txt", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.txt"))));
        sent.clear();
        tell(Messages.MENU_TOPIC, new Messages.Menu(Side.FROM, Messages.Menu.OPEN));
        assertEquals(List.of(new Start(path("a.txt"))), took(Start.class));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.txt"), path("b.txt"))));
        sent.clear();
        tell(Messages.MENU_TOPIC, new Messages.Menu(Side.FROM, Messages.Menu.OPEN));
        assertTrue(seen(Start.class).isEmpty(), "never several programs at once");
        assertTrue(took(Status.class).getFirst().alert());
    }

    @Test
    void theMenusActionIsPickedOnTheRailAndNotCarriedOut() {
        showing(dir, video("a.mp4", 10), video("b.mov", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"))));
        sent.clear();
        tell(Messages.MENU_TOPIC, new Messages.Menu(Side.FROM, Suggestions.Act.DELETE.name()));
        assertEquals("Delete 1 file · 10 B", took(Suggested.class).getLast().card().headline());
        assertTrue(seen(Messages.Run.class).isEmpty(), "the card's button carries it out");
        tell(Messages.MENU_TOPIC, new Messages.Menu(Side.FROM, Suggestions.Act.DELETE.name()));
        assertTrue(seen(Suggested.class).isEmpty(), "chosen again, it stays picked");

        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"), path("b.mov"))));
        sent.clear();
        tell(Messages.MENU_TOPIC, new Messages.Menu(Side.FROM, Suggestions.Act.RENAME.name()));
        assertEquals("Rename is not offered for these", took(Status.class).getFirst().message());
    }

    @Test
    void aFileThatWillNotOpenIsSaid() {
        tell(Messages.STARTED_TOPIC, new Started(path("a.txt"), "no opener"));
        assertTrue(took(Status.class).getFirst().alert());
    }

    // ------------------------------------------------------------------ the Suggestion Rail

    private Row video(String name, long size) {
        return new Row(path(name), name, false, size, Instant.EPOCH, Kind.VIDEO);
    }

    /** The paths of the rows a rail reaches. */
    private static Set<Path> reached(Suggested s) {
        return s.reach().stream().map(Row::path).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void aSelectionIsSuggestedOnAndAChipWidensWhatItReaches() {
        showing(dir, video("a.mp4", 10), video("b.mov", 20), file("notes.txt", 5), video("c.mkv", 30));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"))));
        Suggested one = took(Suggested.class).getLast();
        assertEquals("a.mp4", one.basis());
        assertEquals(Set.of(path("a.mp4")), reached(one), "ignoring the rail costs nothing: it reaches the selection");
        assertEquals("All videos here", one.select().get(1).label());
        assertEquals(3, one.select().get(1).count());

        tell(Messages.PICKED_TOPIC, new Picked(Slot.SELECT, 1));
        Suggested all = took(Suggested.class).getLast();
        assertEquals(Set.of(path("a.mp4"), path("b.mov"), path("c.mkv")), reached(all), "marked, never selected");
        assertTrue(all.select().get(1).on());
        assertEquals("3 files · 60 B", all.card().headline());
        assertEquals("a.mp4, b.mov, c.mkv, all marked in the list", all.card().detail());

        tell(Messages.PICKED_TOPIC, new Picked(Slot.ACTION, 0));
        assertEquals("Move 3 files · 60 B", took(Suggested.class).getLast().card().headline());
        tell(Messages.PICKED_TOPIC, new Picked(Slot.ACTION, 0));
        assertEquals("3 files · 60 B", took(Suggested.class).getLast().card().headline(),
                "pressing the picked action again puts it down");
    }

    @Test
    void nothingSelectedIsAnEmptyRailAndAChipPressedThenDoesNothing() {
        showing(dir, video("a.mp4", 10), video("b.mov", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"))));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of()));
        assertEquals(Suggested.NONE, took(Suggested.class).getLast());
        tell(Messages.PICKED_TOPIC, new Picked(Slot.SELECT, 1));
        assertTrue(took(Suggested.class).isEmpty());
    }

    @Test
    void aNewFolderPutsThePickDown() {
        showing(dir, video("a.mp4", 10), video("b.mov", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"))));
        tell(Messages.PICKED_TOPIC, new Picked(Slot.SELECT, 1));
        tell(Messages.NAVIGATE_TOPIC, new Navigate(dir));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), dir, List.of(video("a.mp4", 10), video("b.mov", 20)),
                null, TODAY, ZoneOffset.UTC));
        assertEquals(Suggested.NONE, took(Suggested.class).getLast(), "the rail is put away as the rows land");
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"))));
        assertEquals(Set.of(path("a.mp4")), reached(took(Suggested.class).getLast()));
    }

    @Test
    void aPickTooWideForTheSelectionFallsBackAndComesBack() {
        showing(dir, video("a.mp4", 10), video("b.mov", 20), file("notes.txt", 5));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"))));
        tell(Messages.PICKED_TOPIC, new Picked(Slot.SELECT, 1));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"), path("notes.txt"))));
        assertEquals(2, reached(took(Suggested.class).getLast()).size(), "a video and a note are not all videos");
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("b.mov"))));
        assertEquals(2, reached(took(Suggested.class).getLast()).size(), "one video again, so all of them again");
    }

    // ------------------------------------------------------------------ carrying it out, and taking it back

    /** Started, with {@code a.txt} and {@code b.txt} shown, {@code a.txt} selected and Delete picked. */
    private void deleteOfAPicked() {
        started();
        showing(dir, file("a.txt", 10), file("b.txt", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.txt"))));
        List<Messages.Chip> acts = took(Suggested.class).getLast().action();
        int delete = 0;
        while (!acts.get(delete).label().startsWith("Delete")) {
            delete++;
        }
        tell(Messages.PICKED_TOPIC, new Picked(Slot.ACTION, delete));
        sent.clear();
    }

    private Done deletedA() {
        return new Done(Plan.Op.DELETE, List.of(new Plan.Step(path("a.txt"), dir.resolve("trash/1/a.txt"))), List.of(),
                10L, false);
    }

    @Test
    void theCardsButtonCarriesOutItsPlanAndTheRunIsRecorded() {
        deleteOfAPicked();
        tell(Messages.CARRY_TOPIC, new Messages.Carry());
        Messages.Run run = took(Messages.Run.class).getFirst();
        assertEquals(Plan.Op.DELETE, run.plan().op());
        assertEquals(List.of(new Plan.Step(path("a.txt"), null)), run.plan().steps(), "the run picks the trash's place");
        assertEquals(dir.resolve("trash"), run.trash());

        tell(Messages.PROGRESS_TOPIC, new Messages.Progress(run.run(), 1, 1));
        assertEquals("Deleting · 1 of 1", took(Suggested.class).getLast().card().running());

        tell(Messages.RAN_TOPIC, new Messages.Ran(run.run(), deletedA()));
        assertEquals(List.of(new Messages.Recorded(1, "Delete 1 file")), seen(Messages.Recorded.class));
        assertTrue(seen(Status.class).getFirst().message().startsWith("Deleted 1 file into Vexplore's trash"));
        assertTrue(asks(dir), "the folder is listed again, so the list says what happened");
    }

    @Test
    void anUndoRunsTheReverseAndARedoTheReverseOfThat() {
        deleteOfAPicked();
        tell(Messages.CARRY_TOPIC, new Messages.Carry());
        long first = took(Messages.Run.class).getFirst().run();
        tell(Messages.RAN_TOPIC, new Messages.Ran(first, deletedA()));
        sent.clear();

        tell(Messages.UNDO_TOPIC, new Messages.Undo(1));
        Messages.Run back = took(Messages.Run.class).getFirst();
        assertEquals(List.of(new Plan.Step(dir.resolve("trash/1/a.txt"), path("a.txt"))), back.plan().steps());
        Done undone = new Done(Plan.Op.MOVE, back.plan().steps(), List.of(), 10L, false);
        tell(Messages.RAN_TOPIC, new Messages.Ran(back.run(), undone));
        assertTrue(seen(Messages.Recorded.class).isEmpty(), "an undo is not a new entry");
        assertEquals("Undone: Delete 1 file", took(Status.class).getFirst().message());

        tell(Messages.UNDO_TOPIC, new Messages.Undo(1));
        assertTrue(took(Messages.Run.class).isEmpty(), "an entry undone is not undone twice");
        tell(Messages.REDO_TOPIC, new Messages.Redo(1));
        assertEquals(List.of(new Plan.Step(path("a.txt"), dir.resolve("trash/1/a.txt"))),
                took(Messages.Run.class).getFirst().plan().steps());
    }

    @Test
    void oneRunAtATime() {
        deleteOfAPicked();
        tell(Messages.CARRY_TOPIC, new Messages.Carry());
        sent.clear();
        tell(Messages.CARRY_TOPIC, new Messages.Carry());
        assertTrue(sent.stream().noneMatch(m -> m instanceof Messages.Run));
        assertTrue(took(Status.class).getFirst().alert());
    }

    @Test
    void aRunThatCouldNotDoEverythingSaysWhatAndWhy() {
        deleteOfAPicked();
        tell(Messages.CARRY_TOPIC, new Messages.Carry());
        long run = took(Messages.Run.class).getFirst().run();
        tell(Messages.RAN_TOPIC, new Messages.Ran(run,
                new Done(Plan.Op.DELETE, List.of(), List.of("a.txt: access is denied"), 10L, false)));
        assertTrue(seen(Messages.Recorded.class).isEmpty(), "nothing done is nothing to undo");
        Status said = took(Status.class).getFirst();
        assertTrue(said.alert());
        assertEquals("Deleted 0 of 1. 1 could not be: a.txt: access is denied", said.message());
    }

    // ------------------------------------------------------------------ where a move or a copy goes

    /** Started, two videos shown, {@code a.mp4} selected and Move picked. */
    private void moveOfAPicked() {
        started();
        showing(dir, video("a.mp4", 10), video("b.mp4", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"))));
        tell(Messages.PICKED_TOPIC, new Picked(Slot.ACTION, 0));
    }

    @Test
    void movingAsksWherePlacesAreAndTheCardWaitsForOne() {
        moveOfAPicked();
        Messages.Destining asked = seen(Messages.Destining.class).getLast();
        assertEquals(Kind.VIDEO, asked.kind());
        assertEquals(dir, asked.folder());
        Suggested rail = took(Suggested.class).getLast();
        assertTrue(rail.placing());
        assertEquals("", rail.card().go(), "nowhere to go yet, so no button");
        assertTrue(rail.card().detail().endsWith("Choose where they go"));

        Path clips = path("clips");
        tell(Messages.DESTINATIONS_TOPIC, new Messages.Destinations(asked.ticket(),
                List.of(new Messages.Dest(clips, "holds 3 videos", 3))));
        assertEquals(List.of(new Messages.Chip("clips", 3, false)), took(Suggested.class).getLast().places());
    }

    @Test
    void aPlacePickedIsPlannedAndTheCardsButtonCarriesOutThePlan() {
        moveOfAPicked();
        long ticket = seen(Messages.Destining.class).getLast().ticket();
        Path clips = path("clips");
        tell(Messages.DESTINATIONS_TOPIC, new Messages.Destinations(ticket,
                List.of(new Messages.Dest(clips, "holds 3 videos", 3))));
        sent.clear();
        tell(Messages.PICKED_TOPIC, new Picked(Slot.DESTINATION, 0));
        Messages.Planning want = seen(Messages.Planning.class).getFirst();
        assertEquals(clips, want.destination());
        assertEquals(Plan.Op.MOVE, want.op());

        Plan plan = new Plan(Plan.Op.MOVE, List.of(new Plan.Step(path("a.mp4"), clips.resolve("a.mp4"))), List.of(),
                10L);
        tell(Messages.PLANNED_TOPIC, new Messages.Planned(want.ticket(), plan));
        assertEquals("Move 1 file to clips", took(Suggested.class).getLast().card().go());

        tell(Messages.CARRY_TOPIC, new Messages.Carry());
        assertEquals(plan, took(Messages.Run.class).getFirst().plan());
    }

    @Test
    void aFolderChosenIsTheDestinationAndALatePlanIsDropped() {
        moveOfAPicked();
        Path far = path("far");
        tell(Messages.DESTINE_TOPIC, new Messages.Destine(far));
        Messages.Planning first = seen(Messages.Planning.class).getLast();
        assertTrue(took(Suggested.class).getLast().places().contains(new Messages.Chip("far", 0, true)),
                "a folder chosen that was not suggested is shown first, picked");
        tell(Messages.DESTINE_TOPIC, new Messages.Destine(path("farther")));
        tell(Messages.PLANNED_TOPIC, new Messages.Planned(first.ticket(), new Plan(Plan.Op.MOVE, List.of(
                new Plan.Step(path("a.mp4"), far.resolve("a.mp4"))), List.of(), 10L)));
        List<Suggested> after = took(Suggested.class);
        assertTrue(after.isEmpty() || after.getLast().card().go().isEmpty(),
                "a plan for the folder chosen before is not this one");
    }

    @Test
    void afterARunTheFolderIsListedAgainKeepingWhatIsStillThereSelected() {
        deleteOfAPicked();
        tell(Messages.CARRY_TOPIC, new Messages.Carry());
        long run = took(Messages.Run.class).getFirst().run();
        tell(Messages.RAN_TOPIC, new Messages.Ran(run,
                new Done(Plan.Op.DELETE, List.of(), List.of("a.txt: in use"), 10L, false)));
        Listing again = asked();
        tell(Messages.LISTED_TOPIC, new Listed(again.ticket(), dir, List.of(file("a.txt", 10), file("b.txt", 20)), null,
                TODAY, ZoneOffset.UTC));
        assertEquals(new Summary(2, Optional.empty(), 1, 10L), seen(Summary.class).getLast(), "a.txt is still there, and still selected");
        assertEquals(Set.of(path("a.txt")), reached(took(Suggested.class).getLast()));
    }

    // ------------------------------------------------------------------ archive and rename

    private void picked(String act) {
        List<Messages.Chip> acts = took(Suggested.class).getLast().action();
        int at = 0;
        while (!acts.get(at).label().startsWith(act)) {
            at++;
        }
        tell(Messages.PICKED_TOPIC, new Picked(Slot.ACTION, at));
    }

    @Test
    void anArchiveGoesBesideItsFilesUnderANameNotTaken() {
        started();
        showing(dir, file("a.txt", 10), file("b.txt", 20), file("Archive.zip", 5));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.txt"), path("b.txt"))));
        picked("Archive");
        assertEquals("Archive 2 files", took(Suggested.class).getLast().card().go());
        tell(Messages.CARRY_TOPIC, new Messages.Carry());
        Messages.Run run = took(Messages.Run.class).getFirst();
        assertEquals(Plan.Op.ARCHIVE, run.plan().op());
        assertEquals(path("Archive-2.zip"), run.plan().steps().getFirst().to());
    }

    @Test
    void renamingAsksForANameOnceAndEnterCarriesItOut() {
        started();
        showing(dir, file("a.txt", 10), file("b.txt", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.txt"))));
        picked("Rename");
        assertEquals(List.of(new Messages.Naming("a.txt")), seen(Messages.Naming.class));
        Suggested rail = took(Suggested.class).getLast();
        assertTrue(rail.card().naming());
        assertEquals("", rail.card().go(), "Enter in the field carries it out, not a button");

        tell(Messages.PICKED_TOPIC, new Picked(Slot.SELECT, 0));
        assertTrue(seen(Messages.Naming.class).isEmpty(), "the name is asked once, not with every rail");

        tell(Messages.RENAME_TOPIC, new Messages.Rename("b.txt"));
        assertTrue(took(Status.class).getFirst().alert(), "b.txt is taken");
        tell(Messages.RENAME_TOPIC, new Messages.Rename("c.txt"));
        Messages.Run run = took(Messages.Run.class).getFirst();
        assertEquals(List.of(new Plan.Step(path("a.txt"), path("c.txt"))), run.plan().steps());
        tell(Messages.RAN_TOPIC, new Messages.Ran(run.run(), new Done(Plan.Op.RENAME, run.plan().steps(), List.of(),
                10L, false)));
        assertEquals(List.of(new Messages.Recorded(1, "Rename a.txt to c.txt")), seen(Messages.Recorded.class));
        assertEquals("Renamed a.txt to c.txt", took(Status.class).getFirst().message());
    }

    // ------------------------------------------------------------------ marks

    @Test
    void keepingAMarkNamesItListsItAndRemembersIt() {
        started();
        showing(dir, video("a.mp4", 10), video("b.mp4", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"))));
        tell(Messages.PICKED_TOPIC, new Picked(Slot.SELECT, 1));
        picked("Mark");
        assertEquals("Keep as Mark", took(Suggested.class).getLast().card().go());
        tell(Messages.CARRY_TOPIC, new Messages.Carry());
        String name = ListFolder.name(dir) + ": All videos here · 2";
        assertEquals(List.of(new Messages.Marks(List.of(name))), seen(Messages.Marks.class));
        assertTrue(seen(Messages.Run.class).isEmpty(), "a mark changes no file");
        Messages.Mark kept = new Messages.Mark(name, Set.of(path("a.mp4"), path("b.mp4")));
        assertEquals(List.of(kept), took(Remember.class).getLast().marks());
    }

    @Test
    void aMarkRecalledShowsItsFolderWithItsRowsSelectedAndOneForgottenGoes() {
        Path sub = path("sub");
        Messages.Mark m = new Messages.Mark("sub: both", Set.of(sub.resolve("a.mp4"), sub.resolve("b.mp4")));
        tell(Messages.RECALLED_TOPIC, new Recalled(Optional.empty(), List.of(), dir, dir.resolve("trash"), List.of(m)));
        assertEquals(List.of(new Messages.Marks(List.of("sub: both"))), seen(Messages.Marks.class));
        sent.clear();

        tell(Messages.RECALL_MARK_TOPIC, new Messages.RecallMark(0));
        Listing l = asked();
        assertEquals(sub, l.folder());
        tell(Messages.LISTED_TOPIC, new Listed(l.ticket(), sub, List.of(), null, TODAY, ZoneOffset.UTC));
        assertEquals(m.paths(), took(Shown.class).getFirst().select());

        tell(Messages.FORGET_MARK_TOPIC, new Messages.ForgetMark(0));
        assertEquals(List.of(new Messages.Marks(List.of())), seen(Messages.Marks.class));
        assertEquals(List.of(), took(Remember.class).getLast().marks());
    }

    // ------------------------------------------------------------------ the Preview Dock

    @Test
    void oneFileSelectedIsPreviewedAndALatePreviewIsDropped() {
        showing(dir, file("a.txt", 10), file("b.txt", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.txt"))));
        Messages.Previewing first = took(Messages.Previewing.class).getFirst();
        assertEquals(path("a.txt"), first.path());
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("b.txt"))));
        Messages.Previewing second = took(Messages.Previewing.class).getFirst();

        Preview a = new Preview(path("a.txt"), Preview.Tier.BYTES, "unknown binary", 10,
                List.of(), 0d, List.of(), false);
        tell(Messages.PREVIEWED_TOPIC, new Messages.Previewed(first.ticket(), a));
        assertTrue(took(Messages.Docked.class).isEmpty(), "a.txt is no longer the one selected");
        Preview b = new Preview(path("b.txt"), Preview.Tier.BYTES, "unknown binary", 20,
                List.of(), 0d, List.of(), false);
        tell(Messages.PREVIEWED_TOPIC, new Messages.Previewed(second.ticket(), b));
        assertEquals(new Messages.Docked(Optional.of(b), ""), took(Messages.Docked.class).getFirst());
    }

    @Test
    void noneOrSeveralSelectedIsANote() {
        showing(dir, file("a.txt", 1024), file("b.txt", 2048));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.txt"), path("b.txt"))));
        assertEquals("2 selected · 3 KB", took(Messages.Docked.class).getLast().note());
        assertTrue(seen(Messages.Previewing.class).isEmpty());
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of()));
        assertEquals("Select one file to see it here", took(Messages.Docked.class).getLast().note());
    }

    private Row picture(String name) {
        return new Row(path(name), name, false, 10L, Instant.EPOCH, Kind.IMAGE);
    }

    private void openWith(Messages.OpenWith.With with) {
        tell(Messages.OPEN_WITH_TOPIC, new Messages.OpenWith(with));
    }

    @Test
    void openWithHandsOnTheFileInTheStripAndNothingWithNone() {
        showing(dir, picture("a.png"), file("notes.txt", 20));
        openWith(Messages.OpenWith.With.SUITED);
        assertTrue(seen(Messages.Handing.class).isEmpty(), "nothing in the strip");
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.png"))));
        openWith(Messages.OpenWith.With.SUITED);
        assertEquals(List.of(new Messages.Handing(path("a.png"), true)), took(Messages.Handing.class),
                "a picture suits Rasterware");
    }

    @Test
    void openWithSuitsTheFileUnlessTheMenuNamesAProgram() {
        showing(dir, picture("a.png"), file("notes.txt", 20));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("notes.txt"))));
        openWith(Messages.OpenWith.With.SUITED);
        assertEquals(List.of(new Messages.Handing(path("notes.txt"), false)), took(Messages.Handing.class),
                "a text suits the desktop's own program");
        openWith(Messages.OpenWith.With.RASTERWARE);
        assertEquals(List.of(new Messages.Handing(path("notes.txt"), true)), took(Messages.Handing.class));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.png"))));
        openWith(Messages.OpenWith.With.DESKTOP);
        assertEquals(List.of(new Messages.Handing(path("a.png"), false)), took(Messages.Handing.class));
    }

    @Test
    void aHandOffSaysWhatOpenedIt() {
        tell(Messages.HANDED_OFF_TOPIC, new Messages.HandedOff(path("a.png"), true, Messages.HandedOff.RASTERWARE,
                null));
        assertEquals("Opened a.png in Rasterware", took(Status.class).getFirst().message());
        tell(Messages.HANDED_OFF_TOPIC, new Messages.HandedOff(path("a.png"), true, Messages.HandedOff.DESKTOP, null));
        Status desktop = took(Status.class).getFirst();
        assertEquals("Rasterware isn't installed, so a.png opened in the desktop's own program", desktop.message());
        assertFalse(desktop.alert());
        tell(Messages.HANDED_OFF_TOPIC, new Messages.HandedOff(path("a.png"), false, Messages.HandedOff.DESKTOP, null));
        assertEquals("Opened a.png in the desktop's own program", took(Status.class).getFirst().message());
        tell(Messages.HANDED_OFF_TOPIC, new Messages.HandedOff(path("a.png"), true, Messages.HandedOff.RASTERWARE,
                "gone"));
        assertTrue(took(Status.class).getFirst().alert());
    }

    // ------------------------------------------------------------------ the folder watched

    private void stir(Path folder) {
        tell(Messages.STIRRED_TOPIC, new Messages.Stirred(Side.FROM, folder));
    }

    @Test
    void aFolderIsWatchedOnceItsRowsLandAndNotWhenItCannotBeRead() {
        tell(Messages.NAVIGATE_TOPIC, new Navigate(dir));
        assertTrue(seen(Messages.Watch.class).isEmpty(), "not before its rows land");
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), dir, List.of(), null, TODAY, ZoneOffset.UTC));
        assertEquals(List.of(new Messages.Watch(Side.FROM, dir)), took(Messages.Watch.class));
        stir(dir);
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), dir, List.of(), null, TODAY, ZoneOffset.UTC));
        assertTrue(took(Messages.Watch.class).isEmpty(), "already watched");
        tell(Messages.NAVIGATE_TOPIC, new Navigate(path("locked")));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), path("locked"), List.of(), "Access is denied", TODAY,
                ZoneOffset.UTC));
        assertTrue(seen(Messages.Watch.class).isEmpty(), "what cannot be read is not watched");
        assertEquals(1, took(Messages.Unwatch.class).size(), "and the folder left is not either");
    }

    @Test
    void aStirListsTheFolderAgainAndKeepsTheSelectionAndThePick() {
        showing(dir, video("a.mp4", 10), video("b.mov", 20), file("notes.txt", 5));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.FROM, Set.of(path("a.mp4"))));
        tell(Messages.PICKED_TOPIC, new Picked(Slot.SELECT, 1));
        sent.clear();
        stir(dir);
        Listing again = asked();
        assertEquals(dir, again.folder());
        tell(Messages.LISTED_TOPIC, new Listed(again.ticket(), dir, List.of(video("a.mp4", 4096), video("b.mov", 20),
                file("notes.txt", 5), video("c.mkv", 30)), null, TODAY, ZoneOffset.UTC));
        assertEquals(Set.of(path("a.mp4"), path("b.mov"), path("c.mkv")), reached(took(Suggested.class).getLast()),
                "the pick stands, and reaches the video that landed");
    }

    @Test
    void aStirFromAFolderNoLongerShownIsDropped() {
        Path a = path("a");
        Path b = path("b");
        showing(a);
        tell(Messages.NAVIGATE_TOPIC, new Navigate(b));
        sent.clear();
        stir(a);
        stir(b);
        assertTrue(seen(Listing.class).isEmpty(), "a left, and b not watched until its rows land");
        tell(Messages.LISTED_TOPIC, new Listed(2L, b, List.of(), null, TODAY, ZoneOffset.UTC));
        sent.clear();
        stir(a);
        assertTrue(seen(Listing.class).isEmpty(), "the folder left");
        stir(b);
        assertEquals(b, asked().folder());
    }

    @Test
    void aWatchThatEndedOnTheFolderShownIsAskedAgainWhenItIsShownAgain() {
        showing(dir);
        tell(Messages.UNWATCHED_TOPIC, new Messages.Unwatched(Side.FROM, dir, "it was moved or removed"));
        stir(dir);
        assertTrue(seen(Listing.class).isEmpty(), "no longer watched");
        tell(Messages.NAVIGATE_TOPIC, new Navigate(dir));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), dir, List.of(), null, TODAY, ZoneOffset.UTC));
        assertEquals(List.of(new Messages.Watch(Side.FROM, dir)), took(Messages.Watch.class));
    }

    @Test
    void whatLandedIsTaggedForTheFolderShownAndNoOther() {
        Path a = path("a");
        Path b = path("b");
        showing(a);
        tell(Messages.FRESHNESS_TOPIC, new Messages.Freshness(Side.FROM, a, Set.of(a.resolve("new.mp4"))));
        assertEquals(List.of(new Messages.Fresh(Side.FROM, Set.of(a.resolve("new.mp4")))), took(Messages.Fresh.class));
        tell(Messages.FRESHNESS_TOPIC, new Messages.Freshness(Side.FROM, a, Set.of(a.resolve("new.mp4"))));
        assertTrue(took(Messages.Fresh.class).isEmpty(), "told again, nothing changed");
        tell(Messages.FRESHNESS_TOPIC, new Messages.Freshness(Side.FROM, b, Set.of(b.resolve("other.txt"))));
        assertTrue(took(Messages.Fresh.class).isEmpty(), "b is not shown");
        tell(Messages.FRESHNESS_TOPIC, new Messages.Freshness(Side.FROM, a, Set.of()));
        assertEquals(List.of(new Messages.Fresh(Side.FROM, Set.of())), took(Messages.Fresh.class), "its time is up");
    }

    @Test
    void goingElsewherePutsTheTagsDown() {
        Path a = path("a");
        showing(a);
        tell(Messages.FRESHNESS_TOPIC, new Messages.Freshness(Side.FROM, a, Set.of(a.resolve("new.mp4"))));
        sent.clear();
        showing(path("b"));
        tell(Messages.FRESHNESS_TOPIC, new Messages.Freshness(Side.FROM, a, Set.of(a.resolve("late.mp4"))));
        assertTrue(seen(Messages.Fresh.class).isEmpty(), "a left");
        tell(Messages.NAVIGATE_TOPIC, new Navigate(a));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), a, List.of(), null, TODAY, ZoneOffset.UTC));
        assertTrue(took(Messages.Fresh.class).isEmpty(), "back in a, nothing has landed since it was watched again");
    }

    @Test
    void goingElsewhereSaysTheTagsAreDown() {
        Path a = path("a");
        showing(a);
        tell(Messages.FRESHNESS_TOPIC, new Messages.Freshness(Side.FROM, a, Set.of(a.resolve("new.mp4"))));
        sent.clear();
        tell(Messages.NAVIGATE_TOPIC, new Navigate(path("b")));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), path("b"), List.of(), null, TODAY, ZoneOffset.UTC));
        assertEquals(List.of(new Messages.Fresh(Side.FROM, Set.of())), took(Messages.Fresh.class));
    }

    // ------------------------------------------------------------------ Two Folders

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    /** Two Folders on, both lists showing {@code dir}'s rows, FROM used, and nothing sent remembered. */
    private void twoFolders(Row... rows) {
        showing(dir, rows);
        tell(Messages.TWO_FOLDERS_TOPIC, new Messages.TwoFolders(true));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), dir, List.of(rows), null, TODAY, UTC));
        sent.clear();
    }

    @Test
    void twoFoldersOpensToOnTheFolderFromShows() {
        showing(dir, file("a.txt", 1));
        tell(Messages.TWO_FOLDERS_TOPIC, new Messages.TwoFolders(true));
        assertEquals(new Messages.Pair(true, Side.FROM, Optional.of(dir), false), seen(Messages.Pair.class).getLast());
        Listing to = asked();
        assertEquals(dir, to.folder());
        tell(Messages.LISTED_TOPIC, new Listed(to.ticket(), dir, List.of(file("a.txt", 1)), null, TODAY, UTC));
        assertEquals(List.of(new Shown(Side.TO, dir, List.of(file("a.txt", 1)), Set.of())), seen(Shown.class));
        assertEquals(List.of(new Messages.Watch(Side.TO, dir)), seen(Messages.Watch.class));
        assertTrue(seen(Suggested.class).isEmpty(), "the rail is FROM's, which is used");
        assertEquals(new Summary(1, Optional.of(1), 0, 0L), took(Summary.class).getLast());
    }

    @Test
    void theListClickedIsTheOneTheTreeDrives() {
        twoFolders(file("a.txt", 1), folder("sub"));
        tell(Messages.CHOSEN_TOPIC, new Chosen(Side.TO, Set.of(path("a.txt"))));
        assertEquals(new Messages.Pair(true, Side.TO, Optional.of(dir), false), took(Messages.Pair.class).getLast());
        tell(Messages.NAVIGATE_TOPIC, new Navigate(path("sub")));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), path("sub"), List.of(), null, TODAY, UTC));
        assertEquals(List.of(new Shown(Side.TO, path("sub"), List.of(), Set.of())), seen(Shown.class));
        assertEquals(new Messages.Pair(true, Side.TO, Optional.of(path("sub")), true),
                took(Messages.Pair.class).getLast());
        tell(Messages.USE_TOPIC, new Messages.Use(Side.FROM));
        assertEquals(new Messages.Pair(true, Side.FROM, Optional.of(dir), false), took(Messages.Pair.class).getLast(),
                "FROM still shows the folder it did");
    }

    @Test
    void aListingLandsInTheListThatAskedForIt() {
        twoFolders();
        tell(Messages.NAVIGATE_TOPIC, new Navigate(path("x")));
        Listing forFrom = asked();
        tell(Messages.USE_TOPIC, new Messages.Use(Side.TO));
        tell(Messages.NAVIGATE_TOPIC, new Navigate(path("y")));
        Listing forTo = asked();
        tell(Messages.LISTED_TOPIC, new Listed(forTo.ticket(), path("y"), List.of(), null, TODAY, UTC));
        tell(Messages.LISTED_TOPIC, new Listed(forFrom.ticket(), path("x"), List.of(), null, TODAY, UTC));
        assertEquals(List.of(new Shown(Side.TO, path("y"), List.of(), Set.of()),
                new Shown(Side.FROM, path("x"), List.of(), Set.of())), seen(Shown.class), "in any order");
    }

    @Test
    void aStirInToListsToAgain() {
        twoFolders(file("a.txt", 1));
        tell(Messages.STIRRED_TOPIC, new Messages.Stirred(Side.TO, dir));
        Listing again = asked();
        tell(Messages.LISTED_TOPIC, new Listed(again.ticket(), dir, List.of(file("a.txt", 1), file("b.txt", 2)), null,
                TODAY, UTC));
        assertEquals(Side.TO, seen(Shown.class).getLast().side());
        assertEquals(new Summary(1, Optional.of(2), 0, 0L), took(Summary.class).getLast());
    }

    @Test
    void swapTradesTheListsAndKeepsTheFolderUsed() {
        twoFolders(file("a.txt", 1));
        tell(Messages.USE_TOPIC, new Messages.Use(Side.TO));
        tell(Messages.NAVIGATE_TOPIC, new Navigate(path("sub")));
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), path("sub"), List.of(), null, TODAY, UTC));
        sent.clear();
        tell(Messages.SWAP_TOPIC, new Messages.Swap());
        assertEquals(new Messages.Pair(true, Side.FROM, Optional.of(path("sub")), true),
                seen(Messages.Pair.class).getLast(), "sub is still used, now on the left");
        List<Shown> shown = seen(Shown.class);
        assertTrue(shown.contains(new Shown(Side.FROM, path("sub"), List.of(), Set.of())));
        assertTrue(shown.contains(new Shown(Side.TO, dir, List.of(file("a.txt", 1)), Set.of())));
        assertEquals(Set.of(new Messages.Watch(Side.FROM, path("sub")), new Messages.Watch(Side.TO, dir)),
                Set.copyOf(seen(Messages.Watch.class)), "each list's watch follows its folder");
    }

    @Test
    void backToOneFolderLetsToGoAndUsesFrom() {
        twoFolders(file("a.txt", 1));
        tell(Messages.USE_TOPIC, new Messages.Use(Side.TO));
        sent.clear();
        tell(Messages.TWO_FOLDERS_TOPIC, new Messages.TwoFolders(false));
        assertEquals(List.of(new Messages.Unwatch(Side.TO)), seen(Messages.Unwatch.class));
        assertEquals(new Messages.Pair(false, Side.FROM, Optional.of(dir), false), took(Messages.Pair.class).getLast());
        tell(Messages.STIRRED_TOPIC, new Messages.Stirred(Side.TO, dir));
        assertTrue(seen(Listing.class).isEmpty(), "there is no TO to list");
    }

    // ------------------------------------------------------------------ Back

    private void listed(Path folder) {
        tell(Messages.LISTED_TOPIC, new Listed(asked().ticket(), folder, List.of(), null, TODAY, UTC));
    }

    @Test
    void backGoesToTheFoldersShownBeforeNewestFirst() {
        tell(Messages.BACK_TOPIC, new Messages.Back());
        assertTrue(seen(Listing.class).isEmpty(), "nothing shown before");
        for (String name : List.of("a", "b", "c")) {
            tell(Messages.NAVIGATE_TOPIC, new Navigate(path(name)));
            listed(path(name));
        }
        assertTrue(took(Messages.Pair.class).getLast().back(), "Back has somewhere to go");
        tell(Messages.BACK_TOPIC, new Messages.Back());
        assertEquals(path("b"), seen(Listing.class).getLast().folder());
        listed(path("b"));
        tell(Messages.BACK_TOPIC, new Messages.Back());
        assertEquals(path("a"), seen(Listing.class).getLast().folder());
        listed(path("a"));
        assertFalse(took(Messages.Pair.class).getLast().back(), "a was the first");
    }

    @Test
    void backKeepsTwentyAndForgetsNoneForARelisting() {
        for (int i = 0; i < 25; i++) {
            tell(Messages.NAVIGATE_TOPIC, new Navigate(path("f" + i)));
            listed(path("f" + i));
        }
        tell(Messages.STIRRED_TOPIC, new Messages.Stirred(Side.FROM, path("f24")));
        listed(path("f24"));
        sent.clear();
        for (int i = 23; i >= 4; i--) {
            tell(Messages.BACK_TOPIC, new Messages.Back());
            Listing back = asked();
            assertEquals(path("f" + i), back.folder());
            tell(Messages.LISTED_TOPIC, new Listed(back.ticket(), back.folder(), List.of(), null, TODAY, UTC));
        }
        tell(Messages.BACK_TOPIC, new Messages.Back());
        assertTrue(seen(Listing.class).isEmpty(), "twenty kept, and a stir's listing added none");
    }
}
