package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.App;
import dev.sibarum.vexplore.core.Messages.HandedOff;
import dev.sibarum.vexplore.core.Messages.Handing;
import dev.vexelray.framework.api.Job;
import dev.vexelray.framework.shell.Apps;
import dev.vexelray.os.NativePlatform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Hand a file or a folder to one of the suite's programs, where the installer put it ({@link Apps#spawn}), or to the
 * desktop's own opener, as the framework's {@code Open} does, and the reply says which opened it. Not waited for: each
 * program is a process of its own, which outlives this one.
 *
 * <ul>
 *   <li><b>Rasterware</b>, {@code pix <path>}: a picture or a video among its folder's, or a folder's first file it
 *   recognises. It is installed as {@code pix}, a command name the suite renames together later.</li>
 *   <li><b>VexelRay IDE</b>, {@code text-editor <path>}: a file in a tab, or a folder as its navigator's project.</li>
 *   <li><b>MainFrame</b>, {@code mainframe --dir=<folder>}, and for a file {@code --dir=<its folder>
 *   --insert-path=<file>}, which types its name on the input line. Vexplore passes paths and never console text:
 *   MainFrame knows the tab's shell and decides whether the name can be typed safely.</li>
 * </ul>
 *
 * <p>Each path goes as one argument, absolute, with no shell between, so it is never read as an option or a command.
 * A file whose suite program is not installed goes to the desktop's opener instead, and the reply says so; a folder,
 * or anything for MainFrame, does not, since the desktop would only show the folder again.
 *
 * <p>In {@code -gui} rather than {@code -core}, because {@link Apps} is the framework shell's and {@code -core}
 * builds against none of it. It calls the engine's opener itself rather than sending {@code Open}: a job sends
 * nothing, and which program answers is its one decision.
 */
@Job
public final class HandOff {

    /** Each suite program's id in the installer's records, which is also its command. */
    static final Map<App, String> IDS = Map.of(App.RASTERWARE, "pix", App.IDE, "text-editor",
            App.MAINFRAME, "mainframe");

    public HandedOff run(Handing h) {
        return handOff(h.path(), h.folder(), h.app(), Apps.registryDir(), f -> NativePlatform.current().open(f));
    }

    /** As {@link #run}, against {@code registry} and with {@code open} for the desktop's opener. */
    static HandedOff handOff(Path path, boolean folder, App app, Optional<Path> registry, Predicate<String> open) {
        Path abs = path.toAbsolutePath().normalize();
        String id = IDS.get(app);
        if (id != null) {
            try {
                if (registry.isPresent() && Apps.spawn(registry.get(), id, args(app, abs, folder)).isPresent()) {
                    return new HandedOff(path, app, app, null);
                }
            } catch (IOException | RuntimeException e) {
                return new HandedOff(path, app, app, app.title() + " would not start: " + why(e));
            }
            if (folder || app == App.MAINFRAME) {
                return new HandedOff(path, app, app, app.title() + " isn't installed");
            }
        }
        try {
            if (open.test(abs.toString())) {
                return new HandedOff(path, app, App.DESKTOP, null);
            }
            return new HandedOff(path, app, App.DESKTOP, id == null ? "this desktop has no opener"
                    : app.title() + " isn't installed, and this desktop has no opener");
        } catch (RuntimeException e) {
            return new HandedOff(path, app, App.DESKTOP, why(e));
        }
    }

    /** The command line after the program's name: the path, or MainFrame's folder and the file to type. */
    static String[] args(App app, Path abs, boolean folder) {
        if (app != App.MAINFRAME) {
            return new String[]{abs.toString()};
        }
        Path dir = folder ? abs : abs.getParent();
        if (dir == null || dir.equals(abs)) {
            return new String[]{"--dir=" + abs};
        }
        return new String[]{"--dir=" + dir, "--insert-path=" + abs};
    }

    private static String why(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
