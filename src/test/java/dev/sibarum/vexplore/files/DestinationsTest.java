package dev.sibarum.vexplore.files;

import dev.sibarum.vexplore.files.Destinations.Dest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The places nearby that already hold what is being moved. */
class DestinationsTest {

    private static void touch(Path dir, String... names) throws IOException {
        Files.createDirectories(dir);
        for (String n : names) {
            Files.createFile(dir.resolve(n));
        }
    }

    @Test
    void theSiblingThatHoldsTheKindComesFirstAndAnEmptyOneIsNotOffered(@TempDir Path root) throws IOException {
        Path here = root.resolve("Downloads");
        touch(here, "a.mp4");
        touch(root.resolve("Videos"), "x.mp4", "y.mkv", "z.mov");
        touch(root.resolve("Pictures"), "p.png");
        touch(root.resolve("Empty"));

        List<Dest> d = Destinations.suggest(here, Kind.VIDEO, Set.of());

        assertEquals(1, d.size(), "only Videos holds videos: " + d);
        assertEquals(root.resolve("Videos"), d.get(0).path());
        assertEquals("holds 3 videos", d.get(0).reason());
    }

    @Test
    void aSubfolderOfHereCounts(@TempDir Path root) throws IOException {
        Path here = root.resolve("Downloads");
        touch(here.resolve("clips"), "c1.mp4", "c2.mp4");
        List<Dest> d = Destinations.suggest(here, Kind.VIDEO, Set.of());
        assertEquals(here.resolve("clips"), d.get(0).path());
    }

    @Test
    void hereIsNeverItsOwnSuggestionAndExclusionsAreHonoured(@TempDir Path root) throws IOException {
        Path here = root.resolve("Downloads");
        touch(here, "a.mp4", "b.mp4");
        touch(root.resolve("Videos"), "x.mp4");
        assertTrue(Destinations.suggest(here, Kind.VIDEO, Set.of(root.resolve("Videos"))).isEmpty());
    }

    @Test
    void foldersAndNoKindAreNoQuestion(@TempDir Path root) {
        assertTrue(Destinations.suggest(root, Kind.FOLDER, Set.of()).isEmpty());
        assertTrue(Destinations.suggest(null, Kind.VIDEO, Set.of()).isEmpty());
    }

    @Test
    void namesAreWhatIsDirectlyThere(@TempDir Path root) throws IOException {
        touch(root, "a.txt", "b.txt");
        assertEquals(Set.of("a.txt", "b.txt"), Destinations.names(root));
    }

    @Test
    void imagesGuessPicturesAndVideosGuessVideos(@TempDir Path home) throws IOException {
        Path here = home.resolve("Downloads");
        touch(here);
        touch(home.resolve("Pictures"));
        touch(home.resolve("Videos"));
        assertEquals(home.resolve("Pictures"), Destinations.home(here, home, Kind.IMAGE).path());
        assertEquals(home.resolve("Videos"), Destinations.home(here, home, Kind.VIDEO).path());
        assertEquals(null, Destinations.home(here, home, Kind.DOCUMENT));
    }

    @Test
    void noGuessForAMissingFolderOrTheOneTheFilesAreIn(@TempDir Path home) throws IOException {
        assertEquals(null, Destinations.home(home.resolve("Downloads"), home, Kind.IMAGE), "no Pictures here");
        touch(home.resolve("Pictures"));
        assertEquals(null, Destinations.home(home.resolve("Pictures"), home, Kind.IMAGE));
    }

    @Test
    void aMixedBunchInAPileFolderIsOfferedAnArchive(@TempDir Path home) {
        List<Kind> mixed = List.of(Kind.IMAGE, Kind.DOCUMENT, Kind.ARCHIVE);
        assertTrue(Destinations.archives(home.resolve("Downloads"), home, mixed));
        assertTrue(Destinations.archives(home.resolve("Desktop"), home, mixed));
        assertTrue(Destinations.archives(home.resolve("documents"), home, mixed));
        assertTrue(!Destinations.archives(home.resolve("Downloads"), home, List.of(Kind.IMAGE, Kind.IMAGE)),
                "one kind is not a mixed bunch");
        assertTrue(!Destinations.archives(home.resolve("Projects"), home, mixed), "not a pile folder");
        assertTrue(!Destinations.archives(home.resolve("Downloads").resolve("old"), home, mixed),
                "only the pile folder itself");
    }
}
