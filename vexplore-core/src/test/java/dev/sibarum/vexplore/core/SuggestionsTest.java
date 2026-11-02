package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Suggested;
import dev.sibarum.vexplore.core.Suggestions.Act;
import dev.sibarum.vexplore.core.Suggestions.Condition;
import dev.sibarum.vexplore.core.Suggestions.Pick;
import dev.sibarum.vexplore.core.Suggestions.Rail;
import dev.sibarum.vexplore.core.Suggestions.Scope;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rail is a pure function, so it is tested against a folder that is a list of values: no disk, no window. The
 * folder is the Downloads of the first mockup, which is why the numbers are the mockup's. 0.1's test, over rows.
 */
class SuggestionsTest {

    private static final long MB = 1L << 20;
    private static final Instant NOW = ZonedDateTime.of(2026, 9, 30, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    private static Row file(String name, long size, int daysAgo) {
        return new Row(Path.of("D", name), name, false, size, NOW.minusSeconds(daysAgo * 86_400L + 60),
                Kind.ofExtension(Kind.extension(name)));
    }

    /** Nine videos, and three other things. */
    private static List<Row> downloads() {
        List<Row> e = new ArrayList<>();
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

    private static Rail rail(List<Row> rows, String selected, Pick pick) {
        return Suggestions.of(rows, Set.of(Path.of("D", selected)), pick, TODAY, ZoneOffset.UTC);
    }

    @Test
    void nothingSelectedIsAnEmptyRail() {
        Rail r = Suggestions.of(downloads(), Set.of(), Pick.NONE, TODAY, ZoneOffset.UTC);
        assertEquals(Rail.EMPTY, r, "the rail may be empty, and saying nothing is the design");
        assertEquals(Suggested.NONE, r.worded());
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
    void acceptingASuggestionReachesTheWholeGroupAndStatesTheEffectFirst() {
        Rail r = rail(downloads(), "trip-recap-final.mp4", Pick.NONE.with(Scope.SAME_KIND).with(Act.MOVE));
        assertEquals(9, r.targets().size());
        Suggested s = r.worded();
        assertEquals(9, s.reach().size());
        assertEquals("Move 9 files · " + Bytes.format(r.bytes()), s.card().headline());
        assertTrue(s.card().detail().endsWith("and 6 more, all marked in the list"));
    }

    @Test
    void ignoringEveryChipLeavesJustTheSelection() {
        Rail r = rail(downloads(), "trip-recap-final.mp4", Pick.NONE);
        assertEquals(1, r.targets().size(), "the default costs nothing: it is the selection");
        assertEquals("trip-recap-final.mp4", r.basis());
    }

    @Test
    void aSuggestionThatChangesNothingIsNotOffered() {
        Rail r = rail(List.of(file("only.mp4", 5 * MB, 0)), "only.mp4", Pick.NONE);
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
        assertEquals(2, today.targets().size(), "trip-recap-final and IMG_2231");
    }

    @Test
    void aStaleConditionFallsBackToAll() {
        Rail r = rail(downloads(), "trip-recap-final.mp4", Pick.NONE.with(Condition.LARGER_THAN));
        assertEquals(1, r.targets().size(), "one file cannot be narrowed by size; the pick no longer applies");
        assertEquals(Condition.ALL, r.applied().condition());
    }

    @Test
    void renameIsOfferedOnlyForOneFile() {
        assertTrue(rail(downloads(), "setup.exe", Pick.NONE).acts().contains(Act.RENAME));
        Rail all = rail(downloads(), "trip-recap-final.mp4", Pick.NONE.with(Scope.SAME_KIND).with(Act.RENAME));
        assertFalse(all.acts().contains(Act.RENAME));
        assertEquals(null, all.applied().act(), "a rename of nine is not offered, so it is not picked");
    }

    @Test
    void theChipPickedIsLit() {
        Suggested s = rail(downloads(), "trip-recap-final.mp4", Pick.NONE.with(Scope.SAME_KIND)).worded();
        assertFalse(s.select().get(0).on());
        assertTrue(s.select().get(1).on());
        assertTrue(s.condition().get(0).on(), "All, until a condition is picked");
        assertTrue(s.action().stream().noneMatch(Messages.Chip::on));
    }
}
