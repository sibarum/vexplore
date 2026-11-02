package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Mark;
import dev.sibarum.vexplore.core.Messages.Remember;
import dev.vexelray.framework.api.Job;
import dev.vexelray.gui.core.app.Settings;

import java.nio.file.Path;
import java.util.List;

/**
 * Keep the session for next time, on the offload lane: the settings store writes a file. Run only for its effect, so
 * it has no reply. The {@link Explorer} sends one only when the session differs from the last it sent.
 */
@Job
public final class WriteSession {

    private final Settings settings;

    public WriteSession(Settings settings) {
        this.settings = settings;
    }

    public void run(Remember r) {
        settings.putString(ReadSession.FOLDER, r.folder().map(Path::toString).orElse(""))
                .putString(ReadSession.MARKS, marks(r.marks())).save();
    }

    /** The marks as {@link ReadSession#marks} reads them. */
    static String marks(List<Mark> marks) {
        StringBuilder b = new StringBuilder();
        for (Mark m : marks) {
            b.append(b.isEmpty() ? "" : "\n").append(m.name().replace('\t', ' ').replace('\n', ' '));
            for (Path p : m.paths()) {
                b.append('\t').append(p);
            }
        }
        return b.toString();
    }
}
