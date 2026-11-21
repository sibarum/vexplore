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
- [ ] Two Folders' check (`CheckSame`) fills its bar by pairs read, not bytes, so two large copies move it in
      halves. `Checking` would carry bytes as `Progress` does.
- [ ] A Stop waits for the step under way, so a large file copying or zipping finishes first. `Operations`
      could read the interrupt between chunks and take the half-written file away.
- [ ] Places on Linux are the Windows names under home; 0.1 read the XDG user directories (`ListPlaces`).
- [ ] A list row's accessible name leaves out the file name (`row "FOLDER Today 22:16"`), so a script cannot
      find a row by name. The name cell is an icon and text in a row box; find out what the table names a row from.
- [ ] Automation coverage: `--automation=0` (or `-Dautomation=0`) opens a driving socket on a free port and prints it, and the landmarks in `Landmarks.java` are
      already the names a script would use. One scripted run through the main path is worth more than several
      unit tests of the view.
- [ ] **Two Folders: NAME is cut to four or five letters in both lists** at 1440 px (FN-8). Slice 3 of M6 hid the
      places in Two Folders, as Orbit's board does, which gave NAME about 40 px back; the rail's third of the width
      and TYPE, SIZE and MODIFIED keep the rest. Waits on FN-8, or on the gallery, which has no columns.
- [ ] **A pane's own Back is always enabled in Two Folders**, and does nothing for a pane with nowhere to go back to
      (but uses it). `Pair.back` says only whether the list used has somewhere; one per side would need `Pair` to
      grow.
- [ ] In a window under about 1000 px wide at zoom 1, the list's NAME column shrinks to its icon while TYPE, SIZE and
      MODIFIED stay whole (framework-notes FN-8).
- [ ] `Picked` is a chip's place, resolved against the rail the explorer last sent. A listing that lands in the frame
      between reading a chip and clicking it can make the place name another chip.
- [ ] The rail's "today" is the day the folder was listed; a folder left open past midnight says "today" of the day
      before until it is listed again (M5's watching relists).
- [ ] The trash keeps an empty dated folder after a delete is undone, and is never emptied.
- [ ] Stop is tested in `OperationsTest` but not driven live: a run of a few files ends before a click lands. Nor is a
      Stop holding the queue (M9), tested in `ExplorerTest` only.
- [ ] An undo refused because a run is going still moves the window's history, so the entry and the history disagree
      until the next Ctrl+Y. A `Change.check()` that asks whether a run goes would refuse it in place.
- [ ] *Choose folder…* is tested in `ExplorerTest` but not driven live: the system's picker is a real dialog.
- [ ] **Ctrl+Z does not take a waiting item off the queue** (M9): its × does. The window's history holds journal entries
      only; an entry per item waiting, dropped when it starts, would need the history to drop an entry.
- [ ] **A ghost cannot be acted on** (M9): a file only queued to arrive cannot itself be moved, copied or renamed in the
      queue. `Projection` would apply an item's steps on the ghosts as on the rows there.
- [ ] **The queue does not watch the disk** (M9): an item whose files changed or went since it was queued is not
      flagged before it runs; it meets the change as any run does, step by step. `WatchFolder`'s stirs could mark it.
- [ ] A plan with files left alone because the queue takes them keeps the whole plan's `bytes` (`Projection.claim`).
      The run counts its own size, so only a card could say the wrong total.
- [ ] Seen once under `ottermate`: a second Ctrl+L, a file's path typed and Enter, left the earlier file selected.
      Ctrl+A before typing did not show it again. Not looked into.
