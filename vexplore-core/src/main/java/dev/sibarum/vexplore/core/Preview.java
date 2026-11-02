package dev.sibarum.vexplore.core;

import java.nio.file.Path;
import java.util.List;

/**
 * What the Preview Dock shows for one file, decided once and read-only: 0.1's, by {@link Previews}.
 *
 * <p>Nothing in a previewed file is ever executed, and this record is why that is easy to keep true: it is text and
 * numbers. A renderer that needed the file to <em>do</em> anything would have to be a different kind of value.
 *
 * @param path      the file
 * @param tier      which renderer produced this
 * @param identity  what the file is, as far as its first bytes say ({@code PNG image}, {@code unknown binary})
 * @param size      the file's size in bytes; a folder's number of entries
 * @param lines     the body: the first lines of a text, or a hex dump
 * @param entropy   Shannon entropy of what was read, in bits per byte, 0 to 8
 * @param strings   printable runs found in what was read; none for a text, which is all readable
 * @param truncated whether the file is longer than what was read
 */
public record Preview(Path path, Tier tier, String identity, long size, List<String> lines, double entropy,
                      List<String> strings, boolean truncated) {

    public Preview {
        lines = List.copyOf(lines);
        strings = List.copyOf(strings);
    }

    /**
     * The design's tiers, best first. A type with no renderer falls to the next tier down, so no file is ever blank;
     * {@link #BYTES} is the floor, and every file can reach it.
     */
    public enum Tier {
        /** A folder: not a file, but the dock is never empty either. */
        FOLDER("folder"),
        /** Tier 3: the first lines of anything that decodes as text. */
        TEXT("tier 3 · text"),
        /** Tier 4: a hex dump, the strings in the file and an entropy bar. */
        BYTES("tier 4 · bytes");

        private final String tag;

        Tier(String tag) {
            this.tag = tag;
        }

        /** The label the dock puts beside the file's name. */
        public String tag() {
            return tag;
        }
    }
}
