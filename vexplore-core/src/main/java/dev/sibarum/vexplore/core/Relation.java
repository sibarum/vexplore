package dev.sibarum.vexplore.core;

/**
 * How a row of one list stands to the other list in Two Folders, from the two listings alone: the ruling's first tier
 * (rebuild.md, M6), size and modified time, no content read. Worked out by {@link Compare}. Each is said from its own
 * row's side, so a file both lists lack is <em>only here</em> on either.
 *
 * <p>Each belongs to one {@link Group}, the section Orbit's Two folders board puts it in: newer, older and different
 * are all <em>different in both</em>, and the row's own words say which.
 */
public enum Relation {
    ONLY_HERE(Group.ONLY_HERE, "only here"),
    NEWER(Group.DIFFERENT, "newer here"),
    OLDER(Group.DIFFERENT, "older here"),
    DIFFERENT(Group.DIFFERENT, "different"),
    PROBABLY_IDENTICAL(Group.PROBABLY_IDENTICAL, "probably identical"),
    IDENTICAL(Group.IDENTICAL, "identical"),
    IN_BOTH(Group.IN_BOTH, "in both");

    private final Group group;
    private final String words;

    Relation(Group group, String words) {
        this.group = group;
        this.words = words;
    }

    /** The section it is shown in. */
    public Group group() {
        return group;
    }

    /** What the row says: {@code newer here}. */
    public String words() {
        return words;
    }

    /**
     * The board's sections, in the order it draws them. Probably identical and folders in both are sections the board
     * does not draw: the ruling shows the one as such rather than guess, and a folder is compared by its name alone.
     */
    public enum Group {
        ONLY_HERE("Only here"),
        DIFFERENT("Different in both"),
        PROBABLY_IDENTICAL("Probably identical"),
        IDENTICAL("Identical"),
        IN_BOTH("In both");

        private final String title;

        Group(String title) {
            this.title = title;
        }

        /** The section's heading: {@code Different in both}. */
        public String title() {
            return title;
        }
    }
}
