package dev.sibarum.vexplore;

import dev.sibarum.suite.git.Status;
import dev.sibarum.vexplore.files.Entry;

/**
 * What the list's Git column says about a row, in words. Pure, so it is tested without a window.
 *
 * <p>A file says what it is: {@code modified}, {@code staged}, {@code part-staged} (staged, then changed again),
 * {@code added}, {@code untracked}, {@code ignored} or {@code conflict}. A folder that is untracked or ignored as a whole
 * says so too; otherwise it says what is beneath it, at any depth, so a change three levels down is visible from the
 * top. It gives one count, the one that most wants attention: conflicts, then changes, then untracked files. A clean
 * row says nothing, and so does every row outside a repository.
 */
final class GitColumn {

    /** How the words are drawn; {@code Ui} turns this into a colour. */
    enum Tone {
        /** Something has stopped and is waiting: a conflict. */
        ALARM,
        /** Changed and not yet staged. */
        CHANGED,
        /** Staged: ready to commit. */
        STAGED,
        /** Not part of the repository yet, or a summary of what is beneath a folder. */
        QUIET,
        /** Ignored. */
        FAINT
    }

    record Mark(String words, Tone tone) {
        static final Mark NONE = new Mark("", Tone.QUIET);
    }

    private GitColumn() {
    }

    static Mark of(Status status, Entry e) {
        if (status == null) {
            return Mark.NONE;
        }
        Status.State own = status.at(e.path());
        if (own != null) {
            return of(own);
        }
        if (!e.folder()) {
            return Mark.NONE;
        }
        Status.Inside in = status.inside(e.path());
        if (in.conflicted() > 0) {
            return new Mark(count(in.conflicted(), "conflict", "conflicts"), Tone.ALARM);
        }
        if (in.changed() > 0) {
            return new Mark(in.changed() + " changed", Tone.QUIET);
        }
        if (in.untracked() > 0) {
            return new Mark(in.untracked() + " untracked", Tone.QUIET);
        }
        return Mark.NONE;
    }

    static Mark of(Status.State s) {
        return switch (s) {
            case CONFLICTED -> new Mark("conflict", Tone.ALARM);
            case STAGED_AND_MODIFIED -> new Mark("part-staged", Tone.CHANGED);
            case MODIFIED -> new Mark("modified", Tone.CHANGED);
            case STAGED -> new Mark("staged", Tone.STAGED);
            case ADDED -> new Mark("added", Tone.STAGED);
            case UNTRACKED -> new Mark("untracked", Tone.QUIET);
            case IGNORED -> new Mark("ignored", Tone.FAINT);
        };
    }

    private static String count(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
