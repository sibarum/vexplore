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
 * written beside each value here so a search for one finds both).
 *
 * <h2>The suite's palette, copied until the suite is ported</h2>
 *
 * <p>The anchors are {@code vex-suite-common}'s {@code SuiteLook}, which Vexplore and Rasterware share. That module is
 * out of the v0.2 build until its viewer is ported, so they are copied here, value for value; when it is back, this
 * takes {@code SuiteLook.THEME} again and keeps only the tokens below. 0.1 measured them against the handoff's colours
 * (its framework notes, FN-6): the design system came after the handoff and replaces its teal with violet, because
 * VexelRay IDE owns cyan in the suite.
 *
 * <h2>Violet is "you chose this", amber is "this is why you are seeing this"</h2>
 *
 * <p><b>Violet</b> ({@code accent}) is the palette's accent <em>and</em> its action: a chosen chip, focus and the one
 * filled button are all the person's own choice, so they share a hue. <b>Amber</b> ({@code anchor}) is not in the
 * palette: it marks provenance only (a fresh file, "based on", a conflict) and is never a fill of a control. Rose
 * ({@code danger}) is destructive and nothing else.
 *
 * <h2>Where the ladder lands</h2>
 *
 * <p>The surface ladder starts at {@code bg-0} and steps 0.03 in Oklab lightness, so rung 1 is {@code bg-1}, 2 is
 * {@code card}, 3 is {@code raised}, 4 is {@code line}. The ink fades from {@code text-hi} so that rung 1 is
 * {@code text-dim} and rung 2 {@code text-disabled}. The tokens between rungs are roles of their own, below.
 */
final class Look {

    // ---------------------------------------------------------------- anchors, from the tokens

    private static final Oklab PAGE = of(0x0e0c14);      // bg-0
    private static final Oklab INK = of(0xece8f5);       // text-hi
    private static final Oklab VIOLET = of(0xb08fff);    // accent
    private static final Oklab DANGER = of(0xe06a7a);    // danger

    /** Lightness per rung. */
    private static final double STEP = 0.03;
    /** text-hi to text-dim is 0.387 of the way to the page; the next rung lands on text-disabled. */
    private static final double FADE = 0.387;
    private static final double SHADOW_ALPHA = 0.55;

    /** Shadows are drawn in the ground itself (the visual guide's drag shadow is "in bg-0"). */
    static final Palette PALETTE = new Palette(PAGE, STEP, INK, FADE, VIOLET, VIOLET, DANGER, PAGE, SHADOW_ALPHA);

    static final Theme THEME = Theme.of(PALETTE, Shading.ON_DARK, Relief.STANDARD, false, false);

    // ---------------------------------------------------------------- tokens the ladders do not reach

    /** {@code chrome}: the top bar, the tree and the status bar; darker than {@code bg-1}, which no rung above is. */
    static final Role CHROME = token(0x13101a);

    /** {@code bg-1}: the file list's body. */
    static final Role LIST = token(0x15121d);

    /** {@code rail}: the Suggestion Rail. */
    static final Role RAIL = token(0x16131f);

    /**
     * {@code scope-wash}: rows the rail's Select and Condition would reach, marked and never selected. A violet wash,
     * not the handoff's amber fill: the design system keeps amber off every fill, and the reach is the person's pick.
     */
    static final Role TARGETED = token(0x2c244c);

    /** {@code anchor}: amber, as text. "based on", and later a fresh file or a conflict. Never a fill. */
    static final Role ANCHOR = token(0xe8a85c);

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
