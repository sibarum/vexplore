package dev.sibarum.vexplore.core;

import sibarum.atchung.Topic;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Every message the application's parts send each other: what each one says, and the topic it travels on.
 *
 * <h2>Message passing is how the parts talk</h2>
 *
 * <p>The application is three kinds of part, and none of them holds another. The {@link Editor} decides; it owns the
 * session, and changes it only by running its own declared steps. The view, in the {@code -gui} module, shows; it
 * owns the widgets. The jobs ({@link FindPath}, {@link ReadSession}, {@link WriteSession}, {@link ListFolder}) touch
 * the disk, on the job lane. Each sends a record on a topic and the others hear it on their own lane, so no state is
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

    // ------------------------------------------------------------------ the view asks the editor

    /** The path bar was submitted, or the navigator asked to show a folder: what the text names is the editor's call. */
    public static final Topic<Go> GO_TOPIC = Topic.edge(Go.class);

    public record Go(String text) {
    }

    // ------------------------------------------------------------------ the editor tells the view

    /** Point the navigator at a folder, or list it again. */
    public static final Topic<ShowFolder> SHOW_FOLDER_TOPIC = Topic.edge(ShowFolder.class);

    public record ShowFolder(Path path) {
    }

    /** Say something on the status line. {@code alert}: something was refused or failed, so draw the eye to it. */
    public static final Topic<Status> STATUS_TOPIC = Topic.edge(Status.class);

    public record Status(String message, boolean alert) {
    }

    // ------------------------------------------------------------------ the editor and the jobs

    /** Why a path is being looked at, carried through the job so its answer can be acted on. */
    public enum Why {
        /** Typed into the path bar. */
        GO,
        /** The folder the last session showed. */
        RESTORE_FOLDER,
        /** A path on the command line, which may be a folder or a file. */
        ARGUMENT
    }

    /** What is at a path ({@link FindPath}). */
    public enum Kind {
        FOLDER, FILE,
        /** Nothing there. */
        NOWHERE
    }

    /** Look at what is at a path. */
    public static final Topic<Find> FIND_TOPIC = Topic.edge(Find.class);

    public record Find(Path path, Why why) {
    }

    public static final Topic<Found> FOUND_TOPIC = Topic.edge(Found.class);

    public record Found(Path path, Why why, Kind kind) {
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
     * Keep the session for next time ({@link WriteSession}): the folder shown. A value, so the editor tells whether it
     * changed with {@code equals}.
     */
    public static final Topic<Remember> REMEMBER_TOPIC = Topic.edge(Remember.class);

    public record Remember(Optional<Path> folder) {
    }

    // ------------------------------------------------------------------ the view and its jobs

    /** List one level of the navigator's tree ({@link ListFolder}): the folder shown's, or a folder in it. */
    public static final Topic<Listing> LISTING_TOPIC = Topic.edge(Listing.class);

    /** {@code roots}: the level is the tree's top, the folder shown, rather than a folder opened in it. */
    public record Listing(String tree, Path dir, boolean roots) {
    }

    /** One entry of a level: where it is, what its row says, and whether it opens. */
    public record Item(Path path, String name, boolean folder) {
    }

    public static final Topic<Listed> LISTED_TOPIC = Topic.edge(Listed.class);

    public record Listed(String tree, Path dir, boolean roots, List<Item> items) {
        public Listed {
            items = List.copyOf(items);
        }
    }
}
