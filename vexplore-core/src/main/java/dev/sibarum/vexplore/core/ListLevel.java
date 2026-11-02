package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Level;
import dev.sibarum.vexplore.core.Messages.LevelWanted;
import dev.vexelray.framework.api.Job;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One level of the tree of places: a folder's subfolders, by name. Kept apart from {@link ListFolder}, which reads
 * every row's size and time for the list; the tree wants the folders only, and asks for many levels the list never
 * shows (ruled 2026-11-01). A folder that cannot be read has no subfolders here: the tree has nowhere to say why, and
 * the list says it when the folder is shown.
 */
@Job
public final class ListLevel {

    public Level run(LevelWanted w) {
        List<Path> out = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(w.folder(), Files::isDirectory)) {
            for (Path p : stream) {
                out.add(p);
            }
        } catch (IOException | RuntimeException e) {
            return new Level(w.place(), w.folder(), List.of());
        }
        out.sort(Comparator.comparing(p -> ListFolder.name(p).toLowerCase(Locale.ROOT)));
        return new Level(w.place(), w.folder(), out);
    }
}
