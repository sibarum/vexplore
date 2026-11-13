package dev.sibarum.vexplore;

import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.style.Oklab;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.core.style.Theme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both looks, each held to what a look has to be true of, whichever way its page faces.
 *
 * <p>Worth having from the first day rather than the day a colour goes wrong, because a palette is derived:
 * one anchor moved by a tenth changes every surface and every shade of text at once, and nothing on screen
 * says which change did it. These are the checks that would have caught it.
 *
 * <p>Every check is written as a <b>distance from the page</b>, not as "lighter" or "darker", so one test holds the
 * dark look and the light one alike. Contrast is WCAG's ratio: 4.5 for words, 3 for what only has to be seen (a mark,
 * a glyph, faint text). Each of this application's own roles is checked on the surface it is actually painted on.
 */
class LookTest {

    static Stream<Theme> looks() {
        return Stream.of(Look.DARK, Look.LIGHT);
    }

    @ParameterizedTest
    @MethodSource("looks")
    void theInkReadsAgainstThePage(Theme look) {
        assertReads(look, Role.INK, Role.PAGE, 7.0, "primary text");
        assertReads(look, Role.DIM, Role.PAGE, 4.5, "dim text");
        assertReads(look, Role.FAINT, Role.PAGE, 3.0, "faint text");
        assertReads(look, Role.ACCENT, Role.PAGE, 4.5, "the accent, as a rail section's number and a dock tag");
    }

    /** The list and the rail are where most words are: text of every shade reads on their panel too. */
    @ParameterizedTest
    @MethodSource("looks")
    void theInkReadsOnThePanels(Theme look) {
        assertReads(look, Role.INK, Look.LIST, 7.0, "primary text on the list");
        assertReads(look, Role.DIM, Look.LIST, 4.5, "dim text on the list");
        assertReads(look, Role.FAINT, Look.RAIL, 3.0, "faint text on the rail");
        assertReads(look, Role.ACCENT, Look.RAIL, 4.5, "the accent on the rail");
        assertReads(look, Role.INK, Look.TARGETED, 7.0, "a name on a targeted row");
    }

    /** Amber's words, the fresh file's tag and "based on", wherever they are painted. */
    @ParameterizedTest
    @MethodSource("looks")
    void amberReads(Theme look) {
        assertReads(look, Look.ANCHOR, Look.LIST, 4.5, "the new tag on the list");
        assertReads(look, Look.ANCHOR, Look.TARGETED, 4.5, "the new tag on a targeted row");
        assertReads(look, Look.ANCHOR, Look.ANCHOR_WASH, 4.5, "a newer pill");
        assertReads(look, Look.ANCHOR, Look.RAIL, 4.5, "an amber count on the rail");
        assertReads(look, Look.ANCHOR_INK, Look.ANCHOR_WASH, 4.5, "the basis chip");
        assertReads(look, Look.ANCHOR_WASH, Look.ANCHOR, 3.0, "the clock on the amber dot");
    }

    /** Two Folders' tags and marks: each ink on its own wash. */
    @ParameterizedTest
    @MethodSource("looks")
    void twoFoldersChipsRead(Theme look) {
        assertReads(look, Look.FROM_INK, Look.FROM_WASH, 4.5, "FROM's tag");
        assertReads(look, Look.TO_INK, Look.TO_WASH, 4.5, "TO's tag");
        assertReads(look, Look.QUIET_INK, Look.QUIET_WASH, 4.5, "the quiet chip");
        assertReads(look, Look.QUIET_INK, Look.LIST, 3.0, "the hollow mark of probably identical");
        assertReads(look, Look.FROM_INK, Look.RAIL, 4.5, "an only-here count on the rail");
        assertReads(look, Look.CHROME, Role.ACCENT, 3.0, "the strip's arrow on its violet disc");
    }

    /** The TYPE badge: each family's colour as words on the ground the badge is filled with. */
    @ParameterizedTest
    @MethodSource("looks")
    void everyFamilyReadsOnItsBadge(Theme look) {
        List<Role> families = List.of(Look.FOLDER, Look.TEXT, Look.VIDEO, Look.TABLE, Look.PDF, Look.DATA,
                Look.PROGRAM);
        for (Role f : families) {
            assertReads(look, f, Look.CHROME, 4.5, "a family badge");
        }
    }

    /** Panels stand off the ground they sit on, a targeted row off the list, and the strip's hairline is seen. */
    @ParameterizedTest
    @MethodSource("looks")
    void surfacesAreTold(Theme look) {
        // Orbit's own panel stands 1.066:1 off its ground: that separation is the bar the light look is held to.
        assertApart(look, Look.LIST, Look.CHROME, 1.06, "a panel on the ground");
        assertApart(look, Look.TARGETED, Look.LIST, 1.2, "a targeted row on the list");
        assertApart(look, Look.RAISED, Look.LIST, 1.04, "a round button on its panel");
        // Orbit's hairline is 1.27:1 off its ground, so 1.25 is the bar.
        assertApart(look, Look.BRIDGE, Look.CHROME, 1.25, "the strip's hairline");
        float page = luminance(look.color(Role.PAGE));
        float panel = Math.abs(luminance(look.color(Role.PANEL)) - page);
        float raised = Math.abs(luminance(look.color(Role.RAISED)) - page);
        assertTrue(panel > 0, "a panel should stand off the page");
        assertTrue(raised > panel, "a raised surface should stand further off the page than a panel");
    }

    @ParameterizedTest
    @MethodSource("looks")
    void textFadesInSteps(Theme look) {
        float page = luminance(look.color(Role.PAGE));
        float ink = Math.abs(luminance(look.color(Role.INK)) - page);
        float dim = Math.abs(luminance(look.color(Role.DIM)) - page);
        float faint = Math.abs(luminance(look.color(Role.FAINT)) - page);
        assertTrue(ink > dim, "dim text should be quieter than primary text");
        assertTrue(dim > faint, "faint text should be quieter than dim text");
    }

    @ParameterizedTest
    @MethodSource("looks")
    void theAccentIsNotTheInk(Theme look) {
        assertNotEquals(look.color(Role.INK), look.color(Role.ACCENT),
                "the accent should be a colour, not the text shade");
    }

    /** The two looks are two: every role of this application's own answers differently in each. */
    @Test
    void theLooksFaceOppositeWaysAndEveryRoleFollows() {
        assertTrue(luminance(Look.DARK.color(Role.INK)) > luminance(Look.DARK.color(Role.PAGE)), "dark: ink above");
        assertTrue(luminance(Look.LIGHT.color(Role.INK)) < luminance(Look.LIGHT.color(Role.PAGE)), "light: ink below");
        List<Role> own = List.of(Look.CHROME, Look.LIST, Look.RAIL, Look.TARGETED, Look.ANCHOR, Look.ANCHOR_WASH,
                Look.ANCHOR_INK, Look.FROM_WASH, Look.FROM_INK, Look.TO_WASH, Look.TO_INK, Look.QUIET_WASH,
                Look.QUIET_INK, Look.RAISED, Look.BRIDGE, Look.FOLDER, Look.TEXT, Look.VIDEO, Look.TABLE, Look.PDF,
                Look.DATA, Look.PROGRAM);
        for (Role r : own) {
            assertNotEquals(Look.DARK.color(r), Look.LIGHT.color(r), "a role of ours has an answer in each look");
        }
    }

    /** The dark look is Orbit's, to the hex: a token moved by the light look's arrival is a regression. */
    @Test
    void theDarkLookIsStillOrbit() {
        assertEquals(Color.rgb(0x0c0b10), Look.DARK.color(Look.CHROME));
        assertEquals(Color.rgb(0x15131c), Look.DARK.color(Look.LIST));
        assertEquals(Color.rgb(0x2d2547), Look.DARK.color(Look.TARGETED));
        assertEquals(Color.rgb(0xe8a85c), Look.DARK.color(Look.ANCHOR));
        assertEquals(Color.rgb(0xc9b6ff), Look.DARK.color(Look.FOLDER));
        assertEquals(Color.rgb(0x86d9ff), Look.DARK.color(Look.PROGRAM));
    }

    /**
     * Orbit's surfaces the dark ladder is meant to land on, each within 0.01 of Oklab lightness: rung 1 is the panel, 2
     * the card, 3 the raised control. A colour that drifts from its rung is a decision to make, not a rounding to absorb.
     */
    @Test
    void theLadderLandsOnTheTokens() {
        assertNear(0x15131c, Look.DARK_PALETTE.surface(1), "panel");
        assertNear(0x1b1824, Look.DARK_PALETTE.surface(2), "card");
        assertNear(0x221f2e, Look.DARK_PALETTE.surface(3), "raised");
    }

    private static void assertNear(int token, Color rung, String name) {
        double want = Oklab.of(Color.rgb(token)).l();
        double got = Oklab.of(rung).l();
        assertTrue(Math.abs(want - got) < 0.01, name + ": the rung is " + got + ", the token " + want);
    }

    private static void assertReads(Theme look, Role ink, Role on, double least, String what) {
        double ratio = contrast(look.color(ink), look.color(on));
        assertTrue(ratio >= least, what + " is " + String.format("%.2f", ratio) + ":1 in the "
                + (look == Look.DARK ? "dark" : "light") + " look; it needs " + least + ":1");
    }

    private static void assertApart(Theme look, Role a, Role b, double least, String what) {
        double ratio = contrast(look.color(a), look.color(b));
        assertTrue(ratio >= least, what + " is only " + String.format("%.3f", ratio) + ":1 apart in the "
                + (look == Look.DARK ? "dark" : "light") + " look; it needs " + least + ":1");
    }

    /** WCAG's contrast ratio between two colours. */
    private static double contrast(Color a, Color b) {
        double la = relative(a);
        double lb = relative(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** WCAG's relative luminance, from sRGB. */
    private static double relative(Color c) {
        return 0.2126 * linear(c.r()) + 0.7152 * linear(c.g()) + 0.0722 * linear(c.b());
    }

    private static double linear(float channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    /** Rough perceived brightness -- enough to order two shades, which is all the ordering checks need. */
    private static float luminance(Color c) {
        return 0.2126f * c.r() + 0.7152f * c.g() + 0.0722f * c.b();
    }
}
