package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Progress;
import dev.sibarum.vexplore.core.Messages.Ran;
import dev.sibarum.vexplore.core.Messages.Run;
import dev.sibarum.vexplore.core.Messages.Stop;
import dev.vexelray.framework.api.Source;
import sibarum.atchung.Sender;

/**
 * Carries out a plan, forward or back, on a thread of its own: the card's button, and every undo and redo.
 *
 * <p>A {@code @Source} rather than a job because a copy of a folder of videos is long and wants a Stop, and a job
 * cannot be stopped. Its topic is unkeyed, so there is one run at a time; the explorer refuses a second while one
 * goes, rather than have the framework stop the first for it. {@link Stop} interrupts the thread, and
 * {@link Operations#execute} reads that between steps, so a stopped run still answers with what it did, and that can
 * be undone.
 *
 * <p>It sends a count after each step ({@link Progress}, a state topic, so a fast run costs the view one count per
 * frame), and answers with what was done ({@link Ran}).
 */
@Source(stop = Stop.class)
public final class RunAction {

    private final Sender<Progress> progress;

    public RunAction(Sender<Progress> progress) {
        this.progress = progress;
    }

    public Ran run(Run r) {
        int total = r.plan().count();
        return new Ran(r.run(), Operations.execute(r.plan(), r.trash(),
                n -> progress.send(new Progress(r.run(), n, total))));
    }
}
