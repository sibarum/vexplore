package dev.sibarum.vexplore;

import dev.vexelray.gui.core.layout.Length;

/**
 * Size, face and gutter, named once. Faces are indices into the font table the framework loads; sizes are in
 * {@code rem}, so zoom scales all of them together, and each is the design's pixel size divided by sixteen.
 */
final class Type {

    /** The face for text. */
    static final int UI = 0;

    /** The face for paths, sizes in labels, status tags and anything tabular. */
    static final int MONO = 1;

    // ------------------------------------------------------------------ type

    /** Section headings and the rail's title: 17 px. */
    static final Length HEADING = Length.rem(1.0625f);

    /** Body text in the list: 14 px. */
    static final Length BODY = Length.rem(0.875f);

    /** Body text in the rail: 13 px. */
    static final Length RAIL = Length.rem(0.8125f);

    /** Secondary text: 12 px. */
    static final Length META = Length.rem(0.75f);

    /** Mono labels, column headers and status tags: 11 px. */
    static final Length SMALL = Length.rem(0.6875f);

    // --------------------------------------------------------------- gutters

    static final Length GAP = Length.dp(8.4f);

    static final Length RULE = Length.dp(1);

    private Type() {
    }
}
