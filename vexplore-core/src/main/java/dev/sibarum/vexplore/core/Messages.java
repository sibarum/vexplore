package dev.sibarum.vexplore.core;

import sibarum.atchung.Topic;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every message the application's parts send each other: what each one says, and the topic it travels on.
 *
 * <h2>Message passing is how the parts talk</h2>
 *
 * <p>The application is three kinds of part, and none of them holds another. The {@link Explorer} decides; it owns the
 * session, and changes it only by running its own declared steps. The view, in the {@code -gui} module, shows; it
 * owns the widgets. The jobs ({@link ListFolder}, {@link FindPath} and the rest) touch the disk, on
 * the job lane. Each sends a record on a topic and the others hear it on their own lane, so no state is
 * shared and nothing needs a lock.
 *
 * <p>A record here is what crossed, and it crosses as itself: the processor refuses a payload that is not deeply
 * immutable (T2.4). A list may cross when what it holds may, and the list itself must not change after it is sent,
 * which no type can show: so each record that holds one copies it in its constructor, and no sender can get that
 * wrong.
 *
 * <h2>Reading this file</h2>
 *
 * <p>Grouped by direction, each record beside its topic. A channel is its record's type: each topic is named by its
 * payload ({@code Topic.edge(Go.class)}), and every part refers to it by the type, a {@code @Subscribe} method's
 * parameter, a {@code Sender<Go>}, a job's {@code run}, a class literal in {@code @Hears}. There is no topic name
 * anywhere to misspell, and a record with no topic here is a compile error wherever it is heard or sent (T4.1).
 */
public final class Messages {

    private Messages() {
    }

    /**
     * Which list: FROM, the one there always is, on the left; TO, the second, on the right in Two Folders. A list's
     * messages say which it is, since both may show one folder and a path cannot tell them apart.
     */
    public enum Side {
        FROM, TO;

        /** The list across from this one. */
        public Side other() {
            return this == FROM ? TO : FROM;
        }
    }

    // ------------------------------------------------------------------ the view asks the explorer

    /** The path field was submitted: what the text names is the explorer's call. */
    public static final Topic<Go> GO_TOPIC = Topic.edge(Go.class);

    public record Go(String text) {
    }

    /**
     * Show this folder: a breadcrumb segment, a place, Enter on a folder's row, Backspace or Alt+Up, in the list used;
     * or a pane's own Up or Choose folder in Two Folders, in that pane ({@code side}), which it then uses.
     */
    public static final Topic<Navigate> NAVIGATE_TOPIC = Topic.edge(Navigate.class);

    public record Navigate(Optional<Side> side, Path folder) {

        /** In the list used. */
        public Navigate(Path folder) {
            this(Optional.empty(), folder);
        }
    }

    /**
     * The order a list shows its rows in, as its table last said, header clicks and all: what the explorer reasons in
     * when it reasons about what is next to what (Shift's ranges). Keyed by the list, so each keeps its latest.
     */
    public static final Topic<Ordered> ORDERED_TOPIC = Topic.state(Ordered.class, Ordered::side);

    public record Ordered(Side side, Order order) {
    }

    /** What is selected in a list, as it last said: its rows turned back into their paths by the view. */
    public static final Topic<Chosen> CHOSEN_TOPIC = Topic.edge(Chosen.class);

    public record Chosen(Side side, Set<Path> paths) {
        public Chosen {
            paths = Set.copyOf(paths);
        }
    }

    /** Enter or a double click on a row: a folder is gone into, a file opened. */
    public static final Topic<Activated> ACTIVATED_TOPIC = Topic.edge(Activated.class);

    public record Activated(Side side, Path path) {
    }

    /**
     * The top bar's Back, or Alt+Left: the folder the list used showed before this one; or a pane's own Back in Two
     * Folders, that pane's ({@code side}), which it then uses.
     */
    public static final Topic<Back> BACK_TOPIC = Topic.edge(Back.class);

    public record Back(Optional<Side> side) {

        /** In the list used. */
        public Back() {
            this(Optional.empty());
        }
    }

    /** The top bar's Two folders ({@code on}) or Back to one folder. */
    public static final Topic<TwoFolders> TWO_FOLDERS_TOPIC = Topic.edge(TwoFolders.class);

    public record TwoFolders(boolean on) {
    }

    /** The top bar's Swap sides: FROM shows what TO did, and TO what FROM did. */
    public static final Topic<Swap> SWAP_TOPIC = Topic.edge(Swap.class);

    public record Swap() {
    }

    /** A click in a list: the tree, the breadcrumb, the rail and the dock now work from that one. */
    public static final Topic<Use> USE_TOPIC = Topic.edge(Use.class);

    public record Use(Side side) {
    }

    /**
     * Rows of one list dropped on the other, by path, in the order shown: they go into the folder {@code onto} shows,
     * moved within one drive and copied across two, as {@link Pair} said while they were carried.
     */
    public static final Topic<Dropped> DROPPED_TOPIC = Topic.edge(Dropped.class);

    public record Dropped(Side onto, List<Path> held) {
        public Dropped {
            held = List.copyOf(held);
        }
    }

    /** Which row of the rail a chip is in. Each knows how a chip at its place changes the pick. */
    public enum Slot {
        SELECT {
            @Override
            Suggestions.Pick press(Suggestions.Rail rail, Suggestions.Pick pick, int index) {
                return index < rail.scopes().size() ? pick.with(rail.scopes().get(index).value()) : pick;
            }
        },
        CONDITION {
            @Override
            Suggestions.Pick press(Suggestions.Rail rail, Suggestions.Pick pick, int index) {
                return index < rail.conditions().size() ? pick.with(rail.conditions().get(index).value()) : pick;
            }
        },
        ACTION {
            @Override
            Suggestions.Pick press(Suggestions.Rail rail, Suggestions.Pick pick, int index) {
                return index < rail.acts().size() ? pick.with(rail.acts().get(index)) : pick;
            }
        },
        /** A destination: the explorer's to resolve against the places it offered, so the pick is left as it is. */
        DESTINATION {
            @Override
            Suggestions.Pick press(Suggestions.Rail rail, Suggestions.Pick pick, int index) {
                return pick;
            }
        };

        /** The pick once the chip at {@code index} of this row of {@code rail} is pressed; unchanged past its end. */
        abstract Suggestions.Pick press(Suggestions.Rail rail, Suggestions.Pick pick, int index);
    }

    /**
     * A chip of the rail was pressed: its row and its place there. The explorer resolves the place against the rail it
     * last sent, which is the one the person saw (docs/shape.md, <i>Why {@code Picked} is an index</i>).
     */
    public static final Topic<Picked> PICKED_TOPIC = Topic.edge(Picked.class);

    public record Picked(Slot slot, int index) {
    }

    /**
     * A row menu's choice, about the selection, which the right click has already made: {@link #OPEN}, or the
     * {@linkplain Suggestions.Act#name() name} of one of the rail's actions, which the menu picks on the rail rather
     * than carrying out, so the card states its effect first as every other way in does.
     */
    public static final Topic<Menu> MENU_TOPIC = Topic.edge(Menu.class);

    public record Menu(Side side, String action) {
        public static final String OPEN = "open";
    }

    /** The card's button: carry out what the card states. */
    public static final Topic<Carry> CARRY_TOPIC = Topic.edge(Carry.class);

    public record Carry() {
    }

    /** The card's Stop, while a run goes: ends {@link RunAction}'s run between two steps. */
    public static final Topic<Stop> STOP_TOPIC = Topic.edge(Stop.class);

    public record Stop() {
    }

    /**
     * An offer's button, in Two Folders' rail: check its copies, carry it out, or stop the check, as the explorer's
     * state says. The view never knows which.
     */
    public static final Topic<Accept> ACCEPT_TOPIC = Topic.edge(Accept.class);

    public record Accept(Offer.Kind kind) {
    }

    /**
     * The dock's <em>Keep newer</em> or <em>Keep both</em>, with the pair it showed, so a press that crosses a new
     * listing is checked against what the person saw.
     */
    public static final Topic<Resolve> RESOLVE_TOPIC = Topic.edge(Resolve.class);

    public record Resolve(Twins twins, Keep keep) {

        /** How a conflict is settled. */
        public enum Keep {
            /** One copy, the newer, in TO; the older into the trash. */
            NEWER,
            /** FROM's copy into TO under the first free {@code name (2).ext}. */
            BOTH
        }
    }

    /**
     * The window's history applied an entry: take back what entry {@code entry} did ({@link Undo}), or do it again
     * ({@link Redo}). The history holds only the number; what the entry did is the explorer's.
     */
    public static final Topic<Undo> UNDO_TOPIC = Topic.edge(Undo.class);

    public record Undo(long entry) {
    }

    public static final Topic<Redo> REDO_TOPIC = Topic.edge(Redo.class);

    public record Redo(long entry) {
    }

    /** A folder picked for a move or a copy, by {@code Choose folder…}: the destination from now on. */
    public static final Topic<Destine> DESTINE_TOPIC = Topic.edge(Destine.class);

    public record Destine(Path folder) {
    }

    /** The rename field submitted: give the one file the rail reaches this name. */
    public static final Topic<Rename> RENAME_TOPIC = Topic.edge(Rename.class);

    public record Rename(String name) {
    }

    /** A mark of the rail pressed: select its rows again, in the folder they are in. */
    public static final Topic<RecallMark> RECALL_MARK_TOPIC = Topic.edge(RecallMark.class);

    public record RecallMark(int index) {
    }

    /** A mark's × pressed: forget it. */
    public static final Topic<ForgetMark> FORGET_MARK_TOPIC = Topic.edge(ForgetMark.class);

    public record ForgetMark(int index) {
    }

    // ------------------------------------------------------------------ the explorer tells the view

    /**
     * One row of a folder, read once by {@link ListFolder}: the list draws these and never touches the disk again, so a
     * slow or vanished volume costs a listing and not a frame. Its name and kind are worked out in the job, so nothing
     * on the GUI thread works them out per row.
     *
     * @param size     bytes; zero for a folder, whose size is a question a listing does not ask
     * @param modified last modification; the epoch for a row that could not be read
     */
    public record Row(Path path, String name, boolean folder, long size, Instant modified, Kind kind) {

        /** What the Type column says: the extension in capitals, {@code FILE} for none, or {@code FOLDER}. */
        public String typeLabel() {
            if (folder) {
                return "FOLDER";
            }
            String e = Kind.extension(name);
            return e.isEmpty() ? "FILE" : e.toUpperCase(java.util.Locale.ROOT);
        }
    }

    /**
     * What a list shows: a folder, its rows in the listing's order, and the rows to select once they are in (a file
     * named on the command line or typed, or a mark's rows). Keyed by the list, so each keeps its latest. The
     * breadcrumb follows {@link Pair}, the folder of the list used.
     */
    public static final Topic<Shown> SHOWN_TOPIC = Topic.state(Shown.class, Shown::side);

    public record Shown(Side side, Path folder, List<Row> rows, Set<Path> select) {
        public Shown {
            rows = List.copyOf(rows);
            select = Set.copyOf(select);
        }
    }

    /**
     * The status line's counts, for the view to put into words: the used list's rows, the other's in Two Folders, and
     * of the used list's, the rows selected and the selected files' bytes.
     */
    public static final Topic<Summary> SUMMARY_TOPIC = Topic.state(Summary.class);

    public record Summary(int items, Optional<Integer> beside, int selected, long bytes) {
    }

    /**
     * Whether there are two lists, which is used, the used list's folder, which the breadcrumb and the path field show
     * (none before the first listing), whether that list has a folder to go Back to, and what a drop from one list on
     * the other does: {@code MOVE} within one drive, {@code COPY} across two, as the lists declare it. The latest is all
     * that matters.
     */
    public static final Topic<Pair> PAIR_TOPIC = Topic.state(Pair.class);

    public record Pair(boolean two, Side used, Optional<Path> at, boolean back, Plan.Op drop) {
    }

    /** Say something on the status line. {@code alert}: something was refused or failed, so draw the eye to it. */
    public static final Topic<Status> STATUS_TOPIC = Topic.edge(Status.class);

    public record Status(String message, boolean alert) {
    }

    /** One chip of the rail, worded: what it says, how many rows it reaches (0 for an action), whether it is picked. */
    public record Chip(String label, int count, boolean on) {
    }

    /**
     * The rail's card: the effect first ({@code Move 9 files · 6.5 GB}), then the first few names. {@code go} is the
     * button's words, or empty when there is nothing it can do yet; {@code running} is how far a run has got
     * ({@code Deleting · 3 of 9}), or empty when none goes, and while one does the button is its Stop. {@code naming}:
     * the card holds a field for a new name, for a rename, whose Enter carries it out.
     */
    public record Card(String headline, String detail, String go, String running, boolean naming) {

        public static final Card NONE = new Card("", "", "", "", false);
    }

    /**
     * The whole Suggestion Rail: what it is based on (the lead file's name), each row of chips, the rows the effect
     * would reach (marked in the list, never selected), and the card. {@link #NONE} when nothing is selected.
     * {@code proposing}: while Shift or Control is held, what the Which files row offers instead, in the words its
     * heading takes ({@code a range}, {@code a rule}); empty otherwise.
     */
    public static final Topic<Suggested> SUGGESTED_TOPIC = Topic.state(Suggested.class);

    public record Suggested(String basis, List<Chip> select, List<Chip> condition, List<Chip> action,
                            Set<Row> reach, Card card, boolean placing, List<Chip> places, String proposing) {

        /** The same rail, its card saying how far a run has got. */
        public Suggested running(String words) {
            return new Suggested(basis, select, condition, action, reach,
                    new Card(card.headline(), card.detail(), card.go(), words, card.naming()), placing, places,
                    proposing);
        }

        /**
         * The same rail with its Destination row ({@code placing}: shown, for a move or a copy) and the card that goes
         * with where they would go.
         */
        public Suggested placed(boolean placing, List<Chip> places, Card card) {
            return new Suggested(basis, select, condition, action, reach, card, placing, places, proposing);
        }

        /** The same rail, its Which files row what a held key proposes ({@code how}), as {@code chips}. */
        public Suggested proposed(String how, List<Chip> chips) {
            return new Suggested(basis, chips, condition, action, reach, card, placing, places, how);
        }

        /** Nothing selected: an empty rail, and no row marked. */
        public static final Suggested NONE = new Suggested("", List.of(), List.of(), List.of(), Set.of(),
                Card.NONE, false, List.of(), "");

        public Suggested {
            select = List.copyOf(select);
            condition = List.copyOf(condition);
            action = List.copyOf(action);
            reach = Set.copyOf(reach);
            places = List.copyOf(places);
        }

        /** Whether there is nothing to suggest. */
        public boolean empty() {
            return select.isEmpty();
        }
    }

    /**
     * A run landed that can be taken back: the view puts entry {@code entry} in the window's history, as
     * {@code words} ({@code Delete 4 files}), so Ctrl+Z sends its {@link Undo}.
     */
    /**
     * Rename was picked, or the file it reaches changed: the view fills the card's field with {@code name}, once, and
     * the person types over it. Sent once, and not with every rail, so what is typed is never written over.
     */
    /**
     * A named set of files kept for later: the rail's reach when it was kept, by path. Its rows are all in one folder,
     * the one it was kept in.
     */
    public record Mark(String name, Set<Path> paths) {
        public Mark {
            paths = Set.copyOf(paths);
        }
    }

    /** The marks kept, newest first, by name: the rail lists them, and a press is said by its place. */
    public static final Topic<Marks> MARKS_TOPIC = Topic.state(Marks.class);

    public record Marks(List<String> names) {
        public Marks {
            names = List.copyOf(names);
        }
    }

    /**
     * What the Preview Dock shows: one file's preview, or, with none, a note ({@code Select one file to see it here},
     * {@code 3 selected · 4.2 MB}). The latest is all that matters.
     */
    public static final Topic<Docked> DOCKED_TOPIC = Topic.state(Docked.class);

    public record Docked(Optional<Preview> preview, String note) {
    }

    /**
     * The conflict the dock shows in Two Folders, in place of its preview: the pair ({@code here} FROM's row,
     * {@code there} TO's), which copy is newer, none when their times agree, and the column's words. {@link #NONE}
     * outside Two Folders or with no conflict to show. The latest is all that matters.
     */
    public static final Topic<Conflict> CONFLICT_TOPIC = Topic.state(Conflict.class);

    public record Conflict(Optional<Twins> twins, Optional<Side> newer, String note) {

        public static final Conflict NONE = new Conflict(Optional.empty(), Optional.empty(), "");
    }

    /**
     * The rows to tag <em>new</em>: what landed in the folder shown lately, by path. Tagged, never selected or marked.
     * The latest is all that matters.
     */
    public static final Topic<Fresh> FRESH_TOPIC = Topic.state(Fresh.class, Fresh::side);

    public record Fresh(Side side, Set<Path> paths) {
        public Fresh {
            paths = Set.copyOf(paths);
        }
    }

    /**
     * In Two Folders, how each of a list's rows stands to the other list, by path ({@link Compare}): what group it is
     * shown in. Keyed by the list, as {@link Shown} is; empty in one folder.
     */
    public static final Topic<Compared> COMPARED_TOPIC = Topic.state(Compared.class, Compared::side);

    public record Compared(Side side, Map<Path, Relation> rows) {
        public Compared {
            rows = Map.copyOf(rows);
        }
    }

    /**
     * Two Folders' rail, from the comparison: FROM's counts for the tiles, and the offers, each a card of its own with
     * its own button. None in one folder. The latest is all that matters.
     */
    public static final Topic<Offers> OFFERS_TOPIC = Topic.state(Offers.class);

    public record Offers(Tally tally, List<Offer> offers) {

        public static final Offers NONE = new Offers(Tally.NONE, List.of());

        public Offers {
            offers = List.copyOf(offers);
        }
    }

    /**
     * FROM's rows by group, for the tiles: {@code here} is FROM's folder's name, the first tile's
     * ({@code only in Downloads}).
     */
    public record Tally(String here, int onlyHere, int different, int identical, int probably) {

        public static final Tally NONE = new Tally("", 0, 0, 0, 0);
    }

    /**
     * One offer: what it does, its card ({@link Card}'s shape: the effect, the first names, the button's words, and
     * how far it has got while it goes), and the rows it reaches, which the list marks while nothing is selected.
     */
    public record Offer(Kind kind, Card card, Set<Row> reach) {

        public Offer {
            reach = Set.copyOf(reach);
        }

        /** What an offer does. */
        public enum Kind {
            /** Move, or copy across two drives, what is only in FROM into TO's folder. */
            MOVE_ONLY_HERE,
            /** Remove FROM's identical copies, once each has been read against its copy in TO. */
            REMOVE_IDENTICAL
        }
    }

    public static final Topic<Naming> NAMING_TOPIC = Topic.edge(Naming.class);

    public record Naming(String name) {
    }

    public static final Topic<Recorded> RECORDED_TOPIC = Topic.edge(Recorded.class);

    public record Recorded(long entry, String words) {
    }

    // ------------------------------------------------------------------ the explorer and the jobs

    /** Why a path is being looked at, carried through the job so its answer can be acted on. */
    public enum Why {
        /** Typed into the path bar. */
        GO,
        /** The folder the last session showed. */
        RESTORE_FOLDER,
        /** A path on the command line, which may be a folder or a file. */
        ARGUMENT
    }

    /** What is at a path ({@link FindPath}). Not a {@link Kind}, which is what a file is once it is known to be one. */
    public enum Is {
        FOLDER, FILE,
        /** Nothing there. */
        NOWHERE
    }

    /** Look at what is at a path. */
    public static final Topic<Find> FIND_TOPIC = Topic.edge(Find.class);

    public record Find(Path path, Why why) {
    }

    public static final Topic<Found> FOUND_TOPIC = Topic.edge(Found.class);

    public record Found(Path path, Why why, Is is) {
    }

    /**
     * List a folder for the list ({@link ListFolder}). {@code ticket} is the explorer's count of what it has asked, so a
     * reply that has been overtaken by a later ask is known by its ticket and dropped.
     */
    public static final Topic<Listing> LISTING_TOPIC = Topic.edge(Listing.class);

    public record Listing(long ticket, Path folder) {
    }

    /**
     * A folder's rows, folders first and then files, each by name. {@code problem} is why it could not be read, or null:
     * a folder that cannot be listed is said, never thrown. {@code today} and {@code zone} are when it was listed: the
     * rail's "today" needs a date, and a step may not read the clock.
     */
    public static final Topic<Listed> LISTED_TOPIC = Topic.edge(Listed.class);

    public record Listed(long ticket, Path folder, List<Row> rows, String problem, LocalDate today, ZoneId zone) {
        public Listed {
            rows = List.copyOf(rows);
        }
    }

    /**
     * The preview strip's Open with: the file it previews, opened by {@code with}. A left click is the program that
     * suits the file; the button's menu names one.
     */
    public static final Topic<OpenWith> OPEN_WITH_TOPIC = Topic.edge(OpenWith.class);

    public record OpenWith(With with) {

        /** Which program: the one that suits the file (Rasterware for a picture or a video), or the one named. */
        public enum With {
            SUITED, RASTERWARE, DESKTOP
        }
    }

    /**
     * Hand a file to Rasterware where it is installed, else to the desktop's opener, or, not {@code rasterware}, to the
     * desktop's opener only ({@code -gui}'s {@code HandOff}). Its own reply, not the framework's {@code Opened}: two jobs answering
     * one record would share one topic, and the explorer could not tell which had opened it.
     */
    public static final Topic<Handing> HANDING_TOPIC = Topic.edge(Handing.class);

    public record Handing(Path file, boolean rasterware) {
    }

    /**
     * What a hand-off came to: {@code to} is what opened it, {@link #RASTERWARE} or {@link #DESKTOP}; {@code rasterware}
     * whether Rasterware was asked; and {@code problem} is why nothing did, or null.
     */
    public static final Topic<HandedOff> HANDED_OFF_TOPIC = Topic.edge(HandedOff.class);

    public record HandedOff(Path file, boolean rasterware, String to, String problem) {
        public static final String RASTERWARE = "Rasterware";
        public static final String DESKTOP = "desktop";
    }

    /** A folder nearby that already holds files of a kind: a place a move or a copy probably wants. */
    public record Dest(Path path, String reason, int count) {
    }

    /** Where files of {@code kind} shown in {@code folder} probably want to go ({@link SuggestDestinations}). */
    public static final Topic<Destining> DESTINING_TOPIC = Topic.edge(Destining.class);

    public record Destining(long ticket, Path folder, Kind kind) {
    }

    /** The places for ticket {@code ticket}, best first; often few, sometimes none. */
    public static final Topic<Destinations> DESTINATIONS_TOPIC = Topic.edge(Destinations.class);

    public record Destinations(long ticket, List<Dest> places) {
        public Destinations {
            places = List.copyOf(places);
        }
    }

    /**
     * What a move or a copy of {@code targets} into {@code destination} would do ({@link PlanAction}): it reads the
     * destination for names already taken.
     */
    public static final Topic<Planning> PLANNING_TOPIC = Topic.edge(Planning.class);

    public record Planning(long ticket, Plan.Op op, List<Row> targets, Path destination) {
        public Planning {
            targets = List.copyOf(targets);
        }
    }

    public static final Topic<Planned> PLANNED_TOPIC = Topic.edge(Planned.class);

    public record Planned(long ticket, Plan plan) {
    }

    /** Preview one file for the dock ({@link ReadPreview}), ticketed: one for a file since unselected is dropped. */
    public static final Topic<Previewing> PREVIEWING_TOPIC = Topic.edge(Previewing.class);

    public record Previewing(long ticket, Path path) {
    }

    public static final Topic<Previewed> PREVIEWED_TOPIC = Topic.edge(Previewed.class);

    public record Previewed(long ticket, Preview preview) {
    }

    /**
     * Carry out a plan, forward or back ({@link RunAction}): run {@code run}, the explorer's count of its runs, so its
     * count and its answer are known as its own. A delete goes into {@code trash}.
     */
    public static final Topic<Run> RUN_TOPIC = Topic.edge(Run.class);

    public record Run(long run, Plan plan, Path trash) {
    }

    /** How far run {@code run} has got: {@code done} of {@code total} steps. The latest is all that matters. */
    public static final Topic<Progress> PROGRESS_TOPIC = Topic.state(Progress.class);

    public record Progress(long run, int done, int total) {
    }

    /** What run {@code run} did, stopped or not. */
    public static final Topic<Ran> RAN_TOPIC = Topic.edge(Ran.class);

    public record Ran(long run, Done done) {
    }

    /** Two copies of a file, FROM's ({@code here}) and TO's ({@code there}), as each list last listed it. */
    public record Twins(Row here, Row there) {
    }

    /** Read these pairs and say which match byte for byte ({@link CheckSame}): run {@code run}. */
    public static final Topic<Check> CHECK_TOPIC = Topic.edge(Check.class);

    public record Check(long run, List<Twins> twins) {
        public Check {
            twins = List.copyOf(twins);
        }
    }

    /** How far check {@code run} has got: {@code done} of {@code total} pairs. The latest is all that matters. */
    public static final Topic<Checking> CHECKING_TOPIC = Topic.state(Checking.class);

    public record Checking(long run, int done, int total) {
    }

    /**
     * What check {@code run} found, stopped or not: the pairs that match and the pairs that differ. A pair it could
     * not read is in neither, and {@code problem} says why, for the first; null when every pair was read.
     */
    public static final Topic<Checked> CHECKED_TOPIC = Topic.edge(Checked.class);

    public record Checked(long run, Set<Twins> same, Set<Twins> differ, String problem) {
        public Checked {
            same = Set.copyOf(same);
            differ = Set.copyOf(differ);
        }
    }

    /** Stop the check in flight, between two pairs ({@link CheckSame}'s {@code stop}). */
    public static final Topic<StopCheck> STOP_CHECK_TOPIC = Topic.edge(StopCheck.class);

    public record StopCheck() {
    }

    /**
     * Watch the folder a list shows ({@link WatchFolder}). Keyed by the list, so each has a run, and a new one for a
     * list stops its last.
     */
    public static final Topic<Watch> WATCH_TOPIC = Topic.edge(Watch.class, Watch::side);

    public record Watch(Side side, Path folder) {
    }

    /** Watch nothing for a list: the folder asked for could not be read, and the one before it is no longer shown. */
    public static final Topic<Unwatch> UNWATCH_TOPIC = Topic.edge(Unwatch.class, Unwatch::side);

    public record Unwatch(Side side) {
    }

    /** Why the watch on {@code folder} ended: stopped, or {@code problem} (the folder went), else null. */
    public static final Topic<Unwatched> UNWATCHED_TOPIC = Topic.edge(Unwatched.class);

    public record Unwatched(Side side, Path folder, String problem) {
    }

    /** Something in {@code folder} changed on the disk: list it again. Sent once a burst is over, not per file. */
    public static final Topic<Stirred> STIRRED_TOPIC = Topic.edge(Stirred.class);

    public record Stirred(Side side, Path folder) {
    }

    /**
     * What landed in {@code folder} lately ({@link WatchFolder}), the whole set each time it grows or something leaves
     * it. A state topic: only the latest matters.
     */
    public static final Topic<Freshness> FRESHNESS_TOPIC = Topic.state(Freshness.class, Freshness::side);

    public record Freshness(Side side, Path folder, Set<Path> fresh) {
        public Freshness {
            fresh = Set.copyOf(fresh);
        }
    }

    /** Read back what the last session remembered, and what the command line named ({@link ReadSession}). */
    public static final Topic<Recall> RECALL_TOPIC = Topic.edge(Recall.class);

    public record Recall() {
    }

    /**
     * What there is to bring back, every path absolute and normalised: the folder shown, the command line's paths, and
     * the folder the application started in, for when there is none, where a delete puts things, and the marks kept.
     */
    public static final Topic<Recalled> RECALLED_TOPIC = Topic.edge(Recalled.class);

    public record Recalled(Optional<Path> folder, List<Path> arguments, Path started, Path trash, List<Mark> marks) {
        public Recalled {
            arguments = List.copyOf(arguments);
            marks = List.copyOf(marks);
        }
    }

    /**
     * Keep the session for next time ({@link WriteSession}): the folder shown, and the marks. A value, so the explorer
     * tells whether it changed with {@code equals}.
     */
    public static final Topic<Remember> REMEMBER_TOPIC = Topic.edge(Remember.class);

    public record Remember(Optional<Path> folder, List<Mark> marks) {
        public Remember {
            marks = List.copyOf(marks);
        }
    }

    // ------------------------------------------------------------------ the view and its jobs: the places

    /*
     * The places beside the list (Orbit's sidebar, M8) are the view's and its job's, never the explorer's: the view asks
     * once, and a place clicked is a Navigate like any other.
     */

    /** The places ({@link ListPlaces}). */
    public static final Topic<PlacesWanted> PLACES_WANTED_TOPIC = Topic.edge(PlacesWanted.class);

    public record PlacesWanted() {
    }

    /** A place: where it is, and what its row says ({@code Documents}, {@code C:}). */
    public record Place(Path path, String label) {
    }

    /** The person's own folders that exist, then the drives. */
    public static final Topic<Places> PLACES_TOPIC = Topic.edge(Places.class);

    public record Places(List<Place> places) {
        public Places {
            places = List.copyOf(places);
        }
    }
}
