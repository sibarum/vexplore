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

**Milestone 1 — read-only browsing, and the rail stating its effect.** It reads real folders, and it does not
change any of them yet.

| Works | Not yet |
| --- | --- |
| Tree of the user's folders and drives, lazy | Any action: the rail's buttons are disabled and say so |
| Breadcrumb, sortable resizable file list, range selection | Modifier keys as intent (`Gui.modifiers()` is not read) |
| Suggestion Rail: **Select** (this file, all of this kind, big ones, same day), **Condition**, **Action** | Suggested destinations, drag and drop |
| The rows a suggestion would reach are *marked*, and the card states file count and size first | Fresh-file highlighting, inline row previews |
| Preview Dock: text (tier 3) and bytes (tier 4: hex, strings, entropy) | Tiers 1 and 2 (images, PDF, CSV, JSON, archives) |
| Draggable dividers; a status bar that never shifts | Two Folders mode, pop-out |
| 29 tests, none of which needs a window | Any test of the layout itself |

Everything the design says a screenshot cannot show holds today: nothing covers anything else, the rail is a fixed
column that may be empty, a suggestion arriving or leaving moves no row, and ignoring a suggestion costs nothing
(the selection is what it was).

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
names), `vexplore.scope`, `vexplore.act`, and `vexplore.sync=true` to list on the calling thread.

### Looking at it without a person

A folder that looks like the first mockup's Downloads, and a script that drives the real window through the
framework's automation socket:

```
sh docs/scenes/fixture.sh
java -jar ../vexelray-gui/vexelray-gui-automation-cli/target/vexelray-gui-automation-cli-0.1.0-SNAPSHOT.jar \
     --script docs/scenes/01-select.txt --launch mvn.cmd -q exec:exec -Dautomation=0 \
     "-Dapp.jvmArgs=-Dvexplore.folder=target/fixture/Downloads"
```

A scene never sleeps. Every document change writes the status line's last slot, and a scene `await`s that — see
FN-4 in [framework-notes.md](docs/framework-notes.md) for why `settle` is not enough. A headless still, with no
window, is `-Dapp.args="--capture out.png"` (use `C:/...` paths, not `/c/...`).

## How it is put together

```
files/     the file system, and nothing else. Entry, Kind, Folders (the only reader), Preview, Previews.
           No GUI. Blocking: callers run these on Gui.offload().
suggest/   the rail's brain as a pure function: Suggestions.of(entries, selected, pick, now, zone) -> Rail.
           Tested against a folder that is a list of values.
Model      the one state (Doc), the only way to change it, and an onChange that never delivers an older
           document after a newer one.
Browser    navigate + list off the frame loop; a listing for a folder you have left is dropped.
Previewer  keeps the dock matching the selection, same rule.
Ui, Dock   the tree. Hold no application state, only a cache of what they last drew.
Look       colour: the design's hex values as palette anchors, and the roles the palette has no name for.
Recipes    what is built; the wiring is generated from it.
```

The rule the layout keeps is the design's: **nothing is drawn over anything else.** There is no popup, tooltip or
overlay anywhere in `Ui`. A change to the rail rewrites the rail's column and touches nothing else.

## Plan

1. **Milestone 1** — read-only, rail states its effect. *Done.*
2. **Milestone 2 — actions, and the design's centre of gravity.** Mark, Copy, Move, Delete through the Recycle Bin,
   each stating its effect first and each undoable from a journal; then **modifier keys as intent signals**, which
   is what makes it Vexplore and not a file list with a side panel.
3. **Milestone 3 — the drag fallback and fresh files.** Drag a row, the *Carrying* card, destinations with reasons;
   a five-second-old file highlighted amber.
4. **Milestone 4 — previews up the ladder.** Inline row previews, CSV/JSON/archive, images, PDF, pop-out.
5. **Milestone 5 — Two Folders.** The comparison engine and its open question about "identical".

Priorities are the author's to move. The design is a set of principles, and where a mockup and a principle
disagree the principle wins.

## Reading order for a newcomer

`docs/design/README.md` → `docs/components.md` → `suggest/Suggestions.java` (the whole idea, in 200 lines with no
GUI in it) → `Ui.java` → `docs/framework-notes.md`.
