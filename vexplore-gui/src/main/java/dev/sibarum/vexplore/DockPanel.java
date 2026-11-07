package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Bytes;
import dev.sibarum.vexplore.core.Dates;
import dev.sibarum.vexplore.core.Messages.Conflict;
import dev.sibarum.vexplore.core.Messages.Docked;
import dev.sibarum.vexplore.core.Messages.Resolve;
import dev.sibarum.vexplore.core.Messages.Row;
import dev.sibarum.vexplore.core.Messages.Side;
import dev.sibarum.vexplore.core.Preview;
import dev.vexelray.gui.core.Feed;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.fileicons.FileFamily;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.icons.IconView;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.IconButton;

import java.time.Instant;
import java.time.ZoneId;
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
    private final Node main;
    private final Node side;

    /** Two Folders' conflict, shown in place of the preview: FROM's half, TO's, and the column with its buttons. */
    private final Node clash;
    private final Half from;
    private final Half to;
    private final Node verdict;
    private final Button keepNewer;
    private final Button keepBoth;

    /** One copy's half: its tag, its name, the newer or older pill, and the tile with its type and its facts. */
    private record Half(Node node, Node name, Node pill, IconView glyph, Node facts) {
    }

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
        main = gui.column().width(Length.grow(1f)).height(Length.FILL).gap(Length.rem(0.6f)).clip(true)
                .children(header, content);
        // Open with, in a column at the strip's right as Orbit draws it, an icon and its words on a pill: a menu button,
        // whose menu the view declares, a line per program (Vexplore has no viewer of its own).
        openWith = new IconButton(gui, Icon.OPEN, "Open with").name(Landmarks.DOCK_OPEN)
                .tile(Length.dp(40), Length.dp(16));
        openWith.node().direction(Direction.ROW).width(Length.FILL).height(Length.dp(40)).corner(Length.dp(20))
                .gap(Length.dp(8));
        openWith.node().visible(false);
        gui.landmark(Landmarks.DOCK_OPEN, openWith.node());
        side = gui.column().width(Length.dp(150)).gap(Length.dp(8)).children(openWith.node());

        // The conflict, as Orbit's Two folders board draws it: two halves and a column 190 px wide, built once.
        from = half(gui, Side.FROM, Landmarks.DOCK_CONFLICT_FROM);
        to = half(gui, Side.TO, Landmarks.DOCK_CONFLICT_TO);
        verdict = gui.text("").font(Type.UI).textSize(Type.SMALL).textColor(Role.DIM).wordWrap(true)
                .width(Length.FILL);
        gui.landmark(Landmarks.DOCK_CONFLICT_NOTE, verdict);
        keepNewer = new Button(gui, "Keep newer").kind(Button.Kind.PRIMARY).undoable(false)
                .name(Landmarks.DOCK_KEEP_NEWER);
        gui.landmark(Landmarks.DOCK_KEEP_NEWER, keepNewer.node());
        keepBoth = new Button(gui, "Keep both").kind(Button.Kind.SECONDARY).undoable(false)
                .name(Landmarks.DOCK_KEEP_BOTH);
        gui.landmark(Landmarks.DOCK_KEEP_BOTH, keepBoth.node());
        keepNewer.node().width(Length.FILL);
        keepBoth.node().width(Length.FILL);
        Node column = gui.column().width(Length.dp(190)).height(Length.FILL).gap(Length.dp(8))
                .justify(Justify.CENTER).children(verdict, keepNewer.node(), keepBoth.node());
        clash = gui.row().width(Length.FILL).height(Length.FILL).gap(Length.rem(1f))
                .children(from.node(), to.node(), column);
        clash.visible(false);
        gui.landmark(Landmarks.DOCK_CONFLICT, clash);

        node = gui.row().width(Length.FILL).height(Length.FILL).padding(Length.rem(0.9f), Length.rem(1.25f))
                .gap(Length.rem(1.1f)).background(Look.LIST).corner(Look.PANEL_CORNER).clip(true)
                .children(main, side, clash);
        gui.landmark(Landmarks.DOCK, node);
        show(new Docked(java.util.Optional.empty(), "Select one file to see it here"));
    }

    private static Half half(Gui gui, Side which, String landmark) {
        Node tag = gui.text(which.name()).font(Type.MONO).textSize(Type.SMALL).wordWrap(false)
                .padding(Length.dp(2), Length.dp(7)).corner(Length.dp(9))
                .background(which == Side.TO ? Look.TO_WASH : Look.FROM_WASH)
                .textColor(which == Side.TO ? Look.TO_INK : Look.FROM_INK);
        Node name = gui.text("").font(Type.UI).textSize(Type.RAIL_TEXT).textColor(Role.INK).wordWrap(false);
        gui.landmark(landmark, name);
        Node pill = gui.text("").font(Type.MONO).textSize(Type.SMALL).wordWrap(false)
                .padding(Length.dp(2), Length.dp(7)).corner(Length.dp(9)).background(Look.QUIET_WASH)
                .textColor(Look.QUIET_INK);
        Node head = gui.row().width(Length.FILL).gap(Length.dp(8)).alignItems(AlignItems.CENTER)
                .scroll(false, false).clip(true).children(tag, name, pill);
        IconView glyph = IconView.of(gui, FileFamily.FOLDERS).size(Length.dp(28));
        Node facts = gui.text("").font(Type.MONO).textSize(Type.SMALL).textColor(Role.INK).wordWrap(false);
        gui.landmark(landmark + ".facts", facts);
        Node tile = gui.row().width(Length.FILL).height(Length.grow(1f)).gap(Length.dp(14))
                .alignItems(AlignItems.CENTER).justify(Justify.CENTER).corner(Length.dp(14))
                .background(Look.RAISED).children(glyph.node(), facts);
        Node node = gui.column().width(Length.grow(1f)).height(Length.FILL).gap(Length.dp(8)).children(head, tile);
        return new Half(node, name, pill, glyph, facts);
    }

    /**
     * Show the conflict the explorer says, or, with none, the preview again. The halves are FROM's then TO's, each
     * with its pill: newer in amber, older quiet, and none when neither is newer, which disables Keep newer.
     */
    void conflict(Conflict c) {
        boolean on = c.twins().isPresent();
        clash.visible(on);
        main.visible(!on);
        side.visible(!on);
        if (!on) {
            return;
        }
        fill(from, c.twins().get().here(), c.newer().map(n -> n == Side.FROM));
        fill(to, c.twins().get().there(), c.newer().map(n -> n == Side.TO));
        verdict.text(c.note());
        keepNewer.enabled(c.newer().isPresent());
    }

    private static void fill(Half h, Row r, java.util.Optional<Boolean> newer) {
        h.name().text(r.name());
        h.pill().text(newer.map(n -> n ? "newer" : "older").orElse("same time"));
        h.pill().background(newer.orElse(false) ? Look.ANCHOR_WASH : Look.QUIET_WASH);
        h.pill().textColor(newer.orElse(false) ? Look.ANCHOR : Look.QUIET_INK);
        h.glyph().icon(Columns.family(r));
        String when = r.modified().equals(Instant.EPOCH) ? ""
                : " · " + Dates.column(r.modified(), Instant.now(), ZoneId.systemDefault());
        h.facts().text((r.folder() ? "folder" : Bytes.format(r.size())) + when);
    }

    /** Which of the conflict's buttons {@code address} is, or null. */
    Resolve.Keep keep(String address) {
        return keepNewer.address().equals(address) ? Resolve.Keep.NEWER
                : keepBoth.address().equals(address) ? Resolve.Keep.BOTH : null;
    }

    String keepAddress(Resolve.Keep keep) {
        return (keep == Resolve.Keep.NEWER ? keepNewer : keepBoth).address();
    }

    Node node() {
        return node;
    }

    /** Declare what Open with drops: a line per program, each sending what it names. */
    void openWith(dev.vexelray.gui.core.input.Menu programs) {
        openWith.menu(programs);
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
