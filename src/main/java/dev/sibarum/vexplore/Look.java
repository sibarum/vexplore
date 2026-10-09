package dev.sibarum.vexplore;

import dev.sibarum.suite.view.SuiteLook;
import dev.vexelray.gui.core.style.Palette;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.core.style.Theme;

import static dev.sibarum.suite.view.SuiteLook.token;

/**
 * Colour, decided once, from the Vexplore design system's tokens (its {@code tokens.json}; the token names are
 * written beside each value here so a search for one finds both). The palette and the tokens the viewer draws with
 * are the suite's ({@link SuiteLook}), shared with Pix; the rest are the explorer's own.
 *
 * <h2>Violet is "you chose this", amber is "this is why you are seeing this"</h2>
 * <b>Violet</b> ({@code accent}, about 258 degrees) is the palette's {@code accent} <em>and</em> its {@code action}:
 * a chosen chip, focus, and the one filled button on a screen are all the user's own choice, so they share a hue.
 * Vex owns cyan in the suite; the accent stays violet so the two windows are told apart at a glance.
 * <b>Amber</b> ({@code anchor}) is not in the palette at all. It marks provenance only — the "based on" label, the
 * fresh file, a name that conflicts — and is never a button fill. Rose ({@code danger}) is destructive and nothing
 * else.
 *
 * <p>Where the palette's ladder lands against the tokens is {@link SuiteLook}'s to say, and {@code LookTest} pins it
 * here too, since it is this application's look that would change.
 */
final class Look {

    static final Palette PALETTE = SuiteLook.PALETTE;

    static final Theme THEME = SuiteLook.THEME;

    // ---------------------------------------------------------------- tokens the ladders do not reach

    /** {@code bg-1}: the file list body. */
    static final Role LIST = token(0x15121d);

    /** {@code chrome}: the tree, the status bar and the dock — darker than the list, which no rung above the page is. */
    static final Role CHROME = SuiteLook.CHROME;

    /** {@code rail}: the Suggestion Rail. */
    static final Role RAIL = token(0x16131f);

    /** {@code card}: the summary card at the foot of the rail. */
    static final Role CARD = token(0x1c1827);

    /** {@code line-strong}: the border of a control or the summary card, 3:1 against every surface it sits on. */
    static final Role LINE_STRONG = SuiteLook.LINE_STRONG;

    /** {@code text}: values — sizes, the summary card's file list, metadata on a washed row. */
    static final Role TEXT = SuiteLook.TEXT;

    /** {@code anchor-wash}: the selected row, the file the suggestions are derived from. */
    static final Role SELECTED = token(0x3e3260);

    /** {@code scope-wash}: rows the current Select and Condition would reach. */
    static final Role TARGETED = token(0x2c244c);

    /** {@code anchor}: amber, as text — "based on", a conflict. Provenance only, never a fill. */
    static final Role ANCHOR = token(0xe8a85c);

    /** {@code anchor-dim}: the wash behind a fresh file or a conflicting row. */
    static final Role ANCHOR_DIM = token(0x2a1f14);

    /** The dock's entropy bar below 5 bits per byte: the accent hue, darker (the visual guide's {@code #6e5fc0}). */
    static final Role ENTROPY_LOW = token(0x6e5fc0);

    /**
     * The dock's entropy bar above 7.5 bits per byte: the accent hue, lighter (the visual guide's {@code #d9ccff}),
     * which is the viewer's chosen ink.
     */
    static final Role ENTROPY_HIGH = SuiteLook.CHOSEN_INK;

    private Look() {
    }
}
