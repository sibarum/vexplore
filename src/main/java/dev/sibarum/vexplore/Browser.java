package dev.sibarum.vexplore;

import dev.sibarum.suite.git.Git;
import dev.sibarum.vexplore.files.Folders;
import dev.sibarum.vexplore.remote.Remotes;
import dev.vexelray.gui.core.Gui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Moving between folders: the model change, and the listing that follows it.
 *
 * <p>The listing is disk work, so it runs on {@link Gui#offload()} and lands through {@link Model#listed}, which
 * drops it if the user has already gone somewhere else. That last check is the whole reason this is a class and not
 * a lambda in {@code Ui}: two quick navigations produce two listings, and only the later one may be drawn.
 *
 * <p><b>Git is asked after the listing, as a task of its own.</b> The rows are drawn when the listing lands, and the
 * Git column fills in when git answers, through {@link Model#gitted} and the same stale-folder check. Status on a
 * large repository is the slow call, and it must never be what keeps a folder's rows from appearing.
 *
 * <p><b>A remote folder is browsed the same way</b> ({@link Remotes}): the listing is a round trip instead of a disk
 * read, which the offload lane already allows for. Two things differ. Git is not asked, since it runs here and the
 * files are there. And a host's home arrives as a path the server has not been asked about yet, so it is resolved,
 * which is what connects, before the model is told where it is; a host that cannot be reached is shown as its root,
 * empty, with ssh's own reason on the rail.
 */
final class Browser {

    private final Gui gui;
    private final Model model;
    private final Git git = Git.onPath();

    Browser(Gui gui, Model model) {
        this.gui = gui;
        this.model = model;
    }

    /** Show {@code folder}. A no-op if it is already the one being shown. */
    void go(Path folder) {
        if (Remotes.is(folder) && !folder.isAbsolute()) {
            offload(() -> {
                Path at;
                try {
                    at = Remotes.absolute(folder);
                } catch (IOException e) {
                    at = folder.getFileSystem().getPath("/");
                }
                go(at);
            });
            return;
        }
        if (!model.navigate(folder)) {
            return;
        }
        list(folder, () -> {
        });
    }

    /**
     * Show the folder {@code file} is in, with {@code file} selected once the listing has arrived — what another
     * application means by "show this file". The selection is dropped if the user has gone elsewhere by then.
     */
    void reveal(Path file) {
        Path folder = file.getParent();
        model.navigate(folder);
        list(folder, () -> {
            if (Objects.equals(model.doc().folder(), folder)) {
                model.select(Set.of(file));
            }
        });
    }

    /** List the folder again, as after something changed it. Selection and picks start over: they described a folder that is gone. */
    void reload() {
        reload(Set.of());
    }

    /**
     * List the folder again, and select whatever of {@code landed} is in it once it is listed: where a drop
     * put things. The files that arrived when it shows the folder they went into, and that folder's own row
     * when it shows the one above it. So letting go ends on something visible, not only on rows disappearing.
     */
    void reload(Set<Path> landed) {
        Path folder = model.doc().folder();
        if (folder == null) {
            return;
        }
        list(folder, () -> {
            if (!Objects.equals(model.doc().folder(), folder)) {
                return;   // gone elsewhere since: what landed is not here
            }
            Set<Path> here = new java.util.HashSet<>();
            for (var e : model.doc().entries()) {
                if (landed.contains(e.path())) {
                    here.add(e.path());
                }
            }
            if (!here.isEmpty()) {
                model.select(Set.copyOf(here));
            }
        });
    }

    /** List {@code folder}, run {@code then}, and ask git about it afterwards, each off the frame loop. */
    private void list(Path folder, Runnable then) {
        offload(() -> {
            model.listed(folder, Folders.list(folder));
            then.run();
        });
        if (!Remotes.is(folder)) {
            offload(() -> model.gitted(folder, git.status(folder).orElse(null)));
        }
    }

    private void offload(Runnable r) {
        if (Startup.synchronous()) {
            r.run();
        } else {
            gui.offload().execute(r);
        }
    }
}
