package dev.sibarum.vexplore;

import dev.sibarum.suite.pictures.Pictures;
import dev.sibarum.vexplore.files.Previews;
import dev.vexelray.framework.shell.Apps;
import dev.vexelray.gui.core.Gui;
import sibarum.probe.Log;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Opening a file, which Vexplore does by handing it to something else.
 *
 * <p>Each goes to the suite's application for it, started as a new process with a window of its own and found
 * through that application's install record ({@link Apps}): text and source to the text editor ({@code text-editor
 * <file>}), pictures to Pix ({@code pix <file>}). Everything else, and anything whose application is not installed,
 * goes to the shell's own open, exactly as Explorer would do it. "Is it text" is the Preview Dock's answer
 * ({@link Previews}), so a file the dock shows as text is a file the editor gets; "is it a picture" is the name
 * ({@link Pictures#named}), which is what Pix steps through its folder by.
 *
 * <p>Each reads the disk, so each runs on {@link Gui#offload()}.
 */
final class Opener {

    /** The editor's install id, which is also its command; agreed between the two apps, not by the framework. */
    static final String EDITOR = "text-editor";

    /** Pix's install id, which is also its command. */
    static final String PIX = "pix";

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

    /** Open {@code file}: the editor for text, Pix for a picture, the shell's open for the rest. */
    void open(Path file) {
        gui.offload().execute(() -> {
            if (Pictures.named(file) && spawn(PIX, file)) {
                return;
            }
            if (Previews.isText(file) && spawn(EDITOR, file)) {
                return;
            }
            shell(file);
        });
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
    private static void shell(Path file) {
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
