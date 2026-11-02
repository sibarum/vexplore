package dev.sibarum.vexplore;

import dev.vexelray.gui.core.Node;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The list's second marked set: the rows tagged <em>new</em>, which a {@code ListView} cannot hold beside the one set
 * it marks, the rail's reach (framework-notes FN-12). Told as {@code ListView.marks} is, the whole set as data each
 * time it changes; a row realized by a later scroll is built wearing what the set says, as the rows already on screen
 * are shown or hidden.
 *
 * <p>Each name cell builds its tag hidden or shown, and hands it here, keyed by the row's path, so that a change of
 * set shows and hides the tag the row already has. Shown and hidden, never added or removed, so it fades in and out.
 * What the list no longer realizes is let go of, by the list's own snapshot, as {@code ListView.realizedItems} asks.
 */
final class Tags {

    private Set<Path> tagged = Set.of();
    private final Map<Path, Node> tags = new HashMap<>();

    /** Whether the row at {@code path} is tagged, for a cell being built. */
    synchronized boolean has(Path path) {
        return tagged.contains(path);
    }

    /** The tag a cell built for the row at {@code path}, to be shown or hidden as the set changes. */
    synchronized void built(Path path, Node tag) {
        tags.put(path, tag);
    }

    /** Tag the rows at {@code paths}, the whole set; {@code realized} is what the list has a row for now. */
    synchronized void mark(Set<Path> paths, Set<Path> realized) {
        tagged = Set.copyOf(paths);
        tags.keySet().retainAll(realized);
        tags.forEach((path, tag) -> tag.visible(tagged.contains(path)));
    }
}
