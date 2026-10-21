package dev.sibarum.vexplore.suggest;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.suggest.Suggestions.Act;
import dev.sibarum.vexplore.suggest.Suggestions.Condition;
import dev.sibarum.vexplore.suggest.Suggestions.Pick;
import dev.sibarum.vexplore.suggest.Suggestions.Rail;
import dev.sibarum.vexplore.suggest.Suggestions.Scope;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rail is a pure function, so it is tested against a folder that is a list of values: no disk, no window.
 * The folder is the Downloads of the first mockup, which is why the numbers are the mockup numbers.
 */
class SuggestionsTest {

    private static final long MB = 1L << 20;
    private static final long GB = 1L << 30;
    private static final Instant NOW = ZonedDateTime.of(2026, 9, 30, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();

    private static Entry file(String name, long size, int daysAgo) {
        return new Entry(Path.of("D", name), false, size, NOW.minusSeconds(daysAgo * 86_400L + 60));
    }

    /** Nine videos, and three other things. */
    private static List<Entry> downloads() {
        List<Entry> e = new ArrayList<>();
        e.add(file("invoice.pdf", 184 * 1024, 0));
        e.add(file("trip-recap-final.mp4", 1455 * MB, 0));
        e.add(file("IMG_2231.mov", 388 * MB, 0));
        e.add(file("lecture-07.mkv", 812 * MB, 1));
        e.add(file("lecture-06.mkv", 790 * MB, 1));
        e.add(file("kiln-cam.mp4", 2150 * MB, 3));
        e.add(file("setup.exe", 96 * MB, 3));
        e.add(file("clip-a.mp4", 146 * MB, 4));
        e.add(file("clip-b.mp4", 171 * MB, 4));
        e.add(file("firing-log.csv", 12 * 1024, 4));
        e.add(file("demo-reel.webm", 264 * MB, 6));
        e.add(file("IMG_2198.mov", 402 * MB, 8));
        return e;
    }

    private static Rail rail(List<Entry> entries, String selected, Pick pick) {
        Set<Path> paths = Set.of(Path.of("D", selected));
        return Suggestions.of(entries, paths, pick, NOW, ZoneOffset.UTC);
    }

    @Test
    void nothingSelectedIsAnEmptyRail() {
        Rail r = Suggestions.of(downloads(), Set.of(), Pick.NONE, NOW, ZoneOffset.UTC);
        assertEquals(Rail.EMPTY, r, "the rail may be empty, and saying nothing is the design");
    }

    @Test
    void oneVideoOffersEveryVideoHere() {
        Rail r = rail(downloads(), "trip-recap-final.mp4", Pick.NONE);
        var all = r.scopes().stream().filter(o -> o.value() == Scope.SAME_KIND).findFirst().orElseThrow();
        assertEquals("All videos here", all.label());
        assertEquals(9, all.count(), "mp4 mov mkv webm across the folder");
    }

    @Test
    void theBigVideosChipNamesARoundThresholdBelowTheSelectedFile() {
        Rail r = rail(downloads(), "trip-recap-final.mp4", Pick.NONE);
        var big = r.scopes().stream().filter(o -> o.value() == Scope.BIG_OF_KIND).findFirst().orElseThrow();
        assertEquals("Videos over 1 GB", big.label(), "1.42 GB selected, so the round number under it is 1 GB");
        assertTrue(big.count() >= 1);
    }

    @Test
    void acceptingASuggestionSelectsTheWholeGroupAndStatesTheEffectFirst() {
        Rail r = rail(downloads(), "trip-recap-final.mp4", Pick.NONE.with(Scope.SAME_KIND).with(Act.MOVE));
        assertEquals(9, r.effect().count());
        long expected = r.effect().targets().stream().mapToLong(Entry::size).sum();
        assertEquals(expected, r.effect().bytes());
    }

    @Test
    void ignoringEveryChipLeavesJustTheSelection() {
        Rail r = rail(downloads(), "trip-recap-final.mp4", Pick.NONE);
        assertEquals(1, r.effect().count(), "the default costs nothing: it is the selection");
        assertEquals("trip-recap-final.mp4", r.basis());
    }

    @Test
    void aSuggestionThatChangesNothingIsNotOffered() {
        List<Entry> one = List.of(file("only.mp4", 5 * MB, 0));
        Rail r = rail(one, "only.mp4", Pick.NONE);
        assertEquals(1, r.scopes().size(), "just this file; there is no wider group to offer");
        assertFalse(r.scopes().stream().anyMatch(o -> o.value() == Scope.SAME_KIND));
    }

    @Test
    void conditionsNarrowTheScopeAndOnlyAppearWhenTheyWould() {
        Rail wide = rail(downloads(), "trip-recap-final.mp4", Pick.NONE.with(Scope.SAME_KIND));
        assertTrue(wide.conditions().stream().anyMatch(o -> o.value() == Condition.MODIFIED_TODAY),
                "some of the videos are from today and some are not");

        Rail today = rail(downloads(), "trip-recap-final.mp4",
                Pick.NONE.with(Scope.SAME_KIND).with(Condition.MODIFIED_TODAY));
        assertEquals(2, today.effect().count(), "trip-recap-final and IMG_2231");
    }

    @Test
    void aStaleConditionFallsBackToAll() {
        Rail r = rail(downloads(), "trip-recap-final.mp4", Pick.NONE.with(Condition.LARGER_THAN));
        assertEquals(1, r.effect().count(), "one file cannot be narrowed by size; the pick no longer applies");
    }

    @Test
    void sizesReadTheWayAPersonWritesThem() {
        assertEquals("184 KB", Bytes.format(184 * 1024));
        assertEquals("1.42 GB", Bytes.format((long) (1.42 * GB)));
        assertEquals("3.4 MB", Bytes.format((long) (3.4 * MB)));
        assertEquals("96 MB", Bytes.format(96 * MB));
        assertEquals("500 MB", Bytes.format(500 * MB));
        assertEquals("4 KB", Bytes.format(4 * 1024));
        assertEquals("0 B", Bytes.format(0));
    }

    @Test
    void datesReadTodayYesterdayThenTheDay() {
        var zone = ZoneOffset.UTC;
        assertEquals("Today 09:30", Dates.column(NOW.minusSeconds(30 * 60), NOW, zone));
        assertEquals("Yesterday", Dates.column(NOW.minusSeconds(86_400), NOW, zone));
        assertEquals("Sep 27", Dates.column(NOW.minusSeconds(3 * 86_400), NOW, zone));
        assertEquals("Sep 30, 2024",
                Dates.column(ZonedDateTime.of(2024, 9, 30, 1, 0, 0, 0, zone).toInstant(), NOW, zone));
    }
}
