package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Dest;
import dev.sibarum.vexplore.core.Messages.Planning;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Where a move or a copy would go: the places suggested, the one picked, and the plan for going there. Part of the
 * explorer's {@link Work}, so the destination picked is kept from one folder to the next, as a person tidying a run
 * of folders into one place would want.
 *
 * <p>Both questions are jobs' ({@link SuggestDestinations}, {@link PlanAction}), so each carries a ticket, and an
 * answer to a question since replaced is dropped, as a listing is.
 *
 * @param asked       the ticket of the last places asked for
 * @param kind        the kind they were asked for, or none yet
 * @param near        the folder they were asked near, or none yet
 * @param offered     the places offered, best first
 * @param destination the folder picked, or none yet
 * @param sent        the last plan asked for, which a new ask is compared with
 * @param plan        its answer, or none while it is awaited
 */
record Aim(long asked, Optional<Kind> kind, Optional<Path> near, List<Dest> offered, Optional<Path> destination,
           Optional<Planning> sent, Optional<Plan> plan) {

    static final Aim NONE = new Aim(0L, Optional.empty(), Optional.empty(), List.of(), Optional.empty(),
            Optional.empty(), Optional.empty());

    Aim {
        offered = List.copyOf(offered);
    }

    /** Places for {@code value} near {@code folder} are asked for, as ticket {@code asked + 1}. */
    Aim asking(Kind value, Path folder) {
        return new Aim(asked + 1, Optional.of(value), Optional.of(folder), List.of(), destination, sent, plan);
    }

    Aim offering(List<Dest> places) {
        return new Aim(asked, kind, near, places, destination, sent, plan);
    }

    Aim to(Path folder) {
        return new Aim(asked, kind, near, offered, Optional.of(folder), sent, plan);
    }

    /** A plan is asked for; the last one no longer says what the card states. */
    Aim planning(Planning value) {
        return new Aim(asked, kind, near, offered, destination, Optional.of(value), Optional.empty());
    }

    Aim planned(Plan value) {
        return new Aim(asked, kind, near, offered, destination, sent, Optional.of(value));
    }

    /** The next plan's ticket. */
    long nextPlan() {
        return sent.map(Planning::ticket).orElse(0L) + 1;
    }

    /** Whether {@code want} asks what the last plan asked, its ticket aside. */
    boolean asks(Planning want) {
        return sent.isPresent() && sent.get().op() == want.op() && sent.get().targets().equals(want.targets())
                && sent.get().destination().equals(want.destination());
    }
}
