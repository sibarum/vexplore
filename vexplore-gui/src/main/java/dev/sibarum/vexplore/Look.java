package dev.sibarum.vexplore;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Oklab;
import dev.vexelray.gui.core.style.Palette;
import dev.vexelray.gui.core.style.Relief;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.core.style.Shading;
import dev.vexelray.gui.core.style.Theme;

/**
 * Colour and shape, decided once, from Orbit: the redesign chosen 2026-11-02 (rebuild.md, M8), drawn on the canvas
 * <i>Vexplore Redesign</i>. Its values replace the design system's tokens M0 used (ruled 2026-11-02); each is written
 * beside the name Orbit's drawing gives it, so a search for one finds both.
 *
 * <h2>Violet is "you chose this", amber is "this is why you are seeing this"</h2>
 *
 * <p><b>Violet</b> ({@code accent}) is the palette's accent <em>and</em> its action: a chosen chip, the place and the
 * path shown, focus and the one filled button are all the person's own choice, so they share a hue. A selected card
 * is violet too, where Orbit drew it amber (ruled 2026-11-02). <b>Amber</b> ({@code anchor}) is not in the palette: it
 * marks provenance only (a fresh file, "based on", a conflict) and is never a fill of a control. Rose
 * ({@code danger}) is destructive and nothing else.
 *
 * <h2>Where the ladder lands</h2>
 *
 * <p>The surface ladder starts at the ground and steps 0.032 in Oklab lightness, so rung 1 is the panel (within
 * 0.009), 2 the card and 3 the raised control (within 0.002). Orbit's surfaces are not evenly spaced, a panel being
 * further above the ground than a card is above the panel, and 0.032 is the step that lands all three within
 * {@code LookTest}'s 0.01. The ink fades 0.30 of the way to the ground a rung, so rung 1 is Orbit's dim text.
 */
final class Look {

    // ---------------------------------------------------------------- anchors, from Orbit

    private static final Oklab PAGE = of(0x0c0b10);      // ground
    private static final Oklab INK = of(0xf2eff8);       // text
    private static final Oklab VIOLET = of(0xb69cff);    // accent
    private static final Oklab DANGER = of(0xf3a9b5);    // delete

    /** Lightness per rung. */
    private static final double STEP = 0.032;
    /** text to dim text is 0.30 of the way to the ground. */
    private static final double FADE = 0.30;
    private static final double SHADOW_ALPHA = 0.55;

    /** Shadows are drawn in the ground itself. */
    static final Palette PALETTE = new Palette(PAGE, STEP, INK, FADE, VIOLET, VIOLET, DANGER, PAGE, SHADOW_ALPHA);

    static final Theme THEME = Theme.of(PALETTE, Shading.ON_DARK, Relief.STANDARD, false, false);

    // ---------------------------------------------------------------- tokens the ladders do not reach

    /** {@code ground}: the top bar, the places and the status line sit on the ground itself, between the panels. */
    static final Role CHROME = token(0x0c0b10);

    /** {@code panel}: the list's panel. */
    static final Role LIST = token(0x15131c);

    /** {@code panel}: the Suggestion Rail's, the same. */
    static final Role RAIL = token(0x15131c);

    /**
     * {@code chosen}: the fill of what is chosen (a chip, the place), and the wash of the rows the rail's Select and
     * Condition would reach, marked and never selected. Violet, since the reach is the person's pick.
     */
    static final Role TARGETED = token(0x2d2547);

    /** {@code anchor}: amber, as text. "based on", and later a fresh file or a conflict. Never a fill. */
    static final Role ANCHOR = token(0xe8a85c);

    // ---------------------------------------------------------------- shape

    /** A panel's corners: Orbit's 24 px. */
    static final Length PANEL_CORNER = Length.dp(24);

    /** The ground between two panels, and around them: Orbit's 12 px. */
    static final Length GAP = Length.dp(12);

    /**
     * The line painted down the middle of a gap, which takes the accent while a pointer is over it or drags it. Orbit
     * draws no line; a hairline is the least that still answers the pointer.
     */
    static final Length SEAM = Length.dp(1);

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
