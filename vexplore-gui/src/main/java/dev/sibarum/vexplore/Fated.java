package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.Fate;
import dev.vexelray.gui.core.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Each row's fate (docs/shape.md, M9), as the list wears it: a row the queue moves away or brings is faded, one it
 * deletes has a bar across its name, a renamed one shows its new name in italic, and each says where it goes or what
 * it was, and the number of its line in the queue. Told as {@link Tags} is, the whole of it each time a list is shown,
 * before its rows, so a cell built for a row realized later wears its fate from the start.
 *
 * <p>A cell hands its nodes here by the row's path, and a fate told shows, hides and fades the nodes the row already
 * has, never adding one: every node is built in the cell, hidden or shown, upright and italic names both, so a change
 * fades as the framework fades any change. What the list no longer realizes is let go of.
 */
final class Fated {

    /** How faded a row that is leaving or not there yet is. */
    static final float FADED = 0.42f;

    /** How much of the name a delete's bar lets through: the bar is a censor's, not a blot. */
    static final float BAR = 0.72f;

    /** The nodes a row's cells built: what fades with it, its two names, its bar, its note and its number. */
    static final class Parts {
        final List<Node> fade = new ArrayList<>();
        Node upright;
        Node italic;
        Node bar;
        Node note;
        Node number;
    }

    private Map<Path, Fate> told = Map.of();
    private final Map<Path, Parts> built = new HashMap<>();

    /** The fate of the row at {@code path}, for a cell being built; null for none. */
    synchronized Fate fate(Path path) {
        return told.get(path);
    }

    /**
     * The parts of the row at {@code path}, new: its name cell, which the table builds first, starts them, so a row
     * built again holds only its new nodes.
     */
    synchronized Parts start(Path path) {
        Parts p = new Parts();
        built.put(path, p);
        return p;
    }

    private Parts parts(Path path) {
        return built.computeIfAbsent(path, p -> new Parts());
    }

    /** A cell's node that fades with its row, built wearing its row's fate. */
    synchronized void fades(Path path, Node node) {
        parts(path).fade.add(node);
        node.opacity(opacity(told.get(path)));
    }

    private static float opacity(Fate fate) {
        Fate.Kind kind = fate == null ? null : fate.kind();
        return kind == Fate.Kind.ARRIVING || kind == Fate.Kind.LEAVING ? FADED : 1f;
    }

    /** Each row's fate, the whole of it; {@code realized} is what the list has a row for now. */
    synchronized void tell(Map<Path, Fate> value, Set<Path> realized) {
        told = Map.copyOf(value);
        built.keySet().retainAll(realized);
        built.forEach((path, parts) -> wear(parts, told.get(path)));
    }

    /** Wear {@code fate}, or none: what a cell does once its nodes are built, and a tell after. */
    static void wear(Parts parts, Fate fate) {
        Fate.Kind kind = fate == null ? null : fate.kind();
        for (Node n : parts.fade) {
            n.opacity(opacity(fate));
        }
        boolean renamed = kind == Fate.Kind.RENAMED;
        if (parts.upright != null) {
            parts.upright.visible(!renamed);
            parts.italic.visible(renamed);
        }
        if (parts.bar != null) {
            parts.bar.visible(kind == Fate.Kind.DELETING);
        }
        if (parts.note != null) {
            parts.note.text(fate == null ? "" : fate.note());
            parts.note.visible(fate != null);
            parts.number.text(fate == null ? "" : fate.queued() == 0L ? "now" : String.valueOf(fate.queued()));
            parts.number.visible(fate != null);
        }
    }
}
