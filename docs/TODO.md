# TODO

Work on **Vexplore** that is known about and not done. Distinct from
[framework-notes.md](framework-notes.md), which is about the framework rather than about this application —
if the fix belongs upstream, it goes there instead.

Keep an entry short enough that it does not need editing, and delete it when it is done rather than ticking it.

## Next

The v0.2 rebuild starts from the template, and 0.1's features come back one slice at a time, in the order in
[rebuild.md](rebuild.md). M0 to M5 are done.

- [ ] **M6 to M8** and packaging, as [rebuild.md](rebuild.md) lists them. M8 is the redesign: one of the three
      directions on the *Vexplore Redesign* canvas, chosen first.

## Later

- [ ] Going up (Backspace, Alt+Up, a breadcrumb segment) selects nothing; an explorer selects the folder you came
      from. `Navigate` would carry the row to select, as `Shown` already does.
- [ ] The tree does not follow the folder shown: navigating from the list leaves its selection where it was.
- [ ] Places on Linux are the Windows names under home; 0.1 read the XDG user directories (`ListPlaces`).
- [ ] A list row's accessible name leaves out the file name (`row "FOLDER Today 22:16"`), so a script cannot
      find a row by name. The name cell is an icon and text in a row box; find out what the table names a row from.
- [ ] Opening a file (Enter on it) is tested in `ExplorerTest` but not driven live: it starts a real program on
      the desktop.
- [ ] Automation coverage: `--automation=0` (or `-Dautomation=0`) opens a driving socket on a free port and prints it, and the landmarks in `Landmarks.java` are
      already the names a script would use. One scripted run through the main path is worth more than several
      unit tests of the view.
- [ ] Held Shift and Control as intent (0.1's `Intents`): waits for a modifiers topic (framework-notes FN-9).
- [ ] In a window under about 1000 px wide at zoom 1, the list's NAME column shrinks to its icon while TYPE, SIZE and
      MODIFIED stay whole (framework-notes FN-8).
- [ ] `Picked` is a chip's place, resolved against the rail the explorer last sent. A listing that lands in the frame
      between reading a chip and clicking it can make the place name another chip.
- [ ] The rail's "today" is the day the folder was listed; a folder left open past midnight says "today" of the day
      before until it is listed again (M5's watching relists).
- [ ] The trash keeps an empty dated folder after a delete is undone, and is never emptied.
- [ ] Stop is tested in `OperationsTest` but not driven live: a run of a few files ends before a click lands.
- [ ] An undo refused because a run is going still moves the window's history, so the entry and the history disagree
      until the next Ctrl+Y. A `Change.check()` that asks whether a run goes would refuse it in place.
- [ ] *Choose folder…* is tested in `ExplorerTest` but not driven live: the system's picker is a real dialog.
