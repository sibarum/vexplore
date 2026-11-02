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

    /** The file list: a table of the folder's rows. */
    static final String LIST = "list";

    /** The Suggestion Rail, at the right: from M2. */
    static final String RAIL = "rail";

    /** The status line, and its slots: the counts at the left, what was said at the right. */
    static final String STATUS = "status";
    static final String STATUS_COUNTS = "status.counts";
    static final String STATUS_MESSAGE = "status.message";

    private Landmarks() {
    }
}
