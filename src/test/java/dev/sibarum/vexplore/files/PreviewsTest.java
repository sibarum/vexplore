package dev.sibarum.vexplore.files;

import dev.sibarum.vexplore.files.Preview.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ladder's floor: no file is ever blank, text is text, and everything else still says something. */
class PreviewsTest {

    private static final Path P = Path.of("x");

    @Test
    void textIsTierThreeWithItsFirstLines() {
        byte[] b = "one\r\ntwo\nthree".getBytes(StandardCharsets.UTF_8);
        Preview p = Previews.of(P, b, false);
        assertEquals(Tier.TEXT, p.tier());
        assertEquals(java.util.List.of("one", "two", "three"), p.lines());
        assertFalse(p.truncated());
    }

    @Test
    void multiByteTextCutInHalfByTheSampleEndIsStillText() {
        byte[] whole = "café 中".getBytes(StandardCharsets.UTF_8);
        byte[] cut = java.util.Arrays.copyOf(whole, whole.length - 1);
        assertEquals(Tier.TEXT, Previews.of(P, cut, true).tier(), "an incomplete tail proves nothing when truncated");
        assertEquals(Tier.BYTES, Previews.of(P, cut, false).tier(), "but a whole file that ends mid-character is not text");
    }

    @Test
    void aNulByteMakesItBytes() {
        Preview p = Previews.of(P, new byte[]{'a', 0, 'b'}, false);
        assertEquals(Tier.BYTES, p.tier());
    }

    @Test
    void bytesGetAHexDumpStringsAndAnEntropy() {
        byte[] b = new byte[64];
        byte[] name = "hello-world".getBytes(StandardCharsets.US_ASCII);
        b[0] = (byte) 0x89;
        System.arraycopy(name, 0, b, 20, name.length);
        Preview p = Previews.of(P, b, false);

        assertEquals(Tier.BYTES, p.tier());
        assertEquals(4, p.lines().size(), "sixty-four bytes is four rows of sixteen");
        assertTrue(p.lines().get(0).startsWith("00000000  89 00"), p.lines().get(0));
        assertTrue(p.lines().get(1).endsWith("|....hello-world.....|") || p.lines().get(1).contains("hello-world"));
        assertEquals(java.util.List.of("hello-world"), p.strings());
        assertTrue(p.entropy() < 2d, "mostly zeros is nowhere near random");
    }

    @Test
    void randomDataIsHighEntropy() {
        byte[] b = new byte[Previews.SAMPLE];
        new Random(1).nextBytes(b);
        assertTrue(Previews.entropy(b) > 7.9d);
    }

    @Test
    void signaturesNameWhatTheyKnowAndNothingElse() {
        assertEquals("PNG image", Previews.identify(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0}));
        assertEquals("PDF document", Previews.identify("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("unknown binary", Previews.identify(new byte[]{1, 2, 3, 4}));
        assertEquals("empty file", Previews.identify(new byte[0]));
    }

    @Test
    void anEmptyFileIsNotBlank(@TempDir Path dir) throws IOException {
        Path f = Files.createFile(dir.resolve("empty"));
        Preview p = Previews.of(f);
        assertEquals("empty file", p.identity());
    }

    @Test
    void aFolderIsAFolderAndAMissingFileIsSaidNotThrown(@TempDir Path dir) {
        assertEquals(Tier.FOLDER, Previews.of(dir).tier());
        Preview gone = Previews.of(dir.resolve("nope"));
        assertTrue(gone.identity().startsWith("unreadable"), gone.identity());
    }

    @Test
    void aBigFileIsReadOnlyToTheSample(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("big.bin");
        Files.write(f, new byte[Previews.SAMPLE * 3]);
        Preview p = Previews.of(f);
        assertTrue(p.truncated(), "the file is longer than what was read");
    }
}
