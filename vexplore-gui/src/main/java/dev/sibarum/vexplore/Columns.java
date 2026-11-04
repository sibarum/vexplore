package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Bytes;
import dev.sibarum.vexplore.core.Dates;
import dev.sibarum.vexplore.core.Kind;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Relation;
import dev.vexelray.gui.core.IconSource;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.widget.Nodes;
import dev.vexelray.gui.widget.Table;
import dev.vexelray.text.TextLayout.HAlign;
import dev.vexelray.text.TextLayout.VAlign;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    static List<Table.Column<Row>> of(Tags<String> fresh, Tags<Relation> compared) {
        Comparator<Row> byName = Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT));
        return List.of(
                Table.Column.of("NAME", Length.grow(1), (g, r) -> name(g, r, fresh, compared), byName),
                Table.Column.of("TYPE", Length.rem(5f), Columns::type, Comparator.comparing(Row::typeLabel)),
                Table.Column.of("SIZE", Length.rem(6.5f), (g, r) -> cell(g, r.folder() ? "" : Bytes.format(r.size()),
                        Type.UI, Type.BODY, Role.INK, HAlign.RIGHT), Comparator.comparingLong(Row::size)),
                Table.Column.of("MODIFIED", Length.rem(9.5f), (g, r) -> cell(g, modified(r), Type.UI, Type.BODY,
                        Role.DIM, HAlign.LEFT), Comparator.comparing(Row::modified)));
    }

    /**
     * The name, after its type icon, and the word <em>new</em> after it in amber while the file has just landed. Amber
     * as text, never a fill ({@code Look.ANCHOR}). In Two Folders, the comparison's mark before the icon. Each built
     * hidden or shown as its {@link Tags} says, and handed to it.
     */
    private static Node name(Nodes g, Row r, Tags<String> fresh, Tags<Relation> compared) {
        // Not a scroller, as the table's own cells are not: a name wider than its column is clipped, not scrolled.
        Node row = g.row().width(Length.FILL).height(Length.FILL).scroll(false, false)
                .alignItems(AlignItems.CENTER).gap(Type.TIGHT).padding(Length.ZERO, PAD);
        for (Map.Entry<Relation, Mark> m : MARKS.entrySet()) {
            Node mark = m.getValue().node(g, m.getKey()).visible(compared.has(r.path(), m.getKey()));
            compared.built(r.path(), m.getKey(), mark);
            row.append(mark);
        }
        Node glyph = g.icon(family(r)).size(ICON).node().hitInert(true);
        Node name = g.text(r.name()).font(Type.UI).textSize(Type.BODY).textColor(g.theme().color(Role.INK))
                .wordWrap(false).height(Length.FILL).align(HAlign.LEFT, VAlign.MIDDLE);
        Node tag = g.text(NEW).font(Type.MONO).textSize(Type.SMALL).textColor(g.theme().color(Look.ANCHOR))
                .wordWrap(false).hitInert(true).visible(fresh.has(r.path(), NEW));
        fresh.built(r.path(), NEW, tag);
        row.append(glyph);
        row.append(name);
        row.append(tag);
        return row;
    }

    /** What a file that has just landed is tagged with. */
    static final String NEW = "new";

    /**
     * The mark of a row's group, as Orbit's Two folders board marks its sections: a plus on violet for only here, a
     * bang on amber for different in both (amber being a reason, never a fill of a control), an equals for identical,
     * and the same equals hollow for probably identical, which the board does not draw. A folder in both has none.
     *
     * @param wash the disc's fill, or null for the hollow one, which is ringed in its ink instead
     */
    private record Mark(IconSource icon, Role wash, Role ink) {

        /** Its disc, 18 dp, named for a script by the relation's words, since an icon has none. */
        Node node(Nodes g, Relation relation) {
            Node glyph = g.icon(icon).size(Length.dp(10)).tint(ink).node();
            Node disc = g.row().size(Length.dp(18), Length.dp(18)).corner(Length.dp(9))
                    .alignItems(AlignItems.CENTER).justify(Justify.CENTER).hitInert(true).role(relation.words())
                    .children(glyph);
            return wash == null ? disc.border(Length.dp(1), ink) : disc.background(wash);
        }
    }

    private static final Map<Relation, Mark> MARKS = marks();

    private static Map<Relation, Mark> marks() {
        Map<Relation, Mark> m = new EnumMap<>(Relation.class);
        m.put(Relation.ONLY_HERE, new Mark(Icon.PLUS, Look.FROM_WASH, Look.FROM_INK));
        for (Relation r : List.of(Relation.NEWER, Relation.OLDER, Relation.DIFFERENT)) {
            m.put(r, new Mark(VexploreIcon.BANG, Look.ANCHOR_WASH, Look.ANCHOR));
        }
        m.put(Relation.PROBABLY_IDENTICAL, new Mark(VexploreIcon.SAME, null, Look.QUIET_INK));
        m.put(Relation.IDENTICAL, new Mark(VexploreIcon.SAME, Look.QUIET_WASH, Look.QUIET_INK));
        return m;
    }

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
