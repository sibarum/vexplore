# TODO

Work on **Vexplore** that is known about and not done. Distinct from
[framework-notes.md](framework-notes.md), which is about the framework rather than about this application —
if the fix belongs upstream, it goes there instead.

Keep an entry short enough that it does not need editing, and delete it when it is done rather than ticking it.

## Next

The v0.2 rebuild starts from the template, and 0.1's features come back one slice at a time, in the order in
[rebuild.md](rebuild.md). M0 and M1 are done.

- [ ] **M2: the rail states its effect**, as [rebuild.md](rebuild.md) has it. The rail goes back in a split beside
      the list, and FN-2 with it: decide what the list keeps in a narrow window before the rail takes 25 rem.
- [ ] **M3 to M7** and packaging, as [rebuild.md](rebuild.md) lists them.

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
