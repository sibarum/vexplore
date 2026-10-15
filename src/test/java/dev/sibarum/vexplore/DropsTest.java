package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.ops.Plan;
import org.junit.jupiter.api.Test;
import sibarum.tactroller.api.Modifier;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What letting go of dragged files means: move or copy, by drive and by key, or nothing at all. */
class DropsTest {

    private static final Entry FILE = new Entry(Path.of("C:/x/notes.txt"), false, 10, Instant.EPOCH);
    private static final Entry FOLDER = new Entry(Path.of("C:/x/stuff"), true, 0, Instant.EPOCH);
    private static final Set<Modifier> NONE = Set.of();

    @Test
    void withinADriveItMovesAndToAnotherItCopies() {
        assertEquals(Plan.Kind.MOVE, Drops.kind(List.of(FILE), Path.of("C:/y"), NONE));
        assertEquals(Plan.Kind.COPY, Drops.kind(List.of(FILE), Path.of("D:/y"), NONE));
    }

    @Test
    void controlCopiesAndShiftMoves() {
        assertEquals(Plan.Kind.COPY, Drops.kind(List.of(FILE), Path.of("C:/y"), Set.of(Modifier.CONTROL)));
        assertEquals(Plan.Kind.MOVE, Drops.kind(List.of(FILE), Path.of("D:/y"), Set.of(Modifier.SHIFT)));
    }

    @Test
    void nothingHappensWhereNothingWould() {
        assertNull(Drops.kind(List.of(FILE), Path.of("C:/x"), NONE), "already there");
        assertNull(Drops.kind(List.of(FOLDER), Path.of("C:/x/stuff"), NONE), "into itself");
        assertNull(Drops.kind(List.of(FOLDER), Path.of("C:/x/stuff/inner"), NONE), "into something inside it");
        assertNull(Drops.kind(List.of(FILE), null, NONE), "no folder shown");
    }

    @Test
    void somethingNotAlreadyThereIsEnough() {
        Entry elsewhere = new Entry(Path.of("C:/z/b.txt"), false, 1, Instant.EPOCH);
        assertEquals(Plan.Kind.MOVE, Drops.kind(List.of(FILE, elsewhere), Path.of("C:/x"), NONE));
    }

    @Test
    void theLabelSaysWhatIsCarried() {
        assertEquals("notes.txt", Drops.label(List.of(FILE)));
        assertEquals("2 items", Drops.label(List.of(FILE, FOLDER)));
        assertEquals("2 files", Drops.label(List.of(FILE, FILE)));
    }
}
