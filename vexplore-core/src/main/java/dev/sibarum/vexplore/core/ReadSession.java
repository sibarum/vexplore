package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Mark;
import dev.sibarum.vexplore.core.Messages.Recall;
import dev.sibarum.vexplore.core.Messages.Recalled;
import dev.vexelray.framework.api.Job;
import dev.vexelray.framework.core.Launch;
import dev.vexelray.gui.core.app.Settings;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read back what the last session remembered, and what the command line named, on the offload lane.
 *
 * <p>A job because the settings store is a file, and the {@link Explorer}'s steps make no effects. It is also where a
 * remembered string becomes a path: one that is not a path any more is left out, because the person moved it and an
 * explorer should not complain about it, and every path is made absolute here, so the explorer never asks where the
 * application started. That is in the reply too ({@code started}), for a session that showed no folder.
 */
@Job
public final class ReadSession {

    static final String FOLDER = "session.folder";

    /** The marks, one a line: its name, then its paths, each after a tab. Neither a name nor a path holds either. */
    static final String MARKS = "session.marks";

    private final Settings settings;
    private final Launch launch;

    public ReadSession(Settings settings, Launch launch) {
        this.settings = settings;
        this.launch = launch;
    }

    public Recalled run(Recall r) {
        return new Recalled(path(settings.getString(FOLDER, "")), paths(launch.rest()), Path.of("").toAbsolutePath(),
                trash(), marks(settings.getString(MARKS, "")));
    }

    /**
     * Vexplore's own trash, {@code ~/.vexplore/trash}: where a delete goes, kept so it can be taken back. Never
     * emptied yet.
     */
    static Path trash() {
        return Path.of(System.getProperty("user.home"), ".vexplore", "trash");
    }

    private static List<Path> paths(List<String> texts) {
        List<Path> out = new ArrayList<>();
        for (String t : texts) {
            path(t).ifPresent(out::add);
        }
        return out;
    }

    /** The marks as {@link WriteSession} wrote them; a path that is not one any more is left out, and an empty mark. */
    static List<Mark> marks(String text) {
        List<Mark> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] parts = line.split("\t");
            Set<Path> paths = new LinkedHashSet<>();
            for (int i = 1; i < parts.length; i++) {
                path(parts[i]).ifPresent(paths::add);
            }
            if (!parts[0].isBlank() && !paths.isEmpty()) {
                out.add(new Mark(parts[0], paths));
            }
        }
        return out;
    }

    /** A path from a remembered or typed string, absolute and normalised; none for blank or one that is not a path. */
    static Optional<Path> path(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(text).toAbsolutePath().normalize());
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
