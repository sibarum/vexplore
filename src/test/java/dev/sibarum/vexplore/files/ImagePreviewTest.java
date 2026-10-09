package dev.sibarum.vexplore.files;

import dev.sibarum.vexplore.files.Preview.Tier;
import dev.sibarum.suite.pictures.Picture;
import dev.sibarum.suite.pictures.Stamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier 1, across the native boundary: these decode for real, so a missing imagelib fails here rather than passing
 * quietly. The raster fixtures are encoded by ImageIO at test time, so each is a real file of the format it claims.
 */
class ImagePreviewTest {

    @Test
    void aPngIsAPictureWithItsSizeAndItsPixels(@TempDir Path dir) throws IOException {
        BufferedImage img = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFF0000);
        Path f = dir.resolve("red.png");
        ImageIO.write(img, "png", f.toFile());

        Preview p = Previews.of(f);
        assertEquals(Tier.IMAGE, p.tier());
        assertEquals("PNG image · 3 × 2", p.identity());
        Picture pic = p.picture();
        assertNotNull(pic);
        assertEquals(3, pic.frameWidth());
        assertEquals(1, pic.count());
        assertFalse(pic.vector());
        assertEquals(255, pic.sheet()[0] & 0xFF, "the first pixel is red");
        assertEquals(255, pic.sheet()[3] & 0xFF, "and opaque");
        assertEquals(0, pic.sheet()[7] & 0xFF, "the second is transparent");
        assertEquals(Stamp.of(f), pic.stamp());
    }

    @Test
    void aLargePhotographIsShrunkToTheFrameSide(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("wide.jpg");
        ImageIO.write(new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB), "jpg", f.toFile());

        Picture pic = Previews.of(f).picture();
        assertEquals(Picture.FRAME_SIDE, pic.frameWidth());
        assertEquals(Picture.FRAME_SIDE / 2, pic.frameHeight());
        assertEquals(2400, pic.sourceWidth(), "the header still says what the file is");
    }

    @Test
    void theViewersSizeComesFromTheSameDecode(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("huge.png");
        ImageIO.write(new BufferedImage(4000, 2000, BufferedImage.TYPE_INT_ARGB), "png", f.toFile());

        Preview dock = Previews.of(f);
        assertNull(dock.large(), "no viewer, no second size");

        Preview both = Previews.of(f, true);
        assertEquals(Picture.FRAME_SIDE, both.picture().frameWidth());
        assertEquals(Picture.VIEW_SIDE, both.large().frameWidth());
        assertEquals(Picture.VIEW_SIDE / 2, both.large().frameHeight());
        assertEquals(both.picture().bytes() + both.large().bytes(), both.bytes());
    }

    @Test
    void aVectorForTheViewerIsRasterisedOnceAtTheLargerSize(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("mark.svg");
        Files.writeString(f, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"20\">"
                + "<rect width=\"40\" height=\"20\" fill=\"#0a0\"/></svg>", StandardCharsets.UTF_8);
        Preview p = Previews.of(f, true);
        assertEquals(Picture.VIEW_SIDE, p.large().frameWidth());
        assertEquals(Picture.FRAME_SIDE, p.picture().frameWidth(), "the dock's is a shrink of it");
    }

    @Test
    void anSvgIsRasterisedAtItsOwnAspect(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("mark.svg");
        Files.writeString(f, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"20\">"
                + "<rect width=\"40\" height=\"20\" fill=\"#0a0\"/></svg>", StandardCharsets.UTF_8);

        Preview p = Previews.of(f);
        assertEquals(Tier.IMAGE, p.tier(), "an SVG is text too, and the picture outranks it");
        assertEquals("SVG image · 40 × 20", p.identity());
        assertTrue(p.picture().vector());
        assertEquals(Picture.FRAME_SIDE, p.picture().frameWidth());
        assertEquals(Picture.FRAME_SIDE / 2, p.picture().frameHeight());
        assertTrue(Previews.isText(f), "the editor still opens it as the text it is");
        assertFalse(Previews.isText(dir.resolve("missing.svg")));
    }

    @Test
    void aFileThatClaimsToBeAnImageAndIsNotFallsToBytes(@TempDir Path dir) throws IOException {
        byte[] b = new byte[64];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, b, 0, signature.length);
        Path f = dir.resolve("broken.png");
        Files.write(f, b);

        Preview p = Previews.of(f);
        assertEquals(Tier.BYTES, p.tier());
        assertNull(p.picture());
    }
}
