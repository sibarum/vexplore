package dev.sibarum.vexplore;

import dev.sibarum.suite.pictures.Picture;
import dev.vexelray.canvas.Color;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.input.ClaimScope;
import dev.vexelray.gui.core.input.CursorShape;
import dev.vexelray.gui.core.input.InteractionState;
import dev.vexelray.gui.core.input.Shortcut;
import dev.vexelray.gui.core.layout.NodeLayout;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.core.style.Theme;
import dev.vexelray.gui.draw.Sketch;
import dev.vexelray.gui.krono.KronoGui;
import sibarum.kronometer.Dur;
import sibarum.kronometer.anim.Ease;
import sibarum.tactroller.api.Key;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The dock's picture as a control: a click on it, or Enter while it has the focus, views the file (in Pix, or in
 * Vexplore's own viewer). It replaced a <i>Pop out</i> button, so it has to say what it does without one.
 *
 * <h2>What says so</h2>
 * At rest the picture sits on a faint mat, a few pixels larger than the picture, with an arrow out of its top right
 * corner: an object that can be lifted, and which way it goes. Hovered or focused, the mat eases outwards, takes the
 * accent's edge and a wash of it, the arrow takes the accent, and the words beside it say where it opens ("View in
 * Pix"). Pressed, the mat draws in a little. The pointer is a hand over the whole of it.
 *
 * <h2>Around the picture, never over it</h2>
 * Every mark is the image well's own drawing, which is painted under the picture, and is placed outside the picture's
 * edge, so nothing covers any of it. The well's layout says where the picture is: the same fit {@code ImageView} makes
 * (whole, centred, a raster never above its own size), worked out here from the well's content box, which is the
 * stage it fits to.
 */
final class Lens {

    private static final Shortcut ENTER = Shortcut.of(Key.ENTER);
    private static final Shortcut SPACE = Shortcut.of(Key.SPACE);

    /** How long the mat takes to answer the pointer. */
    private static final int EASE_MS = 160;

    private final Gui gui;
    private final KronoGui krono;
    private final Node well;
    private final Consumer<Path> view;

    private volatile Path file;
    private volatile Picture picture;
    private volatile String word = "";
    private volatile NodeLayout box = NodeLayout.ABSENT;
    private volatile boolean hovered;
    private volatile boolean focused;
    private volatile boolean pressed;
    /** How far the look has gone from rest (0) to hovered (1). */
    private volatile float lift;
    private final AtomicInteger easing = new AtomicInteger();

    Lens(Gui gui, KronoGui krono, Node well, Consumer<Path> view) {
        this.gui = gui;
        this.krono = krono;
        this.well = well;
        this.view = view;
        well.role("button");
        gui.onResize(well, b -> {
            box = b;
            paint();
        });
        gui.onClick(well, this::activate);
        gui.claim(well, ENTER, ClaimScope.FOCUSED, this::activate);
        gui.claim(well, SPACE, ClaimScope.FOCUSED, this::activate);
        gui.onState(well, s -> {
            pressed = s == InteractionState.PRESSED;
            hover(s != InteractionState.NORMAL);
        });
        gui.bus().subscribe(gui.focusEvents(), e -> {
            if (e.nodeId() == well.id()) {
                focused = e.gained();
                ease();
            }
        });
        show(null, null, "");
    }

    /** Make {@code p}, the picture of {@code f}, the thing a click views; null for no picture, which is inert. */
    void show(Path f, Picture p, String word) {
        this.file = f;
        this.picture = p;
        this.word = word;
        boolean on = f != null && p != null;
        gui.focusable(well, on);
        gui.cursor(well, on ? CursorShape.POINTER : CursorShape.DEFAULT);
        paint();
    }

    private void activate() {
        Path f = file;
        if (f != null && picture != null) {
            view.accept(f);
        }
    }

    private void hover(boolean on) {
        hovered = on;
        ease();
    }

    /** Run the look towards hovered or rest, from wherever it is now. */
    private void ease() {
        float from = lift;
        float to = hovered || focused ? 1f : 0f;
        int t = easing.incrementAndGet();
        if (from == to) {
            paint();
            return;
        }
        krono.ramp(Dur.ms(Math.max(1, Math.round(EASE_MS * Math.abs(to - from)))), Ease.LINEAR, p -> {
            if (easing.get() == t) {
                lift = from + (to - from) * Ease.OUT_CUBIC.at((float) p);
                paint();
            }
        }, () -> {
            if (easing.get() == t) {
                lift = to;
                paint();
            }
        });
    }

    private void paint() {
        Picture p = picture;
        NodeLayout b = box;
        if (p == null || !b.present()) {
            well.picture(null);
            return;
        }
        float cw = b.content().w();
        float ch = b.content().h();
        if (cw <= 0f || ch <= 0f) {
            well.picture(null);
            return;
        }
        float[] r = fit(p, cw, ch);
        float x = b.content().x() - b.rect().x() + r[0];
        float y = b.content().y() - b.rect().y() + r[1];
        float w = r[2];
        float h = r[3];

        Theme theme = gui.theme();
        float u = gui.rootEmPx() * gui.zoom().value() * gui.dpi().value() / 16f;
        float t = lift;
        float grow = u * (4f + 3f * t - (pressed ? 1.5f : 0f));
        float corner = u * 4.8f + grow;
        Color accent = theme.color(Role.ACCENT);
        Color mat = mix(Color.withAlpha(theme.color(Role.RAISED), 0.55f), Color.withAlpha(accent, 0.16f), t);
        Color edge = mix(theme.color(Role.LINE), accent, t);
        Color ink = mix(theme.color(Role.FAINT), accent, t);

        Sketch s = new Sketch().tag("lens")
                .fill(x - grow, y - grow, w + 2 * grow, h + 2 * grow, corner, mat)
                .outline(x - grow, y - grow, w + 2 * grow, h + 2 * grow, corner, Math.max(1f, u), edge);

        // The arrow, out of the mat's top right corner, where there is room for it beside the picture.
        float size = 11f * u;
        float ax = x + w + grow + 6f * u;
        float ay = y - grow;
        float room = cw - (ax - (b.content().x() - b.rect().x()));
        if (room >= size + u) {
            float k = Math.max(1.2f, 1.6f * u);
            s.line(ax, ay + size, ax + size, ay, k, ink)
                    .line(ax + size * 0.4f, ay, ax + size, ay, k, ink)
                    .line(ax + size, ay, ax + size, ay + size * 0.6f, k, ink);
            float textPx = 12f * u;
            String say = word;
            if (t > 0f && !say.isEmpty() && room >= size + 8f * u + say.length() * textPx * 0.56f) {
                s.text(say, ax + size + 7f * u, ay + size, textPx, Color.withAlpha(accent, accent.a() * t));
            }
        }
        well.picture(s.picture());
    }

    /**
     * Where {@code p} sits in a {@code cw x ch} stage, as {@code x, y, w, h}: whole, centred, and a raster never
     * larger than its own pixels. The fit {@code ImageView} makes.
     */
    static float[] fit(Picture p, float cw, float ch) {
        float w = Math.max(1, p.sourceWidth());
        float h = Math.max(1, p.sourceHeight());
        float scale = Math.min(cw / w, ch / h);
        if (!p.vector()) {
            scale = Math.min(scale, 1f);
        }
        float pw = w * scale;
        float ph = h * scale;
        return new float[] {(cw - pw) / 2f, (ch - ph) / 2f, pw, ph};
    }

    private static Color mix(Color a, Color b, float t) {
        return new Color(a.r() + (b.r() - a.r()) * t, a.g() + (b.g() - a.g()) * t, a.b() + (b.b() - a.b()) * t,
                a.a() + (b.a() - a.a()) * t);
    }
}
