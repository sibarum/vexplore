package dev.sibarum.vexplore;

import dev.sibarum.suite.pictures.Picture;
import dev.sibarum.suite.view.PictureViewer;
import dev.sibarum.suite.view.PictureViewer.Showing;
import dev.sibarum.suite.view.Textures;
import dev.sibarum.vexplore.files.Preview;
import dev.vexelray.framework.shell.NamedWindow;
import dev.vexelray.framework.shell.Window;
import dev.vexelray.gui.krono.KronoGui;

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
 * <p><b>Its own tree, not a reparented dock.</b> A node cannot move between trees, so the viewer is built once, into
 * the tree of a named {@link Window} that the framework re-creates around it each time it opens. Its landmarks are
 * the viewer's with {@code "viewer."} in front: {@code viewer.name}, {@code viewer.count}, {@code viewer.next} and
 * the rest.
 *
 * <p>The window is the framework's: its tree on a bus of its own, the look, the mark, the placement remembered under
 * {@code "viewer"} and the controls in the bar. So the viewer is not late-bound: {@link #open} before the window can
 * exist is held and carried out.
 */
final class Viewer {

    /** The window, as {@link Recipes} declares it. A peer: a picture is something to put on another screen. */
    static final NamedWindow DECLARED = NamedWindow.of("viewer", "Vexplore viewer", 1100, 760);

    private final Window window;
    private final Model model;
    private final Previewer previewer;
    private final PictureViewer view;

    private volatile boolean open;

    Viewer(Window window, KronoGui krono, Model model, Previewer previewer, Textures textures) {
        this.window = window;
        this.model = model;
        this.previewer = previewer;
        this.view = new PictureViewer(window.gui(), krono, textures, window.titleBar(), this::thumbnail,
                new PictureViewer.Host() {
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
        // Full-size reads only while there is a window to show them in.
        window.onOpened(() -> {
            open = true;
            previewer.large(true);
            view.show(showing(model.doc()));
            view.active(true);
        });
        window.onClosed(() -> {
            open = false;
            previewer.large(false);
            view.active(false);
        });
    }

    /** Open the viewer on the current selection, or raise it if it is open. From any thread. */
    void open() {
        window.show();
    }

    void close() {
        window.close();
    }

    boolean isOpen() {
        return open;
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
