package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.Menu;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Shown;
import dev.sibarum.vexplore.core.Suggestions.Act;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.drop.DropEffect;
import dev.vexelray.gui.core.drop.PayloadType;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.widget.MenuAction;
import dev.vexelray.gui.widget.Table;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One file list, with what it needs beside it: its heading, Orbit's (the folder's name large and its count, and in Two
 * Folders its side before them, in the accent while it is the list used), and its {@link Tags}. {@link Ui} has two,
 * FROM and TO, built alike, so the two cannot drift apart. Holds no state of its own beyond what its widgets keep.
 */
final class FolderList {

    private final Gui gui;
    private final Table<Row> table;
    private final Tags tags = new Tags();
    private final Node header;
    private final Node side;
    private final Node name;
    private final Node count;
    private final Node column;

    FolderList(Gui gui, String landmark, String headerLandmark, String sideWord) {
        this.gui = gui;
        table = new Table<>(gui, Columns.ROW_REM, Columns.of(tags)).name(landmark)
                .headers(Type.MONO, Type.SMALL, Role.DIM);
        table.node().width(Length.FILL).height(Length.FILL);
        gui.landmark(landmark, table.node());
        table.rows().looks(Role.SELECTION, Look.TARGETED);
        // The row menu: Open, then the rail's actions, which a choice picks on the rail rather than carries out.
        table.rows().action(MenuAction.of(Menu.OPEN, "Open"));
        for (Act a : Act.values()) {
            table.rows().action(MenuAction.of(a.name(), a.chip()));
        }

        side = gui.text(sideWord).font(Type.MONO).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(false);
        side.visible(false);
        name = gui.text("").font(Type.UI).textSize(Type.TITLE).textColor(Role.INK).wordWrap(false);
        count = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(false);
        // Orbit's heading: the folder's name large, its count beside it; FROM or TO before it in Two Folders.
        header = gui.row().width(Length.FILL).height(Length.rem(3.5f)).padding(Length.ZERO, Length.rem(1.25f))
                .gap(Length.rem(0.75f))
                .alignItems(AlignItems.CENTER).scroll(false, false).clip(true).children(side, name, count);
        gui.landmark(headerLandmark, header);
        // The rows on the panel, where the list would draw them in the theme's well.
        table.rows().node().background(Look.LIST);
        column = gui.column().width(Length.grow(1f)).height(Length.FILL).background(Look.LIST)
                .children(header, table.node());
    }

    /** The list and its header, as one column. */
    Node node() {
        return column;
    }

    /** The table's: what its selection is said with. */
    String address() {
        return table.address();
    }

    /** The table's rows': what a row activated, clicked or chosen from its menu is said with. */
    String rowsAddress() {
        return table.rows().address();
    }

    /** The node that takes the keyboard. */
    Node rowsNode() {
        return table.rows().node();
    }

    /** How many rows the list holds, for a test. */
    int count() {
        return table.rows().count();
    }

    /**
     * Its rows, with the rows the explorer names selected and the first scrolled to. Selecting them is said like any
     * selection, so the explorer counts them.
     */
    void show(Shown s) {
        // A row is a value, so a file that grew since the last listing is a new one, and the list would let it go. The
        // folder listed again keeps what was selected, by path, where it stands.
        Set<Path> held = table.selection().selection().stream().map(Row::path).collect(Collectors.toSet());
        table.items(s.rows());
        List<Row> kept = s.rows().stream().filter(r -> held.contains(r.path())).toList();
        if (!kept.isEmpty() && s.select().isEmpty()) {
            table.selection().set(kept);
        }
        List<Row> select = s.rows().stream().filter(r -> s.select().contains(r.path())).toList();
        if (!select.isEmpty()) {
            table.selection().set(select);
            table.rows().reveal(select.getFirst());
        }
        Path n = s.folder().getFileName();
        name.text(n == null ? s.folder().toString() : n.toString());
        count.text(s.rows().size() == 1 ? "1 item" : s.rows().size() + " items");
    }

    /** Put {@code control} at the header's far end: TO's Swap sides. Shown with the header. */
    void append(Node control) {
        header.append(gui.row().width(Length.grow(1f)));
        header.append(control);
    }

    /** In Two Folders, FROM or TO before the name, in the accent while this is the list used; neither in one. */
    void side(boolean two, boolean used) {
        side.visible(two);
        side.textColor(used ? Role.ACCENT : Role.DIM);
    }

    /**
     * Let its rows be carried as {@code type}: the selection, or the row grabbed. The label rides with the pointer: a
     * file's name, or how many.
     */
    void carries(PayloadType<List<Row>> type) {
        table.rows().draggable(type, rows -> rows.size() == 1 ? rows.getFirst().name() : rows.size() + " files");
    }

    /**
     * Take the other list's rows, {@code type}, meaning {@code effect}: declared again whenever the two folders change,
     * since what a drop does depends on their drives, and a declaration replaces the last.
     */
    void takes(PayloadType<List<Row>> type, DropEffect effect) {
        table.rows().droppable(type, effect);
    }

    /** The rows the rail would reach, marked; none on the list not used. */
    void marks(Collection<Row> reach) {
        table.rows().marks(reach);
    }

    /** The rows to tag new (framework-notes FN-12). */
    void fresh(Set<Path> paths) {
        tags.mark(paths, table.rows().realizedItems().stream().map(Row::path).collect(Collectors.toSet()));
    }
}
