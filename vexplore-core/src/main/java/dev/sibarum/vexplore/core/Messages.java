package dev.sibarum.vexplore.core;

import sibarum.atchung.Topic;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Every message the application's parts send each other: what each one says, and the topic it travels on.
 *
 * <h2>Message passing is how the parts talk</h2>
 *
 * <p>The application is three kinds of part, and none of them holds another. The {@link Explorer} decides; it owns the
 * session, and changes it only by running its own declared steps. The view, in the {@code -gui} module, shows; it
 * owns the widgets. The jobs ({@link ListFolder}, {@link FindPath}, {@link Launch} and the rest) touch the disk, on
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

    // ------------------------------------------------------------------ the view asks the explorer

    /** The path field was submitted: what the text names is the explorer's call. */
    public static final Topic<Go> GO_TOPIC = Topic.edge(Go.class);

    public record Go(String text) {
    }

    /** Show this folder: a breadcrumb segment, a tree row, Enter on a folder's row, Backspace or Alt+Up. */
    public static final Topic<Navigate> NAVIGATE_TOPIC = Topic.edge(Navigate.class);

    public record Navigate(Path folder) {
    }

    /** What is selected in the list, as the list last said: its rows turned back into their paths by the view. */
    public static final Topic<Chosen> CHOSEN_TOPIC = Topic.edge(Chosen.class);

    public record Chosen(Set<Path> paths) {
        public Chosen {
            paths = Set.copyOf(paths);
        }
    }

    /** Enter or a double click on a row: a folder is gone into, a file opened. */
    public static final Topic<Activated> ACTIVATED_TOPIC = Topic.edge(Activated.class);

    public record Activated(Path path) {
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
     * What the list shows: a folder, its rows in the listing's order, and a row to select once they are in (a file named
     * on the command line, or typed). The breadcrumb is the folder's own parts. The latest is all that matters.
     */
    public static final Topic<Shown> SHOWN_TOPIC = Topic.state(Shown.class);

    public record Shown(Path folder, List<Row> rows, Optional<Path> select) {
        public Shown {
            rows = List.copyOf(rows);
        }
    }

    /** The status line's counts, for the view to put into words: rows, rows selected, and the selected files' bytes. */
    public static final Topic<Summary> SUMMARY_TOPIC = Topic.state(Summary.class);

    public record Summary(int items, int selected, long bytes) {
    }

    /** Say something on the status line. {@code alert}: something was refused or failed, so draw the eye to it. */
    public static final Topic<Status> STATUS_TOPIC = Topic.edge(Status.class);

    public record Status(String message, boolean alert) {
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
     * a folder that cannot be listed is said, never thrown.
     */
    public static final Topic<Listed> LISTED_TOPIC = Topic.edge(Listed.class);

    public record Listed(long ticket, Path folder, List<Row> rows, String problem) {
        public Listed {
            rows = List.copyOf(rows);
        }
    }

    /** Open a file as the shell would, association and all ({@link Launch}). */
    public static final Topic<Start> START_TOPIC = Topic.edge(Start.class);

    public record Start(Path file) {
    }

    /** {@code problem} is why it could not be opened, or null. */
    public static final Topic<Started> STARTED_TOPIC = Topic.edge(Started.class);

    public record Started(Path file, String problem) {
    }

    /** Read back what the last session remembered, and what the command line named ({@link ReadSession}). */
    public static final Topic<Recall> RECALL_TOPIC = Topic.edge(Recall.class);

    public record Recall() {
    }

    /**
     * What there is to bring back, every path absolute and normalised: the folder shown, the command line's paths, and
     * the folder the application started in, for when there is none.
     */
    public static final Topic<Recalled> RECALLED_TOPIC = Topic.edge(Recalled.class);

    public record Recalled(Optional<Path> folder, List<Path> arguments, Path started) {
        public Recalled {
            arguments = List.copyOf(arguments);
        }
    }

    /**
     * Keep the session for next time ({@link WriteSession}): the folder shown. A value, so the explorer tells whether it
     * changed with {@code equals}.
     */
    public static final Topic<Remember> REMEMBER_TOPIC = Topic.edge(Remember.class);

    public record Remember(Optional<Path> folder) {
    }

    // ------------------------------------------------------------------ the view and its jobs: the tree

    /*
     * The tree of places is the view's and its jobs', never the explorer's: the tree asks for a level, the view asks a
     * job, and the answer goes back to the tree. A row clicked there is a Navigate like any other.
     */

    /** The tree's top ({@link ListPlaces}). */
    public static final Topic<PlacesWanted> PLACES_WANTED_TOPIC = Topic.edge(PlacesWanted.class);

    public record PlacesWanted() {
    }

    /** A place at the tree's top: where it is, and what its row says ({@code Documents}, {@code C:}). */
    public record Place(Path path, String label) {
    }

    /** The person's own folders that exist, then the drives. */
    public static final Topic<Places> PLACES_TOPIC = Topic.edge(Places.class);

    public record Places(List<Place> places) {
        public Places {
            places = List.copyOf(places);
        }
    }

    /**
     * The subfolders of {@code folder}, reached in the tree from the place at {@code place} ({@link ListLevel}). The
     * place is carried through so the same folder under two places is two rows, not one row in two places.
     */
    public static final Topic<LevelWanted> LEVEL_WANTED_TOPIC = Topic.edge(LevelWanted.class);

    public record LevelWanted(Path place, Path folder) {
    }

    /** {@code folders}, by name; empty for a folder that has none or cannot be read. */
    public static final Topic<Level> LEVEL_TOPIC = Topic.edge(Level.class);

    public record Level(Path place, Path folder, List<Path> folders) {
        public Level {
            folders = List.copyOf(folders);
        }
    }
}
