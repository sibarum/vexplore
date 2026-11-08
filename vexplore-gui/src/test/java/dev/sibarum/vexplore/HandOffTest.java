package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.HandedOff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rasterware where the installer's record says it is, else the desktop's opener, and the reply says which. */
class HandOffTest {

    @TempDir
    Path dir;

    @Test
    void notInstalledIsTheDesktopsOpenerAndSaysSo() {
        List<String> opened = new ArrayList<>();
        HandedOff h = HandOff.handOff(dir.resolve("a.png"), true, Optional.of(dir), opened::add);
        assertEquals(HandedOff.DESKTOP, h.to());
        assertNull(h.problem());
        assertEquals(List.of(dir.resolve("a.png").toAbsolutePath().toString()), opened);
    }

    @Test
    void noOpenerEitherIsSaid() {
        HandedOff h = HandOff.handOff(dir.resolve("a.png"), true, Optional.empty(), f -> false);
        assertEquals(HandedOff.DESKTOP, h.to());
        assertTrue(h.problem().startsWith("Rasterware isn't installed"), h.problem());
    }

    @Test
    void installedIsRasterwareStartedWithThePath() throws Exception {
        // A program that is surely there and ends at once with a file it cannot run: the JVM running this test.
        String java = ProcessHandle.current().info().command().orElseThrow();
        Files.writeString(dir.resolve(HandOff.RASTERWARE + ".json"), "{\"commands\": {\"" + HandOff.RASTERWARE
                + "\": {\"path\": \"" + java.replace("\\", "\\\\") + "\"}}}");
        List<String> opened = new ArrayList<>();
        HandedOff h = HandOff.handOff(dir.resolve("a.png"), true, Optional.of(dir), opened::add);
        assertEquals(HandedOff.RASTERWARE, h.to());
        assertNull(h.problem());
        assertTrue(opened.isEmpty(), "the desktop's opener is not asked");
    }

    @Test
    void theDesktopNamedIsTheDesktopEvenWithRasterwareInstalled() throws Exception {
        String java = ProcessHandle.current().info().command().orElseThrow();
        Files.writeString(dir.resolve(HandOff.RASTERWARE + ".json"), "{\"commands\": {\"" + HandOff.RASTERWARE
                + "\": {\"path\": \"" + java.replace("\\", "\\\\") + "\"}}}");
        List<String> opened = new ArrayList<>();
        HandedOff h = HandOff.handOff(dir.resolve("a.txt"), false, Optional.of(dir), opened::add);
        assertEquals(HandedOff.DESKTOP, h.to());
        assertFalse(h.rasterware(), "Rasterware was not asked");
        assertEquals(List.of(dir.resolve("a.txt").toAbsolutePath().toString()), opened);
    }
}
