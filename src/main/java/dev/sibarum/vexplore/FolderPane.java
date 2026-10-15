package dev.sibarum.vexplore;

import dev.sibarum.suite.git.Status;
import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Folders;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.widget.Breadcrumb;
import dev.vexelray.gui.widget.Table;
import dev.vexelray.gui.widget.Unclip;

import java.nio.file.Path;
import java.util.Set;

/**
 * The second folder: a list of its own, beside the first, in the place the Suggestion Rail had (README, <em>Two
 * Folders Mode</em>). Its own {@link Model} and {@link Browser}, so it goes where it is told without disturbing the
 * first folder, its selection or the rail's suggestions, which are all about the first.
 *
 * <p>It is a place to put files and take them from, so it offers what that needs and no more: the breadcrumb, the
 * same columns as the main list, opening by double-click or Enter, and dragging rows to and from the first folder
 * ({@link Drops}). Suggestions, the dock and the row menu stay with the first folder.
 */
final class FolderPane {

    private final Gui gui;
    private final Model model = new Model();
    private final Browser browser;
    private final Breadcrumb<Path> crumbs;
    private final Table<Entry> table;
    private final Node count;
    private final Node strip;
    private final Node node;

    /** The last document drawn, compared by identity as {@code Ui.show} does. */
    private Doc shown;
    private volatile Status gitShown;

    FolderPane(Gui gui, Opener opener, Actor actor, Unclip names, Runnable onUse) {
        this.gui = gui;
        this.browser = new Browser(gui, model);
        Routes routes = Routes.standard(gui.offload(), opener, browser::go);

        // Fewer segments than the top bar's: this bar has half the width. A count, not a measurement (Breadcrumb).
        this.crumbs = new Breadcrumb<Path>(gui, Ui::crumbLabel).font(Type.UI).maxSegments(3).onNavigate(p -> {
            onUse.run();
            browser.go(p);
        });
        this.count = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(gui.theme().color(Role.FAINT))
                .wordWrap(false);
        gui.landmark(Landmarks.OTHER_ITEMS, count);
        // Clipped, not scrolled: a long name is cut at the pane's edge rather than growing a scrollbar under it.
        Node header = gui.row().width(Length.FILL).height(Length.rem(3f)).scroll(false, false)
                .alignItems(AlignItems.CENTER)
                .padding(Length.ZERO, Length.rem(1.25f))
                .background(gui.theme().color(Look.CHROME))
                .children(crumbs.node(), gui.box().width(Length.grow(1f)).height(Length.FILL), count);

        this.table = new Table<>(gui, Columns.ROW_REM, Columns.of(names, () -> gitShown));
        table.node().width(Length.FILL).height(Length.grow(1f));
        table.headers(Type.MONO, Type.SMALL, Role.DIM);
        table.rows().looks(Look.SELECTED, Look.TARGETED);
        table.selection().onChange(sel -> model.select(Ui.paths(sel)));
        // A click, not a selection change: a listing arriving changes the selection too, and must not take the tree.
        table.rows().onRowClicked(e -> onUse.run());
        table.rows().onActivate(routes::open);
        table.sortBy(3, Table.Sort.DESCENDING);
        gui.landmark(Landmarks.OTHER_LIST, table.node());
        Drops.wire(gui, table.rows(), () -> model.doc().folder(), actor);

        this.strip = gui.box().width(Length.FILL).height(Length.dp(2)).background(gui.theme().color(Role.LINE));
        this.node = gui.column().width(Length.grow(1f)).height(Length.FILL)
                .background(gui.theme().color(Look.LIST)).children(header, strip, table.node());
        gui.landmark(Landmarks.OTHER, node);

        model.onChange(this::show);
        actor.onChanged(browser::reload);
    }

    Node node() {
        return node;
    }

    /** Show {@code folder}. */
    void go(Path folder) {
        browser.go(folder);
    }

    /** The folder shown, or null before one has been asked for. */
    Path folder() {
        return model.doc().folder();
    }

    /** Whether the tree's clicks come here: drawn as the accent strip under the breadcrumb. */
    void active(boolean active) {
        strip.background(gui.theme().color(active ? Role.ACCENT : Role.LINE));
    }

    private synchronized void show(Doc doc) {
        Doc before = shown;
        shown = doc;
        if ((before == null || before.folder() != doc.folder()) && doc.folder() != null) {
            crumbs.path(Folders.chain(doc.folder()));
        }
        if (before == null || before.entries() != doc.entries() || before.git() != doc.git()) {
            gitShown = doc.git();
            table.items(doc.entries());
        }
        if (doc.selected().isEmpty() && !table.selection().selection().isEmpty() && before != null
                && before.entries() != doc.entries()) {
            table.selection().set(Set.of());   // listed again: what was chosen described a folder that is gone
        }
        count.text(doc.loading() ? "reading..." : doc.entries().size() + " items");
    }
}
