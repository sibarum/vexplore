package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * What opening an entry means: the first route that takes it, is ready and starts. Routes here record instead of
 * launching anything, and run on the calling thread, so each test reads what happened straight after.
 */
class RoutesTest {

    private static final Entry FOLDER = new Entry(Path.of("C:/x/stuff"), true, 0, Instant.EPOCH);
    private static final Entry PICTURE = new Entry(Path.of("C:/x/kiln.png"), false, 10, Instant.EPOCH);
    private static final Entry NOTES = new Entry(Path.of("C:/x/notes.txt"), false, 10, Instant.EPOCH);
    private static final Entry OTHER = new Entry(Path.of("C:/x/setup.exe"), false, 10, Instant.EPOCH);

    private final List<String> log = new ArrayList<>();
    private boolean pixInstalled = true;
    private boolean pixStarts = true;

    private Routes routes() {
        return new Routes(Runnable::run, List.of(
                new Routes.Route("Open", Entry::folder, () -> true, p -> log.add("browse " + p.getFileName())),
                new Routes.Route(Routes.IN_PIX, e -> e.extension().equals("png"), () -> pixInstalled,
                        p -> pixStarts && log.add("pix " + p.getFileName())),
                new Routes.Route(Routes.IN_VEX, e -> e.extension().equals("txt"), () -> true,
                        p -> log.add("vex " + p.getFileName()))),
                p -> log.add("shell " + p.getFileName()));
    }

    @Test
    void eachEntryGoesToTheFirstRouteThatTakesIt() {
        Routes r = routes();
        r.open(FOLDER);
        r.open(PICTURE);
        r.open(NOTES);
        r.open(OTHER);
        assertEquals(List.of("browse stuff", "pix kiln.png", "vex notes.txt", "shell setup.exe"), log);
    }

    @Test
    void theMenuNamesTheRouteADoubleClickTakes() {
        Routes r = routes();
        assertEquals(Routes.IN_PIX, r.pick(PICTURE).label());
        assertEquals(Routes.IN_VEX, r.pick(NOTES).label());
        assertSame(r.fallback(), r.pick(OTHER));
        assertEquals(Routes.DEFAULT, r.fallback().label());
    }

    @Test
    void aRouteWhoseAppIsNotInstalledPassesItsEntriesOn() {
        pixInstalled = false;
        Routes r = routes();
        assertSame(r.fallback(), r.pick(PICTURE));
        r.open(PICTURE);
        assertEquals(List.of("shell kiln.png"), log);
    }

    @Test
    void anAppThatWillNotStartPassesItsEntryOn() {
        pixStarts = false;
        routes().open(PICTURE);
        assertEquals(List.of("shell kiln.png"), log);
    }

    @Test
    void openByDefaultSkipsEveryOtherRoute() {
        Routes r = routes();
        r.openByDefault(PICTURE);
        r.openByDefault(NOTES);
        assertEquals(List.of("shell kiln.png", "shell notes.txt"), log);
    }
}
