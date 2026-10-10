package dev.sibarum.vexplore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The header's file name, shortened so it never widens the rail. */
class RailViewTest {

    @Test
    void aShortNameIsLeftAlone() {
        assertEquals("holiday.png", RailView.shorten("holiday.png", 28));
    }

    @Test
    void aLongNameIsCutInTheMiddleAndKeepsItsExtension() {
        String s = RailView.shorten("a-really-very-long-screenshot-name-from-yesterday.png", 28);
        assertEquals(28, s.length(), s);
        assertTrue(s.startsWith("a-really-"), s);
        assertTrue(s.endsWith(".png"), s);
        assertTrue(s.contains("…"), s);
    }

    @Test
    void aLongExtensionlessNameStillFits() {
        String s = RailView.shorten("x".repeat(80), 28);
        assertEquals(28, s.length(), s);
    }
}
