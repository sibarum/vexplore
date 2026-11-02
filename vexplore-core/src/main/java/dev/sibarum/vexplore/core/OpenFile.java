package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Start;
import dev.sibarum.vexplore.core.Messages.Started;
import dev.vexelray.framework.api.Job;
import dev.vexelray.os.NativePlatform;

/**
 * Open a file as a double click in the desktop's own file manager would, association and all. The opener is the
 * platform's ({@link NativePlatform#open}: {@code explorer.exe} on Windows, {@code xdg-open} on Linux), since which
 * program that is differs by OS and the stack keeps every such difference in an OS module. Not waited for: what the
 * file opens in is its own program, and its life is not this application's.
 *
 * <p>The engine's seam, reached past the framework, whose own use of it (the About window's links) is not public
 * (framework-notes FN-4). Named for what it does rather than {@code Launch} as docs/shape.md had it, which is the
 * framework's command line ({@code dev.vexelray.framework.core.Launch}). Rasterware and VexelRay IDE come with M4.
 */
@Job
public final class OpenFile {

    public Started run(Start s) {
        try {
            if (NativePlatform.current().open(s.file().toAbsolutePath().toString())) {
                return new Started(s.file(), null);
            }
            return new Started(s.file(), "this desktop has no opener, or it would not start");
        } catch (RuntimeException e) {
            String m = e.getMessage();
            return new Started(s.file(), m == null || m.isBlank() ? e.getClass().getSimpleName() : m);
        }
    }
}
