package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** What the viewer steps through and the previewer reads ahead: pure, over the folder's listing. */
class PreviewerImagesTest {

    private static final Path A = Path.of("a.png");
    private static final Path B = Path.of("b.jpg");
    private static final Path C = Path.of("c.gif");

    @Test
    void onlyImagesAreSteppedThroughInTheOrderShown() {
        List<Entry> ordered = List.of(entry("b.jpg", false), entry("notes.txt", false), entry("pics.png", true),
                entry("a.png", false), entry("c.gif", false));
        assertEquals(List.of(B, A, C), Previewer.images(ordered), "a folder named like an image is still a folder");
    }

    @Test
    void readingAheadIsNextThenPreviousThenTheOneAfterNext() {
        List<Entry> ordered = List.of(entry("a.png", false), entry("b.jpg", false), entry("c.gif", false),
                entry("d.webp", false));
        assertEquals(List.of(C, A, Path.of("d.webp")), Previewer.around(ordered, B));
        assertEquals(List.of(B, C), Previewer.around(ordered, A), "nothing before the first");
        assertEquals(List.of(), Previewer.around(ordered, Path.of("notes.txt")));
    }

    @Test
    void theFilmstripsReachIsWhatAroundLeavesWithinThree() {
        List<Entry> ordered = new java.util.ArrayList<>();
        for (char c = 'a'; c <= 'h'; c++) {
            ordered.add(entry(c + ".png", false));
        }
        Path d = Path.of("d.png");
        assertEquals(List.of(Path.of("b.png"), Path.of("g.png"), Path.of("a.png")), Previewer.reach(ordered, d));
        assertEquals(List.of(Path.of("d.png")), Previewer.reach(ordered, A),
                "from the first, only three after: around has the next two");
        assertEquals(List.of(), Previewer.reach(ordered, Path.of("notes.txt")));
    }

    private static Entry entry(String name, boolean folder) {
        return new Entry(Path.of(name), folder, 1, Instant.EPOCH);
    }
}
