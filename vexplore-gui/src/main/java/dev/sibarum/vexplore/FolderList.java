package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.Menu;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Shown;
import dev.sibarum.vexplore.core.Messages.Side;
import dev.sibarum.vexplore.core.Relation;
import dev.sibarum.vexplore.core.Suggestions.Act;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.drop.DropEffect;
import dev.vexelray.gui.core.drop.PayloadType;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.widget.IconButton;
import dev.vexelray.gui.widget.MenuAction;
import dev.vexelray.gui.widget.Table;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One file list on its own panel, with what it needs beside it: its heading, Orbit's, and its two {@link Tags}, the
 * tag <em>new</em> and the comparison's marks. {@link Ui} has two, FROM and TO, built alike, so the two cannot drift
 * apart.
 *
 * <p>The heading is the folder's name large and its count. In Two Folders it is the Two folders board's pane header
 * as well: the list's side as a tag, lit while the list is used, its own Back, Up and Choose folder, and the folder's
 * path under its name.
 *
 * <p>Holds no state of its own beyond what its widgets keep, and the folder it shows, which its words already say and
 * its Up needs.
 */
final class FolderList {

    /** A pane's own round buttons: the board's 34 px, the icon 16. */
    private static final Length ROUND = Length.dp(34);
    private static final Length ROUND_ICON = Length.dp(16);

    private final Gui gui;
    private final Side which;
    private final Table<Row> table;
    private final Tags<String> fresh = new Tags<>();
    private final Tags<Relation> compared = new Tags<>();
    private final Node tag;
    private final Node bar;
    private final IconButton back;
    private final IconButton up;
    private final IconButton choose;
    private final Node name;
    private final Node count;
    private final Node path;
    private final Node column;
    private volatile Path folder;

    /** Each landmark it is reached by: the list's, its header's, and its own Back, Up and Choose folder. */
    record Names(String list, String header, String back, String up, String choose) {
        static final Names FROM = new Names(Landmarks.LIST, Landmarks.HEADER_FROM, Landmarks.BACK_FROM,
                Landmarks.UP_FROM, Landmarks.CHOOSE_FROM);
        static final Names TO = new Names(Landmarks.LIST_TO, Landmarks.HEADER_TO, Landmarks.BACK_TO,
                Landmarks.UP_TO, Landmarks.CHOOSE_TO);
    }

    FolderList(Gui gui, Side which, Names names) {
        String landmark = names.list();
        this.gui = gui;
        this.which = which;
        table = new Table<>(gui, Columns.ROW_REM, Columns.of(fresh, compared)).name(landmark)
                .headers(Type.MONO, Type.SMALL, Role.DIM);
        table.node().width(Length.FILL).height(Length.FILL);
        gui.landmark(landmark, table.node());
        table.rows().looks(Role.SELECTION, Look.TARGETED);
        // The row menu: Open, then the rail's actions, which a choice picks on the rail rather than carries out.
        table.rows().action(MenuAction.of(Menu.OPEN, "Open"));
        for (Act a : Act.values()) {
            table.rows().action(MenuAction.of(a.name(), a.chip()));
        }

        // The pane header's first line, Two Folders only: the side's tag, then the pane's own buttons at the far end.
        tag = gui.text(which.name()).font(Type.MONO).textSize(Type.SMALL).wordWrap(false)
                .padding(Length.dp(3), Length.dp(8)).corner(Length.dp(10));
        back = round(gui, Icon.ARROW_LEFT, "Back", names.back());
        up = round(gui, Icon.ARROW_UP, "Up one folder", names.up());
        choose = round(gui, Icon.FOLDER, "Choose folder", names.choose());
        bar = gui.row().width(Length.FILL).alignItems(AlignItems.CENTER).gap(Length.dp(8))
                .children(tag, gui.row().width(Length.grow(1f)), back.node(), up.node(), choose.node());
        bar.visible(false);
        name = gui.text("").font(Type.UI).textSize(Type.TITLE).textColor(Role.INK).wordWrap(false);
        count = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(false);
        Node title = gui.row().width(Length.FILL).gap(Length.rem(0.75f)).alignItems(AlignItems.CENTER)
                .scroll(false, false).clip(true).children(name, count);
        path = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(false);
        path.visible(false);
        Node header = gui.column().width(Length.FILL).padding(Length.rem(0.75f), Length.rem(1.25f))
                .gap(Length.rem(0.5f)).scroll(false, false).clip(true).children(bar, title, path);
        gui.landmark(names.header(), header);
        // The rows on the panel, where the list would draw them in the theme's well.
        table.rows().node().background(Look.LIST);
        column = gui.column().width(Length.grow(1f)).height(Length.FILL).background(Look.LIST)
                .corner(Look.PANEL_CORNER).clip(true).children(header, table.node());
        side(false, true);
    }

    /** A pane's round button, bordered on a step above the panel as the board draws it, named for scripts. */
    private static IconButton round(Gui gui, Icon icon, String name, String landmark) {
        IconButton b = new IconButton(gui, icon, name).name(landmark).size(ROUND, ROUND_ICON);
        b.node().corner(Length.dp(17)).border(Length.dp(1), Role.LINE).background(Look.RAISED);
        gui.landmark(landmark, b.node());
        return b;
    }

    /** The list and its header, as one panel. */
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

    /** Whether {@code address} is this pane's own Back, Up or Choose folder. */
    boolean back(String address) {
        return back.address().equals(address);
    }

    boolean up(String address) {
        return up.address().equals(address);
    }

    boolean choose(String address) {
        return choose.address().equals(address);
    }

    /** The folder shown, or null before the first listing: what the pane's Up goes above. */
    Path folder() {
        return folder;
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
        folder = s.folder();
        name.text(name(s.folder()));
        count.text(s.rows().size() == 1 ? "1 item" : s.rows().size() + " items");
        path.text(s.folder().toString());
    }

    /** A folder's own name, or a drive's root as it is written ({@code C:\}). */
    static String name(Path folder) {
        Path n = folder.getFileName();
        return n == null ? folder.toString() : n.toString();
    }

    /**
     * In Two Folders, the pane header's first line and the path: the side's tag in its own colours while this is the
     * list used, quiet while it is not. Neither in one folder, whose heading is the name and the count.
     */
    void side(boolean two, boolean used) {
        bar.visible(two);
        path.visible(two);
        boolean lit = used && two;
        tag.background(lit ? (which == Side.TO ? Look.TO_WASH : Look.FROM_WASH) : Look.QUIET_WASH);
        tag.textColor(lit ? (which == Side.TO ? Look.TO_INK : Look.FROM_INK) : Look.QUIET_INK);
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
        Map<Path, String> told = new HashMap<>();
        for (Path p : paths) {
            told.put(p, Columns.NEW);
        }
        fresh.tell(told, realized());
    }

    /** Each row's relation to the other list, marked by its group; none in one folder. */
    void compared(Map<Path, Relation> rows) {
        compared.tell(rows, realized());
    }

    private Set<Path> realized() {
        return table.rows().realizedItems().stream().map(Row::path).collect(Collectors.toSet());
    }
}
