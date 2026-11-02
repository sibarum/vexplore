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

    /** The card's headline: the effect, first ({@code Move 9 files · 6.5 GB}). */
    static final String RAIL_CARD = "rail.card";

    /** The card's tag: {@code nothing has changed yet}, or how far a run has got ({@code Deleting · 3 of 9}). */
    static final String RAIL_TAG = "rail.tag";

    /** The card's button, which carries out what it states, and the Stop in its place while that runs. */
    static final String RAIL_GO = "rail.go";
    static final String RAIL_STOP = "rail.stop";

    /** The status line, and its slots: the counts at the left, what was said at the right. */
    static final String STATUS = "status";
    static final String STATUS_COUNTS = "status.counts";
    static final String STATUS_MESSAGE = "status.message";

    private Landmarks() {
    }
}
