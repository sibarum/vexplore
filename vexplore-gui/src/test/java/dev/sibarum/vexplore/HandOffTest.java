package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.App;
import dev.sibarum.vexplore.core.Messages.HandedOff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A suite program where the installer's record says it is, else the desktop's opener for a file, and the reply says
 * which.
 */
class HandOffTest {

    @TempDir
    Path dir;

    /** Record {@code app} as installed: a program that is surely there and ends at once, the JVM running this test. */
    private void installed(App app) throws Exception {
        String java = ProcessHandle.current().info().command().orElseThrow();
        String id = HandOff.IDS.get(app);
        Files.writeString(dir.resolve(id + ".json"), "{\"commands\": {\"" + id
                + "\": {\"path\": \"" + java.replace("\\", "\\\\") + "\"}}}");
    }

    @Test
    void aFileWhoseProgramIsNotInstalledIsTheDesktopsOpenerAndSaysSo() {
        for (App app : List.of(App.RASTERWARE, App.IDE)) {
            List<String> opened = new ArrayList<>();
            HandedOff h = HandOff.handOff(dir.resolve("a.png"), false, app, Optional.of(dir), opened::add);
            assertEquals(new HandedOff(dir.resolve("a.png"), app, App.DESKTOP, null), h);
            assertEquals(List.of(dir.resolve("a.png").toAbsolutePath().toString()), opened);
        }
    }

    @Test
    void aFolderOrMainFrameNotInstalledIsSaidAndTheDesktopNotAsked() {
        List<String> opened = new ArrayList<>();
        HandedOff folder = HandOff.handOff(dir, true, App.IDE, Optional.of(dir), opened::add);
        assertEquals(new HandedOff(dir, App.IDE, App.IDE, "VexelRay IDE isn't installed"), folder);
        HandedOff file = HandOff.handOff(dir.resolve("a.txt"), false, App.MAINFRAME, Optional.of(dir), opened::add);
        assertEquals("MainFrame isn't installed", file.problem());
        assertTrue(opened.isEmpty());
    }

    @Test
    void noOpenerEitherIsSaid() {
        HandedOff h = HandOff.handOff(dir.resolve("a.png"), false, App.RASTERWARE, Optional.empty(), f -> false);
        assertEquals(App.DESKTOP, h.to());
        assertTrue(h.problem().startsWith("Rasterware isn't installed"), h.problem());
    }

    @Test
    void installedIsTheProgramStartedWithThePath() throws Exception {
        for (App app : List.of(App.RASTERWARE, App.IDE, App.MAINFRAME)) {
            installed(app);
            List<String> opened = new ArrayList<>();
            HandedOff h = HandOff.handOff(dir, true, app, Optional.of(dir), opened::add);
            assertEquals(new HandedOff(dir, app, app, null), h);
            assertTrue(opened.isEmpty(), "the desktop's opener is not asked");
        }
    }

    @Test
    void theDesktopNamedIsTheDesktopEvenWithTheSuiteInstalled() throws Exception {
        installed(App.RASTERWARE);
        List<String> opened = new ArrayList<>();
        HandedOff h = HandOff.handOff(dir.resolve("a.png"), false, App.DESKTOP, Optional.of(dir), opened::add);
        assertEquals(new HandedOff(dir.resolve("a.png"), App.DESKTOP, App.DESKTOP, null), h);
        assertEquals(List.of(dir.resolve("a.png").toAbsolutePath().toString()), opened);
    }

    @Test
    void eachProgramIsGivenPathsAndNeverConsoleText() {
        Path abs = dir.toAbsolutePath().normalize();
        Path file = abs.resolve("a b&c.txt");
        assertArrayEquals(new String[]{file.toString()}, HandOff.args(App.RASTERWARE, file, false));
        assertArrayEquals(new String[]{abs.toString()}, HandOff.args(App.IDE, abs, true));
        assertArrayEquals(new String[]{"--dir=" + abs}, HandOff.args(App.MAINFRAME, abs, true));
        assertArrayEquals(new String[]{"--dir=" + abs, "--insert-path=" + file},
                HandOff.args(App.MAINFRAME, file, false));
    }
}
