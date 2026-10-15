package dev.sibarum.vexplore;

import dev.sibarum.suite.git.Status;
import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.suggest.Bytes;
import dev.sibarum.vexplore.suggest.Dates;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.widget.Table;
import dev.vexelray.gui.widget.Unclip;
import dev.vexelray.text.TextLayout.HAlign;
import dev.vexelray.text.TextLayout.VAlign;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * The file list's columns, for every table of a folder's entries: the main list and the second folder beside it.
 * Stated once so the two read alike, sort alike, and cannot drift apart.
 */
final class Columns {

    /** A row's height, in rem. */
    static final float ROW_REM = 2.125f;

    /** A type icon in the list: 16 px, the grid {@link FileIcon} is drawn on. */
    private static final Length ICON = Length.rem(1f);

    private Columns() {
    }

    /**
     * NAME, TYPE, SIZE, MODIFIED and GIT. {@code names} shows a cut-off name whole while it is hovered; {@code git}
     * is what git said about the folder shown, or null, read as each row is built so only rows on screen ask.
     */
    static List<Table.Column<Entry>> of(Unclip names, Supplier<Status> git) {
        Comparator<Entry> byName = Comparator.comparing(e -> e.name().toLowerCase(java.util.Locale.ROOT));
        return List.of(
                Table.Column.of("NAME", Length.grow(1), (g, e) -> nameCell(g, e, names), byName),
                Table.Column.of("TYPE", Length.rem(5f), (g, e) -> cell(g, e.typeLabel(), Type.MONO, Type.SMALL,
                        Role.DIM, HAlign.LEFT), Comparator.comparing(Entry::typeLabel)),
                Table.Column.of("SIZE", Length.rem(6.5f), (g, e) -> cell(g, e.folder() ? "" : Bytes.format(e.size()),
                        Type.UI, Type.BODY, Role.INK, HAlign.RIGHT), Comparator.comparingLong(Entry::size)),
                Table.Column.of("MODIFIED", Length.rem(9.5f), (g, e) -> cell(g,
                        Dates.column(e.modified(), Instant.now(), ZoneId.systemDefault()), Type.UI, Type.BODY,
                        Role.DIM, HAlign.LEFT), Comparator.comparing(Entry::modified)),
                // Not sortable yet: Order shares each column's comparator with the suggestion engine, and this one
                // reads the git status beside the entries rather than the entries themselves.
                Table.Column.of("GIT", Length.rem(6.25f), (g, e) -> gitCell(g, e, git.get())));
    }

    /** The name, after its type icon: two cells of the file-icon atlas, so a zoom or a resize redraws nothing. */
    private static Node nameCell(Gui g, Entry e, Unclip names) {
        Node glyph = FileIcon.node(g, e, ICON);
        Node name = g.text(e.name()).font(Type.UI).textSize(Type.BODY).textColor(g.theme().color(Role.INK))
                .height(Length.FILL).align(HAlign.LEFT, VAlign.MIDDLE);
        // Not a scroller, as the table's own cells are not: a name wider than its column is clipped, not scrolled.
        Node cell = g.row().width(Length.FILL).height(Length.FILL).scroll(false, false)
                .alignItems(AlignItems.CENTER).gap(Type.GAP)
                .padding(Length.ZERO, Length.dp(10)).children(glyph, name);
        // The cut-off name, shown whole while its cell is hovered — the one thing drawn over something else (see
        // Ui's class comment). Attached here because the name is inside the cell's content rather than being it,
        // which is the one shape the table cannot attach for itself.
        names.attach(cell, name);
        return cell;
    }

    /** What git says about the row; the rows are rebuilt when git answers. */
    private static Node gitCell(Gui g, Entry e, Status status) {
        GitColumn.Mark m = GitColumn.of(status, e);
        Role ink = switch (m.tone()) {
            case ALARM -> Look.ANCHOR;
            case CHANGED -> Role.INK;
            case STAGED -> Role.ACCENT;
            case QUIET -> Role.DIM;
            case FAINT -> Role.FAINT;
        };
        return cell(g, m.words(), Type.MONO, Type.SMALL, ink, HAlign.LEFT);
    }

    static Node cell(Gui g, String text, int face, Length size, Role ink, HAlign align) {
        return g.text(text).font(face).textSize(size).textColor(g.theme().color(ink)).wordWrap(false)
                .height(Length.FILL).padding(Length.ZERO, Length.dp(10)).align(align, VAlign.MIDDLE);
    }
}
