package dev.sibarum.vexplore;

import dev.vexelray.codec.image.gui.ImageStarter;
import dev.vexelray.framework.api.VexelApp;
import dev.vexelray.framework.automation.AutomationStarter;

/**
 * The application declaration: {@link Vexplore}'s facts, {@link ImageStarter} (the textures, bound to the window), and
 * {@link AutomationStarter}, the automation sockets, in every build, the release one included.
 *
 * <p>The sockets are off unless the launch asks ({@code --automation}, {@code VEXELRAY_AUTOMATION}) or a person
 * switches them on from the title bar's plug, and loopback-only when they are on. The plug's lamps show each one's
 * port open or not and anything connected now or earlier; {@code --automation.available=none} leaves them out of a
 * run altogether. The processor generates {@code VexploreAppWiring} from this.
 */
@VexelApp(name = Vexplore.APP, title = Vexplore.TITLE, width = Vexplore.W, height = Vexplore.H,
        icon = "/vexplore.ico", starters = {ImageStarter.class, AutomationStarter.class})
final class VexploreApp {

    private VexploreApp() {
    }
}
