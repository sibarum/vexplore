package dev.sibarum.vexplore.core;

import dev.sibarum.vexplore.core.Messages.Find;
import dev.sibarum.vexplore.core.Messages.Found;
import dev.sibarum.vexplore.core.Messages.Is;
import dev.vexelray.framework.api.Job;

import java.nio.file.Files;
import java.nio.file.Path;

/** What is at a path: a folder, a file, or nothing. */
@Job
public final class FindPath {

    public Found run(Find f) {
        Path p = f.path();
        Is is = Files.isDirectory(p) ? Is.FOLDER
                : Files.isRegularFile(p) ? Is.FILE
                : Is.NOWHERE;
        return new Found(p, f.why(), is);
    }
}
