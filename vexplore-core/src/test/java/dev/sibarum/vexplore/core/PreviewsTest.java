package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Preview.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The preview ladder against real files: what the dock is given for a folder, a file, and one that cannot be read. */
class PreviewsTest {

    @TempDir
    Path dir;

    @Test
    void aFolderSaysHowManyItHolds() throws Exception {
        Path sub = Files.createDirectory(dir.resolve("sub"));
        Files.writeString(sub.resolve("a"), "a");
        Files.writeString(sub.resolve("b"), "b");
        Preview p = Previews.of(sub);
        assertEquals(Tier.FOLDER, p.tier());
        assertEquals(2, p.size());
    }

    @Test
    void anyFileIsItsBytesNamedByItsFirstOnes() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 'I', 'H', 'D', 'R', 'h', 'e', 'l', 'l', 'o', 'w'};
        Path f = Files.write(dir.resolve("x.png"), png);
        Preview p = Previews.of(f);
        assertEquals(Tier.BYTES, p.tier());
        assertEquals("PNG image", p.identity());
        assertEquals(png.length, p.size());
        assertEquals(2, p.lines().size(), "sixteen bytes a row");
        assertTrue(p.lines().getFirst().startsWith("00000000  89 50 4e 47"));
        assertFalse(p.truncated());
    }

    @Test
    void stringsAreTheReadableRunsAndEntropyMeasuresTheRest() {
        byte[] b = "\u0000\u0001KILN-CTRL v2\u0000ramp\u0000hold-time\u0002"
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        assertEquals(List.of("KILN-CTRL v2", "hold-time"), Previews.strings(b), "runs of six or more");
        assertEquals(0d, Previews.entropy(new byte[100]), 1e-9, "one value repeated says nothing");
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        assertEquals(8d, Previews.entropy(all), 1e-9, "every value once is as random as bytes get");
    }

    @Test
    void aLargeFileIsReadOnlyAsFarAsTheSampleAndSaysSo() throws Exception {
        Path f = Files.write(dir.resolve("big.bin"), new byte[Previews.SAMPLE + 10]);
        Preview p = Previews.of(f);
        assertTrue(p.truncated());
        assertEquals(Previews.SAMPLE + 10, p.size());
        assertEquals(Previews.DUMP / 16, p.lines().size());
    }

    @Test
    void aFileThatIsNotThereIsSaidNotThrown() {
        Preview p = Previews.of(dir.resolve("gone.bin"));
        assertTrue(p.identity().startsWith("unreadable: "));
    }
}
