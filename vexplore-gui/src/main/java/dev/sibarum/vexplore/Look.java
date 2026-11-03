package dev.sibarum.vexplore;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Oklab;
import dev.vexelray.gui.core.style.Palette;
import dev.vexelray.gui.core.style.Relief;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.core.style.Shading;
import dev.vexelray.gui.core.style.Theme;
import dev.vexelray.gui.fileicons.FileFamily;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    /**
     * {@code anchor-wash} and {@code anchor-ink}: the rail's basis chip, a dark amber ground with light amber words and
     * an amber dot, as Orbit draws it. A notice, not a control, so amber stays off every fill of a control.
     */
    static final Role ANCHOR_WASH = token(0x2a1f14);
    static final Role ANCHOR_INK = token(0xf3d9a8);

    // ---------------------------------------------------------------- families

    /**
     * Orbit's seven families of file, each a colour of its own, beside the tokens (ruled 2026-11-02): folder, text,
     * video, table, PDF, data and program. The list's TYPE badge is in its family's colour, and the gallery's tiles
     * will be. A picture and a sound are media, with video; a mail and a calendar are text.
     */
    private static final Role FOLDER = token(0xc9b6ff);
    private static final Role TEXT = token(0xe8e4f2);
    private static final Role VIDEO = token(0x9bbcff);
    private static final Role TABLE = token(0x86e3b6);
    private static final Role PDF = token(0xffa0ae);
    private static final Role DATA = token(0xc8c2d8);
    private static final Role PROGRAM = token(0x86d9ff);

    private static final Map<FileFamily, Role> FAMILIES = families();

    private static Map<FileFamily, Role> families() {
        Map<FileFamily, Role> m = new EnumMap<>(FileFamily.class);
        for (FileFamily f : FileFamily.values()) {
            m.put(f, DATA);
        }
        m.put(FileFamily.FOLDERS, FOLDER);
        for (FileFamily f : List.of(FileFamily.PLAIN_TEXT, FileFamily.DOCUMENTS, FileFamily.SOURCE_CODE,
                FileFamily.SCRIPTS, FileFamily.SHADERS, FileFamily.EMAIL, FileFamily.CALENDARS_AND_CONTACTS)) {
            m.put(f, TEXT);
        }
        for (FileFamily f : List.of(FileFamily.VIDEO, FileFamily.AUDIO, FileFamily.IMAGES,
                FileFamily.VECTOR_GRAPHICS)) {
            m.put(f, VIDEO);
        }
        m.put(FileFamily.SPREADSHEETS, TABLE);
        m.put(FileFamily.PDF, PDF);
        m.put(FileFamily.PRESENTATIONS, PDF);
        m.put(FileFamily.PROGRAMS, PROGRAM);
        m.put(FileFamily.INSTALLERS, PROGRAM);
        return m;
    }

    /** A family's colour: Orbit's, the rest of the families as data. */
    static Role family(FileFamily f) {
        return FAMILIES.get(f);
    }

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
