package dev.sibarum.vexplore;

import dev.sibarum.vexplore.remote.Remotes;
import dev.vexelray.framework.shell.Apps;
import dev.vexelray.gui.core.Gui;
import sibarum.probe.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Opening a file, which Vexplore does by handing it to something else. Which of these a file gets is
 * {@link Routes}' to decide; this is only how each one is started.
 *
 * <p>The suite's applications are started as new processes, each with a window of its own, and found through the
 * application's install record ({@link Apps}): the text editor ({@code text-editor <file>}) and Pix
 * ({@code pix <file>}). The path goes as one argument, absolute, so it is never read as an option. The rest goes to
 * the shell's own open, exactly as Explorer would do it.
 *
 * <p>Each reads the disk. {@link #edit}, {@link #play} and {@link #view} go to {@link Gui#offload()} themselves; {@link #pix},
 * {@link #editor} and {@link #shell} run where they are called, which is the lane {@link Routes} opens on.
 *
 * <p>A remote file is downloaded first ({@link Remotes#local}) and the copy is what opens: every application here
 * wants a file on this disk. The copy is a copy, so saving it in the editor does not change the server.
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

    /** Open {@code file} in Pix, a picture or a video. Nothing happens when Pix is not installed. */
    void play(Path file) {
        gui.offload().execute(() -> {
            if (!spawn(PIX, file)) {
                LOG.warn("Pix is not installed; {} was not opened", file);
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

    /** Whether an installed Vexplore can be started for New window. Reads one small file. */
    boolean windowInstalled() {
        return Apps.find(Vexplore.APP).isPresent();
    }

    /**
     * Another Vexplore, a process and a window of its own, opened on {@code folder}: its path, or for a remote folder
     * its {@code sftp://} address, which {@code Startup} reads the same way. On home when {@code folder} is null.
     */
    void window(Path folder) {
        gui.offload().execute(() -> {
            try {
                boolean started = folder == null ? Apps.spawn(Vexplore.APP).isPresent()
                        : Apps.spawn(Vexplore.APP, Remotes.is(folder) ? Remotes.address(folder)
                        : folder.toAbsolutePath().toString()).isPresent();
                if (!started) {
                    LOG.warn("Vexplore is not installed; no new window was opened");
                }
            } catch (IOException e) {
                LOG.warn("could not start another Vexplore at {}", folder, e);
            }
        });
    }

    /** Start the application installed as {@code id} on {@code file}; false if it is not installed or did not start. */
    private static boolean spawn(String id, Path file) {
        try {
            if (Remotes.is(file) && Apps.find(id).isEmpty()) {
                return false;   // asked before downloading, so a missing app costs no transfer
            }
            return Apps.spawn(id, Remotes.local(file).toAbsolutePath().toString()).isPresent();
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
            new ProcessBuilder("explorer.exe", Remotes.local(file).toAbsolutePath().toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            LOG.warn("could not open {}", file, e);
        }
    }

    /**
     * Windows Explorer at {@code path}: a folder opens as itself, a file opens its folder with it selected.
     * {@code /select,} and the path go as two arguments, because Explorer does not read a quoted
     * {@code "/select,C:\a b"}, which is what one argument with a space in it becomes.
     */
    static void explorer(Path path, boolean folder) {
        String where = path.toAbsolutePath().toString();
        List<String> command = folder ? List.of("explorer.exe", where) : List.of("explorer.exe", "/select,", where);
        try {
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            LOG.warn("could not show {} in Explorer", path, e);
        }
    }
}
