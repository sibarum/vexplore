package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Entry;
import dev.sibarum.vexplore.files.Folders;
import dev.sibarum.vexplore.suggest.Suggestions;
import dev.sibarum.vexplore.suggest.Suggestions.Act;
import dev.sibarum.vexplore.suggest.Suggestions.Scope;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where Vexplore opens, and what is already picked when it does.
 *
 * <p>Read from system properties, {@code -Dvexplore.folder=...}, so the same four values start a real session where
 * the user left off and a headless capture in a known state. The framework's {@code Wiring} keeps its parts to
 * itself, which leaves a capture no way to reach the model and drive it; this is the seam that stands in for that
 * (see {@code docs/TODO.md}, <em>a capture cannot reach the parts</em>).
 *
 * <ul>
 *   <li>{@code vexplore.folder} — the folder to open; the user's Downloads, or their home, when absent;</li>
 *   <li>{@code vexplore.select} — file names to select once the listing has arrived, comma separated;</li>
 *   <li>{@code vexplore.scope}, {@code vexplore.act} — a {@link Scope} and an {@link Act} to have picked;</li>

 *   <li>{@code vexplore.hold} — {@code shift} or {@code control}: act as though that key is down;</li>
 *   <li>{@code vexplore.sync} — list on the calling thread, so a capture is not taken before the folder is read.</li>
 * </ul>
 */
final class Startup {

    private Startup() {
    }

    static Path folder() {
        String given = System.getProperty("vexplore.folder");
        if (given != null && !given.isBlank()) {
            return Path.of(given);
        }
        Path home = Path.of(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        return Files.isDirectory(downloads) ? downloads : home;
    }

    static boolean synchronous() {
        return Boolean.getBoolean("vexplore.sync");
    }

    /** Open the starting folder and apply whatever selection and picks were asked for. */
    static void apply(Browser browser, Model model) {
        browser.go(folder());
        String select = System.getProperty("vexplore.select");
        if (select == null || select.isBlank()) {
            return;
        }
        // Only meaningful once the listing is in, which the synchronous listing guarantees and a real one
        // does not: a session that wants a selection restored asks for the sync listing.
        Set<String> names = Set.of(select.split(","));
        List<Entry> entries = model.doc().entries();
        Set<Path> paths = entries.stream().filter(e -> names.contains(e.name())).map(Entry::path)
                .collect(Collectors.toUnmodifiableSet());
        model.select(paths);
        String scope = System.getProperty("vexplore.scope");
        String act = System.getProperty("vexplore.act");
        String hold = System.getProperty("vexplore.hold", "");
        model.holding(hold.contains("shift"), hold.contains("control"));
        model.pick(p -> {
            Suggestions.Pick out = p;
            if (scope != null) {
                out = out.with(Scope.valueOf(scope));
            }
            if (act != null) {
                out = out.with(Act.valueOf(act));
            }
            return out;
        });
    }

    /** {@link Folders#list} on the calling thread — what {@code vexplore.sync} makes the browser use. */
    static List<Entry> list(Path folder) {
        return Folders.list(folder);
    }
}
