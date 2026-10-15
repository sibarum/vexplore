package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.ops.Plan;
import dev.sibarum.vexplore.remote.Remotes;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.drop.Drop;
import dev.vexelray.gui.core.drop.PayloadType;
import dev.vexelray.gui.core.layout.Rect;
import dev.vexelray.gui.widget.ListView;
import sibarum.tactroller.api.Modifier;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dragging files between the folders on screen: what is carried, and what letting go means.
 *
 * <p>The design's fallback (README, <em>Drag and Drop Is the Fallback</em>), so it does what every explorer does
 * and nothing cleverer: files dropped on a folder row go into that folder, and dropped anywhere else on a list go
 * into the folder that list shows. Within one drive they move; to another drive, or to or from a server, they are
 * copied. Control held makes it a copy, Shift a move, as in the shell — except that nothing is moved off or onto
 * a server, which Vexplore only copies to and from.
 *
 * <p>A drop runs through {@link Actor#dropping}, so it says what it did on the notice line, leaves alone what is
 * already there, and is undone by Ctrl+Z like any other file operation.
 */
final class Drops {

    /** What a list of entries is carried as. Both tables are told about it, which is how they take each other's rows. */
    static final PayloadType<List<Entry>> FILES = PayloadType.of("vexplore.files");

    private Drops() {
    }

    /** What rides with the pointer: the name, or how many. */
    static String label(List<Entry> held) {
        if (held.size() == 1) {
            return held.getFirst().name();
        }
        boolean folders = held.stream().allMatch(Entry::folder);
        boolean files = held.stream().noneMatch(Entry::folder);
        return held.size() + (folders ? " folders" : files ? " files" : " items");
    }

    /**
     * Let {@code table} carry its rows and take others', for the folder {@code shown} reports. Asked on the GUI
     * thread once a frame while a drag is over it, so it touches no disk: what is already at the destination is
     * found when the drop runs.
     */
    static void wire(Gui gui, ListView<Entry> table, Supplier<Path> shown, Actor actor) {
        table.draggable(FILES, Drops::label);
        table.droppable(FILES, (held, onto) -> {
            Path into = onto == null ? shown.get() : onto.folder() ? onto.path() : null;
            Plan.Kind kind = kind(held, into, gui.modifiers().value());
            return kind == null ? Drop.NONE
                    : kind == Plan.Kind.MOVE ? Drop.move(Rect.ZERO, actor.dropping(kind, held, into))
                    : Drop.copy(Rect.ZERO, actor.dropping(kind, held, into));
        });
    }

    /**
     * Whether dropping {@code held} into {@code into} moves or copies, or null if it would do nothing: no
     * destination, a folder into itself or below itself, or every file already there.
     */
    static Plan.Kind kind(List<Entry> held, Path into, Set<Modifier> keys) {
        if (into == null || held.isEmpty()) {
            return null;
        }
        boolean elsewhere = false;
        boolean remote = Remotes.is(into);
        boolean sameRoot = true;
        for (Entry e : held) {
            Path p = e.path();
            if (p.getFileSystem() == into.getFileSystem() && into.startsWith(p)) {
                return null;   // into itself, or into something inside it
            }
            elsewhere |= !into.equals(p.getParent());
            remote |= Remotes.is(p);
            sameRoot &= p.getFileSystem() == into.getFileSystem() && Objects.equals(p.getRoot(), into.getRoot());
        }
        if (!elsewhere) {
            return null;
        }
        if (remote || keys.contains(Modifier.CONTROL)) {
            return Plan.Kind.COPY;
        }
        return keys.contains(Modifier.SHIFT) || sameRoot ? Plan.Kind.MOVE : Plan.Kind.COPY;
    }
}
