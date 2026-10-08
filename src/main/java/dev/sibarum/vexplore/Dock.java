package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Picture;
import dev.sibarum.vexplore.files.Preview;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.ImageRegion;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.layout.NodeLayout;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.krono.KronoGui;
import dev.vexelray.gui.krono.Scheduled;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.vulkan.present.SampledImage;
import sibarum.kronometer.Dur;
import sibarum.kronometer.Time;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Preview Dock: a dedicated place under the list that shows the selected file more fully than its row can.
 *
 * <p>It is always there. With nothing selected it says so, in the same place and at the same size, so choosing a
 * file never makes the list above it move. Its contents are read-only text and numbers — see {@link Preview} — and
 * it is drawn as a rebuild of its body, not an edit of it: a preview is a picture of one file, and the previous
 * file's picture has nothing worth keeping.
 *
 * <p>Three tiers exist so far: images (the picture, animated if the file is), text (the first lines, with the
 * encoding) and bytes (a hex dump, the strings in the file, and how random it looks). Bytes is the floor of the
 * design's ladder — every file reaches it — which is why it was built first.
 *
 * <p><b>An image is shown whole, at its own aspect, never larger than it is.</b> The layout has no aspect-ratio
 * length, so the picture sits in a well that fills the dock and is sized in percent of it from the well's measured
 * box ({@link Gui#onResize}), which is recomputed whenever the dock is resized. A vector has no size of its own and
 * fills the well. The well and its picture are made once and shown or hidden, so the resize registration is made
 * once too; the picture is cleared when another tier takes the dock, so its texture is free to be given back.
 */
final class Dock {

    /** GIF's degenerate delays (0 and 10 ms) mean "as fast as you like"; browsers show them at 100 ms, and so does this. */
    private static final int FLOOR_MS = 20;
    private static final int DEGENERATE_MS = 100;

    private final Gui gui;
    private final KronoGui krono;
    private final Opener opener;
    private final Textures textures;
    private final Node frame;
    private final Node header;
    private final Node body;
    private final Node well;
    private final Node plate;
    private final List<Node> headerKids = new ArrayList<>();
    private final List<Node> bodyKids = new ArrayList<>();
    private Preview shown;
    private Boolean empty;

    /** Which picture is on show; bumped by every {@link #show}, so a late texture or a stale frame step is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Picture picture;
    private volatile NodeLayout wellBox = NodeLayout.ABSENT;
    private Scheduled animation;

    Dock(Gui gui, KronoGui krono, Opener opener, Textures textures) {
        this.gui = gui;
        this.krono = krono;
        this.opener = opener;
        this.textures = textures;
        this.header = gui.row().width(Length.FILL).height(Length.rem(2.75f))
                .alignItems(AlignItems.CENTER).padding(Length.ZERO, Length.rem(1.25f)).gap(Length.rem(0.75f))
                .scroll(false, false);
        this.body = gui.column().width(Length.FILL).height(Length.grow(1f))
                .padding(Length.rem(0.5f), Length.rem(1.25f)).gap(Length.rem(0.15f)).scroll(false, true);
        this.plate = gui.box().size(Length.ZERO, Length.ZERO).corner(Length.rem(0.3f))
                .background(gui.theme().color(Role.WELL));
        this.well = gui.column().width(Length.FILL).height(Length.grow(1f))
                .padding(Length.rem(0.5f), Length.rem(1.25f)).justify(Justify.CENTER).alignItems(AlignItems.CENTER)
                .scroll(false, false).visible(false).children(plate);
        this.frame = gui.column().role("preview").width(Length.FILL).height(Length.FILL)
                .background(gui.theme().color(Look.RAIL)).scroll(false, false).children(header, body, well);
        gui.onResize(well, box -> {
            wellBox = box;
            fit();
        });
        show(null);
    }

    Node node() {
        return frame;
    }

    /** Draw {@code preview}, or the empty dock for null. A preview already on show is left alone. */
    synchronized void show(Preview preview) {
        boolean nothing = preview == null;
        if (empty != null && shown == preview) {
            return;
        }
        empty = nothing;
        shown = preview;
        clear();
        stopImage();
        if (nothing) {
            addHeader(text("Preview", Type.RAIL, Role.DIM, Type.UI));
            addBody(text("Select a file to see it here. The dock never covers the list.", Type.META, Role.FAINT,
                    Type.UI));
            return;
        }
        Node name = text(preview.path().getFileName() == null ? preview.path().toString()
                : preview.path().getFileName().toString(), Type.RAIL, Role.INK, Type.UI);
        addHeader(name);
        addHeader(text(preview.identity() + (preview.truncated() ? " · first 64 KB" : ""), Type.SMALL,
                Role.DIM, Type.MONO));
        addHeader(gui.box().width(Length.grow(1f)).height(Length.rem(1f)));
        addHeader(text(preview.tier().tag(), Type.SMALL, Role.ACCENT, Type.MONO)
                .padding(Length.rem(0.2f), Length.rem(0.5f)).corner(Length.rem(0.3f))
                .border(Length.dp(1), gui.theme().color(Role.ACCENT)));
        if (preview.tier() == Preview.Tier.TEXT) {
            // Said even when it cannot be done, so the way to edit a file is visible before the editor is installed.
            boolean editor = opener.editorInstalled();
            Path file = preview.path();
            addHeader(new Button(gui, editor ? "Open in Vex" : "Vex not installed").enabled(editor)
                    .onPress(() -> opener.edit(file)).node());
        }
        addHeader(new Button(gui, "Pop out").enabled(false).node());

        switch (preview.tier()) {
            case FOLDER -> addBody(text("A folder. Open it to see what is inside.", Type.META, Role.DIM, Type.UI));
            case TEXT -> {
                for (String line : preview.lines()) {
                    addBody(text(line.isEmpty() ? " " : line, Type.META, Role.INK, Type.MONO));
                }
            }
            case BYTES -> bytes(preview);
            case IMAGE -> image(preview.picture());
        }
    }

    /**
     * Show {@code p} in the well. The texture arrives later, on the GUI thread, and is dropped if another file has
     * been chosen by then. With no window (a headless capture) there is nothing to upload into, and the dock says so.
     */
    private void image(Picture p) {
        int g = generation.get();
        picture = p;
        fit();
        boolean posted = textures.show(p, texture -> {
            if (generation.get() != g) {
                return;
            }
            if (!p.animated()) {
                plate.image(texture);
                return;
            }
            plate.image(texture, ImageRegion.cell(0, p.columns(), p.rows()));
            animate(g, p, texture);
        });
        if (posted) {
            body.visible(false);
            well.visible(true);
        } else {
            addBody(text("An image. There is no window to draw it in.", Type.META, Role.DIM, Type.UI));
        }
    }

    /** Step through the sheet's cells on the clock, each for its own delay, until another file is shown. */
    private synchronized void animate(int g, Picture p, SampledImage texture) {
        if (generation.get() != g) {
            return;
        }
        animation = krono.spork("dock-animation", () -> {
            int i = 0;
            while (generation.get() == g) {
                int d = p.delays()[i];
                Time.advance(Dur.ms(d < FLOOR_MS ? DEGENERATE_MS : d));
                if (generation.get() != g) {
                    return;
                }
                i = (i + 1) % p.count();
                plate.image(texture, ImageRegion.cell(i, p.columns(), p.rows()));
            }
        });
    }

    /** Leave the image tier: stop the animation and let go of the texture, so the cache may give it back. */
    private void stopImage() {
        generation.incrementAndGet();
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        if (picture != null) {
            picture = null;
            plate.image(null);
            well.visible(false);
            body.visible(true);
        }
    }

    /**
     * Size the picture to the well: whole, at its own aspect, and a raster no larger than its own pixels. In percent
     * of the well's content box, which is the one unit that needs neither the density nor a pixel length.
     */
    private void fit() {
        Picture p = picture;
        NodeLayout box = wellBox;
        if (p == null || !box.present()) {
            return;
        }
        float cw = box.content().w();
        float ch = box.content().h();
        if (cw <= 0f || ch <= 0f) {
            return;
        }
        float w = Math.max(1, p.sourceWidth());
        float h = Math.max(1, p.sourceHeight());
        float scale = Math.min(cw / w, ch / h);
        if (!p.vector()) {
            scale = Math.min(scale, 1f);
        }
        plate.size(Length.percent(100f * w * scale / cw), Length.percent(100f * h * scale / ch));
    }

    private void bytes(Preview p) {
        double e = Math.max(0.05, Math.min(8.0, p.entropy()));
        Node bar = gui.row().width(Length.FILL).height(Length.dp(6)).corner(Length.dp(3)).scroll(false, false)
                .background(gui.theme().color(Role.WELL))
                .children(gui.box().width(Length.grow((float) e)).height(Length.FILL)
                                .background(gui.theme().color(e > 7.5 ? Look.ENTROPY_HIGH
                                        : e < 5.0 ? Look.ENTROPY_LOW : Role.ACCENT)),
                        gui.box().width(Length.grow((float) (8.0 - e) + 0.001f)).height(Length.FILL));
        addBody(text(String.format(Locale.ROOT, "entropy %.1f of 8 bits per byte · %s", p.entropy(),
                p.entropy() > 7.5 ? "looks compressed or encrypted"
                        : p.entropy() < 1.0 ? "mostly one value" : "structured"), Type.SMALL, Role.DIM, Type.MONO));
        addBody(bar);
        addBody(gui.box().width(Length.FILL).height(Length.rem(0.4f)));
        for (String row : p.lines()) {
            addBody(text(row, Type.META, Role.INK, Type.MONO));
        }
        if (!p.strings().isEmpty()) {
            addBody(gui.box().width(Length.FILL).height(Length.rem(0.4f)));
            addBody(text("strings", Type.SMALL, Role.FAINT, Type.MONO));
            for (String s : p.strings()) {
                addBody(text(s, Type.META, Role.DIM, Type.MONO));
            }
        }
    }

    private Node text(String s, Length size, Role ink, int face) {
        return gui.text(s).font(face).textSize(size).textColor(gui.theme().color(ink)).wordWrap(false);
    }

    private void addHeader(Node n) {
        headerKids.add(n);
        header.append(n);
    }

    private void addBody(Node n) {
        bodyKids.add(n);
        body.append(n);
    }

    private void clear() {
        for (Node n : headerKids) {
            n.remove();
        }
        for (Node n : bodyKids) {
            n.remove();
        }
        headerKids.clear();
        bodyKids.clear();
    }
}
