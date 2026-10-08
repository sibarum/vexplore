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

    @Test
    void aPictureComesInFromTheSideItIsOn() {
        assertEquals(1, Viewer.direction(PICTURES, A, C), "later in the folder: in from the right");
        assertEquals(-1, Viewer.direction(PICTURES, C, B));
        assertEquals(0, Viewer.direction(PICTURES, B, B), "a sharper copy of the same file does not slide");
        assertEquals(0, Viewer.direction(PICTURES, null, A), "the first picture shown just appears");
        assertEquals(0, Viewer.direction(PICTURES, Path.of("gone.png"), A));
    }

    @Test
    void aPictureIsShownWholeAndNeverEnlarged() {
        // 400 x 200 in a 1000 x 1000 stage: its own size, so 40% by 20%.
        var small = new dev.sibarum.vexplore.files.Picture(null, new byte[0], 400, 200, 1, 1, new int[1], 400, 200,
                false);
        float[] f = ImageView.fraction(small, 1000f, 1000f);
        assertEquals(0.4f, f[0], 1e-6f);
        assertEquals(0.2f, f[1], 1e-6f);
        // The same in a 200 x 200 stage: limited by its width, so all of it across and half down.
        f = ImageView.fraction(small, 200f, 200f);
        assertEquals(1f, f[0], 1e-6f);
        assertEquals(0.5f, f[1], 1e-6f);
    }

    @Test
    void thumbnailsTakeWhatTheTwoSidesLeave() {
        // 72 px tiles and 8 px gaps, as drawn at the design's size: the middle slab is 16 + 72 + (n + 1) * 80.
        float tile = 72f;
        float gap = 8f;
        float side = 300f;
        float middleFor7 = 2 * gap + 9 * tile + 8 * gap;
        assertEquals(7, Viewer.thumbnails(2 * side + 2 * 16f + middleFor7, side, tile, gap, 16f));
        assertEquals(6, Viewer.thumbnails(2 * side + 2 * 16f + middleFor7 - 1f, side, tile, gap, 16f));
        assertEquals(0, Viewer.thumbnails(500f, side, tile, gap, 16f), "no room: Previous and Next only");
    }

    private static Entry entry(String name, boolean folder) {
        return new Entry(Path.of(name), folder, 1, Instant.EPOCH);
    }
}
