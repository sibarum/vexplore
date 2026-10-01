package dev.sibarum.vexplore.suggest;

import dev.sibarum.vexplore.files.Entry;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The order the list is showing, as a value.
 *
 * <p>The table sorts itself, and the suggestion engine has to know the order it ended up in: "down to the end of
 * this run" means down the screen, whatever the sort. So the order is part of the document and the same
 * comparators sit on both sides — the table's columns and this — which is what keeps the two from disagreeing.
 *
 * @param column     0 name, 1 type, 2 size, 3 modified; -1 for the order the listing came in
 * @param descending the other way round
 */
public record Order(int column, boolean descending) {

    /** The listing as it came: folders first, then by name. */
    public static final Order LISTED = new Order(-1, false);

    /** The default of the design: newest first. */
    public static final Order NEWEST = new Order(3, true);

    /** The comparator of a column; shared with the table so the two cannot drift. */
    public static Comparator<Entry> column(int column) {
        return switch (column) {
            case 0 -> Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
            case 1 -> Comparator.comparing(Entry::typeLabel);
            case 2 -> Comparator.comparingLong(Entry::size);
            default -> Comparator.comparing(Entry::modified);
        };
    }

    /** The entries as they would be displayed. */
    public List<Entry> apply(List<Entry> entries) {
        if (column < 0) {
            return entries;
        }
        Comparator<Entry> c = column(column);
        return entries.stream().sorted(descending ? c.reversed() : c).toList();
    }
}
