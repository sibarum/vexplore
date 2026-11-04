package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Row;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompareTest {

    private static final Instant T = Instant.parse("2026-10-30T12:00:00Z");

    private static Row file(String folder, String name, long size, Instant modified) {
        return new Row(Path.of(folder, name), name, false, size, modified, Kind.TEXT);
    }

    private static Row folder(String folder, String name) {
        return new Row(Path.of(folder, name), name, true, 0L, T, Kind.FOLDER);
    }

    private static Relation of(Row here, Row there) {
        return Compare.of(List.of(here), List.of(there)).get(here.path());
    }

    @Test
    void theListingsDecideByNameSizeAndTime() {
        Row a = file("here", "a", 10, T);
        assertEquals(Relation.IDENTICAL, of(a, file("there", "a", 10, T)));
        assertEquals(Relation.IDENTICAL, of(a, file("there", "a", 10, T.plusMillis(1999))), "FAT's two seconds");
        assertEquals(Relation.PROBABLY_IDENTICAL, of(a, file("there", "a", 10, T.plusSeconds(60))));
        assertEquals(Relation.NEWER, of(a, file("there", "a", 11, T.minusSeconds(60))));
        assertEquals(Relation.OLDER, of(a, file("there", "a", 11, T.plusSeconds(60))));
        assertEquals(Relation.DIFFERENT, of(a, file("there", "a", 11, T)), "a different size at the same time");
        assertEquals(Relation.ONLY_HERE, of(a, file("there", "A", 10, T)), "names match exactly, as Plan's do");
    }

    @Test
    void aFolderIsComparedByItsNameAlone() {
        Row sub = folder("here", "sub");
        assertEquals(Relation.IN_BOTH, of(sub, folder("there", "sub")));
        assertEquals(Relation.DIFFERENT, of(sub, file("there", "sub", 0, T)), "a file of a folder's name");
        assertEquals(Relation.DIFFERENT, of(file("here", "sub", 0, T), folder("there", "sub")));
    }

    @Test
    void eachSideSaysItFromItself() {
        List<Row> here = List.of(file("here", "a", 1, T), file("here", "b", 2, T.plusSeconds(60)));
        List<Row> there = List.of(file("there", "b", 3, T), file("there", "c", 1, T));
        assertEquals(Map.of(Path.of("here", "a"), Relation.ONLY_HERE, Path.of("here", "b"), Relation.NEWER),
                Compare.of(here, there));
        assertEquals(Map.of(Path.of("there", "b"), Relation.OLDER, Path.of("there", "c"), Relation.ONLY_HERE),
                Compare.of(there, here));
    }

    @Test
    void fiftyThousandRowsASideAreComparedInAStep() {
        List<Row> here = new ArrayList<>();
        List<Row> there = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            here.add(file("here", "f" + i, i, T));
            there.add(file("there", "f" + (i + 25_000), i + 25_000, T));
        }
        Compare.of(here, there);
        long start = System.nanoTime();
        Map<Path, Relation> compared = Compare.of(here, there);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertEquals(50_000, compared.size());
        assertEquals(25_000, compared.values().stream().filter(r -> r == Relation.IDENTICAL).count());
        assertTrue(ms < 250, "took " + ms + " ms");
    }
}
