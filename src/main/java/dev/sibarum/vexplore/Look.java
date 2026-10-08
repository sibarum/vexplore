package dev.sibarum.vexplore;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.style.Oklab;
import dev.vexelray.gui.core.style.Palette;
import dev.vexelray.gui.core.style.Relief;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.core.style.Shading;
import dev.vexelray.gui.core.style.Theme;

/**
 * Colour, decided once, from the Vexplore design system's tokens (its {@code tokens.json}; the token names are
 * written beside each value here so a search for one finds both). The palette's anchors are read straight from
 * token values ({@link Oklab#of}) and the ladders give the framework's widgets their surfaces; the roles below
 * that the ladders cannot reach are the tokens themselves, by name.
 *
 * <h2>Violet is "you chose this", amber is "this is why you are seeing this"</h2>
 * <b>Violet</b> ({@code accent}, about 258 degrees) is the palette's {@code accent} <em>and</em> its {@code action}:
 * a chosen chip, focus, and the one filled button on a screen are all the user's own choice, so they share a hue.
 * Vex owns cyan in the suite; the accent stays violet so the two windows are told apart at a glance.
 * <b>Amber</b> ({@code anchor}) is not in the palette at all. It marks provenance only — the "based on" label, the
 * fresh file, a name that conflicts — and is never a button fill. Rose ({@code danger}) is destructive and nothing
 * else.
 *
 * <h2>Where the ladder lands</h2>
 * The surface ladder starts at {@code bg-0} and steps 0.03 in Oklab lightness, which puts the framework's levels
 * within 0.01 of the tokens a widget would have used: level 1 is {@code bg-1}, 2 is {@code card}, 3 is
 * {@code raised}/{@code hover}, 4 is {@code tree-active}/{@code line}. The ink fades from {@code text-hi} so that
 * level 1 is {@code text-dim} and level 2 is {@code text-disabled}. {@code LookTest} pins each of these.
 */
final class Look {

    // ---------------------------------------------------------------- anchors, from the tokens

    private static final Oklab PAGE = of(0x0e0c14);      // bg-0
    private static final Oklab INK = of(0xece8f5);       // text-hi
    private static final Oklab VIOLET = of(0xb08fff);    // accent
    private static final Oklab DANGER = of(0xe06a7a);    // danger

    /** Lightness per rung: bg-0, bg-1, card, raised, line are rungs 0 to 4 of this. */
    private static final double STEP = 0.03;
    /** text-hi to text-dim is 0.387 of the way to the page; the next rung lands on text-disabled. */
    private static final double FADE = 0.387;
    private static final double SHADOW_ALPHA = 0.55;

    /** Shadows are drawn in the ground itself (the visual guide's drag shadow is "in bg-0"). */
    static final Palette PALETTE = new Palette(PAGE, STEP, INK, FADE, VIOLET, VIOLET, DANGER, PAGE, SHADOW_ALPHA);

    static final Theme THEME = Theme.of(PALETTE, Shading.ON_DARK, Relief.STANDARD, false, false);

    // ---------------------------------------------------------------- tokens the ladders do not reach

    /** {@code bg-1}: the file list body. */
    static final Role LIST = token(0x15121d);

    /** {@code chrome}: the tree, the status bar and the dock — darker than the list, which no rung above the page is. */
    static final Role CHROME = token(0x13101a);

    /** {@code rail}: the Suggestion Rail. */
    static final Role RAIL = token(0x16131f);

    /** {@code card}: the summary card at the foot of the rail. */
    static final Role CARD = token(0x1c1827);

    /** {@code line-strong}: the border of a control or the summary card, 3:1 against every surface it sits on. */
    static final Role LINE_STRONG = token(0x70669a);

    /** {@code text}: values — sizes, the summary card's file list, metadata on a washed row. */
    static final Role TEXT = token(0xbdb6cc);

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

    /** The dock's entropy bar above 7.5 bits per byte: the accent hue, lighter (the visual guide's {@code #d9ccff}). */
    static final Role ENTROPY_HIGH = token(0xd9ccff);

    private static Role token(int rgb) {
        Color c = Color.rgb(rgb);
        return p -> c;
    }

    private static Oklab of(int rgb) {
        return Oklab.of(Color.rgb(rgb));
    }

    private Look() {
    }
}
