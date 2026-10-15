package dev.sibarum.vexplore;

import dev.sibarum.vexplore.remote.Host;
import dev.sibarum.vexplore.remote.Remotes;
import dev.vexelray.gui.core.Gui;
import dev.vexelray.gui.core.Node;
import dev.vexelray.gui.core.WindowRegion;
import dev.vexelray.gui.core.input.ClickEvent;
import dev.vexelray.gui.core.input.InteractionState;
import dev.vexelray.gui.core.input.MenuItem;
import dev.vexelray.gui.core.input.MenuPresenter;
import sibarum.tactroller.api.MouseButton;
import dev.vexelray.gui.core.layout.LayoutEnums.AlignItems;
import dev.vexelray.gui.core.layout.LayoutEnums.Justify;
import dev.vexelray.gui.core.layout.Length;
import dev.vexelray.gui.core.layout.Rect;
import dev.vexelray.gui.core.style.Role;
import dev.vexelray.gui.icons.Icon;
import dev.vexelray.gui.icons.IconView;
import dev.vexelray.gui.widget.ContextMenu;
import dev.vexelray.gui.widget.Tooltip;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The title bar's Open button and the menu it drops: the ways into a folder that are not already on screen. A
 * folder from the dialog, a new window on this one, the person's home and Downloads, the hosts in
 * {@code ~/.ssh/config} and those added here, and New host for one that is neither. The tree holds the same places,
 * but scrolled away or collapsed; this is one click from anywhere.
 *
 * <p><b>It is the window's context menu, opened by a left click.</b> Core has no "menu from a button": menus open on
 * a right click, by dispatch. So this asks the installed {@link MenuPresenter} directly, with a click made up at the
 * button's bottom-left corner, which is where a drop-down hangs. The menu is built when it opens, so a host added to
 * the config since appears, and nothing here is held between openings.
 *
 * <p>Everything it opens goes where the tree's clicks go ({@code go}), which with two folders is the active one.
 */
final class OpenMenu {

    private final Gui gui;
    private final Node button;
    private final Consumer<Path> go;
    private final Supplier<Path> shown;
    private final Actor actor;
    private final Opener opener;
    private final Hosts hosts;
    /** Turn the rail into the Connect panel, for a host the menu does not list. */
    private final Runnable newHost;
    /** The button's box, in the pixels a click is given in; the menu hangs from its bottom-left. */
    private volatile Rect box = new Rect(0f, 0f, 0f, 0f);

    /**
     * @param go    open a folder where the tree would
     * @param shown the folder the window is showing now, or null before the first
     */
    OpenMenu(Gui gui, Consumer<Path> go, Supplier<Path> shown, Actor actor, Opener opener, Hosts hosts,
             Runnable newHost) {
        this.gui = gui;
        this.go = go;
        this.shown = shown;
        this.actor = actor;
        this.opener = opener;
        this.hosts = hosts;
        this.newHost = newHost;
        ContextMenu.presentOn(gui);
        // Caption-sized, hover-shaded and declared interactive, as the bar's own buttons are: on a caption, a click
        // that is not claimed starts a window drag.
        this.button = gui.row().role("open").width(Length.dp(36)).height(Length.dp(32))
                .justify(Justify.CENTER).alignItems(AlignItems.CENTER)
                .background(gui.theme().color(Role.NONE)).scroll(false, false)
                .windowRegion(WindowRegion.INTERACTIVE)
                .children(IconView.of(gui, Icon.OPEN).size(Length.dp(16)).tint(Role.INK).node());
        gui.onResizeUi(button, laid -> box = laid.rect());
        gui.onClick(button, this::open);
        gui.onState(button, state -> button.background(
                gui.theme().color(state == InteractionState.NORMAL ? Role.NONE : Role.RAISED)));
        new Tooltip(gui).attach(button, "Open");
        gui.landmark(Landmarks.OPEN, button);
    }

    Node node() {
        return button;
    }

    /** Drop the menu from the button. Runs on the handler lane, where reading the config file is allowed. */
    void open() {
        MenuPresenter menus = gui.menus();
        if (menus == null) {
            return;
        }
        Rect at = box;
        menus.present(new ClickEvent(button.id(), MouseButton.LEFT, at.x(), at.y() + at.h()), items());
    }

    /** What the menu offers now. */
    List<MenuItem> items() {
        List<MenuItem> out = new ArrayList<>();
        Path here = shown.get();
        out.add(MenuItem.of(Icon.FOLDER, "Open folder…", () -> actor.askFolder(here, go)));
        out.add(opener.windowInstalled()
                ? MenuItem.of(Icon.POP_OUT, "New window here", () -> opener.window(here))
                : MenuItem.disabled(Icon.POP_OUT, "New window here (Vexplore is not installed)"));
        out.add(MenuItem.SEPARATOR);

        Path home = Startup.home();
        out.add(MenuItem.of(Icon.HOME, "Home", () -> go.accept(home)));
        for (String name : List.of("Desktop", "Documents", "Downloads")) {
            Path p = home.resolve(name);
            if (Files.isDirectory(p)) {
                out.add(MenuItem.of(Icon.FOLDER, name, () -> go.accept(p)));
            }
        }
        out.add(MenuItem.SEPARATOR);

        // Naming a host connects to nothing; choosing it does, and opens its home folder. New host is the way to one
        // the config does not name, typed into the rail.
        for (Host h : hosts.all()) {
            out.add(MenuItem.of(Icon.TERMINAL, "Connect to " + h.label(), () -> go.accept(Remotes.home(h))));
        }
        out.add(MenuItem.of(Icon.PLUS, "New host…", newHost));
        return out;
    }
}
