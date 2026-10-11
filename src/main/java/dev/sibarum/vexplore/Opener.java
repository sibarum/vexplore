package dev.sibarum.vexplore;

import dev.vexelray.framework.shell.Apps;
import dev.vexelray.gui.core.Gui;
import sibarum.probe.Log;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Opening a file, which Vexplore does by handing it to something else. Which of these a file gets is
 * {@link Routes}' to decide; this is only how each one is started.
 *
 * <p>The suite's applications are started as new processes, each with a window of its own, and found through the
 * application's install record ({@link Apps}): the text editor ({@code text-editor <file>}) and Pix
 * ({@code pix <file>}). The path goes as one argument, absolute, so it is never read as an option. The rest goes to
 * the shell's own open, exactly as Explorer would do it.
 *
 * <p>Each reads the disk. {@link #edit} and {@link #view} go to {@link Gui#offload()} themselves; {@link #pix},
 * {@link #editor} and {@link #shell} run where they are called, which is the lane {@link Routes} opens on.
 */
final class Opener {

    /** The editor's install id, which is also its command; agreed between the two apps, not by the framework. */
    static final String EDITOR = "text-editor";

    /** Pix's install id, which is also its command. */
    static final String PIX = "pix";

    /** MainFrame's install id, which is also its command. */
    static final String MAINFRAME = "mainframe";

    private static final Log LOG = Log.of("vexplore.open");

    private final Gui gui;

    Opener(Gui gui) {
        this.gui = gui;
    }

    /** Whether the text editor is installed, which is what decides the dock's button. Reads one small file. */
    boolean editorInstalled() {
        return Apps.find(EDITOR).isPresent();
    }

    /** Whether Pix is installed, which is what decides where a picture is viewed. Reads one small file. */
    boolean pixInstalled() {
        return Apps.find(PIX).isPresent();
    }

    /** Open {@code file} in Pix; false if it is not installed or did not start. */
    boolean pix(Path file) {
        return spawn(PIX, file);
    }

    /** Open {@code file} in the text editor; false if it is not installed or did not start. */
    boolean editor(Path file) {
        return spawn(EDITOR, file);
    }

    /** Open {@code file} in the text editor, whatever it holds. Nothing happens when the editor is not installed. */
    void edit(Path file) {
        gui.offload().execute(() -> {
            if (!spawn(EDITOR, file)) {
                LOG.warn("the text editor is not installed; {} was not opened", file);
            }
        });
    }

    /**
     * View the picture {@code file}: in Pix when it is installed, and by running {@code fallback} when it is not, which
     * is Vexplore's own viewer. The fallback runs on the offload lane.
     */
    void view(Path file, Runnable fallback) {
        gui.offload().execute(() -> {
            if (!spawn(PIX, file)) {
                fallback.run();
            }
        });
    }

    /** Whether MainFrame is installed, which is what enables Open MainFrame Here. Reads one small file. */
    boolean mainframeInstalled() {
        return Apps.find(MAINFRAME).isPresent();
    }

    /**
     * Open MainFrame in {@code at}'s folder: in {@code at} itself if it is one, and otherwise in the folder holding it,
     * with its name on the input line.
     *
     * <p><b>Vexplore passes a path and never console text.</b> Quoting belongs to MainFrame, which alone knows which
     * shell the tab runs and refuses a name it cannot prove inert there ({@code --insert-path}). Each argument is one
     * argv entry with no shell between, nothing is positional (MainFrame would run that as its shell), and both
     * paths are absolute.
     */
    void mainframe(Path at, boolean folder) {
        gui.offload().execute(() -> {
            Path abs = at.toAbsolutePath().normalize();
            Path dir = folder ? abs : abs.getParent();
            if (dir == null) {
                return;
            }
            try {
                boolean started = folder
                        ? Apps.spawn(MAINFRAME, "--dir=" + dir).isPresent()
                        : Apps.spawn(MAINFRAME, "--dir=" + dir, "--insert-path=" + abs).isPresent();
                if (!started) {
                    LOG.warn("MainFrame is not installed; it was not opened at {}", abs);
                }
            } catch (IOException e) {
                LOG.warn("could not start MainFrame at {}", abs, e);
            }
        });
    }

    /** Start the application installed as {@code id} on {@code file}; false if it is not installed or did not start. */
    private static boolean spawn(String id, Path file) {
        try {
            return Apps.spawn(id, file.toAbsolutePath().toString()).isPresent();
        } catch (IOException e) {
            LOG.warn("could not start {} for {}", id, file, e);
            return false;
        }
    }

    /**
     * The shell's own open: {@code explorer.exe <file>} is what a double-click in Explorer does, association and
     * all. Started directly, so a path with spaces or an ampersand is one argument and never a command line.
     */
    static void shell(Path file) {
        try {
            new ProcessBuilder("explorer.exe", file.toAbsolutePath().toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            LOG.warn("could not open {}", file, e);
        }
    }
}
