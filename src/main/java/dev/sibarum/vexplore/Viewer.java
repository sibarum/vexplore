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
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Direction;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.krono.KronoGui;
import dev.vexelray.gui.widget.Button;
import dev.vexelray.gui.widget.TitleBar;
import dev.vexelray.os.Decorations;
import dev.vexelray.os.WindowConfig;
import sibarum.atchung.Atchung;
import sibarum.tactroller.api.Key;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p><b>Its own tree, not a reparented dock.</b> A node cannot move between trees, so the viewer is built once, on its
 * own {@link Gui} the way {@code Popout} builds its away tree, and shown in a named {@link AppWindow} that the
 * framework re-creates around the same tree each time it opens. It wears the application's title bar.
 *
 * <p>Late-bound like {@link Chooser}: a window needs the {@code GuiApp}, which {@link Recipes#windowBinding} hands
 * it. Until then {@link #open} does nothing.
 */
final class Viewer {

    static final String KEY = "viewer";

    private final Gui gui;
    private final Model model;
    private final Previewer previewer;
    private final ImageView image;
    private final TitleBar bar;
    private final Node name;
    private final Node info;
    private final Node count;
    private final Button prev;
    private final Button next;
    private final Map<Backdrop, Button> backdrops = new EnumMap<>(Backdrop.class);

    private volatile GuiApp app;
    private volatile AppWindow window;
    private volatile boolean open;
    private Picture shown;

    Viewer(Gui host, KronoGui krono, Model model, Previewer previewer, Textures textures, Appearance look) {
        this.model = model;
        this.previewer = previewer;
        this.gui = new Gui(Atchung.create(), host.handlers(), host.offload());
        look.applyTo(gui);

        this.bar = new TitleBar(gui, WindowControls.NONE, "Viewer");
        this.name = text("", Type.RAIL, Role.INK, Type.UI);
        this.info = text("", Type.SMALL, Role.DIM, Type.MONO);
        this.count = text("", Type.SMALL, Role.DIM, Type.MONO);
        this.prev = new Button(gui, "‹ Previous").onPress(() -> step(-1));
        this.next = new Button(gui, "Next ›").onPress(() -> step(1));
        gui.landmark(Landmarks.VIEWER_NAME, name);
        gui.landmark(Landmarks.VIEWER_COUNT, count);

        Node chips = gui.row().height(Length.AUTO).gap(Length.rem(0.35f)).alignItems(AlignItems.CENTER)
                .scroll(false, false).children(text("Backdrop", Type.SMALL, Role.FAINT, Type.UI));
        for (Backdrop b : Backdrop.values()) {
            Button chip = new Button(gui, b.label()).toggle(true).show(b == Backdrop.DARK)
                    .onToggle(on -> backdrop(b));
            gui.landmark(Landmarks.VIEWER_BACKDROP + b.name().toLowerCase(java.util.Locale.ROOT), chip.node());
            backdrops.put(b, chip);
            chips.append(chip.node());
        }

        Node top = gui.row().width(Length.FILL).height(Length.rem(3f)).alignItems(AlignItems.CENTER)
                .padding(Length.ZERO, Length.rem(1f)).gap(Length.rem(0.75f)).scroll(false, false)
                .background(gui.theme().color(Look.CHROME))
                .children(prev.node(), count, next.node(), gui.box().width(Length.grow(1f)).height(Length.rem(1f)),
                        chips);
        Node foot = gui.row().width(Length.FILL).height(Length.rem(2.5f)).alignItems(AlignItems.CENTER)
                .padding(Length.ZERO, Length.rem(1f)).gap(Length.rem(0.75f)).scroll(false, false)
                .background(gui.theme().color(Look.CHROME)).children(name, info);
        this.image = new ImageView(gui, krono, textures, Length.rem(1f), Length.rem(1f));
        gui.root().direction(Direction.COLUMN).background(gui.theme().color(Look.RAIL))
                .children(bar.node(), top, image.node(), foot);

        gui.shortcut(Key.LEFT, () -> step(-1));
        gui.shortcut(Key.RIGHT, () -> step(1));
        gui.shortcut(Key.PAGE_UP, () -> step(-1));
        gui.shortcut(Key.PAGE_DOWN, () -> step(1));
        gui.shortcut(Key.HOME, () -> step(Integer.MIN_VALUE));
        gui.shortcut(Key.END, () -> step(Integer.MAX_VALUE));
        gui.shortcut(Key.ESCAPE, this::close);

        model.onChange(this::follow);
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
                })
                .onClosed(() -> {
                    open = false;
                    previewer.large(false);
                    synchronized (this) {
                        shown = null;
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
        count.text(i < 0 ? "– / " + pictures.size() : (i + 1) + " / " + pictures.size());
        prev.enabled(i > 0);
        next.enabled(i >= 0 && i < pictures.size() - 1);
        String file = at == null ? "" : at.getFileName() == null ? at.toString() : at.getFileName().toString();
        name.text(at == null ? "Nothing selected" : file);
        bar.title(at == null ? "Viewer" : file + " — Viewer");

        Preview p = doc.preview();
        if (p == null || at == null || !at.equals(p.path())) {
            info.text(at == null ? "" : "reading…");
            return;
        }
        info.text(p.identity());
        if (p.tier() != Preview.Tier.IMAGE) {
            shown = null;
            image.clear();
            info.text(p.identity() + " · not an image");
            return;
        }
        Picture want = p.large() != null ? p.large() : p.picture();
        if (want != shown) {
            shown = want;
            image.show(want);
        }
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

    private void backdrop(Backdrop b) {
        image.backdrop(b);
        // A radio group made of chips: the one chosen stays pressed, even when it was the one clicked again.
        backdrops.forEach((k, chip) -> chip.show(k == b));
    }

    private Node text(String s, Length size, Role ink, int face) {
        return gui.text(s).font(face).textSize(size).textColor(gui.theme().color(ink)).wordWrap(false);
    }
}
