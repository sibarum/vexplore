package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Kind;
import dev.sibarum.vexplore.files.Preview;
import dev.sibarum.vexplore.files.Previews;
import dev.sibarum.vexplore.files.Stamp;
import dev.vexelray.gui.core.Gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

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
 * so stepping through finds them waiting. Reading ahead stops the moment a real request arrives.
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
    private final Deque<Path> ahead = new ArrayDeque<>();

    private volatile boolean large;

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

    private void follow(Doc doc) {
        follow(doc, false);
    }

    private void follow(Doc doc, boolean again) {
        Path want = doc.single();
        List<Path> next = large && want != null ? around(doc.ordered(), want) : List.of();
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
                synchronized (this) {
                    path = wanted;
                    wanted = null;
                    real = path != null;
                    if (!real) {
                        path = ahead.poll();
                    }
                    if (path == null) {
                        running = false;
                        return;
                    }
                }
                Preview p = preview(path);
                if (real) {
                    model.previewed(path, p);
                }
            }
        } catch (RuntimeException | Error e) {
            synchronized (this) {
                running = false;
            }
            throw e;
        }
    }

    private Preview preview(Path path) {
        boolean big = large;
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
