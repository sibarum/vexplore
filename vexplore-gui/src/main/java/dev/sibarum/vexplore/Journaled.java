package dev.sibarum.vexplore;

import dev.sibarum.vexplore.core.Messages.Redo;
import dev.sibarum.vexplore.core.Messages.Undo;
import dev.vexelray.gui.core.edit.Change;
import sibarum.atchung.Sender;

/**
 * An entry of the explorer's journal, in the window's history: applied, it asks the explorer to take the entry back
 * ({@link Undo}) or do it again ({@link Redo}), and returns the change that asks the other. The history's ruled
 * pattern (vexelray-gui's {@code ValueWidgetUndoTest}): its changes send messages, and hold only the entry's number,
 * so what an entry did, and whether it can still be taken back, stays the explorer's.
 *
 * @param entry the journal entry
 * @param undo  whether applying it takes the entry back, rather than doing it again
 */
record Journaled(long entry, boolean undo, Sender<Undo> undos, Sender<Redo> redos) implements Change {

    @Override
    public Change apply() {
        if (undo) {
            undos.send(new Undo(entry));
        } else {
            redos.send(new Redo(entry));
        }
        return new Journaled(entry, !undo, undos, redos);
    }
}
