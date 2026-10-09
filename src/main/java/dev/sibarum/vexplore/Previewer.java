package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;
import dev.sibarum.vexplore.files.Preview;
import dev.sibarum.vexplore.files.Previews;
import dev.sibarum.suite.Lru;
import dev.sibarum.suite.pictures.Stamp;
import dev.vexelray.gui.core.Gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Keeps the Preview Dock's contents matching the selection.
 *
 * <p>Reading a file is disk work, so it runs on {@link Gui#offload()} and lands through {@link Model#previewed},
 * which drops it if the selection has moved on. It listens to the model rather than being called by the list, so a
 * selection that arrives from anywhere (a click, the viewer, a restored session, a script) is followed the same way.
 *
 * <p><b>Latest wins.</b> One worker, one slot. Each new selection overwrites the slot rather than queueing behind the
 * last, so holding an arrow key through a folder of photographs decodes the one under the cursor when the worker
 * comes free, not every one passed on the way. The decode already running is not abandoned (imagelib has no
 * cancel); it lands, is dropped as stale, and the worker goes straight to the newest request.
 *
 * <p><b>Decoded images are kept</b>, least recently used first out, so going back finds them without decoding
 * again. Only images: a text or bytes preview is one 64 KB read, cheaper to redo than to hold. The key is the file's
 * {@link Stamp}, so a file changed since it was decoded is decoded again.
 *
 * <p><b>While the viewer is open</b> ({@link #large}), each image is also made at the viewer's size from the same
 * decode, and an idle worker reads ahead: the next image in the folder, then the previous, then the one after next,
 * so stepping through finds them waiting. Then, at the dock's size only, the rest of what the viewer's filmstrip
 * shows ({@link #reach}), so its thumbnails fill in. Reading ahead stops the moment a real request arrives, and each
 * image it reads is announced to {@link #onRead}, since no model change says so.
 */
final class Previewer {

    /** Decoded pixels kept for going back: a few photographs at the viewer's size, many at the dock's. */
    static final long BUDGET = 256L * 1024 * 1024;

    private final Gui gui;
    private final Model model;
    private final Lru<Stamp, Preview> images = new Lru<>(BUDGET, Preview::bytes, p -> { });

    // Guarded by this.
    private Path last;
    private Path wanted;
    private boolean running;
    private final Deque<Ahead> ahead = new ArrayDeque<>();

    private volatile boolean large;
    private volatile Consumer<Path> read = p -> { };

    /** A read ahead: an image, and whether it is wanted at the viewer's size or only as a thumbnail. */
    private record Ahead(Path path, boolean big) {
    }

    Previewer(Gui gui, Model model) {
        this.gui = gui;
        this.model = model;
        model.onChange(this::follow);
    }

    /**
     * Whether pictures are also wanted at the viewer's size. Turning it on re-reads the current selection, since the
     * preview it has was made without one.
     */
    void large(boolean on) {
        large = on;
        if (on) {
            follow(model.doc(), true);
        } else {
            synchronized (this) {
                ahead.clear();
            }
        }
    }

    /** Who to tell when a read ahead lands, on the worker that read it. One listener: the viewer. */
    void onRead(Consumer<Path> listener) {
        read = listener == null ? p -> { } : listener;
    }

    /**
     * The preview already decoded for {@code path}, or null if there is none or the file has changed since. Reads the
     * file's stamp, so not on the GUI thread.
     */
    Preview kept(Path path) {
        try {
            return images.get(Stamp.of(path));
        } catch (IOException e) {
            return null;
        }
    }

    private void follow(Doc doc) {
        follow(doc, false);
    }

    private void follow(Doc doc, boolean again) {
        Path want = doc.single();
        List<Ahead> next = new ArrayList<>();
        if (large && want != null) {
            around(doc.ordered(), want).forEach(p -> next.add(new Ahead(p, true)));
            reach(doc.ordered(), want).forEach(p -> next.add(new Ahead(p, false)));
        }
        synchronized (this) {
            if (Objects.equals(want, last) && !again) {
                return;
            }
            last = want;
            ahead.clear();
            ahead.addAll(next);
            if (want == null) {
                wanted = null;
                return;
            }
            ask(want);
        }
    }

    /** Put {@code path} in the slot, and start the worker if it is not running. Holding this. */
    private void ask(Path path) {
        wanted = path;
        if (running) {
            return;
        }
        running = true;
        if (Startup.synchronous()) {
            work();
        } else {
            gui.offload().execute(this::work);
        }
    }

    /** Drain the slot, then read ahead, until there is nothing to do. */
    private void work() {
        try {
            while (true) {
                Path path;
                boolean real;
                boolean big;
                synchronized (this) {
                    path = wanted;
                    wanted = null;
                    real = path != null;
                    big = large;
                    if (!real) {
                        Ahead a = ahead.poll();
                        path = a == null ? null : a.path();
                        big = a != null && a.big() && large;
                    }
                    if (path == null) {
                        running = false;
                        return;
                    }
                }
                Preview p = preview(path, big);
                if (real) {
                    model.previewed(path, p);
                } else {
                    read.accept(path);
                }
            }
        } catch (RuntimeException | Error e) {
            synchronized (this) {
                running = false;
            }
            throw e;
        }
    }

    private Preview preview(Path path, boolean big) {
        try {
            Preview kept = images.get(Stamp.of(path));
            if (kept != null && (!big || kept.large() != null)) {
                return kept;
            }
        } catch (IOException e) {
            // Gone or unreadable: Previews says so in the dock.
        }
        Preview fresh = Previews.of(path, big);
        if (fresh.picture() != null) {
            images.put(fresh.picture().stamp(), fresh);
        }
        return fresh;
    }

    /** The images worth reading ahead of {@code at}, nearest first: next, previous, the one after next. */
    static List<Path> around(List<Entry> ordered, Path at) {
        List<Path> pictures = images(ordered);
        int i = pictures.indexOf(at);
        if (i < 0) {
            return List.of();
        }
        List<Path> out = new ArrayList<>(3);
        for (int d : new int[] {1, -1, 2}) {
            int j = i + d;
            if (j >= 0 && j < pictures.size()) {
                out.add(pictures.get(j));
            }
        }
        return out;
    }

    /**
     * The rest of what the viewer's filmstrip shows around {@code at}, beyond {@link #around}: two before, then three
     * after and three before. Wanted as thumbnails only.
     */
    static List<Path> reach(List<Entry> ordered, Path at) {
        List<Path> pictures = images(ordered);
        int i = pictures.indexOf(at);
        if (i < 0) {
            return List.of();
        }
        List<Path> out = new ArrayList<>(3);
        for (int d : new int[] {-2, 3, -3}) {
            int j = i + d;
            if (j >= 0 && j < pictures.size()) {
                out.add(pictures.get(j));
            }
        }
        return out;
    }

    /** The images in {@code ordered}, in the order shown: what the viewer steps through. */
    static List<Path> images(List<Entry> ordered) {
        List<Path> out = new ArrayList<>();
        for (Entry e : ordered) {
            if (!e.folder() && e.kind() == Kind.IMAGE) {
                out.add(e.path());
            }
        }
        return out;
    }
}
