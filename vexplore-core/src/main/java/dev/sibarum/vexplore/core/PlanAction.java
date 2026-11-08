package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Planned;
import dev.sibarum.vexplore.core.Messages.Planning;
import dev.vexelray.framework.api.Job;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * What a move or a copy would do, worked out on the offload lane because it reads the destination: the names already
 * there are what a plan is checked against, so the card can say what would be left alone before anything moves.
 * A destination that cannot be read plans nothing, every target left alone with the reason.
 */
@Job
public final class PlanAction {

    public Planned run(Planning p) {
        Set<String> taken = new HashSet<>();
        try (DirectoryStream<Path> in = Files.newDirectoryStream(p.destination())) {
            for (Path child : in) {
                taken.add(ListFolder.name(child));
            }
        } catch (IOException | RuntimeException e) {
            String why = "the destination cannot be read: " + ListFolder.problem(e);
            return new Planned(p.ticket(), new Plan(p.op(), java.util.List.of(), p.targets().stream()
                    .map(r -> new Plan.Skip(r.path(), why)).toList(), 0L));
        }
        return new Planned(p.ticket(), Plan.to(p.op(), p.targets(), p.destination(), taken));
    }
}
