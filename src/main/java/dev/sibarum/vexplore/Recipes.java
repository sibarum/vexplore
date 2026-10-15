package dev.sibarum.vexplore;

import dev.sibarum.suite.update.Updates;
import dev.sibarum.suite.view.UpdateNotice;
import dev.vexelray.codec.image.gui.Textures;
import dev.vexelray.framework.api.Configuration;
import dev.vexelray.framework.api.Provides;
import dev.vexelray.framework.core.Launch;
import dev.vexelray.framework.shell.Appearance;
import dev.vexelray.framework.shell.Apps;
import dev.vexelray.framework.shell.Dialogs;
import dev.vexelray.framework.shell.Windows;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.app.Settings;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.krono.KronoGui;
import dev.vexelray.gui.widget.TitleBar;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import sibarum.tactroller.api.Key;
import sibarum.tactroller.api.Modifier;

/**
 * What this application builds — one recipe per part — and nothing about when.
 *
 * <p>{@code VexploreAppWiring} is written by {@code vexelray-framework-processor} while this project compiles and
 * calls each method below once, in the phase its parameters put it in: a part's phase is the latest phase of
 * anything it takes. So the look and the model, which take nothing the framework builds late, exist before the
 * {@code Gui} does; {@link Browser} needs the {@code Gui} for its executors and no window; and the tree waits for
 * the {@code Gui} and its title bar.
 *
 * <p>Two things are not here, because a starter that {@link VexploreApp} names in its {@code @VexelApp} provides them:
 * the {@code Textures} the dock and the viewer show pictures through, built with the tree and bound to the window,
 * from {@code ImageStarter}; and the driving socket, from {@code AutomationStarter} (debug edition only).
 */
@Configuration
final class Recipes {

    /** The look, and the smallest window this UI is still coherent in — a floor, not the design size. */
    @Provides
    Appearance look() {
        return Appearance.of(Look.THEME, Length.rem(Vexplore.MIN_W_EM), Length.rem(Vexplore.MIN_H_EM));
    }

    /** The one authoritative state, built before the tree because every control is a view onto it. */
    @Provides
    Model model() {
        return new Model();
    }

    /** Folder navigation: the model change and the listing behind it. Needs only the {@code Gui}'s executors. */
    @Provides
    Browser browser(Gui gui, Model model) {
        return new Browser(gui, model);
    }

    /**
     * The image viewer: a named window whose tree the framework builds beside the main one, in the same look, and
     * whose window it claims once there is a device. It opens when the dock's Pop out is pressed.
     */
    @Provides
    Viewer viewer(Windows windows, KronoGui krono, Model model, Previewer previewer, Textures textures) {
        return new Viewer(windows.named(Viewer.DECLARED), krono, model, previewer, textures);
    }

    /** Where deleted files wait. Not the system recycle bin, which Java reaches only through AWT. */
    @Provides
    Actor actor(Gui gui, Model model, Browser browser, Dialogs dialogs) {
        Path trash = Path.of(System.getProperty("vexplore.trash",
                Path.of(System.getProperty("user.home"), ".vexplore", "trash").toString()));
        return new Actor(gui, model, browser, dialogs, trash);
    }

    /** Opening a file: the suite's text editor for text, the shell's open for the rest. */
    @Provides
    Opener opener(Gui gui) {
        return new Opener(gui);
    }

    /** Suggested destinations, kept current while a move or copy is chosen. */
    @Provides
    Destinator destinator(Gui gui, Model model) {
        return new Destinator(gui, model);
    }

    /** The hosts offered: the ssh config's, then those added with New host, kept in Vexplore's own settings. */
    @Provides
    Hosts hosts(Settings settings) {
        return new Hosts(settings);
    }

    /**
     * The Preview Dock's reader. A part of its own, and named by {@link #ui} though {@code Ui} never touches it,
     * because a listener has to exist before the state it listens to is seeded: the wiring builds what a recipe
     * <em>takes</em> first, so taking it is how the order is said.
     */
    @Provides
    Previewer previewer(Gui gui, Model model) {
        return new Previewer(gui, model);
    }

    /**
     * The tree. Needs the {@code Gui}, its clock, the browser and the title bar, and no window — which is what
     * lets {@link Capture} photograph it headlessly, and why the first folder is opened here rather than later: the
     * tree a capture gets should be the tree a user gets, already carrying a listing.
     */
    @Provides
    Ui ui(Gui gui, KronoGui krono, Model model, Browser browser, Opener opener, Textures textures, Viewer viewer,
          Previewer previewer, Destinator destinator, Actor actor, TitleBar titleBar, Launch launch,
          Windows windows, Hosts hosts) {
        Ui ui = new Ui(gui, krono, model, browser, opener, textures, viewer, actor, titleBar, hosts);
        // Every change to the state redraws what is derived from it, on the committing thread -- which is a
        // worker, because every control's handler is. The GUI thread never reads the model.
        model.onChange(ui::show);
        ui.show(model.doc());
        zoomShortcuts(gui);
        // Modifier keys are an intent signal, so the model hears them: Shift announces a range, Control a rule.
        gui.modifiers().onCommit(v -> model.holding(v.value().contains(Modifier.SHIFT),
                v.value().contains(Modifier.CONTROL)));
        // No Ctrl+Z here: the window binds it, and what Actor does is recorded in the window's history.
        // Last, and after the listener above: opening the first folder is a change, and a change nobody is
        // listening for is a window that opens onto an empty list.
        Startup.apply(browser, model, launch.rest());
        Startup.other().ifPresent(ui::showSecond);
        updates(gui, model, windows);
        return ui;
    }

    /**
     * Word of a newer version, in the window's top right corner, when this is the installed copy: Update and restart
     * closes Vexplore, lets the installer's updater replace it, and opens it again on the folder that was open (a
     * folder on this computer: a remote one is not an argument a new start can take).
     */
    private static void updates(Gui gui, Model model, Windows windows) {
        Updates.of(Apps.registryDir(), Vexplore.APP).ifPresent(u -> {
            UpdateNotice notice = new UpdateNotice(gui, u, () -> windows.main().close(), () -> {
                Path folder = model.doc().folder();
                return folder == null || folder.getFileSystem() != FileSystems.getDefault()
                        ? List.of() : List.of(folder.toString());
            }, "");
            gui.root().append(notice.node());
            notice.start();
        });
    }

    /**
     * Ctrl+= / Ctrl+- / Ctrl+0, and the numpad's three.
     *
     * <p>An application decision, which is why it is here: which chord zooms, or whether zooming exists at all, is
     * not something a framework should choose. <b>How far the zoom goes is</b>, and it is not stated here — that is
     * {@code Appearance.ZoomRange}, applied by the framework before the first widget.
     */
    private static void zoomShortcuts(Gui gui) {
        gui.shortcut(Key.EQUAL, gui::zoomIn, Modifier.CONTROL);
        gui.shortcut(Key.MINUS, gui::zoomOut, Modifier.CONTROL);
        gui.shortcut(Key.DIGIT_0, gui::resetZoom, Modifier.CONTROL);
        gui.shortcut(Key.NUMPAD_ADD, gui::zoomIn, Modifier.CONTROL);
        gui.shortcut(Key.NUMPAD_SUBTRACT, gui::zoomOut, Modifier.CONTROL);
        gui.shortcut(Key.NUMPAD_0, gui::resetZoom, Modifier.CONTROL);
    }
}
