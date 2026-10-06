package dev.sibarum.vexplore;

import dev.vexelray.framework.api.VexelApp;
import dev.vexelray.framework.automation.AutomationStarter;

/**
 * The debug edition's application declaration: {@link Vexplore}'s facts plus {@link AutomationStarter}, the driving
 * socket (off unless {@code --automation} or {@code -Dautomation} asks, and loopback-only when it is, because it
 * hands whoever reaches it full control of the application's input).
 *
 * <p>The processor generates {@code VexploreAppWiring} from this. The release edition
 * ({@code src/edition-release}) declares the same application with no starters and no dependency on the
 * automation module, so the release binary links no socket at all. The pom chooses which one is compiled
 * (property {@code edition.src}); keep the two annotations identical apart from {@code starters}.
 */
@VexelApp(name = Vexplore.APP, title = Vexplore.TITLE, width = Vexplore.W, height = Vexplore.H,
        starters = AutomationStarter.class)
final class VexploreApp {

    private VexploreApp() {
    }
}
