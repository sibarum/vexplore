package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Bytes;
import dev.sibarum.vexplore.core.Messages.Docked;
import dev.sibarum.vexplore.core.Preview;
import dev.vexelray.gui.core.Feed;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.widget.IconButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Preview Dock, under the list: the one file selected, previewed read-only, or a note when there is not one. The
 * handoff's screen 03 and the tier 4 card of screen 04, drawn from {@link Docked}.
 *
 * <h2>Every node is built once</h2>
 *
 * <p>As the rail: a preview that changes rewrites what the nodes say and whether they show, and never builds or
 * removes one, so nothing changes in one frame. The body is a pool of lines as long as the longest a tier gives. The
 * entropy bar is a row of cells lit or not, so a new file changes colours, which fade, and never a width. A text's
 * lines and a hex dump's rows are the same nodes; a text is drawn in ink, the dump dimmer. A picture or a video's
 * poster frame is one node given each new feed, which the framework crossfades; the view gives the old ones back.
 *
 * <h2>Nothing in it is executed</h2>
 *
 * <p>It shows text and numbers, which is all a {@link Preview} holds.
 */
final class DockPanel {

    /** Lines the body holds: as many as a text gives, which is more than the hex dump's sixteen rows. */
    private static final int LINES = 60;

    /** Cells in the entropy bar: one for each quarter of a bit per byte. */
    private static final int CELLS = 32;

    /**
     * The most a picture takes, in rem: the strip's height on a first run (Orbit's 168 px) less its header, and most
     * of its width beside Open with.
     */
    private static final float PICTURE_H = 6f;
    private static final float PICTURE_W = 36f;

    private final Node node;
    private final Node name;
    private final Node about;
    private final Node tag;
    private final Node note;
    private final Node body;
    private final List<Node> lines = new ArrayList<>();
    private final Node measure;
    private final Node entropy;
    private final Node reading;
    private final List<Node> cells = new ArrayList<>();
    private final Node strings;
    private final Node picture;
    private final IconButton openWith;

    DockPanel(Gui gui) {
        name = gui.text("").font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK).wordWrap(false);
        gui.landmark(Landmarks.DOCK_NAME, name);
        about = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(false);
        tag = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Role.ACCENT).wordWrap(false);
        gui.landmark(Landmarks.DOCK_TAG, tag);
        Node words = gui.row().width(Length.grow(1f)).gap(Length.rem(0.75f)).alignItems(AlignItems.CENTER)
                .scroll(false, false).clip(true).children(name, about);
        Node header = gui.row().width(Length.FILL).gap(Length.rem(0.75f)).alignItems(AlignItems.CENTER)
                .justify(Justify.SPACE_BETWEEN).children(words, tag);

        note = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.FAINT).wordWrap(true)
                .width(Length.FILL);
        gui.landmark(Landmarks.DOCK_NOTE, note);

        body = gui.column().width(Length.FILL).gap(Length.rem(0.1f));
        for (int i = 0; i < LINES; i++) {
            Node line = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(false);
            lines.add(line);
            body.append(line);
        }
        gui.landmark(Landmarks.DOCK_LINE, lines.getFirst());

        entropy = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.INK).wordWrap(false);
        reading = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(false);
        Node said = gui.row().width(Length.FILL).justify(Justify.SPACE_BETWEEN).children(entropy, reading);
        Node bar = gui.row().width(Length.FILL).gap(Length.dp(2));
        for (int i = 0; i < CELLS; i++) {
            Node cell = gui.box().width(Length.grow(1f)).height(Length.rem(0.375f)).corner(Length.dp(1))
                    .background(Role.LINE);
            cells.add(cell);
            bar.append(cell);
        }
        strings = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.INK).wordWrap(true)
                .width(Length.FILL);
        measure = gui.column().width(Length.FILL).gap(Length.rem(0.4f)).children(said, bar, strings);

        picture = gui.box().corner(Length.rem(0.25f)).visible(false);
        gui.landmark(Landmarks.DOCK_PICTURE, picture);
        Node stage = gui.column().width(Length.FILL).alignItems(AlignItems.CENTER).children(picture);

        Node content = gui.column().width(Length.FILL).height(Length.grow(1f)).gap(Length.rem(0.8f))
                .scroll(true, true).clip(true).children(note, stage, measure, body);
        Node main = gui.column().width(Length.grow(1f)).height(Length.FILL).gap(Length.rem(0.6f)).clip(true)
                .children(header, content);
        // Open with, in a column at the strip's right as Orbit draws it, an icon and its words on a pill. A left click
        // opens the file with what suits it (Vexplore has no viewer of its own: a picture or a video is Rasterware's);
        // the view declares its menu, which names one (framework-notes FN-15).
        openWith = new IconButton(gui, Icon.OPEN, "Open with").name(Landmarks.DOCK_OPEN)
                .tile(Length.dp(40), Length.dp(16));
        openWith.node().direction(Direction.ROW).width(Length.FILL).height(Length.dp(40)).corner(Length.dp(20))
                .gap(Length.dp(8));
        openWith.node().visible(false);
        gui.landmark(Landmarks.DOCK_OPEN, openWith.node());
        Node side = gui.column().width(Length.dp(150)).gap(Length.dp(8)).children(openWith.node());
        node = gui.row().width(Length.FILL).height(Length.FILL).padding(Length.rem(0.9f), Length.rem(1.25f))
                .gap(Length.rem(1.1f)).background(Look.LIST).corner(Look.PANEL_CORNER).clip(true)
                .children(main, side);
        gui.landmark(Landmarks.DOCK, node);
        show(new Docked(java.util.Optional.empty(), "Select one file to see it here"));
    }

    Node node() {
        return node;
    }

    /** Whether {@code address} is Open with. */
    boolean openWith(String address) {
        return openWith.address().equals(address);
    }

    /** Open with's node, for the menu the view declares on it. */
    Node openWithNode() {
        return openWith.node();
    }

    /** Show what the explorer says the dock holds. */
    void show(Docked d) {
        Preview p = d.preview().orElse(null);
        note.visible(p == null);
        note.text(d.note());
        name.text(p == null ? "" : p.path().getFileName() == null ? p.path().toString()
                : p.path().getFileName().toString());
        about.text(p == null ? "" : about(p));
        tag.text(p == null ? "" : p.tier().tag());
        boolean bytes = p != null && p.tier() == Preview.Tier.BYTES && !p.lines().isEmpty();
        boolean text = p != null && p.tier() == Preview.Tier.TEXT;
        body.visible((bytes || text) && !p.lines().isEmpty());
        for (int i = 0; i < LINES; i++) {
            boolean there = (bytes || text) && i < p.lines().size();
            Node line = lines.get(i);
            line.text(there ? p.lines().get(i) : "");
            line.textColor(text ? Role.INK : Role.DIM);
            line.visible(there);
        }
        Feed feed = p == null ? null : p.picture().orElse(null);
        if (feed != null) {
            // Whole, at its own aspect, and never larger than its own pixels at the root's 16 px.
            float rem = Math.min(1f / 16f, Math.min(PICTURE_W / feed.width(), PICTURE_H / feed.height()));
            picture.image(feed).width(Length.rem(feed.width() * rem)).height(Length.rem(feed.height() * rem));
        }
        // Hidden with its picture still on it, so it fades out rather than going blank first.
        picture.visible(feed != null);
        openWith.node().visible(p != null && p.tier() != Preview.Tier.FOLDER);
        measure.visible(bytes);
        if (bytes) {
            entropy.text(String.format(Locale.ROOT, "Entropy %.1f bits per byte", p.entropy()));
            reading.text(reading(p.entropy()));
            int lit = (int) Math.round(p.entropy() / 8d * CELLS);
            for (int i = 0; i < CELLS; i++) {
                cells.get(i).background(i < lit ? Look.ANCHOR : Role.LINE);
            }
            strings.text(p.strings().isEmpty() ? "No readable strings in the first "
                    + Bytes.format(Math.min(p.size(), 64L * 1024))
                    : "Strings found: " + String.join(", ", p.strings()));
        }
    }

    /** {@code PNG image · 3.4 MB}, or a folder's count. */
    private static String about(Preview p) {
        if (p.tier() == Preview.Tier.FOLDER) {
            return "folder · " + p.size() + (p.size() == 1 ? " item" : " items");
        }
        return p.identity() + " · " + Bytes.format(p.size());
    }

    /** What an entropy says, in the words of screen 04. */
    private static String reading(double bits) {
        return bits < 1 ? "nearly all one value" : bits < 5 ? "plain or structured"
                : bits < 7.5 ? "structured, not compressed" : "compressed or encrypted";
    }
}
