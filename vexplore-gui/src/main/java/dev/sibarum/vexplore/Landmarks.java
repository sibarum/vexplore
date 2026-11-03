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

    /** The tree of places, at the left. */
    static final String TREE = "tree";

    /** The file list: a table of the folder's rows. In Two Folders, the FROM list, on the left. */
    static final String LIST = "list";

    /** Two Folders' TO list, on the right; hidden in one folder. */
    static final String LIST_TO = "list.to";

    /** Each list's header in Two Folders: its side and its folder's name, in the accent on the list used. */
    static final String HEADER_FROM = "list.from.header";
    static final String HEADER_TO = "list.to.header";

    /** The top bar's Two folders, and in Two Folders its Swap sides and Back to one folder. */
    static final String TWO_FOLDERS = "top.two";
    static final String SWAP = "top.swap";
    static final String ONE_FOLDER = "top.one";

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

    /** The marks, each row numbered from 0: {@code rail.mark.0} recalls the newest, {@code rail.forget.0} forgets it. */
    static final String RAIL_MARK = "rail.mark";
    static final String RAIL_FORGET = "rail.forget";

    /** The card's headline: the effect, first ({@code Move 9 files · 6.5 GB}). */
    static final String RAIL_CARD = "rail.card";

    /** The card's tag: {@code nothing has changed yet}, or how far a run has got ({@code Deleting · 3 of 9}). */
    static final String RAIL_TAG = "rail.tag";

    /** The card's button, which carries out what it states, and the Stop in its place while that runs. */
    static final String RAIL_GO = "rail.go";
    static final String RAIL_STOP = "rail.stop";

    /** The card's field for a rename's new name: Enter carries it out. */
    static final String RAIL_NAME = "rail.name";

    /** The Preview Dock, under the list, and its header: the file's name, its tier, and the note when there is none. */
    static final String DOCK = "dock";
    static final String DOCK_NAME = "dock.name";
    static final String DOCK_TAG = "dock.tag";
    static final String DOCK_NOTE = "dock.note";

    /** The body's first line: a text's first line, or the hex dump's first row. */
    static final String DOCK_LINE = "dock.line";

    /** A picture's thumbnail, or a video's poster frame. */
    static final String DOCK_PICTURE = "dock.picture";

    /** Open in Rasterware, under a picture or a poster frame. */
    static final String DOCK_OPEN = "dock.open";

    /** The status line, and its slots: the counts at the left, what was said at the right. */
    static final String STATUS = "status";
    static final String STATUS_COUNTS = "status.counts";
    static final String STATUS_MESSAGE = "status.message";

    private Landmarks() {
    }
}
