# Vexplore

A file explorer that suggests instead of asking. Built on [vexelray-gui](../vexelray-gui) through
[vexelray-framework](../vexelray-framework), and deliberately a **framework witness**: every gap it finds goes into
the framework's own `docs/TODO.md`, and the reusable parts go into `vexelray-gui-widget` rather than staying here.

- **The design** — [docs/design/README.md](docs/design/README.md) holds the principles and they are the
  requirements; [HANDOFF.md](docs/design/HANDOFF.md) holds the numbers; the mockups only show one way of drawing them.
- **What is needed** — [docs/components.md](docs/components.md): which GUI components the interface needs, which
  existed, which were added, which are still missing.
- **What is not done** — [docs/TODO.md](docs/TODO.md).
- **What the framework did to us** — [docs/framework-notes.md](docs/framework-notes.md), and the framework-side
  account in `vexelray-framework/docs/TODO.md`, *Found by Vexplore*.

![Screen 01: select, condition, action](docs/screenshots/01-select-condition-action.png)

*Screen 01, from `docs/scenes/01-select.txt`: a click on one video, a click on "All videos here", a click on
"Move to…". The nine rows a suggestion would reach are marked, not selected; the card states the effect first.*

## Status

**Milestone 1 — read-only browsing, the rail stating its effect.** Done.
**Milestone 2 — actions and modifier keys.** Done, with the exceptions in the table.

| Works | Not yet |
| --- | --- |
| Tree of the user's folders and drives, lazy | Rename (the chip is there, disabled) |
| Breadcrumb, sortable resizable file list, range selection | Marks do not survive a restart |
| Suggestion Rail: **Select**, **Condition**, **Action**, and a card that states the effect first | The folder dialog has never been opened by a person (see TODO) |
| **Shift** held: where the range likely ends, in the order shown. **Control** held: the rule the picks are examples of. Same slot as the ordinary chips; ignoring them costs nothing | A modifier cannot be held from `ottermate`, so the live behaviour is checked by unit tests and `-Dvexplore.hold=` captures |
| **Move, Copy, Archive, Delete, Mark.** Each plans first (counts, bytes, what would be left alone and why), runs off the frame loop, and can be undone with Ctrl+Z or the Undo button | Drag and drop, fresh-file highlighting |
| Suggested destinations: the neighbouring folder that already holds that kind | A history of destinations |
| Delete goes to Vexplore's own trash (`~/.vexplore/trash`), so it is recoverable | Inline row previews, preview tiers 1 and 2, Two Folders |
| Preview Dock: text (tier 3), bytes (tier 4) | Any test of the layout itself |
| 54 tests, none of which needs a window | |

Everything the design says a screenshot cannot show holds: nothing covers anything else, the rail is a fixed
column that may be empty, a suggestion arriving or leaving moves no row, ignoring a suggestion costs nothing, and
every action states what it will affect before it runs and is recoverable.

## Run it

```
mvn compile exec:exec
mvn compile exec:exec -Dapp.jvmArgs=-Dvexplore.folder=C:/Users/me/Downloads
mvn test
```

Needs the stack installed to the local Maven repository first, in the order in
[vexelray-gui/CLAUDE.md](../vexelray-gui/CLAUDE.md). `exec:exec` rather than `exec:java` so native access can be
enabled for Panama.

Startup properties, for a known state (see `Startup`): `vexplore.folder`, `vexplore.select` (comma-separated
names), `vexplore.scope`, `vexplore.act`, `vexplore.hold`, `vexplore.trash`, and `vexplore.sync=true` to list on the calling thread.

A path on the command line wins over `vexplore.folder`: `vexplore <folder>` opens it, and `vexplore <file>` opens the
folder it is in with that file selected. That is how the suite's other apps say "show this here".

## Opening a file, and the suite

Enter or a double-click on a file opens it. Text and source (whatever the Preview Dock shows as text) go to the
suite's text editor, started as `text-editor <file>` in a new window; everything else, and text when the editor is
not installed, goes to the shell's own open, as in Explorer. The dock's header has *Open in Vex* for a text
file, and says *Vex not installed* instead when it is not. The editor is found through its install record
(`vexelray-installer`, read by the framework's `Apps`), so it has to be installed; a run from the checkout still
finds an installed editor.


### Looking at it without a person

A folder that looks like the first mockup's Downloads, and scripts that drive the real window through the
framework's automation socket. Every control a scene needs has a landmark (`act.move`, `go`, `undo`, `dest.1`), so a
scene does not depend on where anything is drawn:

```
sh docs/scenes/fixture.sh
java -jar ../vexelray-gui/vexelray-gui-automation-cli/target/vexelray-gui-automation-cli-0.1.0-SNAPSHOT.jar \
     --script docs/scenes/02-move.txt --launch mvn.cmd -q exec:exec -Dautomation=0 \
     "-Dapp.jvmArgs=-Dvexplore.folder=target/fixture/Downloads -Dvexplore.trash=target/fixture/trash"
```

`01-select` is the first mockup; `02-move` moves nine videos to the folder that already holds videos and then undoes
it; `03-delete-and-mark` does the same for a delete and keeps a mark. A scene never sleeps: every document change
writes the status line last, and the card's primary button names what it is about to do, so a scene `await`s those —
see FN-4 in [framework-notes.md](docs/framework-notes.md) for why `settle` is not enough. A headless still, with no
window, is `-Dapp.args="--capture out.png"` (use `C:/...` paths, not `/c/...`); `-Dvexplore.hold=shift` or `control`
puts the model as though that key were down, which `ottermate` cannot do.

## How it is put together

```
files/     the file system, and nothing else. Entry, Kind, Folders (the only reader), Preview, Previews,
           Destinations. No GUI. Blocking: callers run these on Gui.offload().
suggest/   the rail's brain, as pure functions: Suggestions (Select/Condition/Action), Intents (what Shift and
           Control are announcing), Order (the sort, shared with the table). Tested against a folder that is a
           list of values.
ops/       Plan (what an action would do, a value the card can draw) and Operations (doing it, and undoing it).
           Tested against real temporary folders.
Model      the one state (Doc: input + work + the rest), the only way to change it, and an onChange that never
           delivers an older document after a newer one.
Browser    navigate + list off the frame loop; a listing for a folder you have left is dropped.
Previewer  keeps the dock matching the selection, same rule. Destinator does the same for destinations.
Actor      the one place a button becomes a change to the disk: run, undo, mark, choose a destination.
Chooser    the native folder dialog, built with the tree and given its window later (see Recipes).
Ui, RailView, Dock   the tree. Hold no application state, only a cache of what they last drew.
Look       colour: the design's hex values as palette anchors, and the roles the palette has no name for.
Recipes    what is built; the wiring is generated from it.
```

The rule the layout keeps is the design's: **nothing is drawn over anything else.** There is no popup, tooltip or
overlay anywhere in `Ui`. A change to the rail rewrites the rail's column and touches nothing else.

## Plan

1. **Milestone 1** — read-only, rail states its effect. *Done.*
2. **Milestone 2 — actions, and modifier keys as intent signals.** *Done* except Rename, persistence of marks,
   and having watched a person use the folder dialog.
3. **Milestone 3 — the drag fallback and fresh files.** Drag a row, the *Carrying* card, destinations with reasons;
   a five-second-old file highlighted amber.
4. **Milestone 4 — previews up the ladder.** Inline row previews, CSV/JSON/archive, images, PDF, pop-out.
5. **Milestone 5 — Two Folders.** The comparison engine and its open question about "identical".

Priorities are the author's to move. The design is a set of principles, and where a mockup and a principle
disagree the principle wins.

## Reading order for a newcomer

`docs/design/README.md` → `docs/components.md` → `suggest/Suggestions.java` (the whole idea, in 200 lines with no
GUI in it) → `Ui.java` → `docs/framework-notes.md`.

## Native builds

Windows, GraalVM 25 as `JAVA_HOME`, from a Visual Studio developer prompt (or after `vcvars64.bat`) so `link.exe` is
MSVC's and not Git Bash's. Two profiles build the same code as two editions:

```
mvn -Pnative-release package -DskipTests   # target/vexplore.exe        what ships and is signed
mvn -Pnative package -DskipTests           # target/vexplore-debug.exe  for ottermate
```

- **release** (`installer.json` points at this one): linked as a Windows GUI subsystem program, so no console window
  ever appears, and built without the automation module: the source root `src/edition-release` is compiled instead
  of `src/edition-debug` and `vexelray-*-automation` is not on its classpath, so the binary cannot open a driving
  socket (`--automation` is accepted and does nothing). stdout and stderr go nowhere; the log files are still written.
- **debug**: console subsystem, automation present: `vexplore-debug.exe --automation=0` prints
  `automation: localhost:<port>` for `ottermate --launch`.

The plain JVM build, tests and `exec:exec` are the debug edition. The linker options are
`/SUBSYSTEM:WINDOWS|CONSOLE` and `/ENTRY:mainCRTStartup` (pom, `pluginManagement`). Both profiles also link
`src/main/rc/vexplore.rc`, the executable's icon (the `vexplore` mark from `vexelray-icons`), compiled by `rc.exe`.
