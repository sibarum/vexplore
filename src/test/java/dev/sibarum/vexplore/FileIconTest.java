package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;
import dev.sibarum.suite.view.SuiteLook;
import dev.vexelray.gui.core.style.Theme;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.fileicons.FileIconView;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The list's type icons: which family a row gets, and that the suite's theme draws them in the dark row. */
class FileIconTest {

    /**
     * Extensions the rail knows that the canvas does not claim yet. They are to be added on the design canvas and
     * re-extracted into families.tsv; until then they draw as {@link FileFamily#UNKNOWN}.
     */
    private static final String AWAITING_THE_CANVAS = "m4v mpg mpeg tif hpp tgz appx";

    @Test
    void aFileTakesTheFamilyOfItsExtension() {
        assertEquals(FileFamily.SPREADSHEETS, FileIcon.of(file("budget.csv")));
        assertEquals(FileFamily.PDF, FileIcon.of(file("REPORT.PDF")), "case does not matter");
        assertEquals(FileFamily.LIBRARIES_AND_BYTECODE, FileIcon.of(file("app.jar")));
        assertEquals(FileFamily.UNKNOWN, FileIcon.of(file("notes.qqq")));
        assertEquals(FileFamily.UNKNOWN, FileIcon.of(file("Makefile")));
    }

    @Test
    void aFolderIsAFolderWhateverItIsCalled() {
        Entry folder = new Entry(Path.of("photos.zip"), true, 0, Instant.EPOCH);
        assertEquals(FileFamily.FOLDERS, FileIcon.of(folder));
        assertEquals(FileFamily.ARCHIVES, FileIcon.of(file("photos.zip")));
    }

    @Test
    void everyExtensionTheRailKnowsHasAnIcon() {
        for (String e : ("mp4 mov mkv webm avi wmv png jpg jpeg gif webp bmp tiff svg heic ico "
                + "wav mp3 flac ogg m4a aac opus pdf doc docx odt rtf xls xlsx ods ppt pptx epub "
                + "txt md log csv tsv json xml yaml yml ini toml cfg "
                + "java py js ts c cpp h rs go cs html css sh bat ps1 kt sql "
                + "zip 7z rar tar gz bz2 xz exe msi dll").split(" ")) {
            assertNotEquals(Kind.OTHER, Kind.ofExtension(e));
            assertNotEquals(FileFamily.UNKNOWN, FileIcon.of(file("a." + e)), e);
        }
    }

    @Test
    void theRailsExtensionsAwaitingTheCanvasAreStillUnclaimed() {
        for (String e : AWAITING_THE_CANVAS.split(" ")) {
            assertNotEquals(Kind.OTHER, Kind.ofExtension(e));
            assertEquals(FileFamily.UNKNOWN, FileIcon.of(file("a." + e)),
                    "the canvas claims ." + e + " now: move it into everyExtensionTheRailKnowsHasAnIcon");
        }
    }

    @Test
    void theSuiteThemeTakesTheDarkRowAndALightThemeTheLight() {
        assertTrue(FileIconView.dark(SuiteLook.THEME));
        assertFalse(FileIconView.dark(Theme.LIGHT));
    }

    private static Entry file(String name) {
        return new Entry(Path.of(name), false, 1, Instant.EPOCH);
    }
}
