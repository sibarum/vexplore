package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Bytes;
import dev.sibarum.vexplore.core.Dates;
import dev.sibarum.vexplore.core.Kind;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.widget.Nodes;
import dev.vexelray.gui.widget.Table;
import dev.vexelray.text.TextLayout.HAlign;
import dev.vexelray.text.TextLayout.VAlign;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The file list's columns: NAME, TYPE, SIZE and MODIFIED, as 0.1 had them and the handoff draws them. GIT comes with
 * M7. A cell is drawn from its {@link Row} alone, which the listing filled in, so drawing one touches no disk.
 */
final class Columns {

    /** A row's height, in rem: the handoff's 34 px. */
    static final float ROW_REM = 2.125f;

    /** A type icon in the list: 16 px, the grid the file icons are drawn on. */
    private static final Length ICON = Length.rem(1f);

    /** The space either side of a cell's words. */
    private static final Length PAD = Length.dp(10);

    private Columns() {
    }

    static List<Table.Column<Row>> of(Tags tags) {
        Comparator<Row> byName = Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT));
        return List.of(
                Table.Column.of("NAME", Length.grow(1), (g, r) -> name(g, r, tags), byName),
                Table.Column.of("TYPE", Length.rem(5f), Columns::type, Comparator.comparing(Row::typeLabel)),
                Table.Column.of("SIZE", Length.rem(6.5f), (g, r) -> cell(g, r.folder() ? "" : Bytes.format(r.size()),
                        Type.UI, Type.BODY, Role.INK, HAlign.RIGHT), Comparator.comparingLong(Row::size)),
                Table.Column.of("MODIFIED", Length.rem(9.5f), (g, r) -> cell(g, modified(r), Type.UI, Type.BODY,
                        Role.DIM, HAlign.LEFT), Comparator.comparing(Row::modified)));
    }

    /**
     * The name, after its type icon, and the word <em>new</em> after it in amber while the file has just landed. Amber
     * as text, never a fill ({@code Look.ANCHOR}); built hidden or shown as {@link Tags} says, and handed to it.
     */
    private static Node name(Nodes g, Row r, Tags tags) {
        Node glyph = g.icon(family(r)).size(ICON).node().hitInert(true);
        Node name = g.text(r.name()).font(Type.UI).textSize(Type.BODY).textColor(g.theme().color(Role.INK))
                .wordWrap(false).height(Length.FILL).align(HAlign.LEFT, VAlign.MIDDLE);
        Node tag = g.text(NEW).font(Type.MONO).textSize(Type.SMALL).textColor(g.theme().color(Look.ANCHOR))
                .wordWrap(false).hitInert(true).visible(tags.has(r.path()));
        tags.built(r.path(), tag);
        // Not a scroller, as the table's own cells are not: a name wider than its column is clipped, not scrolled.
        return g.row().width(Length.FILL).height(Length.FILL).scroll(false, false)
                .alignItems(AlignItems.CENTER).gap(Type.TIGHT)
                .padding(Length.ZERO, PAD).children(glyph, name, tag);
    }

    /** What a file that has just landed is tagged with. */
    static final String NEW = "new";

    /**
     * The icon's family: finer than a {@link Kind} (the rail speaks of "documents", the icon tells a spreadsheet from a
     * PDF), and only ever drawn. A folder is a folder whatever it is called.
     */
    static FileFamily family(Row r) {
        return r.folder() ? FileFamily.FOLDERS : FileFamily.ofExtension(Kind.extension(r.name()));
    }

    /** A row that could not be read has no time, and says none rather than 1970. */
    private static String modified(Row r) {
        return r.modified().equals(Instant.EPOCH) ? "" : Dates.column(r.modified(), Instant.now(), ZoneId.systemDefault());
    }

    /**
     * The type as Orbit's badge: the extension, or {@code FOLDER}, in its family's colour on the ground, rounded. The
     * one place the family colours show in the list (Look's families).
     */
    private static Node type(Nodes g, Row r) {
        Node badge = g.text(r.typeLabel()).font(Type.MONO).textSize(Type.SMALL)
                .textColor(g.theme().color(Look.family(family(r)))).wordWrap(false)
                .padding(Length.dp(2), Length.dp(6)).corner(Length.dp(6)).background(Look.CHROME);
        return g.row().width(Length.FILL).height(Length.FILL).scroll(false, false).clip(true)
                .alignItems(AlignItems.CENTER).padding(Length.ZERO, PAD).children(badge);
    }

    private static Node cell(Nodes g, String text, int face, Length size, Role ink, HAlign align) {
        return g.text(text).font(face).textSize(size).textColor(g.theme().color(ink)).wordWrap(false)
                .height(Length.FILL).padding(Length.ZERO, PAD).align(align, VAlign.MIDDLE);
    }
}
