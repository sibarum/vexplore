package dev.sibarum.vexplore;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.style.Oklab;
import dev.vexelray.gui.core.style.Palette;
import dev.vexelray.gui.core.style.Relief;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.core.style.Shading;
import dev.vexelray.gui.core.style.Theme;

/**
 * Colour, decided once. The design handoff names its colours as hex values; the palette wants anchors, so each is
 * read straight from the value the design gives ({@link Oklab#of}) and the ladders do the rest.
 *
 * <h2>Two chromatic anchors, and they do not mean the same thing</h2>
 * <b>Teal is selection</b> and is the palette's {@code accent}. <b>Amber is Vexplore speaking</b> — a suggestion, a
 * new file, a decision to make — and is the palette's {@code action}, so the one filled button on a screen (the
 * suggestion's own action) is amber without a widget naming a colour. The design is explicit that they are not
 * interchangeable: a screen where amber marked selection would teach the user to ignore the rail.
 */
final class Look {

    // ---------------------------------------------------------------- anchors, from the handoff's hex values

    private static final Oklab PAGE = of(0x16171a);
    private static final Oklab INK = of(0xe8e6e1);
    private static final Oklab TEAL = of(0x7cc4c4);
    private static final Oklab AMBER = of(0xe0a458);
    private static final Oklab DANGER = of(0xc4353b);
    private static final Oklab DEPTH = of(0x08090b);

    /** Lightness per rung; the handoff's page, panel, rail, card, divider and border are rungs 0 to 5 of this. */
    private static final double STEP = 0.0185;
    private static final double FADE = 0.2;
    private static final double SHADOW_ALPHA = 0.55;

    static final Palette PALETTE = new Palette(PAGE, STEP, INK, FADE, TEAL, AMBER, DANGER, DEPTH, SHADOW_ALPHA);

    static final Theme THEME = Theme.of(PALETTE, Shading.ON_DARK, Relief.STANDARD, false, false);

    // ---------------------------------------------------------------- roles the palette does not name

    /** The selected rows: the page with a fifth of teal in it (the handoff's {@code #223338}). */
    static final Role SELECTED = p -> p.page().mix(p.accent(), 0.20).toColor();

    /** The row the selection is anchored on: a little more (the handoff's {@code #2a4247}). */
    static final Role ANCHOR = p -> p.page().mix(p.accent(), 0.27).toColor();

    /** Rows a suggestion would reach: the selected look, quieter, so a selection still reads through it. */
    static final Role TARGETED = p -> p.page().mix(p.accent(), 0.13).toColor();

    /** A card or a row that Vexplore is suggesting or that is new: the page with amber in it. */
    static final Role AMBER_WASH = p -> p.page().mix(p.action(), 0.12).toColor();

    /** Amber as text on the dark ground: the action colour lifted toward the ink so it reads at small sizes. */
    static final Role AMBER_INK = p -> p.action().mix(p.ink(), 0.35).toColor();

    /** The rail's own ground, one rung above the page's chrome. */
    static final Role RAIL = p -> p.surface(2);

    private static Oklab of(int rgb) {
        return Oklab.of(Color.rgb(rgb));
    }

    private Look() {
    }
}
