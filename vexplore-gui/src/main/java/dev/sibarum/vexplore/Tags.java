package dev.sibarum.vexplore;

import dev.vexelray.gui.core.Node;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A set a list marks its rows with beside the one {@code ListView} holds, the rail's reach (framework-notes FN-12):
 * each row at most one {@code K}, the tag <em>new</em> or the comparison's {@code Relation}. Told as
 * {@code ListView.marks} is, the whole of it as data each time it changes; a row realized by a later scroll is built
 * wearing what it says, as the rows already on screen are shown or hidden.
 *
 * <p>A name cell builds a node for each {@code K} its row could wear, hidden or shown, and hands each here, keyed by
 * the row's path, so that a change shows and hides the nodes the row already has. Shown and hidden, never added or
 * removed, so they fade in and out. What the list no longer realizes is let go of, by the list's own snapshot, as
 * {@code ListView.realizedItems} asks.
 */
final class Tags<K> {

    private Map<Path, K> told = Map.of();
    private final Map<Path, Map<K, Node>> built = new HashMap<>();

    /** Whether the row at {@code path} wears {@code key}, for a cell being built. */
    synchronized boolean has(Path path, K key) {
        return key.equals(told.get(path));
    }

    /** The node a cell built to show {@code key} on the row at {@code path}, to be shown or hidden as told. */
    synchronized void built(Path path, K key, Node node) {
        built.computeIfAbsent(path, p -> new HashMap<>()).put(key, node);
    }

    /** What each row wears, the whole of it; {@code realized} is what the list has a row for now. */
    synchronized void tell(Map<Path, K> value, Set<Path> realized) {
        told = Map.copyOf(value);
        built.keySet().retainAll(realized);
        built.forEach((path, nodes) -> nodes.forEach((key, node) -> node.visible(key.equals(told.get(path)))));
    }
}
