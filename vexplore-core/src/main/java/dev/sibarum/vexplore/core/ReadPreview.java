package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Previewed;
import dev.sibarum.vexplore.core.Messages.Previewing;
import dev.vexelray.framework.api.Job;

/** One file's preview for the dock, on the offload lane: a bounded read of its first bytes ({@link Previews}). */
@Job
public final class ReadPreview {

    public Previewed run(Previewing p) {
        return new Previewed(p.ticket(), Previews.of(p.path()));
    }
}
