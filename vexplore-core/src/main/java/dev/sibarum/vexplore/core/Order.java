package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Row;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The order a list shows its rows in: the listing's, or by one of its columns, either way. The list's table sorts by
 * these same comparators ({@code -gui}'s {@code Columns}), and the explorer hears which one the person clicked
 * ({@code Ordered}), so what the explorer reasons about in order (Shift's ranges, {@link Intents}) is the order on
 * the screen.
 *
 * @param by         the column, or {@link By#LISTED} for the order the folder was listed in
 * @param descending whether it runs the other way; never for {@link By#LISTED}
 */
public record Order(By by, boolean descending) {

    /** The listing's own order: what a list shows until a header is clicked. */
    public static final Order LISTED = new Order(By.LISTED, false);

    public Order {
        java.util.Objects.requireNonNull(by, "by");
        descending = descending && by != By.LISTED;
    }

    /** What a list can be ordered by: its columns, in the order the table shows them, after the listing's own. */
    public enum By {
        LISTED(null),
        NAME(Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT))),
        TYPE(Comparator.comparing(Row::typeLabel)),
        SIZE(Comparator.comparingLong(Row::size)),
        MODIFIED(Comparator.comparing(Row::modified));

        private final Comparator<Row> compare;

        By(Comparator<Row> compare) {
            this.compare = compare;
        }

        /** How rows compare on this column; null for {@link #LISTED}, which is no column. */
        public Comparator<Row> comparator() {
            return compare;
        }
    }

    /**
     * {@code rows} in this order, as the table puts them: a stable sort by the column, reversed for descending, so rows
     * the column calls equal keep the listing's order between them.
     */
    public List<Row> apply(List<Row> rows) {
        if (by == By.LISTED) {
            return rows;
        }
        List<Row> out = new ArrayList<>(rows);
        out.sort(descending ? by.comparator().reversed() : by.comparator());
        return List.copyOf(out);
    }
}
