package dev.sibarum.vexplore;

/**
 * The names an automation script is allowed to depend on. A landmark is a promise: it is what
 * {@code Gui.navigate} and the driving socket's {@code click} and {@code shot} take, so renaming one is an edit
 * to every script that uses it, and adding one is free.
 */
final class Landmarks {

    static final String TOP_BAR = "topbar";
    static final String TREE = "tree";
    static final String LIST = "list";
    static final String DOCK = "dock";
    static final String SPLIT = "split";
    static final String RAIL = "rail";
    static final String SUMMARY = "summary";
    static final String STATUS = "status";
    /** The rail's "based on ..." tag: it names the file the rail was computed for, so a script can await it. */
    static final String BASIS = "basis";
    /** The status line's selection count. It is the last thing a document change writes, so scripts await it. */
    static final String SELECTED = "selected";
    /** The status line's item count: "reading..." until a listing lands, so scripts await it before touching rows. */
    static final String ITEMS = "items";
    /** The rail's one-line account of the last action, or of what is running. Scripts await it. */
    static final String NOTICE = "notice";
    static final String UNDO = "undo";
    /** The card's primary button: the action itself. */
    static final String GO = "go";
    static final String CHOOSE = "choose";
    static final String DESTINATION = "destination";
    /** The first suggested destination, which a script can await to know the suggestions have arrived. */
    static final String DEST_FIRST = "dest.1";
    /** The dock's Pop out button: opens the viewer on an image. */
    static final String POP_OUT = "popout";
    /** In the viewer's own window: the file shown, and "3 / 17", written at once on every step, so scripts await it. */
    static final String VIEWER_NAME = "viewer.name";
    static final String VIEWER_COUNT = "viewer.count";
    /** The viewer's backdrop chips, one per {@code ImageView.Backdrop}: "viewer.backdrop.checker" and so on. */
    static final String VIEWER_BACKDROP = "viewer.backdrop.";

    private Landmarks() {
    }
}
