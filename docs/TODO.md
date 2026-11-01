# TODO

Work on **Vexplore** that is known about and not done. Distinct from
[framework-notes.md](framework-notes.md), which is about the framework rather than about this application —
if the fix belongs upstream, it goes there instead.

Keep an entry short enough that it does not need editing, and delete it when it is done rather than ticking it.

## Next

The v0.2 rebuild starts from the template, and 0.1's features come back one slice at a time, in the order in
[rebuild.md](rebuild.md). M0 is done.

- [ ] **M1: browsing, read only**, in the shape [shape.md](shape.md) sketches: the places tree, the list, the
      breadcrumb's and the status line's counts, Enter to open.
- [ ] **M2 to M7** and packaging, as [rebuild.md](rebuild.md) lists them.

## Later

- [ ] Automation coverage: `--automation=0` (or `-Dautomation=0`) opens a driving socket on a free port and prints it, and the landmarks in `Landmarks.java` are
      already the names a script would use. One scripted run through the main path is worth more than several
      unit tests of the view.
