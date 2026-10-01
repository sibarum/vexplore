package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Folders;
import dev.vexelray.gui.core.Gui;

import java.nio.file.Path;

/**
 * Moving between folders: the model change, and the listing that follows it.
 *
 * <p>The listing is disk work, so it runs on {@link Gui#offload()} and lands through {@link Model#listed}, which
 * drops it if the user has already gone somewhere else. That last check is the whole reason this is a class and not
 * a lambda in {@code Ui}: two quick navigations produce two listings, and only the later one may be drawn.
 */
final class Browser {

    private final Gui gui;
    private final Model model;

    Browser(Gui gui, Model model) {
        this.gui = gui;
        this.model = model;
    }

    /** Show {@code folder}. A no-op if it is already the one being shown. */
    void go(Path folder) {
        if (!model.navigate(folder)) {
            return;
        }
        Runnable listing = () -> model.listed(folder, Folders.list(folder));
        if (Startup.synchronous()) {
            listing.run();
        } else {
            gui.offload().execute(listing);
        }
    }
}
