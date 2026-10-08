package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Where the viewer's steps land, and what the previewer reads ahead: both pure, both over the folder's images. */
class ViewerStepTest {

    private static final Path A = Path.of("a.png");
    private static final Path B = Path.of("b.jpg");
    private static final Path C = Path.of("c.gif");
    private static final List<Path> PICTURES = List.of(A, B, C);

    @Test
    void stepsStopAtTheEnds() {
        assertEquals(B, Viewer.target(PICTURES, A, 1));
        assertEquals(A, Viewer.target(PICTURES, A, -1), "no wrap: the first stays the first");
        assertEquals(C, Viewer.target(PICTURES, C, 1), "and the last the last");
        assertEquals(C, Viewer.target(PICTURES, A, 5));
    }

    @Test
    void homeAndEndAreTheFirstAndLast() {
        assertEquals(A, Viewer.target(PICTURES, B, Integer.MIN_VALUE));
        assertEquals(C, Viewer.target(PICTURES, B, Integer.MAX_VALUE));
    }

    @Test
    void fromSomethingThatIsNotAnImageAStepStartsAtTheFirst() {
        assertEquals(A, Viewer.target(PICTURES, Path.of("notes.txt"), 1));
        assertEquals(A, Viewer.target(PICTURES, null, -1));
        assertNull(Viewer.target(List.of(), A, 1));
    }

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

    private static Entry entry(String name, boolean folder) {
        return new Entry(Path.of(name), folder, 1, Instant.EPOCH);
    }
}
