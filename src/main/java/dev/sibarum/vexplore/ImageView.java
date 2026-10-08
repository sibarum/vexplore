package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Picture;
import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.ImageRegion;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.layout.NodeLayout;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.draw.Sketch;
import dev.vexelray.gui.krono.KronoGui;
import dev.vexelray.gui.krono.Scheduled;
import dev.vexelray.vulkan.present.SampledImage;
import sibarum.kronometer.Dur;
import sibarum.kronometer.Time;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A place to show one {@link Picture}: whole, at its own aspect, a raster never larger than its own pixels, animated
 * if it is, over a chosen {@link Backdrop}. The Preview Dock has one and the viewer has one.
 *
 * <p><b>Sized in percent of a well.</b> The layout has no aspect-ratio length, so the picture sits in a well that
 * takes whatever room it is given, {@link Gui#onResize} reports the well's box, and the picture is sized in percent
 * of it: the one unit that needs neither the density nor a pixel length. A vector has no size of its own and fills
 * the well.
 *
 * <p><b>Two boxes, so the backdrop is under the picture.</b> A node draws its own drawing over its own image, so the
 * checkerboard is the drawing of an outer box and the picture is the image of an inner one that fills it.
 *
 * <p><b>Letting go.</b> {@link #clear} stops the animation and stops naming the texture, which is all
 * {@code GuiApp.release} asks before it may close one. Everything here is made once per view and shown or hidden.
 */
final class ImageView {

    /** What shows through a transparent pixel. */
    enum Backdrop {
        DARK("Dark"),
        LIGHT("Light"),
        CHECKER("Checker"),
        MAGENTA("Magenta"),
        GREEN("Green");

        private final String label;

        Backdrop(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** GIF's degenerate delays (0 and 10 ms) mean "as fast as you like"; browsers show them at 100 ms, as does this. */
    private static final int FLOOR_MS = 20;
    private static final int DEGENERATE_MS = 100;

    /** A checker square, in px: big enough to read as a pattern, small enough to sit behind an icon. */
    static final int CHECK_PX = 12;
    private static final Color CHECK_LIGHT = Color.rgb(0xF4F4F4);
    private static final Color CHECK_DARK = Color.rgb(0xCDCDCD);

    private final Gui gui;
    private final KronoGui krono;
    private final Textures textures;
    private final Node well;
    private final Node frame;
    private final Node plate;

    /** Bumped by every {@link #show} and {@link #clear}, so a late texture or a stale animation step is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Picture picture;
    private volatile NodeLayout wellBox = NodeLayout.ABSENT;
    private volatile Backdrop backdrop = Backdrop.DARK;
    private Scheduled animation;
    private long checkered = -1;

    ImageView(Gui gui, KronoGui krono, Textures textures, Length padY, Length padX) {
        this.gui = gui;
        this.krono = krono;
        this.textures = textures;
        Length corner = Length.rem(0.3f);
        this.plate = gui.box().width(Length.FILL).height(Length.FILL).corner(corner);
        this.frame = gui.column().size(Length.ZERO, Length.ZERO).corner(corner).scroll(false, false)
                .children(plate);
        this.well = gui.column().width(Length.FILL).height(Length.grow(1f)).padding(padY, padX)
                .justify(Justify.CENTER).alignItems(AlignItems.CENTER).scroll(false, false).children(frame);
        paint();
        gui.onResize(well, box -> {
            wellBox = box;
            fit();
        });
    }

    Node node() {
        return well;
    }

    /**
     * Show {@code p}. The texture arrives later, on the GUI thread, and is dropped if something else has been shown
     * by then. False if there is no window to upload into (a headless capture), in which case nothing is shown.
     */
    boolean show(Picture p) {
        clear();
        int g = generation.get();
        picture = p;
        fit();
        return textures.show(p, texture -> {
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
    }

    /** Show nothing: stop the animation and let go of the texture, so the cache may give it back. */
    synchronized void clear() {
        generation.incrementAndGet();
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        if (picture != null) {
            picture = null;
            plate.image(null);
        }
    }

    Backdrop backdrop() {
        return backdrop;
    }

    /** Change what shows through a transparent pixel. Takes effect at once, picture or no picture. */
    void backdrop(Backdrop b) {
        backdrop = b == null ? Backdrop.DARK : b;
        checkered = -1;
        paint();
        fit();
    }

    /** Step through the sheet's cells on the clock, each for its own delay, until something else is shown. */
    private synchronized void animate(int g, Picture p, SampledImage texture) {
        if (generation.get() != g) {
            return;
        }
        animation = krono.spork("image-animation", () -> {
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

    /** Size the frame to the well: whole, at the picture's aspect, and a raster no larger than its own pixels. */
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
        frame.size(Length.percent(100f * w * scale / cw), Length.percent(100f * h * scale / ch));
        if (backdrop == Backdrop.CHECKER) {
            checker(Math.round(w * scale), Math.round(h * scale));
        }
    }

    /** The plain backdrops are a background colour; the checkerboard is drawn, in {@link #fit}, at the frame's size. */
    private void paint() {
        Color c = switch (backdrop) {
            case DARK -> gui.theme().color(Role.WELL);
            case LIGHT -> Color.rgb(0xF4F4F4);
            case CHECKER -> CHECK_LIGHT;
            case MAGENTA -> Color.rgb(0xFF00FF);
            case GREEN -> Color.rgb(0x00FF00);
        };
        frame.background(c);
        if (backdrop != Backdrop.CHECKER) {
            frame.picture(null);
        }
    }

    /** Redraw the checks when the frame's size changes, and not otherwise: one picture value, a few thousand fills. */
    private synchronized void checker(int w, int h) {
        long size = (long) w << 32 | h;
        if (size == checkered) {
            return;
        }
        checkered = size;
        frame.picture(checks(w, h).picture());
    }

    /** The dark squares of a {@code w x h} checkerboard; the light ones are the frame's background. */
    static Sketch checks(int w, int h) {
        Sketch s = new Sketch().tag("checker");
        for (int y = 0, row = 0; y < h; y += CHECK_PX, row++) {
            for (int x = (row & 1) * CHECK_PX; x < w; x += 2 * CHECK_PX) {
                s.fill(x, y, Math.min(CHECK_PX, w - x), Math.min(CHECK_PX, h - y), CHECK_DARK);
            }
        }
        return s;
    }
}
