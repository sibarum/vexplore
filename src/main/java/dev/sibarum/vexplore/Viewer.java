package dev.sibarum.vexplore;

import dev.sibarum.suite.pictures.Picture;
import dev.sibarum.suite.view.PictureViewer;
import dev.sibarum.suite.view.PictureViewer.Showing;
import dev.sibarum.suite.view.Textures;
import dev.sibarum.vexplore.files.Preview;
import dev.vexelray.framework.shell.Appearance;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.WindowControls;
import dev.vexelray.gui.core.app.AppWindow;
import dev.vexelray.gui.core.app.GuiApp;
import dev.vexelray.gui.core.app.WindowSpec;
import dev.vexelray.gui.krono.KronoGui;
import dev.vexelray.gui.widget.TitleBar;
import dev.vexelray.os.Decorations;
import dev.vexelray.os.WindowConfig;
import sibarum.atchung.Atchung;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * The image viewer: the selected picture in a window of its own, as large as the window allows, and a step to the
 * next or previous image in the folder. The tree is the suite's {@link PictureViewer}, the same one Pix is; what is
 * Vexplore's is that it is a second window, and that it moves through the folder by moving the selection.
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
 * framework re-creates around the same tree each time it opens. Its landmarks are the viewer's with {@code "viewer."}
 * in front: {@code viewer.name}, {@code viewer.count}, {@code viewer.next} and the rest.
 *
 * <p>Late-bound like {@link Chooser}: a window needs the {@code GuiApp}, which {@link Recipes#windowBinding} hands
 * it. Until then {@link #open} does nothing.
 */
final class Viewer {

    static final String KEY = "viewer";

    private final Gui gui;
    private final Model model;
    private final Previewer previewer;
    private final TitleBar bar;
    private final PictureViewer view;

    private volatile GuiApp app;
    private volatile AppWindow window;
    private volatile boolean open;

    Viewer(Gui host, KronoGui krono, Model model, Previewer previewer, Textures textures, Appearance look) {
        this.model = model;
        this.previewer = previewer;
        this.gui = new Gui(Atchung.create(), host.handlers(), host.offload());
        look.applyTo(gui);
        this.bar = new TitleBar(gui, WindowControls.NONE, "Viewer");
        this.view = new PictureViewer(gui, krono, textures, bar, this::thumbnail, new PictureViewer.Host() {
            @Override
            public void step(int delta) {
                Viewer.this.step(delta);
            }

            @Override
            public void pick(Path path) {
                model.change(d -> d.selected().equals(Set.of(path)) ? d : d.selecting(Set.of(path)));
            }

            @Override
            public void close() {
                Viewer.this.close();
            }
        }, "viewer.");
        model.onChange(this::follow);
        previewer.onRead(p -> {
            if (open) {
                view.film();
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
                    view.show(showing(model.doc()));
                    view.active(true);
                })
                .onClosed(() -> {
                    open = false;
                    previewer.large(false);
                    view.active(false);
                });
    }

    private void follow(Doc doc) {
        if (open) {
            view.show(showing(doc));
        }
    }

    /**
     * What the viewer shows for {@code doc}: the selected file's name and place among the folder's images at once, and
     * its picture when the preview of it has arrived.
     */
    static Showing showing(Doc doc) {
        Path at = doc.single();
        List<Path> pictures = Previewer.images(doc.ordered());
        int i = at == null ? -1 : pictures.indexOf(at);
        String count = i < 0 ? pictures.size() + " images" : "Image " + (i + 1) + " of " + pictures.size();
        String file = at == null ? "" : at.getFileName() == null ? at.toString() : at.getFileName().toString();
        String name = at == null ? "Nothing selected" : file;
        String title = at == null ? "Viewer" : file + " — Viewer";
        Preview p = doc.preview();
        if (p == null || at == null || !at.equals(p.path())) {
            return new Showing(pictures, i, name, title, count, at == null ? "" : "reading…", null, at != null);
        }
        if (p.tier() != Preview.Tier.IMAGE) {
            return new Showing(pictures, i, name, title, count, p.identity() + " · not an image", null, false);
        }
        Picture want = p.large() != null ? p.large() : p.picture();
        return new Showing(pictures, i, name, title, count, p.identity(), want, true);
    }

    private Picture thumbnail(Path path) {
        Preview p = previewer.kept(path);
        return p == null ? null : p.picture();
    }

    /**
     * Select the image {@code delta} places along from the selected one, stopping at the ends; the extreme values mean
     * the first and the last. Nothing selected, or a selection that is not an image, starts from the first.
     */
    private void step(int delta) {
        // Inside the commit, not from a read before it: key handlers run on a pool, so two presses read apart
        // from their writes would both step from the same picture, and holding an arrow key would lose half of them.
        model.change(d -> {
            Path target = PictureViewer.target(Previewer.images(d.ordered()), d.single(), delta);
            return target == null || d.selected().equals(Set.of(target)) ? d : d.selecting(Set.of(target));
        });
    }
}
