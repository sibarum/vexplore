package dev.sibarum.vexplore.suggest;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.suggest.Intents.Candidate;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What Shift and Control are about to mean, for a folder shown in a known order. */
class IntentsTest {

    private static final Instant NOW = ZonedDateTime.of(2026, 9, 30, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();

    private static Entry f(String name, int daysAgo) {
        return new Entry(Path.of("D", name), false, 1L, NOW.minusSeconds(daysAgo * 86_400L + 60));
    }

    /** As displayed: a run of lecture videos, a photo, the lecture notes, a text file. */
    private static List<Entry> shown() {
        List<Entry> e = new ArrayList<>();
        e.add(f("invoice.pdf", 0));
        e.add(f("lecture-05.mkv", 1));
        e.add(f("lecture-06.mkv", 1));
        e.add(f("lecture-07.mkv", 1));
        e.add(f("lecture-08.mkv", 1));
        e.add(f("photo.png", 2));
        e.add(f("lecture-notes.pdf", 2));
        e.add(f("notes.txt", 2));
        return e;
    }

    private static Set<Path> sel(String... names) {
        java.util.HashSet<Path> s = new java.util.HashSet<>();
        for (String n : names) {
            s.add(Path.of("D", n));
        }
        return s;
    }

    private static Candidate find(List<Candidate> cs, String startsWith) {
        return cs.stream().filter(c -> c.label().startsWith(startsWith)).findFirst()
                .orElseThrow(() -> new AssertionError(startsWith + " not in " + cs.stream().map(Candidate::label).toList()));
    }

    @Test
    void shiftOnOneLectureProposesWhereTheRunEnds() {
        List<Candidate> c = Intents.shift(shown(), sel("lecture-06.mkv"), ZoneOffset.UTC);
        Candidate down = find(c, "Down to the end of this video run");
        assertEquals(sel("lecture-06.mkv", "lecture-07.mkv", "lecture-08.mkv"), down.paths());
        Candidate up = find(c, "Up to the start of this video run");
        assertEquals(sel("lecture-05.mkv", "lecture-06.mkv"), up.paths());
    }

    @Test
    void theWholeRunIsOfferedWhenBothWaysReach() {
        Candidate whole = find(Intents.shift(shown(), sel("lecture-06.mkv"), ZoneOffset.UTC), "The whole video run");
        assertEquals(4, whole.count());
    }

    @Test
    void aRunIsDecidedInTheOrderShownNotInTheFolders() {
        List<Entry> reversed = new ArrayList<>(shown());
        java.util.Collections.reverse(reversed);
        Candidate down = find(Intents.shift(reversed, sel("lecture-07.mkv"), ZoneOffset.UTC), "Down to the end");
        assertEquals(sel("lecture-07.mkv", "lecture-06.mkv", "lecture-05.mkv"), down.paths(),
                "down is the way the eye goes: with the list reversed, it is toward lecture-05");
    }

    @Test
    void shiftWithNothingSelectedSaysNothing() {
        assertEquals(List.of(), Intents.shift(shown(), Set.of(), ZoneOffset.UTC));
    }

    @Test
    void twoIdenticalResultsAreOneChip() {
        List<Candidate> c = Intents.shift(shown(), sel("lecture-06.mkv"), ZoneOffset.UTC);
        long distinct = c.stream().map(Candidate::paths).distinct().count();
        assertEquals(c.size(), distinct);
    }

    @Test
    void controlOnTwoLecturesProposesTheRuleTheyAreExamplesOf() {
        List<Candidate> c = Intents.control(shown(), sel("lecture-05.mkv", "lecture-07.mkv"), NOW, ZoneOffset.UTC);
        Candidate prefix = find(c, "Names starting");
        assertTrue(prefix.label().contains("lecture-"), prefix.label());
        assertEquals(5, prefix.count(), "four videos and the notes");
    }

    @Test
    void controlDoesNotOfferARuleThatReachesNoFurther() {
        Set<Path> all = sel("lecture-05.mkv", "lecture-06.mkv", "lecture-07.mkv", "lecture-08.mkv");
        List<Candidate> c = Intents.control(shown(), all, NOW, ZoneOffset.UTC);
        assertTrue(c.stream().noneMatch(x -> x.paths().equals(all)), "that is what is already selected");
    }

    @Test
    void controlOnOneFileGuessesFromItsName() {
        List<Candidate> c = Intents.control(shown(), sel("lecture-06.mkv"), NOW, ZoneOffset.UTC);
        assertTrue(c.stream().anyMatch(x -> x.label().startsWith("Names starting")));
        assertTrue(c.stream().anyMatch(x -> x.label().startsWith("All videos")));
    }

    @Test
    void aFolderInTheSelectionMeansNoRule() {
        List<Entry> es = new ArrayList<>(shown());
        es.add(new Entry(Path.of("D", "sub"), true, 0L, NOW));
        assertEquals(List.of(), Intents.control(es, sel("sub"), NOW, ZoneOffset.UTC));
    }

    @Test
    void sharedPrefixesAreWholeWords() {
        List<Entry> two = List.of(f("lecture-06.mkv", 1), f("lecture-07.mkv", 1));
        assertEquals("lecture-", Intents.commonPrefix(two));
    }
}
