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

    /** One volume is one drive letter, as on Windows; each OS's own meaning is tested in its module. */
    private static Plan.Kind kind(List<Entry> held, Path into, Set<Modifier> keys) {
        return Drops.kind(held, into, keys,
                (a, b) -> Character.toUpperCase(a.toString().charAt(0)) == Character.toUpperCase(b.toString().charAt(0)));
    }

    @Test
    void withinADriveItMovesAndToAnotherItCopies() {
        assertEquals(Plan.Kind.MOVE, kind(List.of(FILE), Path.of("C:/y"), NONE));
        assertEquals(Plan.Kind.COPY, kind(List.of(FILE), Path.of("D:/y"), NONE));
    }

    @Test
    void controlCopiesAndShiftMoves() {
        assertEquals(Plan.Kind.COPY, kind(List.of(FILE), Path.of("C:/y"), Set.of(Modifier.CONTROL)));
        assertEquals(Plan.Kind.MOVE, kind(List.of(FILE), Path.of("D:/y"), Set.of(Modifier.SHIFT)));
    }

    @Test
    void nothingHappensWhereNothingWould() {
        assertNull(kind(List.of(FILE), Path.of("C:/x"), NONE), "already there");
        assertNull(kind(List.of(FOLDER), Path.of("C:/x/stuff"), NONE), "into itself");
        assertNull(kind(List.of(FOLDER), Path.of("C:/x/stuff/inner"), NONE), "into something inside it");
        assertNull(kind(List.of(FILE), null, NONE), "no folder shown");
    }

    @Test
    void somethingNotAlreadyThereIsEnough() {
        Entry elsewhere = new Entry(Path.of("C:/z/b.txt"), false, 1, Instant.EPOCH);
        assertEquals(Plan.Kind.MOVE, kind(List.of(FILE, elsewhere), Path.of("C:/x"), NONE));
    }

    @Test
    void theLabelSaysWhatIsCarried() {
        assertEquals("notes.txt", Drops.label(List.of(FILE)));
        assertEquals("2 items", Drops.label(List.of(FILE, FOLDER)));
        assertEquals("2 files", Drops.label(List.of(FILE, FILE)));
    }
}
