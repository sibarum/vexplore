package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Listed;
import dev.sibarum.vexplore.core.Messages.Row;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One list's folder, as the explorer knows it: a field of its {@code State}, once for FROM and, in Two Folders, once
 * for TO (docs/shape.md, M6). Changed only by the explorer's steps.
 *
 * @param folder   the folder shown, or being listed to be shown; none yet
 * @param asked    the ticket of the listing last asked for: a reply with another has been overtaken
 * @param select   the row to select when that listing lands: a file named on the command line or typed
 * @param rows     the folder's rows, as last listed. The order the list shows is the table's.
 * @param today    the day the rows were listed on, which the rail's "today" means
 * @param zone     the zone a row's day is read in
 * @param selected the rows selected, as the list last said
 * @param back     the folders this list showed before, newest first, for the top bar's Back (M8)
 * @param order    the order the list's table shows {@code rows} in, as it last said ({@code Ordered}); kept from
 *                 folder to folder, as the table keeps its sort
 */
record Pane(Optional<Path> folder, long asked, Set<Path> select, List<Row> rows, LocalDate today, ZoneId zone,
            Set<Path> selected, List<Path> back, Order order) {

    /** How many folders Back can go to. */
    static final int BACK = 20;

    static final Pane NONE = new Pane(Optional.empty(), 0L, Set.of(), List.of(), LocalDate.EPOCH, ZoneOffset.UTC,
            Set.of(), List.of(), Order.LISTED);

    Pane {
        select = Set.copyOf(select);
        rows = List.copyOf(rows);
        selected = Set.copyOf(selected);
        back = List.copyOf(back);
    }

    /**
     * Asking for {@code value}'s listing as {@code ticket}: what was listed before is no longer what is shown, and the
     * folder left is the first Back would go to.
     */
    Pane asking(Path value, Set<Path> then, long ticket) {
        List<Path> before = back;
        if (folder.isPresent() && !folder.get().equals(value)) {
            List<Path> next = new ArrayList<>(back);
            next.addFirst(folder.get());
            before = next.size() > BACK ? next.subList(0, BACK) : next;
        }
        return new Pane(Optional.of(value), ticket, then, List.of(), today, zone, Set.of(), before, order);
    }

    /** Back: asking as {@code ticket} for the folder shown before this one, no longer one to go back to then. */
    Pane returning(long ticket) {
        return new Pane(Optional.of(back.getFirst()), ticket, Set.of(), List.of(), today, zone, Set.of(),
                back.subList(1, back.size()), order);
    }

    /** The folder shown, asked for again as {@code ticket}: what is selected is kept, as the list keeps it. */
    Pane relisting(long ticket) {
        return new Pane(folder, ticket, Set.of(), rows, today, zone, selected, back, order);
    }

    /** The folder's rows landed: of what was selected, only what is still there. */
    Pane withRows(Listed l) {
        Set<Path> still = new HashSet<>();
        for (Row r : l.rows()) {
            if (selected.contains(r.path())) {
                still.add(r.path());
            }
        }
        return new Pane(folder, asked, Set.of(), l.rows(), l.today(), l.zone(), still, back, order);
    }

    Pane withSelected(Set<Path> value) {
        return new Pane(folder, asked, select, rows, today, zone, value, back, order);
    }

    Pane withOrder(Order value) {
        return new Pane(folder, asked, select, rows, today, zone, selected, back, value);
    }

    /** The rows in the order the list shows them. */
    List<Row> shown() {
        return order.apply(rows);
    }

    /** The row at {@code path}, or none: one the list showed before this listing, or never. */
    Optional<Row> row(Path path) {
        for (Row r : rows) {
            if (r.path().equals(path)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
