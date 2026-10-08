package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Preview;
import dev.sibarum.vexplore.files.Previews;
import dev.sibarum.vexplore.files.Stamp;
import dev.vexelray.gui.core.Gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Keeps the Preview Dock's contents matching the selection.
 *
 * <p>Reading the start of a file is disk work, so it runs on {@link Gui#offload()} and lands through
 * {@link Model#previewed}, which drops it if the selection has moved on. It listens to the model rather than being
 * called by the list, so a selection that arrives from anywhere (a click, a restored session, a script) is
 * followed the same way, and it asks for a preview once per newly selected file, not once per change.
 *
 * <p><b>Decoded images are kept</b>, least recently used first out, so going back up a folder of photographs
 * finds them without decoding again. Only images: a text or bytes preview is one 64 KB read, cheaper to redo than to
 * hold. The key is the file's {@link Stamp}, so a file changed since it was decoded is decoded again. The GPU side
 * has its own budget, in {@link Textures}.
 */
final class Previewer {

    /** Decoded pixels kept for going back. A 1024 px still is 4 MB. */
    static final long BUDGET = 128L * 1024 * 1024;

    private final Gui gui;
    private final Model model;
    private final Lru<Stamp, Preview> images = new Lru<>(BUDGET, p -> p.picture().bytes(), p -> { });
    private Path last;

    Previewer(Gui gui, Model model) {
        this.gui = gui;
        this.model = model;
        model.onChange(this::follow);
    }

    private void follow(Doc doc) {
        Path want = doc.single();
        synchronized (this) {
            if (Objects.equals(want, last)) {
                return;
            }
            last = want;
        }
        if (want == null) {
            return;
        }
        Runnable read = () -> model.previewed(want, preview(want));
        if (Startup.synchronous()) {
            read.run();
        } else {
            gui.offload().execute(read);
        }
    }

    private Preview preview(Path path) {
        try {
            Preview kept = images.get(Stamp.of(path));
            if (kept != null) {
                return kept;
            }
        } catch (IOException e) {
            // Gone or unreadable: Previews says so in the dock.
        }
        Preview fresh = Previews.of(path);
        if (fresh.picture() != null) {
            images.put(fresh.picture().stamp(), fresh);
        }
        return fresh;
    }
}
