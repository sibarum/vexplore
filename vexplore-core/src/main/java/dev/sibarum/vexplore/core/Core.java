package dev.sibarum.vexplore.core;

import dev.vexelray.framework.api.Configuration;

/**
 * What this module is made of: the {@link Explorer}, the jobs that touch the disk, and {@link RunAction}, the source
 * that carries out what the rail offers.
 *
 * <p>The annotation processor sees only the sources of the module it is compiling, so when {@code -gui} compiles,
 * everything here is a class file it would never construct. This list is how it hears of them: {@code -gui}'s
 * {@code VexploreApp} names this class among its {@code starters}, and the generated wiring builds what is listed
 * here as if it were its own, and checks every message between them. A new component or job in this module goes on
 * this list, or nothing builds it.
 */
@Configuration(components = Explorer.class,
        jobs = {ListFolder.class, ListPlaces.class, ListLevel.class, FindPath.class, OpenFile.class, PlanAction.class,
                ReadPreview.class, SuggestDestinations.class, ReadSession.class, WriteSession.class},
        sources = RunAction.class)
public final class Core {
}
