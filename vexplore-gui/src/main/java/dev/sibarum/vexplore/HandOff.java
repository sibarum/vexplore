package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.HandedOff;
import dev.sibarum.vexplore.core.Messages.Handing;
import dev.vexelray.framework.api.Job;
import dev.vexelray.framework.shell.Apps;
import dev.vexelray.os.NativePlatform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Hand a file to Rasterware, the suite's viewer, where the installer put it ({@link Apps#spawn}); where it is not
 * installed, to the desktop's own opener, as {@code OpenFile} does, and the reply says which. Not waited for:
 * Rasterware is a process of its own, which outlives this one.
 *
 * <p>In {@code -gui} rather than beside {@code OpenFile}, because {@link Apps} is the framework shell's and
 * {@code -core} builds against none of it. Rasterware takes the file's path as its one argument; it is installed as
 * {@code pix}, a command name the suite renames together later.
 */
@Job
public final class HandOff {

    /** Rasterware's id in the installer's records. */
    static final String RASTERWARE = "pix";

    public HandedOff run(Handing h) {
        return handOff(h.file(), h.rasterware(), Apps.registryDir(), f -> NativePlatform.current().open(f));
    }

    /**
     * As {@link #run}, against {@code registry} and with {@code open} for the desktop's opener. Not {@code rasterware},
     * the desktop's opener alone: the strip's Open with named it.
     */
    static HandedOff handOff(Path file, boolean rasterware, Optional<Path> registry, Predicate<String> open) {
        String path = file.toAbsolutePath().toString();
        try {
            if (rasterware && registry.isPresent() && Apps.spawn(registry.get(), RASTERWARE, path).isPresent()) {
                return new HandedOff(file, true, HandedOff.RASTERWARE, null);
            }
        } catch (IOException | RuntimeException e) {
            return new HandedOff(file, true, HandedOff.RASTERWARE, "Rasterware would not start: " + why(e));
        }
        try {
            if (open.test(path)) {
                return new HandedOff(file, rasterware, HandedOff.DESKTOP, null);
            }
            return new HandedOff(file, rasterware, HandedOff.DESKTOP, rasterware
                    ? "Rasterware isn't installed, and this desktop has no opener" : "this desktop has no opener");
        } catch (RuntimeException e) {
            return new HandedOff(file, rasterware, HandedOff.DESKTOP, why(e));
        }
    }

    private static String why(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
