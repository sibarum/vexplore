package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.Place;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.IconSource;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.IconButton;
import dev.vexelray.gui.widget.ListView;
import dev.vexelray.gui.widget.Nodes;
import dev.vexelray.text.TextLayout.HAlign;
import dev.vexelray.text.TextLayout.VAlign;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Orbit's sidebar, on the ground beside the list (M8): the places, flat, the one the folder shown is in filled; and
 * under them the marks kept, which were the rail's until M8. A place is gone to when it is clicked; which one is filled
 * follows the folder shown, so the sidebar cannot disagree with the breadcrumb.
 */
final class Sidebar {

    /** A place's row: Orbit's 42 px. */
    private static final float ROW_REM = 2.625f;

    /** The marks listed, as many as the explorer keeps. */
    private static final int MARKS = 6;

    private final ListView<Place> places;
    private final List<Button> mark = new ArrayList<>();
    private final List<IconButton> forget = new ArrayList<>();
    private final List<Node> markRows = new ArrayList<>();
    private final Node none;
    private final Node node;

    /** What the sidebar was told last, as a widget keeps its items: the places, and the folder shown. */
    private List<Place> known = List.of();
    private Optional<Path> at = Optional.empty();

    Sidebar(Gui gui) {
        places = new ListView<Place>(gui, ROW_REM, Sidebar::row).name(Landmarks.PLACES);
        places.looks(Look.TARGETED, Look.TARGETED);
        places.node().width(Length.FILL).height(Length.grow(1f)).background(Look.CHROME);
        gui.landmark(Landmarks.PLACES, places.node());

        Node heading = gui.text("Marks").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(false)
                .padding(Length.ZERO, Type.SIDE);
        none = gui.text("Marked groups appear here.").font(Type.UI).textSize(Type.SMALL).textColor(Role.FAINT)
                .wordWrap(true).width(Length.FILL).padding(Length.rem(0.75f), Length.rem(0.9f))
                .corner(Length.dp(16)).border(Length.dp(1), Role.LINE);
        Node kept = gui.column().width(Length.FILL).gap(Length.rem(0.3f)).children(none);
        for (int i = 0; i < MARKS; i++) {
            Button recall = new Button(gui, "").kind(Button.Kind.GHOST).name(Landmarks.MARK + "." + i);
            gui.landmark(Landmarks.MARK + "." + i, recall.node());
            IconButton drop = new IconButton(gui, Icon.CLOSE, "Forget this mark").name(Landmarks.FORGET + "." + i);
            gui.landmark(Landmarks.FORGET + "." + i, drop.node());
            Node row = gui.row().width(Length.FILL).gap(Length.rem(0.3f)).alignItems(AlignItems.CENTER)
                    .scroll(false, false).clip(true).children(recall.node(), drop.node());
            row.visible(false);
            mark.add(recall);
            forget.add(drop);
            markRows.add(row);
            kept.append(row);
        }
        node = gui.column().width(Length.FILL).height(Length.FILL).gap(Length.rem(0.6f))
                .padding(Length.rem(0.5f), Length.ZERO)
                .children(places.node(), heading, kept);
    }

    /** A place's row: its icon and its name. */
    private static Node row(Nodes g, Place p) {
        Node glyph = g.icon(icon(p)).size(Length.rem(1.125f)).node().hitInert(true);
        Node name = g.text(p.label()).font(Type.UI).textSize(Type.BODY).textColor(g.theme().color(Role.INK))
                .wordWrap(false).height(Length.FILL).align(HAlign.LEFT, VAlign.MIDDLE);
        return g.row().width(Length.FILL).height(Length.FILL).scroll(false, false).alignItems(AlignItems.CENTER)
                .gap(Length.rem(0.75f)).padding(Length.ZERO, Length.rem(0.875f)).corner(Length.dp(21))
                .children(glyph, name);
    }

    /**
     * A place's icon, from the VexelRay sets: the suite's, a file family's, or Vexplore's own where the suite has none
     * (a drive). Told by the folder's own name, as {@code ListPlaces} names the person's folders.
     */
    static IconSource icon(Place p) {
        if (p.path().getParent() == null) {
            return VexploreIcon.DRIVE;
        }
        Path name = p.path().getFileName();
        String n = name == null ? "" : name.toString().toLowerCase(Locale.ROOT);
        return n.equals("desktop") ? Icon.DISPLAY
                : n.equals("documents") ? FileFamily.DOCUMENTS
                : n.equals("downloads") ? Icon.ARROW_DOWN
                : n.equals("music") ? Icon.AUDIO
                : n.equals("pictures") ? FileFamily.IMAGES
                : n.equals("videos") ? FileFamily.VIDEO
                : FileFamily.FOLDERS;
    }

    Node node() {
        return node;
    }

    /** The places' rows': what a place clicked or entered is said with. */
    String placesAddress() {
        return places.address();
    }

    /** The places, as {@code ListPlaces} found them; the one the folder shown is in filled, whichever came first. */
    void places(List<Place> list) {
        known = List.copyOf(list);
        places.items(known);
        at(at);
    }

    /** Fill the place the folder shown is in: the deepest place above it, or none. */
    void at(Optional<Path> folder) {
        at = folder;
        Place best = null;
        if (folder.isPresent()) {
            for (Place p : known) {
                if (folder.get().startsWith(p.path())
                        && (best == null || p.path().getNameCount() > best.path().getNameCount())) {
                    best = p;
                }
            }
        }
        if (best == null) {
            places.selection().clear();
        } else {
            places.selection().set(List.of(best));
        }
    }

    /** The marks kept, by name, newest first: one row each, the rest hidden in their places; a note when none. */
    void marks(List<String> names) {
        for (int i = 0; i < MARKS; i++) {
            boolean there = i < names.size();
            mark.get(i).label(there ? names.get(i) : "");
            markRows.get(i).visible(there);
        }
        none.visible(names.isEmpty());
    }

    /** Which mark {@code address} recalls, or -1 for none. */
    int recall(String address) {
        for (int i = 0; i < mark.size(); i++) {
            if (mark.get(i).address().equals(address)) {
                return i;
            }
        }
        return -1;
    }

    /** Which mark {@code address} forgets, or -1 for none. */
    int forget(String address) {
        for (int i = 0; i < forget.size(); i++) {
            if (forget.get(i).address().equals(address)) {
                return i;
            }
        }
        return -1;
    }
}
