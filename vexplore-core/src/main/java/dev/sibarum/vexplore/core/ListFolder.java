package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Listed;
import dev.sibarum.vexplore.core.Messages.Listing;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.vexelray.framework.api.Job;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A folder's rows for the list, read on the offload lane: folders first, then files, each by name, ignoring case.
 * Hidden entries are listed; an explorer that hides what is there is one you cannot trust.
 *
 * <p>A listing may cross a network share or a disk that has gone to sleep, which is why it is a job. A folder that
 * cannot be read is a {@code problem} in the reply, said in a person's words, never a throw: a file browser that stops
 * at the first permission error is one that cannot open {@code C:\}. A row that cannot be read (gone between the
 * listing and the look, or not the person's to read) is still a row, with no size and no time, because it is there.
 */
@Job
public final class ListFolder {

    static final Comparator<Row> ORDER = Comparator
            .comparing((Row r) -> !r.folder())
            .thenComparing(r -> r.name().toLowerCase(Locale.ROOT));

    public Listed run(Listing l) {
        ZoneId zone = ZoneId.systemDefault();
        List<Row> rows = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(l.folder())) {
            for (Path p : stream) {
                rows.add(row(p));
            }
        } catch (IOException | RuntimeException e) {
            return new Listed(l.ticket(), l.folder(), List.of(), problem(e), LocalDate.now(zone), zone);
        }
        rows.sort(ORDER);
        return new Listed(l.ticket(), l.folder(), rows, null, LocalDate.now(zone), zone);
    }

    static Row row(Path p) {
        String name = name(p);
        try {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
            boolean folder = a.isDirectory();
            return new Row(p, name, folder, folder ? 0L : a.size(), a.lastModifiedTime().toInstant(),
                    folder ? Kind.FOLDER : Kind.ofExtension(Kind.extension(name)));
        } catch (IOException | RuntimeException e) {
            return new Row(p, name, false, 0L, Instant.EPOCH, Kind.ofExtension(Kind.extension(name)));
        }
    }

    /** The last path element; a drive's root has none, so it is the root as written. */
    static String name(Path p) {
        Path n = p.getFileName();
        return n == null ? p.toString() : n.toString();
    }

    /** Why a folder could not be listed, as the status line says it. */
    static String problem(Exception e) {
        if (e instanceof AccessDeniedException) {
            return "access is denied";
        }
        if (e instanceof NoSuchFileException) {
            return "it is not there any more";
        }
        if (e instanceof NotDirectoryException) {
            return "it is not a folder";
        }
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
