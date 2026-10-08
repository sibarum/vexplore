package dev.sibarum.vexplore;

import dev.sibarum.vexplore.ImageView.Backdrop;
import dev.sibarum.vexplore.files.Picture;
import dev.sibarum.vexplore.files.Preview;
import dev.vexelray.framework.shell.Appearance;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.WindowControls;
import dev.vexelray.gui.core.app.AppWindow;
import dev.vexelray.gui.core.app.GuiApp;
import dev.vexelray.gui.core.app.WindowSpec;
import dev.vexelray.gui.core.input.InputTopics;
import dev.vexelray.gui.core.input.InteractionState;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.layout.NodeLayout;
import dev.vexelray.gui.core.style.Relief;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.krono.KronoGui;
import dev.vexelray.gui.krono.Scheduled;
import dev.vexelray.gui.widget.TitleBar;
import dev.vexelray.os.Decorations;
import dev.vexelray.os.WindowConfig;
import sibarum.atchung.Atchung;
import sibarum.kronometer.Dur;
import sibarum.kronometer.anim.Ease;
import sibarum.tactroller.api.InputEvent;
import sibarum.tactroller.api.Key;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The image viewer: the selected picture in a window of its own, as large as the window allows, and a step to the
 * next or previous image in the folder.
 *
 * <p><b>It drives the selection rather than keeping a place of its own.</b> Stepping selects the next image in the
 * list, so the list, the dock and the Suggestion Rail follow, and closing the viewer leaves the user on the picture
 * they were looking at, ready to act on it. What it shows is whatever the model's preview is, so a click in the list
 * moves the viewer too. One position, in one place.
 *
 * <p><b>Fast stepping costs nothing it should not.</b> The viewer asks for nothing itself; it changes the selection,
 * and {@link Previewer} reads only the newest request and reads ahead of it. Until the new picture lands the old one
 * stays up, under the new name and count, so holding an arrow key moves the counter at once and never blanks.
 *
 * <p><b>Every control is a {@link Tile}</b>: one size, an icon over a word. They sit in one bar under the picture, in
 * three groups: what this is and Close on the left, moving through the folder in the middle (Previous, the
 * {@link Filmstrip}, Next), and how it is shown on the right (Background, Keep shown).
 *
 * <p><b>The picture is never covered, and never moves.</b> The bar has its own room under the picture. When the
 * pointer rests the bar fades out but keeps that room, so the picture does not grow and shrink as the mouse comes and
 * goes; moving the mouse, clicking or pressing Tab brings it back, and Keep shown stops it going. The one thing drawn
 * over the picture is the backdrop menu, which the user opens and which closes on a choice, a second press, a click on
 * the picture, or Escape.
 *
 * <p><b>Its own tree, not a reparented dock.</b> A node cannot move between trees, so the viewer is built once, on its
 * own {@link Gui} the way {@code Popout} builds its away tree, and shown in a named {@link AppWindow} that the
 * framework re-creates around the same tree each time it opens. It wears the application's title bar.
 *
 * <p>Late-bound like {@link Chooser}: a window needs the {@code GuiApp}, which {@link Recipes#windowBinding} hands
 * it. Until then {@link #open} does nothing.
 */
final class Viewer {

    static final String KEY = "viewer";

    /** How long the pointer rests before the bar fades, and how long the fade takes. */
    static final int IDLE_MS = 2500;
    private static final int FADE_MS = 240;

    private static final Length SLAB_CORNER = Length.rem(1.625f);
    /** The title's text column is this wide and clips, so a long name never pushes the filmstrip aside. */
    private static final Length TITLE_W = Length.rem(10f);
    private static final Length SWATCH = Length.rem(1.75f);

    private final Gui gui;
    private final KronoGui krono;
    private final Model model;
    private final Previewer previewer;
    private final ImageView image;
    private final TitleBar bar;
    private final Node name;
    private final Node info;
    private final Node count;
    private final Tile prev;
    private final Tile next;
    private final Tile background;
    private final Tile keep;
    private final Filmstrip strip;
    private final Node controls;
    private final Node title;
    private final Node tools;
    private final Node menu;
    private final Map<Backdrop, Tile> swatches = new EnumMap<>(Backdrop.class);

    /** The window's box, for drawing the checkerboard across it, and the size it was last drawn at. */
    private volatile NodeLayout windowBox = NodeLayout.ABSENT;
    private long checkered = -1;

    private volatile GuiApp app;
    private volatile AppWindow window;
    private volatile boolean open;
    private Picture shown;
    /** The file {@link #shown} is of: where the next picture is coming from, for the side it slides in on. */
    private Path shownPath;

    /** The bar's idle fade. Guarded by this, except the volatiles, which input reads on its own thread. */
    private volatile long stirred;
    private volatile boolean hidden;
    private volatile boolean keepShown;
    private volatile boolean menuOpen;
    private volatile boolean overBar;
    private final AtomicInteger fade = new AtomicInteger();
    private Scheduled idle;

    Viewer(Gui host, KronoGui krono, Model model, Previewer previewer, Textures textures, Appearance look) {
        this.krono = krono;
        this.model = model;
        this.previewer = previewer;
        this.gui = new Gui(Atchung.create(), host.handlers(), host.offload());
        look.applyTo(gui);

        this.bar = new TitleBar(gui, WindowControls.NONE, "Viewer");
        this.name = text("", Type.BODY, Role.INK, Type.UI);
        this.info = text("", Type.SMALL, Look.TEXT, Type.MONO);
        this.count = text("", Type.META, Look.TEXT, Type.UI);
        gui.landmark(Landmarks.VIEWER_NAME, name);
        gui.landmark(Landmarks.VIEWER_COUNT, count);

        Tile close = Tile.of(gui, Icons.CLOSE, "Close").onPress(this::close);
        this.prev = Tile.of(gui, Icons.PREVIOUS, "Previous").onPress(() -> step(-1));
        this.next = Tile.of(gui, Icons.NEXT, "Next").onPress(() -> step(1));
        this.background = Tile.of(gui, Icons.BACKGROUND, "Background").toggle().onPress(this::toggleMenu);
        this.keep = Tile.of(gui, Icons.PIN, "Keep shown").toggle().onPress(this::toggleKeep);
        gui.landmark(Landmarks.VIEWER_CLOSE, close.node());
        gui.landmark(Landmarks.VIEWER_PREVIOUS, prev.node());
        gui.landmark(Landmarks.VIEWER_NEXT, next.node());
        gui.landmark(Landmarks.VIEWER_BACKGROUND, background.node());
        gui.landmark(Landmarks.VIEWER_KEEP, keep.node());

        this.strip = new Filmstrip(gui, krono, textures, this::thumbnail, this::pick);

        Node words = gui.column().width(TITLE_W).height(Length.AUTO).gap(Length.rem(0.375f))
                .padding(Length.ZERO, Length.rem(0.375f)).clip(true).scroll(false, false)
                .children(name, info, count);
        this.title = slab(gui.row()).alignItems(AlignItems.CENTER).children(close.node(), words);
        Node browse = slab(gui.row()).alignItems(AlignItems.CENTER).children(prev.node(), strip.node(), next.node());
        this.tools = slab(gui.row()).alignItems(AlignItems.CENTER).children(background.node(), keep.node());

        Node left = gui.row().width(Length.grow(1f)).height(Length.AUTO).scroll(false, false).children(title);
        Node right = gui.row().width(Length.grow(1f)).height(Length.AUTO).justify(Justify.END).scroll(false, false)
                .children(tools);
        this.controls = gui.row().width(Length.FILL).height(Length.AUTO).alignItems(AlignItems.CENTER)
                .padding(Length.ZERO, Length.rem(1f)).gap(Length.rem(1f)).scroll(false, false)
                .children(left, browse, right);
        gui.onResize(controls, this::fitStrip);
        gui.onState(controls, s -> overBar = s != InteractionState.NORMAL);

        Node choices = gui.row().height(Length.AUTO).gap(Tile.GAP).scroll(false, false);
        for (Backdrop b : Backdrop.values()) {
            Tile t = Tile.of(gui, swatch(b), b.label()).onPress(() -> {
                backdrop(b);
                closeMenu();
            });
            gui.landmark(Landmarks.VIEWER_BACKDROP + b.name().toLowerCase(Locale.ROOT), t.node());
            swatches.put(b, t);
            choices.append(t.node());
        }
        Node caption = text("BACKGROUND", Type.SMALL, Role.DIM, Type.UI);
        this.menu = gui.column().height(Length.AUTO).padding(Length.rem(1f)).scroll(false, false)
                .floatAt(Length.percent(100f), Length.percent(100f)).visible(false)
                .children(slab(gui.column()).alignItems(AlignItems.CENTER).children(caption, choices));

        this.image = new ImageView(gui, krono, textures, Length.rem(1f), Length.rem(1f)).bare();
        gui.onClick(image.node(), this::closeMenu);
        Node stage = gui.column().width(Length.FILL).height(Length.grow(1f)).scroll(false, false)
                .children(image.node(), menu);
        gui.root().direction(Direction.COLUMN).children(bar.node(), stage, controls);
        gui.onResize(gui.root(), box -> {
            windowBox = box;
            paintBackdrop();
        });
        backdrop(Backdrop.DARK);

        gui.shortcut(Key.LEFT, () -> step(-1));
        gui.shortcut(Key.RIGHT, () -> step(1));
        gui.shortcut(Key.PAGE_UP, () -> step(-1));
        gui.shortcut(Key.PAGE_DOWN, () -> step(1));
        gui.shortcut(Key.HOME, () -> step(Integer.MIN_VALUE));
        gui.shortcut(Key.END, () -> step(Integer.MAX_VALUE));
        gui.shortcut(Key.ESCAPE, () -> {
            if (menuOpen) {
                closeMenu();
            } else {
                close();
            }
        });

        // The framework has no per-node pointer-move hook, so the bar's idle fade reads input off the bus.
        gui.bus().subscribe(InputTopics.INPUT, e -> {
            if (e instanceof InputEvent.PointerMoved || e instanceof InputEvent.ButtonPressed
                    || e instanceof InputEvent.Scrolled
                    || e instanceof InputEvent.KeyPressed k && k.key() == Key.TAB) {
                stir();
            }
        });

        model.onChange(this::follow);
        previewer.onRead(p -> {
            if (open) {
                film(model.doc());
            }
        });
    }

    /** Main thread. Null on the way out. */
    void bind(GuiApp app) {
        this.app = app;
        if (app == null) {
            window = null;
        }
    }

    /** Open the viewer on the current selection, or raise it if it is open. Nothing without a window to open from. */
    void open() {
        GuiApp a = app;
        if (a == null) {
            return;
        }
        AppWindow w = a.window(KEY, this::spec);
        window = w;
        w.show();
    }

    void close() {
        AppWindow w = window;
        if (w != null) {
            w.close();
        }
    }

    boolean isOpen() {
        return open;
    }

    private WindowSpec spec() {
        WindowConfig config = WindowConfig.of("Vexplore viewer", 1100, 760).decorations(Decorations.CLIENT);
        return bar.commands(WindowSpec.of(config, gui))
                .onCreated(w -> {
                    open = true;
                    previewer.large(true);
                    show(model.doc());
                    stir();
                })
                .onClosed(() -> {
                    open = false;
                    previewer.large(false);
                    closeMenu();
                    reveal();
                    synchronized (this) {
                        if (idle != null) {
                            idle.cancel();
                            idle = null;
                        }
                        shown = null;
                        shownPath = null;
                        image.clear();
                    }
                });
    }

    private void follow(Doc doc) {
        if (open) {
            show(doc);
        }
    }

    /**
     * Draw what the document says: the selected file's name and place among the folder's images at once, and its
     * picture when the preview of it has arrived. The picture already up stays until then.
     */
    private synchronized void show(Doc doc) {
        Path at = doc.single();
        List<Path> pictures = Previewer.images(doc.ordered());
        int i = at == null ? -1 : pictures.indexOf(at);
        count.text(i < 0 ? pictures.size() + " images" : "Image " + (i + 1) + " of " + pictures.size());
        prev.enabled(i > 0);
        next.enabled(i >= 0 && i < pictures.size() - 1);
        strip.show(pictures, i);
        String file = at == null ? "" : at.getFileName() == null ? at.toString() : at.getFileName().toString();
        name.text(at == null ? "Nothing selected" : file);
        bar.title(at == null ? "Viewer" : file + " — Viewer");

        Preview p = doc.preview();
        if (p == null || at == null || !at.equals(p.path())) {
            info.text(at == null ? "" : "reading…");
            return;
        }
        if (p.tier() != Preview.Tier.IMAGE) {
            shown = null;
            shownPath = null;
            image.clear();
            info.text(p.identity() + " · not an image");
            return;
        }
        Picture want = p.large() != null ? p.large() : p.picture();
        info.text(p.identity());
        if (want != shown) {
            shown = want;
            image.show(want, direction(pictures, shownPath, at));
            shownPath = at;
        }
    }

    /** Bring the filmstrip up to date: a neighbour's thumbnail may have been read since the last step. */
    private synchronized void film(Doc doc) {
        List<Path> pictures = Previewer.images(doc.ordered());
        Path at = doc.single();
        strip.show(pictures, at == null ? -1 : pictures.indexOf(at));
    }

    private Picture thumbnail(Path path) {
        Preview p = previewer.kept(path);
        return p == null ? null : p.picture();
    }

    /** Show {@code path}, picked from the filmstrip. */
    private void pick(Path path) {
        model.change(d -> d.selected().equals(Set.of(path)) ? d : d.selecting(Set.of(path)));
    }

    /**
     * Select the image {@code delta} places along from the selected one, stopping at the ends; the extreme values
     * mean the first and the last. Nothing selected, or a selection that is not an image, starts from the first.
     */
    private void step(int delta) {
        // Inside the commit, not from a read before it: key handlers run on a pool, so two presses read apart
        // from their writes would both step from the same picture, and holding an arrow key would lose half of them.
        model.change(d -> {
            Path target = target(Previewer.images(d.ordered()), d.single(), delta);
            return target == null || d.selected().equals(Set.of(target)) ? d : d.selecting(Set.of(target));
        });
    }

    /**
     * Which side the picture of {@code to} comes in from, after the picture of {@code from}: 1 if it is later in the
     * folder, -1 if earlier, 0 if it is the same file (a sharper copy arriving) or either is not among {@code pictures}.
     */
    static int direction(List<Path> pictures, Path from, Path to) {
        int a = from == null ? -1 : pictures.indexOf(from);
        int b = to == null ? -1 : pictures.indexOf(to);
        return a < 0 || b < 0 ? 0 : Integer.signum(b - a);
    }

    /** Where a step of {@code delta} from {@code at} lands among {@code pictures}, or null when there are none. */
    static Path target(List<Path> pictures, Path at, int delta) {
        if (pictures.isEmpty()) {
            return null;
        }
        int i = at == null ? -1 : pictures.indexOf(at);
        long to;
        if (delta == Integer.MIN_VALUE) {
            to = 0;
        } else if (delta == Integer.MAX_VALUE) {
            to = pictures.size() - 1;
        } else if (i < 0) {
            to = 0;
        } else {
            to = (long) i + delta;
        }
        return pictures.get((int) Math.max(0, Math.min(pictures.size() - 1, to)));
    }

    /**
     * How many thumbnails fit between the bar's two side groups, which the middle group must not crowd: the strip
     * takes what is left once both sides have their width, so the middle stays centred.
     */
    static int thumbnails(float barWidth, float sideWidth, float tile, float gap, float barGap) {
        float middle = barWidth - 2f * sideWidth - 2f * barGap;
        // The middle slab: its padding, Previous and Next, and a gap after each tile but the last.
        float room = middle - 2f * gap - 2f * tile - gap;
        return room <= 0f ? 0 : (int) (room / (tile + gap));
    }

    private void fitStrip(NodeLayout box) {
        float tile = prev.node().layout().rect().w();
        if (!box.present() || tile <= 0f) {
            return;
        }
        float side = Math.max(title.layout().rect().w(), tools.layout().rect().w());
        float gap = tile / 9f;
        strip.fit(thumbnails(box.content().w(), side, tile, gap, 2f * gap));
    }

    // ---------------------------------------------------------------- background

    private void backdrop(Backdrop b) {
        image.backdrop(b);
        paintBackdrop();
        swatches.forEach((k, t) -> t.on(k == b));
    }

    /**
     * The backdrop is the whole window's, not only the picture's: the window's own fill, and for the checkerboard its
     * drawing, redrawn when the window changes size. The picture's frames are bare, so one pattern runs behind it all.
     */
    private synchronized void paintBackdrop() {
        Backdrop b = image.backdrop();
        Node root = gui.root();
        root.background(ImageView.color(gui.theme(), b));
        if (b != Backdrop.CHECKER) {
            root.picture(null);
            checkered = -1;
            return;
        }
        NodeLayout box = windowBox;
        int w = Math.round(box.rect().w());
        int h = Math.round(box.rect().h());
        long size = (long) w << 32 | h;
        if (box.present() && w > 0 && h > 0 && size != checkered) {
            checkered = size;
            root.picture(ImageView.checks(w, h).picture());
        }
    }

    private void toggleMenu() {
        if (menuOpen) {
            closeMenu();
        } else {
            menuOpen = true;
            menu.visible(true);
            background.on(true);
        }
    }

    private void closeMenu() {
        if (!menuOpen) {
            return;
        }
        menuOpen = false;
        menu.visible(false);
        background.on(false);
        stir();
    }

    /** A swatch of what {@code b} shows behind a picture: its colour, or a small checkerboard. */
    private Node swatch(Backdrop b) {
        Node s = gui.box().size(SWATCH, SWATCH).corner(Length.rem(0.5f)).clip(true)
                .background(ImageView.color(gui.theme(), b))
                .border(Length.dp(1.5f), gui.theme().color(Role.INK));
        if (b == Backdrop.CHECKER) {
            gui.onResize(s, box -> {
                int w = Math.round(box.rect().w());
                int h = Math.round(box.rect().h());
                if (w > 0 && h > 0) {
                    s.picture(ImageView.checks(w, h, Math.max(3, w / 4)).picture());
                }
            });
        }
        return s;
    }

    // ---------------------------------------------------------------- the idle fade

    private void toggleKeep() {
        keepShown = !keepShown;
        keep.on(keepShown);
        stir();
    }

    /** The user did something: show the bar, and start counting again. */
    private void stir() {
        stirred = System.nanoTime();
        if (hidden) {
            reveal();
        }
        arm(IDLE_MS);
    }

    private synchronized void arm(long ms) {
        if (idle == null && open) {
            idle = krono.after(Dur.ms(ms), this::rest);
        }
    }

    /** The timer ran out: fade the bar if nothing has happened since, or wait out what is left. */
    private void rest() {
        synchronized (this) {
            idle = null;
        }
        if (!open || hidden) {
            return;
        }
        long quiet = (System.nanoTime() - stirred) / 1_000_000L;
        if (quiet < IDLE_MS) {
            arm(IDLE_MS - quiet);
            return;
        }
        if (keepShown || menuOpen || overBar) {
            return;
        }
        hidden = true;
        int g = fade.incrementAndGet();
        controls.hitInert(true);
        krono.ramp(Dur.ms(FADE_MS), Ease.LINEAR, p -> {
            if (fade.get() == g) {
                controls.opacity((float) (1.0 - p));
            }
        }, () -> { });
    }

    private void reveal() {
        fade.incrementAndGet();
        hidden = false;
        controls.opacity(1f).hitInert(false);
    }

    // ---------------------------------------------------------------- building

    /** A group of tiles on a panel of its own. */
    private Node slab(Node n) {
        return n.width(Length.AUTO).height(Length.AUTO).padding(Tile.GAP).gap(Tile.GAP).corner(SLAB_CORNER)
                .scroll(false, false).background(gui.theme().color(Look.CHROME))
                .border(Length.dp(1), gui.theme().color(Role.LINE))
                .elevation(gui.theme().elevation(Relief.FLOATING));
    }

    private Node text(String s, Length size, Role ink, int face) {
        return gui.text(s).font(face).textSize(size).textColor(gui.theme().color(ink)).wordWrap(false);
    }
}
