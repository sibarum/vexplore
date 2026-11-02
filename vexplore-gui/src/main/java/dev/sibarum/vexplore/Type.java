package dev.sibarum.vexplore;

import dev.vexelray.gui.core.layout.Length;

/**
 * The two faces, the type scale, the regions' sizes, and the gutters.
 *
 * <h2>Type is rem; gutters are dp</h2>
 *
 * <p>That is the framework's rule and it decides what zoom does. {@code rem} grows with the user's zoom because it
 * is proportional to text; {@code dp} does not, because tripling the frame around content you zoomed in to read
 * means seeing less of it. Getting this backwards is not a subtle bug -- it is an application whose zoom makes the
 * window emptier. The regions hold text, so they are rem too: the handoff's pixels at its 16 px root.
 *
 * <h2>The faces are the atlas's, not yours</h2>
 *
 * <p>The framework's text atlas is baked at build time from the fonts in {@code vexelray-text}, so face 0 and face
 * 1 are whatever it shipped -- a sans and a mono -- and so is which characters each has. The design names Instrument
 * Sans and IBM Plex Mono; neither is in the atlas, and an application cannot add one (framework-notes FN-1). Check
 * the charset in {@code vexelray-text}'s pom before reaching for a symbol: a character the atlas lacks draws as the
 * missing-glyph box.
 */
final class Type {

    /** Face 0: the sans. Labels, names, the status line. */
    static final int UI = 0;

    /** Face 1: the mono. Paths, sizes, anything tabular. */
    static final int MONO = 1;

    // ------------------------------------------------------------------ type

    /** The path field, a hint, the bars' chrome, the list's TYPE column. */
    static final Length SMALL = Length.rem(0.75f);

    /** A row's words: a name, a size, a time. The handoff's 13.5 px. */
    static final Length BODY = Length.rem(0.84375f);

    /** The rail's chips, its section titles and the card's headline. The handoff's 13 px. */
    static final Length RAIL_TEXT = Length.rem(0.8125f);

    /** The rail's title. 17 px. */
    static final Length HEADING = Length.rem(1.0625f);

    // ------------------------------------------------------------------ regions, from the handoff at 1440 x 900

    /** The top bar: the breadcrumb, or the path field in its place. 48 px. */
    static final Length TOP_BAR = Length.rem(3f);

    /** The tree's width on a first run. 250 px. */
    static final Length TREE = Length.rem(15.625f);

    /**
     * The Suggestion Rail's width on a first run: a third of the room beside the tree, which is the handoff's 400 of
     * 1190 px. A share rather than rem, so a narrow or zoomed window narrows the rail with the list and the list stays
     * the widest region (framework-notes FN-2; ruled 2026-11-02).
     */
    static final Length RAIL = Length.percent(33.6f);

    /** The Preview Dock's height on a first run: two fifths of the column it shares with the list. */
    static final Length DOCK = Length.percent(40f);

    /** The least a drag of the dock's divider leaves the list: a few rows. */
    static final Length LIST_MIN_HEIGHT = Length.rem(10f);

    /** The least a drag of the rail's divider leaves the file list (and only a drag: framework-notes FN-2). */
    static final Length LIST_MIN = Length.rem(20f);

    /** The least a drag leaves the rail: two chips to a line still fit. */
    static final Length RAIL_MIN = Length.rem(15f);

    // --------------------------------------------------------------- gutters

    /** A tight gap, and the bars' padding. */
    static final Length TIGHT = Length.dp(5.6f);

    /** The top bar's padding at its sides. */
    static final Length SIDE = Length.dp(12f);

    private Type() {
    }
}
