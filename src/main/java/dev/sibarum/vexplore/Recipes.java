package dev.sibarum.vexplore;

import dev.vexelray.framework.api.Configuration;
import dev.vexelray.framework.api.Provides;
import dev.vexelray.framework.shell.Appearance;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.krono.KronoGui;
import dev.vexelray.gui.widget.TitleBar;
import sibarum.tactroller.api.Key;
import sibarum.tactroller.api.Modifier;

/**
 * What this application builds — one recipe per part — and nothing about when.
 *
 * <p>{@code VexploreWiring} is written by {@code vexelray-framework-processor} while this project compiles and
 * calls each method below once, in the phase its parameters put it in: a part's phase is the latest phase of
 * anything it takes. So the look and the model, which take nothing the framework builds late, exist before the
 * {@code Gui} does; {@link Browser} needs the {@code Gui} for its executors and no window; and the tree waits for
 * the {@code Gui} and its title bar.
 *
 * <p>The driving socket is not here either: it comes from {@code AutomationStarter}, which {@link Vexplore} names
 * in its {@code @VexelApp}.
 */
@Configuration
final class Recipes {

    /** The look, and the smallest window this UI is still coherent in — a floor, not the design size. */
    @Provides
    Appearance look() {
        return Appearance.of(Look.THEME, Length.em(Vexplore.MIN_W_EM), Length.em(Vexplore.MIN_H_EM));
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
    Ui ui(Gui gui, KronoGui krono, Model model, Browser browser, Previewer previewer, TitleBar titleBar) {
        Ui ui = new Ui(gui, krono, model, browser, titleBar);
        // Every change to the state redraws what is derived from it, on the committing thread -- which is a
        // worker, because every control's handler is. The GUI thread never reads the model.
        model.onChange(ui::show);
        ui.show(model.doc());
        zoomShortcuts(gui);
        // Last, and after the listener above: opening the first folder is a change, and a change nobody is
        // listening for is a window that opens onto an empty list.
        Startup.apply(browser, model);
        return ui;
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
