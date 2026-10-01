package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Preview;
import dev.sibarum.vexplore.suggest.Suggestions;
import dev.sibarum.vexplore.suggest.Suggestions.Pick;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * What Vexplore knows, as one immutable value.
 *
 * @param folder   the folder being shown; null until the first {@link Model#navigate}
 * @param entries  what was listed in it, or empty while {@code loading}
 * @param loading  whether a listing of {@code folder} is still on its way
 * @param selected what the user has selected in the list
 * @param pick     what they have chosen on the Suggestion Rail so far
 * @param preview  what the Preview Dock shows, or null when nothing single is selected or it has not arrived yet
 * @param now      the instant the listing was taken at: what "today" means for everything derived from it
 */
record Doc(Path folder, List<Entry> entries, boolean loading, Set<Path> selected, Pick pick, Preview preview,
           Instant now) {

    static Doc initial() {
        return new Doc(null, List.of(), false, Set.of(), Pick.NONE, null, Instant.now());
    }

    Doc navigating(Path to) {
        return new Doc(to, List.of(), true, Set.of(), Pick.NONE, null, now);
    }

    Doc listed(List<Entry> listing, Instant at) {
        return new Doc(folder, listing, false, Set.of(), Pick.NONE, null, at);
    }

    Doc selecting(Set<Path> paths) {
        // A new selection is a new question: what was picked on the rail was an answer to the old one, and a
        // preview of another file is not a preview of this one. The preview of the same single file stays,
        // because re-selecting it must not make the dock blink.
        Preview keep = preview != null && paths.size() == 1 && paths.contains(preview.path()) ? preview : null;
        return new Doc(folder, entries, loading, paths, Pick.NONE, keep, now);
    }

    Doc picking(Pick p) {
        return new Doc(folder, entries, loading, selected, p, preview, now);
    }

    Doc previewing(Preview p) {
        return new Doc(folder, entries, loading, selected, pick, p, now);
    }

    /** The one selected path, or null if there are none or several: the only case the dock speaks to. */
    Path single() {
        return selected.size() == 1 ? selected.iterator().next() : null;
    }

    /** The rail for this moment. Pure, and cheap enough to ask for on every change. */
    Suggestions.Rail rail() {
        return Suggestions.of(entries, selected, pick, now, java.time.ZoneId.systemDefault());
    }

    long selectedBytes() {
        return entries.stream().filter(e -> selected.contains(e.path())).mapToLong(Entry::size).sum();
    }
}
