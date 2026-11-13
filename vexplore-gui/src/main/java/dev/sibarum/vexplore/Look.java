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
 * Colour and shape, decided once, in two looks the person chooses between in the app menu (Theme, then Light or
 * Dark). The dark look is Orbit's: the redesign chosen 2026-11-02 (rebuild.md, M8), drawn on the canvas <i>Vexplore
 * Redesign</i>. Its values replace the design system's tokens M0 used (ruled 2026-11-02); each is written beside the
 * name Orbit's drawing gives it, so a search for one finds both.
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
 *
 * <h2>The light look is authored, not reflected</h2>
 *
 * <p>Orbit drew no light board, so the light look is written here, in Orbit's family: every hue is the dark token's
 * own (violet at -65, amber at 69, the Two Folders cyan at -131), and only lightness and chroma are decided again.
 * A reflected dark page would land on a grey; a light page wants about 0.97. The accent comes down until it reads on
 * white, a shadow that is depth on a dark ground is a smudge on a light one so its alpha drops by two thirds, and the
 * edge light stays off. The ground is a faint violet and the panels on it near-white, so a panel still stands off the
 * ground by being lighter, as it does in the dark look. Every wash becomes a light tint of its hue, and every ink and
 * family colour is darkened until it reads on the light ground (WCAG 4.5 for words, 3 for marks; {@code LookTest}
 * holds both looks to it).
 *
 * <h2>Write the role, never the colour</h2>
 *
 * <p>A node that holds a {@link Role} is repainted when the person changes the look, and fades to it; a node handed
 * {@code gui.theme().color(role)} keeps the colour it was given, forever. So every colour below is a role with an
 * answer in each look, and nothing in this application resolves one early.
 */
final class Look {

    // ---------------------------------------------------------------- the dark look: anchors, from Orbit

    private static final Oklab DARK_PAGE = of(0x0c0b10);      // ground
    private static final Oklab DARK_INK = of(0xf2eff8);       // text
    private static final Oklab DARK_VIOLET = of(0xb69cff);    // accent
    private static final Oklab DARK_DANGER = of(0xf3a9b5);    // delete

    /** Lightness per rung. */
    private static final double DARK_STEP = 0.032;
    /** text to dim text is 0.30 of the way to the ground. */
    private static final double DARK_FADE = 0.30;
    private static final double DARK_SHADOW_ALPHA = 0.55;

    /** Shadows are drawn in the ground itself. */
    static final Palette DARK_PALETTE = new Palette(DARK_PAGE, DARK_STEP, DARK_INK, DARK_FADE, DARK_VIOLET,
            DARK_VIOLET, DARK_DANGER, DARK_PAGE, DARK_SHADOW_ALPHA);

    static final Theme DARK = Theme.of(DARK_PALETTE, Shading.ON_DARK, Relief.STANDARD, false, false);

    // ---------------------------------------------------------------- the light look: anchors, authored

    /** The ground: near-white at 0.97, with a trace of the dark ground's violet so the two read as one family. */
    private static final Oklab LIGHT_PAGE = Oklab.polar(0.970, 0.008, -65.7);

    /** Text: a deep violet slate rather than black, at the dark text's own hue; 15.9:1 on the ground. */
    private static final Oklab LIGHT_INK = Oklab.polar(0.220, 0.025, -60.0);

    /**
     * Orbit's one violet, accent and action as in the dark look, brought down from 0.75 to 0.50 so a link, a focus
     * ring and the white label of the filled button all read (6.0:1 on the ground, 6.5:1 for white on it).
     */
    private static final Oklab LIGHT_VIOLET = Oklab.polar(0.500, 0.180, -65.0);

    /** Rose, at the dark delete's hue, darkened for the same reason as the violet: 5.5:1 on the ground. */
    private static final Oklab LIGHT_DANGER = Oklab.polar(0.520, 0.160, 8.5);

    /** Shadows: a near-black at the ground's hue, since a light ground cannot cast itself as the dark one does. */
    private static final Oklab LIGHT_DEPTH = Oklab.polar(0.200, 0.010, -65.7);

    /** Each surface a step down from the ground, as the template's light look does. */
    private static final double LIGHT_STEP = 0.030;
    /** Dim text a third of the way to the ground, still 6.5:1 on it. */
    private static final double LIGHT_FADE = 0.32;
    /** A shadow on a light ground is a hint, not a void: a third of the dark look's. */
    private static final double LIGHT_SHADOW_ALPHA = 0.18;

    static final Palette LIGHT_PALETTE = new Palette(LIGHT_PAGE, LIGHT_STEP, LIGHT_INK, LIGHT_FADE, LIGHT_VIOLET,
            LIGHT_VIOLET, LIGHT_DANGER, LIGHT_DEPTH, LIGHT_SHADOW_ALPHA);

    /** No edge light, which on a white panel reads as a smear, and no letterpress. */
    static final Theme LIGHT = Theme.of(LIGHT_PALETTE, Shading.ON_LIGHT, Relief.STANDARD, false, false);

    // ---------------------------------------------------------------- tokens the ladders do not reach

    /** {@code ground}: the top bar, the places and the status line sit on the ground itself, between the panels. */
    static final Role CHROME = token(0x0c0b10, LIGHT_PAGE);

    /** {@code panel}: the list's panel. In light, near-white, so it stands off the ground by being lighter. */
    static final Role LIST = token(0x15131c, Oklab.polar(0.995, 0.003, -65.7));

    /** {@code panel}: the Suggestion Rail's, the same. */
    static final Role RAIL = token(0x15131c, Oklab.polar(0.995, 0.003, -65.7));

    /**
     * {@code chosen}: the fill of what is chosen (a chip, the place), and the wash of the rows the rail's Select and
     * Condition would reach, marked and never selected. Violet, since the reach is the person's pick. In light, a pale
     * violet tint that text and amber still read on.
     */
    static final Role TARGETED = token(0x2d2547, Oklab.polar(0.910, 0.045, -67.0));

    /** {@code anchor}: amber, as text. "based on", and later a fresh file or a conflict. Never a fill. */
    static final Role ANCHOR = token(0xe8a85c, Oklab.polar(0.500, 0.110, 69.0));

    /**
     * {@code anchor-wash} and {@code anchor-ink}: the rail's basis chip, a dark amber ground with light amber words and
     * an amber dot, as Orbit draws it. A notice, not a control, so amber stays off every fill of a control. In light,
     * a pale amber ground with deep amber words.
     */
    static final Role ANCHOR_WASH = token(0x2a1f14, Oklab.polar(0.950, 0.035, 75.0));
    static final Role ANCHOR_INK = token(0xf3d9a8, Oklab.polar(0.420, 0.090, 70.0));

    // ---------------------------------------------------------------- Two Folders, from Orbit's Two folders board

    /** FROM's tag: the violet wash of {@code chosen}, with light violet words. The marks of <em>only here</em> too. */
    static final Role FROM_WASH = token(0x2d2547, Oklab.polar(0.910, 0.045, -67.0));
    static final Role FROM_INK = token(0xd8c8ff, Oklab.polar(0.420, 0.150, -62.0));

    /** TO's tag, in a cyan of its own, so the two sides are told apart at a glance and in a screenshot. */
    static final Role TO_WASH = token(0x12303d, Oklab.polar(0.930, 0.035, -131.0));
    static final Role TO_INK = token(0xc7f0ff, Oklab.polar(0.400, 0.080, -135.0));

    /**
     * The quiet chip: an identical row's mark, and the tag of the list not used. Neither violet nor amber, since an
     * identical copy is neither a choice nor a reason.
     */
    static final Role QUIET_WASH = token(0x221f2e, Oklab.polar(0.925, 0.012, -66.0));
    static final Role QUIET_INK = token(0xc8c2d8, Oklab.polar(0.420, 0.030, -62.0));

    /** A pane's own round buttons: a step off the panel they sit on (in light, a step below the white panel). */
    static final Role RAISED = token(0x1b1824, Oklab.polar(0.955, 0.012, -65.0));

    /** The hairlines of the strip between the two lists. */
    static final Role BRIDGE = token(0x262233, Oklab.polar(0.860, 0.025, -65.0));

    // ---------------------------------------------------------------- families

    /**
     * Orbit's seven families of file, each a colour of its own, beside the tokens (ruled 2026-11-02): folder, text,
     * video, table, PDF, data and program. The list's TYPE badge is in its family's colour, and the gallery's tiles
     * will be. A picture and a sound are media, with video; a mail and a calendar are text. In light, each keeps its
     * hue and comes down to about 0.5 in lightness, enough to read as words on the ground the badge sits on.
     */
    static final Role FOLDER = token(0xc9b6ff, Oklab.polar(0.480, 0.150, -64.0));
    static final Role TEXT = token(0xe8e4f2, Oklab.polar(0.350, 0.020, -61.0));
    static final Role VIDEO = token(0x9bbcff, Oklab.polar(0.480, 0.130, -96.0));
    static final Role TABLE = token(0x86e3b6, Oklab.polar(0.500, 0.100, 162.0));
    static final Role PDF = token(0xffa0ae, Oklab.polar(0.520, 0.150, 10.0));
    static final Role DATA = token(0xc8c2d8, Oklab.polar(0.500, 0.030, -62.0));
    static final Role PROGRAM = token(0x86d9ff, Oklab.polar(0.500, 0.100, -131.0));

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

    /** A token of Orbit's in the dark look, and its authored answer in the light one. */
    private static Role token(int dark, Oklab light) {
        Color d = Color.rgb(dark);
        Color l = light.toColor();
        return p -> p.equals(LIGHT_PALETTE) ? l : d;
    }

    private static Oklab of(int rgb) {
        return Oklab.of(Color.rgb(rgb));
    }

    private Look() {
    }
}
