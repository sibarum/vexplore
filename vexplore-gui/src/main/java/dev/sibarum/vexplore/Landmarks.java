package dev.sibarum.vexplore;

/**
 * Every name an automation script may write down, in one place.
 *
 * <p><b>A ref is minted per run; a landmark still means something tomorrow.</b> That is the whole distinction
 * (vexelray-gui/docs/automation.md 5), and it is why these are constants rather than string literals scattered
 * through the view: a landmark is a published contract, so renaming one breaks a script somebody else wrote, and
 * the compiler should be the thing that notices.
 *
 * <p>Where you can, carry <em>state in the accessible name</em>. {@code await <landmark> <text>} is how a driver
 * waits for the application to be ready, and it needs no hook when readiness is declared somewhere the read-model
 * can already see. {@link #PATH} works that way: its text is the folder shown, so {@code await path Downloads} waits
 * for a folder to be shown.
 */
final class Landmarks {

    /** The top bar's breadcrumb: the folder shown, as a chain to step back along. */
    static final String CRUMBS = "crumbs";

    /** The path field, in the breadcrumb's place while Ctrl+L has it: type a folder, or a file to show its folder. */
    static final String PATH = "path";

    /** The places, flat, beside the list (Orbit's sidebar, M8). */
    static final String PLACES = "places";

    /**
     * The marks under the places, each row numbered from 0: {@code mark.0} recalls the newest, {@code mark.forget.0}
     * forgets it.
     */
    static final String MARK = "mark";
    static final String FORGET = "mark.forget";

    /** The file list: a table of the folder's rows. In Two Folders, the FROM list, on the left. */
    static final String LIST = "list";

    /** Two Folders' TO list, on the right; hidden in one folder. */
    static final String LIST_TO = "list.to";

    /** Each list's header: its folder's name and count, and in Two Folders its side's tag and its path. */
    static final String HEADER_FROM = "list.from.header";
    static final String HEADER_TO = "list.to.header";

    /** Each pane's own Back, Up and Choose folder, in its header in Two Folders (Orbit's Two folders board). */
    static final String BACK_FROM = "list.from.back";
    static final String UP_FROM = "list.from.up";
    static final String CHOOSE_FROM = "list.from.choose";
    static final String BACK_TO = "list.to.back";
    static final String UP_TO = "list.to.up";
    static final String CHOOSE_TO = "list.to.choose";

    /**
     * The top bar's Back and Up, and its view switch: List, one folder, and Two folders. In Two Folders, its
     * title ({@code Compare & move}, {@code Downloads → Videos}) where Back, Up and the path were, and Swap sides in the
     * strip between the two lists.
     */
    static final String BACK = "top.back";
    static final String UP = "top.up";
    static final String ONE_FOLDER = "top.list";
    static final String TWO_FOLDERS = "top.two";
    static final String TITLE = "top.title";
    static final String SWAP = "bridge.swap";

    /** The Suggestion Rail, at the right. */
    static final String RAIL = "rail";

    /** The rail's "based on" line: the file the suggestions are worked out from. */
    static final String RAIL_BASIS = "rail.basis";

    /** What the rail says when nothing is selected. */
    static final String RAIL_EMPTY = "rail.empty";

    /** The rail's chips, each row's numbered from 0: {@code rail.select.1} is the second Select chip. */
    static final String RAIL_SELECT = "rail.select";
    static final String RAIL_CONDITION = "rail.condition";
    static final String RAIL_ACTION = "rail.action";
    static final String RAIL_PLACE = "rail.place";

    /** The Destination row's {@code Choose folder…}: the system's folder picker. */
    static final String RAIL_CHOOSE = "rail.choose";


    /** The card's headline: the effect, first ({@code Move 9 files · 6.5 GB}). */
    static final String RAIL_CARD = "rail.card";

    /** The card's tag: {@code nothing has changed yet}, or how far a run has got ({@code Deleting · 3 of 9}). */
    static final String RAIL_TAG = "rail.tag";

    /** The card's button, which carries out what it states, and the Stop in its place while that runs. */
    static final String RAIL_GO = "rail.go";
    static final String RAIL_STOP = "rail.stop";

    /** The card's field for a rename's new name: Enter carries it out. */
    static final String RAIL_NAME = "rail.name";

    /**
     * Two Folders' offers on the rail: the comparison's tiles ({@code rail.tally.only}, {@code .different},
     * {@code .identical}, {@code .probably}) and each offer's card and its button ({@code rail.offer.move},
     * {@code rail.offer.move.go}, {@code rail.offer.remove}, {@code rail.offer.remove.go}).
     */
    static final String RAIL_TALLY = "rail.tally";
    static final String RAIL_OFFER = "rail.offer";

    /** The Preview Dock, under the list, and its header: the file's name, its tier, and the note when there is none. */
    static final String DOCK = "dock";
    static final String DOCK_NAME = "dock.name";
    static final String DOCK_TAG = "dock.tag";
    static final String DOCK_NOTE = "dock.note";

    /** The body's first line: a text's first line, or the hex dump's first row. */
    static final String DOCK_LINE = "dock.line";

    /** A picture's thumbnail, or a video's poster frame. */
    static final String DOCK_PICTURE = "dock.picture";

    /** The preview strip's Open with: a left click opens the file with what suits it, its menu names a program. */
    static final String DOCK_OPEN = "dock.open-with";

    /**
     * Two Folders' conflict in the dock, in place of the preview: each copy's half (its name, then its facts, as the
     * readout), the column's words, and its buttons.
     */
    static final String DOCK_CONFLICT = "dock.conflict";
    static final String DOCK_CONFLICT_FROM = "dock.conflict.from";
    static final String DOCK_CONFLICT_TO = "dock.conflict.to";
    static final String DOCK_CONFLICT_NOTE = "dock.conflict.note";
    static final String DOCK_KEEP_NEWER = "dock.keep.newer";
    static final String DOCK_KEEP_BOTH = "dock.keep.both";

    /** The status line, and its slots: the counts at the left, what was said at the right. */
    static final String STATUS = "status";
    static final String STATUS_COUNTS = "status.counts";
    static final String STATUS_MESSAGE = "status.message";

    private Landmarks() {
    }
}
