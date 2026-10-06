package dev.sibarum.vexplore;

import dev.sibarum.vexplore.files.Preview;
import dev.sibarum.vexplore.files.Previews;
import dev.vexelray.framework.shell.Apps;
import dev.vexelray.gui.core.Gui;
import sibarum.probe.Log;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Opening a file, which Vexplore does by handing it to something else.
 *
 * <p>Text and source go to the suite's text editor, started as {@code text-editor <file>}: a new process and a new
 * window, found through the editor's install record ({@link Apps}). Everything else, and text when the editor is
 * not installed, goes to the shell's own open, exactly as Explorer would do it. "Is it text" is the Preview Dock's
 * answer ({@link Previews}), so a file the dock shows as text is a file the editor gets.
 *
 * <p>Both read the disk, so both run on {@link Gui#offload()}.
 */
final class Opener {

    /** The editor's install id, which is also its command; agreed between the two apps, not by the framework. */
    static final String EDITOR = "text-editor";

    private static final Log LOG = Log.of("vexplore.open");

    private final Gui gui;

    Opener(Gui gui) {
        this.gui = gui;
    }

    /** Whether the text editor is installed, which is what decides the dock's button. Reads one small file. */
    boolean editorInstalled() {
        return Apps.find(EDITOR).isPresent();
    }

    /** Open {@code file}: the editor for text, the shell's open for the rest. */
    void open(Path file) {
        gui.offload().execute(() -> {
            if (Previews.of(file).tier() == Preview.Tier.TEXT && start(file)) {
                return;
            }
            shell(file);
        });
    }

    /** Open {@code file} in the text editor, whatever it holds. Nothing happens when the editor is not installed. */
    void edit(Path file) {
        gui.offload().execute(() -> {
            if (!start(file)) {
                LOG.warn("the text editor is not installed; {} was not opened", file);
            }
        });
    }

    private static boolean start(Path file) {
        try {
            return Apps.spawn(EDITOR, file.toAbsolutePath().toString()).isPresent();
        } catch (IOException e) {
            LOG.warn("could not start the text editor for {}", file, e);
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
