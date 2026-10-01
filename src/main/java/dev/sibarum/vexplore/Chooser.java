package dev.sibarum.vexplore;

import dev.vexelray.gui.core.app.GuiApp;
import dev.vexelray.gui.nfd.FileDialog;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The folder dialog: the fallback for a destination the rail did not suggest.
 *
 * <p>It exists in the tree's phase and is given its window later, because the dialog needs the window's handle and
 * the window does not exist until after the tree has been built. A part that took {@code GuiApp} directly would put
 * {@code Ui} in the window's phase and a headless capture could no longer build it; this is the late-bound shape the
 * framework README shows for a clipboard. {@link Recipes#chooserBinding} binds it.
 *
 * <p>Call {@link #pick} from a worker, never the frame loop: it posts the dialog to the GUI thread, where its
 * contract requires it to run, and waits for the answer.
 */
final class Chooser {

    private volatile GuiApp app;

    void bind(GuiApp app) {
        this.app = app;
    }

    /** Ask for a folder, starting at {@code start}. Empty if cancelled, or if there is no window to ask from. */
    Optional<Path> pick(Path start) {
        GuiApp window = app;
        if (window == null) {
            return Optional.empty();
        }
        CompletableFuture<Optional<Path>> answer = new CompletableFuture<>();
        window.post(() -> {
            try {
                answer.complete(FileDialog.pickFolder(window.windowHandle(), start));
            } catch (RuntimeException | LinkageError e) {
                answer.complete(Optional.empty());
            }
        });
        try {
            return answer.get(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            return Optional.empty();
        }
    }
}
