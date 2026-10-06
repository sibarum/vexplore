package dev.sibarum.vexplore;

import dev.vexelray.framework.shell.VexelApplication;

import java.io.IOException;

/**
 * A file explorer that suggests instead of asking.
 *
 * <h2>What this class is</h2>
 *
 * <p>The entry point, and the constants the rest of this package reads. That is all it is, and the reason is
 * worth knowing before anything else here: <b>the application edge is the framework's now.</b> Opening an
 * input backend and settling its coordinate space, installing a clipboard, remembering where the window was,
 * attaching a clock, wiring the frame loop with its wakes and its pacing, installing the dialogs, parsing the
 * command line and closing everything in the right order — this file used to be three hundred lines of exactly
 * that, near-identically to every other application on this stack.
 *
 * <p>What this application actually builds is in {@link Recipes}, one method per part. {@code VexploreAppWiring},
 * which builds those parts in order, is generated from them and from the annotation on this class while the project
 * compiles — so the facts below are stated once, here, and the wiring reads them. Everything above that is in
 * {@link Ui}; everything the application <em>knows</em> is in {@link Model}. This class holds no state of its own,
 * and that is a rule worth keeping: the moment the edge starts remembering things, there are two places a value can
 * live.
 *
 * <pre>
 * Vexplore                     the window, interactively
 * Vexplore &lt;frames&gt;            run a fixed number of frames and quit (a script, not a session)
 * Vexplore --key=value         override a setting for this launch
 * Vexplore --capture out.png   headless PNG; see {@link Capture}
 * </pre>
 *
 * <p>A misspelled flag is refused by name with the alternatives listed, rather than a stack trace before any
 * window. Needs {@code --enable-native-access=ALL-UNNAMED}.
 *
 * <p><b>The {@code @VexelApp} declaration is {@link VexploreApp}, and there are two of it</b>: the debug edition
 * ({@code src/edition-debug}) names {@code AutomationStarter}, the driving socket; the release edition
 * ({@code src/edition-release}) names no starters and its build has no automation module at all. This class
 * holds the facts both read.
 */
public final class Vexplore {

    /** The application's own name, which is what its settings directory is called. Stable across releases. */
    static final String APP = "vexplore";

    /** The window's title. */
    static final String TITLE = "Vexplore";

    /** Window size on a first run, in the engine's logical coordinates. */
    static final int W = 1440;
    static final int H = 900;

    /**
     * The smallest this UI is still coherent at, in root ems — a floor, not the design size.
     *
     * <p>Named here rather than written at each use because it is read from two places that have to agree:
     * {@link Recipes#look} declares it to the framework, and a capture of the tree at exactly the
     * minimum is the picture that shows a panel outgrowing it.
     */
    static final float MIN_W_EM = 24;
    static final float MIN_H_EM = 16;

    /**
     * Entry point.
     *
     * <p>{@code --capture} is handled before the framework sees the arguments, deliberately: {@link Capture} is
     * an application-specific instrument with its own scenes and its own output paths, and routing it through
     * the framework would read the scene name as something else. An application with its own capture tooling
     * intercepts its own flag first.
     */
    public static void main(String[] args) throws IOException {
        String[] cleaned = java.util.Arrays.stream(args).filter(s -> !s.isBlank()).toArray(String[]::new);
        if (cleaned.length >= 1 && cleaned[0].equals("--capture")) {
            Capture.run(cleaned);
            return;
        }
        VexelApplication.run(new VexploreAppWiring(), cleaned);
    }

    private Vexplore() {
    }
}
