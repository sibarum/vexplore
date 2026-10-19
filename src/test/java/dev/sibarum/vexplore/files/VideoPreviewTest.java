package dev.sibarum.vexplore.files;

import dev.sibarum.vexplore.files.Preview.Tier;
import dev.vexelray.codec.image.Picture;
import dev.vexelray.codec.image.Stamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The video tier, through FFmpeg for real, so a missing ffmpeg-wrapper fails here rather than passing quietly.
 * {@code rgb.webm} is vexelray-codec's fixture: three seconds at 64x48, one red, one green, one blue.
 */
class VideoPreviewTest {

    @Test
    void aVideoIsASlideshowOfFramesFromAcrossIt(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("rgb.webm");
        try (InputStream in = VideoPreviewTest.class.getResourceAsStream("/rgb.webm")) {
            Files.copy(in, f);
        }
        Preview p = Previews.of(f);
        assertEquals(Tier.VIDEO, p.tier());
        assertEquals("VP9 video · 64 × 48 · 0:03", p.identity());
        assertNull(p.large(), "the viewer shows pictures, so a video is never made at its size");
        Picture pic = p.picture();
        assertEquals(Previews.SLIDES, pic.count());
        assertTrue(pic.animated(), "it steps through its frames as an animated image does");
        assertEquals(Previews.SLIDE_MS, pic.delays()[0]);
        assertEquals(64, pic.frameWidth(), "never larger than the video");
        assertEquals(Stamp.of(f), pic.key(), "kept by the Previewer as a picture is");
        // The middle of the first frame, an eighth of the way in: the red second.
        int centre = (24 * pic.sheetWidth() + 32) * 4;
        assertTrue((pic.sheet()[centre] & 0xFF) > 200 && (pic.sheet()[centre + 1] & 0xFF) < 60, "the first is red");
    }

    @Test
    void theFirstFrameIsAPreviewBeforeTheSlideshow(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("rgb.webm");
        try (InputStream in = VideoPreviewTest.class.getResourceAsStream("/rgb.webm")) {
            Files.copy(in, f);
        }
        List<Preview> early = new ArrayList<>();
        Preview whole = Previews.of(f, false, early::add);
        assertEquals(1, early.size());
        Preview first = early.get(0);
        assertEquals(Tier.VIDEO, first.tier());
        assertEquals(whole.identity(), first.identity());
        assertEquals(1, first.picture().count(), "one frame, shown as a still");
        assertEquals(Previews.SLIDES, whole.picture().count());
    }

    @Test
    void aFileNamedLikeAVideoThatIsNotOneFallsATier(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("notes.mkv");
        Files.writeString(f, "not a video at all");
        assertEquals(Tier.TEXT, Previews.of(f).tier());
    }
}
