package dev.sibarum.vexplore;

/**
 * The names an automation script is allowed to depend on. A landmark is a promise: it is what
 * {@code Gui.navigate} and the driving socket's {@code click} and {@code shot} take, so renaming one is an edit
 * to every script that uses it, and adding one is free. The viewer's own are {@code PictureViewer}'s, with
 * {@code "viewer."} in front: {@code viewer.name}, {@code viewer.count}, {@code viewer.next} and the rest.
 */
final class Landmarks {

    static final String TOP_BAR = "topbar";
    static final String TREE = "tree";
    static final String LIST = "list";
    static final String DOCK = "dock";
    static final String SPLIT = "split";
    static final String RAIL = "rail";
    /** The folders and the rail, with the divider between them. */
    static final String RAIL_SPLIT = "railsplit";
    static final String SUMMARY = "summary";
    static final String STATUS = "status";
    /** The rail's "based on ..." tag: it names the file the rail was computed for, so a script can await it. */
    static final String BASIS = "basis";
    /** The status line's selection count. It is the last thing a document change writes, so scripts await it. */
    static final String SELECTED = "selected";
    /** The status line's item count: "reading..." until a listing lands, so scripts await it before touching rows. */
    static final String ITEMS = "items";
    /** The status line's branch, empty outside a repository. Written when git answers, after the listing. */
    static final String BRANCH = "branch";
    /** The rail's one-line account of the last action, or of what is running. Scripts await it. */
    static final String NOTICE = "notice";
    static final String UNDO = "undo";
    /** The card's primary button: the action itself. */
    static final String GO = "go";
    static final String CHOOSE = "choose";
    static final String DESTINATION = "destination";
    /** The first suggested destination, which a script can await to know the suggestions have arrived. */
    static final String DEST_FIRST = "dest.1";
    /** Step 4's "To compressed archive", offered for a mixed bunch in Desktop, Downloads or Documents. */
    static final String DEST_ARCHIVE = "dest.archive";
    /** The remote panel's address field: {@code user@host:path} or {@code sftp://host/path}, then Enter. */
    static final String ADDRESS = "address";
    /** The remote panel's new-folder name field: a name, then Enter. */
    static final String NEW_FOLDER = "newfolder";
    /** The card's new-name field for a rename: Enter puts the name in the plan; {@link #GO} does it. */
    static final String RENAME_TO = "renameto";
    /** The remote panel's account of why a host could not be reached: ssh's own last line. */
    static final String PROBLEM = "problem";
    /** The dock's picture, which is its own control: a click views it in Pix, or in the viewer without Pix. */
    static final String POP_OUT = "popout";
    /** The top bar's toggle that swaps the Suggestion Rail for a second folder, and back. */
    static final String TWO = "two";
    /** The second folder: its whole pane, its list, and its item count ("reading..." until a listing lands). */
    static final String OTHER = "other";
    static final String OTHER_LIST = "other.list";
    static final String OTHER_ITEMS = "other.items";
    /** The status line's account of what is running or what last ran, written while the rail is put away. */
    static final String WORK = "work";
    /** The Connect panel's host field and its Connect button, from the Open menu's New host. */
    static final String HOST = "host";
    static final String CONNECT = "connect";
    /** The title bar's Open button, which drops the Open menu. */
    static final String OPEN = "open";

    private Landmarks() {
    }
}
