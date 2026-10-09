package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;
import dev.vexelray.gui.draw.Picture;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The list's type icons: which family a file gets, and that every icon stays inside its square. */
class FileIconTest {

    @Test
    void anExtensionPicksItsFamily() {
        assertEquals(FileIcon.SPREADSHEET, FileIcon.ofExtension("csv"));
        assertEquals(FileIcon.PDF, FileIcon.ofExtension("PDF"), "case does not matter");
        assertEquals(FileIcon.LIBRARY, FileIcon.ofExtension("jar"));
        assertEquals(FileIcon.UNKNOWN, FileIcon.ofExtension("qqq"));
        assertEquals(FileIcon.UNKNOWN, FileIcon.ofExtension(""));
    }

    @Test
    void aFolderIsAFolderWhateverItIsCalled() {
        Entry folder = new Entry(Path.of("photos.zip"), true, 0, Instant.EPOCH);
        assertEquals(FileIcon.FOLDER, FileIcon.of(folder));
        assertEquals(FileIcon.ARCHIVE, FileIcon.of(new Entry(Path.of("photos.zip"), false, 1, Instant.EPOCH)));
    }

    @Test
    void everyExtensionTheRailKnowsHasAnIcon() {
        for (String e : ("mp4 mov mkv webm avi wmv m4v mpg mpeg png jpg jpeg gif webp bmp tif tiff svg heic ico "
                + "wav mp3 flac ogg m4a aac opus pdf doc docx odt rtf xls xlsx ods ppt pptx epub "
                + "txt md log csv tsv json xml yaml yml ini toml cfg "
                + "java py js ts c cpp h hpp rs go cs html css sh bat ps1 kt sql "
                + "zip 7z rar tar gz tgz bz2 xz exe msi dll appx").split(" ")) {
            assertNotEquals(Kind.OTHER, Kind.ofExtension(e));
            assertNotEquals(FileIcon.UNKNOWN, FileIcon.ofExtension(e), e);
        }
    }

    @Test
    void everyIconStaysInsideItsSquare() {
        for (FileIcon icon : FileIcon.values()) {
            Picture p = icon.sketch(16).picture();
            assertTrue(p.marks().size() > 0, icon + " draws something");
            for (Picture.Mark m : p.marks()) {
                if (m instanceof Picture.Fill f) {
                    assertTrue(f.x() >= 0 && f.y() >= 0 && f.x() + f.w() <= 16 && f.y() + f.h() <= 16,
                            icon + " has a mark outside 16 px: " + f);
                }
            }
        }
    }
}
