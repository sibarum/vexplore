package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Previews;
import dev.vexelray.gui.core.Gui;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Keeps the Preview Dock's contents matching the selection.
 *
 * <p>Reading the start of a file is disk work, so it runs on {@link Gui#offload()} and lands through
 * {@link Model#previewed}, which drops it if the selection has moved on. It listens to the model rather than being
 * called by the list, so a selection that arrives from anywhere (a click, a restored session, a script) is
 * followed the same way, and it asks for a preview once per newly selected file, not once per change.
 */
final class Previewer {

    private final Gui gui;
    private final Model model;
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
        Runnable read = () -> model.previewed(want, Previews.of(want));
        if (Startup.synchronous()) {
            read.run();
        } else {
            gui.offload().execute(read);
        }
    }
}
