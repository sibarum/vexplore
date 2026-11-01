# TODO

Work on **Vexplore** that is known about and not done. Distinct from
[framework-notes.md](framework-notes.md), which is about the framework rather than about this application —
if the fix belongs upstream, it goes there instead.

Keep an entry short enough that it does not need editing, and delete it when it is done rather than ticking it.

## Next

The v0.2 rebuild starts from the template, and 0.1's features come back one slice at a time, in the order in
[rebuild.md](rebuild.md). Its questions for the author come first.

- [ ] **M0: the template becomes an explorer.** The editor deleted, the handoff's regions in place, `Look.java`'s
      palette anchors on the handoff's colours, measured in Oklab (see the note in that file about which authored
      colours the construction can reproduce and which have to be declared).
- [ ] **M1 to M8** and packaging, as [rebuild.md](rebuild.md) lists them.

## Later

- [ ] Automation coverage: `--automation=0` (or `-Dautomation=0`) opens a driving socket on a free port and prints it, and the landmarks in `Landmarks.java` are
      already the names a script would use. One scripted run through the main path is worth more than several
      unit tests of the view.
