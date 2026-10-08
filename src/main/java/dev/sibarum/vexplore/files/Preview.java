package dev.sibarum.vexplore.files;

import java.nio.file.Path;
import java.util.List;

/**
 * What the Preview Dock shows for one file, decided once and read-only.
 *
 * <p>Nothing in a previewed file is ever executed, and this record is the reason that is easy to keep true: it is
 * text, numbers and decoded pixels, produced by {@link Previews}. A renderer that needed the file to <em>do</em>
 * anything would have to be a different kind of value, and would be refused at review.
 *
 * @param path     the file
 * @param tier     which renderer produced this — see {@link Tier}
 * @param identity what the file is, as far as its first bytes say ("PNG image", "UTF-8 text", "unknown binary")
 * @param lines    the body: the first lines of text, or a hex dump
 * @param entropy  Shannon entropy of the sample in bits per byte, 0 to 8; meaningful for {@link Tier#BYTES}
 * @param strings  printable runs found in the sample; meaningful for {@link Tier#BYTES}
 * @param truncated whether the file is longer than what was read
 * @param picture  the decoded image for {@link Tier#IMAGE}, and null for every other tier
 * @param large    the same image at the viewer's size, made from the same decode while the viewer is open; null
 *                 otherwise, and for every other tier
 */
public record Preview(Path path, Tier tier, String identity, List<String> lines, double entropy,
                      List<String> strings, boolean truncated, Picture picture, Picture large) {

    /** What this holds in decoded pixels: the weight a cache of previews is bounded by. */
    public long bytes() {
        return (picture == null ? 0 : picture.bytes()) + (large == null ? 0 : large.bytes());
    }

    /**
     * The tiers of the design, from best to fallback. A type with no renderer falls to the next tier down, so no
     * file is ever blank; {@link #BYTES} is the floor and every file can reach it.
     */
    public enum Tier {
        /** A folder: not a file, but the dock is never empty either. */
        FOLDER("folder"),
        /** Tier 1: the picture itself, animated if the file is. */
        IMAGE("tier 1 · image"),
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
