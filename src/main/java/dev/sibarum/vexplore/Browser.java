package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Folders;
import dev.vexelray.gui.core.Gui;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

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

    /**
     * Show the folder {@code file} is in, with {@code file} selected once the listing has arrived — what another
     * application means by "show this file". The selection is dropped if the user has gone elsewhere by then.
     */
    void reveal(Path file) {
        Path folder = file.getParent();
        model.navigate(folder);
        Runnable listing = () -> {
            model.listed(folder, Folders.list(folder));
            if (Objects.equals(model.doc().folder(), folder)) {
                model.select(Set.of(file));
            }
        };
        if (Startup.synchronous()) {
            listing.run();
        } else {
            gui.offload().execute(listing);
        }
    }

    /** List the folder again, as after something changed it. Selection and picks start over: they described a folder that is gone. */
    void reload() {
        Path folder = model.doc().folder();
        if (folder == null) {
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
