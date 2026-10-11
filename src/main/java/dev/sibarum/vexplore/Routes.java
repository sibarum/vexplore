package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Previews;
import dev.vexelray.codec.image.Pictures;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * What "open" means for an entry, decided in one place: a double-click, Enter and the menu's Open all come here, and
 * the menu's label is the route's own, so what a row says it will do and what it does cannot drift apart.
 *
 * <p><b>The first route that takes the entry and is ready wins</b>, and the order is the policy: a folder is browsed
 * into, a picture goes to Pix, text to Vex, and everything else to the shell's own open, exactly as Explorer would do
 * it. A route whose application is not installed is not ready, so its entries fall to the next; so is one whose
 * application is installed but would not start. The last route takes everything and is always ready, so every
 * entry opens somewhere. A picture is asked first because the name decides it and costs nothing, where "is it text"
 * reads the start of the file (the Preview Dock's answer, {@link Previews#isText}).
 */
final class Routes {

    static final String IN_PIX = "Open in Pix";
    static final String IN_VEX = "Open in Vex";
    static final String DEFAULT = "Open";

    /**
     * One rule.
     *
     * @param label what the menu calls it
     * @param takes which entries it is for; may read the disk
     * @param ready whether it can run now, which is whether its application is installed; may read the disk
     * @param open  open the entry, and say whether it started; false falls through to the next route
     */
    record Route(String label, Predicate<Entry> takes, BooleanSupplier ready, Predicate<Path> open) {

        boolean fits(Entry e) {
            return takes.test(e) && ready.getAsBoolean();
        }
    }

    private final Executor lane;
    private final List<Route> routes;

    /**
     * {@code routes} in the order they are asked, then {@code fallback}, which is made the route that takes
     * everything: a list that could end without one is a list some entry could fall off.
     */
    Routes(Executor lane, List<Route> routes, Consumer<Path> fallback) {
        this.lane = lane;
        this.routes = java.util.stream.Stream.concat(routes.stream(), java.util.stream.Stream.of(
                new Route(DEFAULT, e -> true, () -> true, p -> {
                    fallback.accept(p);
                    return true;
                }))).toList();
    }

    /** Vexplore's own: browse a folder, Pix for a picture, Vex for text, the shell for the rest. */
    static Routes standard(Executor lane, Opener opener, Consumer<Path> browse) {
        return new Routes(lane, List.of(
                new Route(DEFAULT, Entry::folder, () -> true, p -> {
                    browse.accept(p);
                    return true;
                }),
                new Route(IN_PIX, e -> Pictures.named(e.path()), opener::pixInstalled, opener::pix),
                new Route(IN_VEX, e -> Previews.isText(e.path()), opener::editorInstalled, opener::editor)),
                Opener::shell);
    }

    /** The route Open would take for {@code e} now. Reads the disk, so not on the frame loop. */
    Route pick(Entry e) {
        for (Route r : routes) {
            if (r.fits(e)) {
                return r;
            }
        }
        return fallback();
    }

    /** The route that takes everything: the shell's own open. */
    Route fallback() {
        return routes.get(routes.size() - 1);
    }

    /** Open {@code e} by the first route that fits it and starts, off the caller's thread. */
    void open(Entry e) {
        lane.execute(() -> {
            for (Route r : routes) {
                if (r.fits(e) && r.open().test(e.path())) {
                    return;
                }
            }
        });
    }

    /** Open {@code e} by the shell's own open, whatever the other routes would say. */
    void openByDefault(Entry e) {
        lane.execute(() -> fallback().open().test(e.path()));
    }
}
