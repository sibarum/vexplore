# The shape, for M0's second step

What [rebuild.md](rebuild.md)'s M0 builds once the editor is gone: the controller's state, the messages M1 needs,
the parts the wiring builds, and the window's regions. A sketch to agree before code is written against it, as
`CLAUDE.md` asks of anything that touches `Messages`, `Recipes`, a lane or the state. Names are proposals.

M0's second step built the controller and the regions, empty; M1 adds the messages below and fills them. So this is
sized for M1 and stops there. M2 and later add fields and records; nothing here should have to change shape for them.

## The controller: `Explorer`

`Editor` is renamed `Explorer`. Same pattern: one `State` on a `Ledger`, static steps, a `@Component` on the logic
lane that takes an `Inbox`.

```java
record State(
    Optional<Path> folder,       // the folder shown
    long asked,                  // the listing in flight: its ticket; a reply with another is stale and dropped
    Optional<Path> select,       // what to select when that listing lands (a file named on the command line)
    List<Row> rows,              // the folder's rows, as listed
    Set<Path> selected,          // as the list last said
    Optional<Path> started, boolean armed, Optional<Remember> last)   // the session, as now
```

`rows` is in the state because M2's suggestions are computed from it in a step. The order the list shows is the
table's, not the state's, until M2 needs it (Shift reaches along the order shown).

## The messages M1 adds

The view asks the explorer:

| Record | Sent when |
| --- | --- |
| `Go(String text)` | the path bar is submitted (kept) |
| `Navigate(Path folder)` | the breadcrumb, a tree row, Enter on a folder row, Backspace or Alt+Up |
| `Chosen(Set<Path> paths)` | `Table.SELECTED`, rows turned back into paths by the view |
| `Activated(Path path)` | Enter or a double click on a row |

The explorer tells the view:

| Record | Topic | Says |
| --- | --- | --- |
| `Shown(Path folder, List<Row> rows, Optional<Path> select)` | state | what the list shows; the breadcrumb is the folder's own parts |
| `Summary(int items, int selected, long bytes)` | state | the status line's counts; the view words them |
| `Status(String message, boolean alert)` | edge | kept |

The explorer and its jobs:

| Request → reply | Job | Does |
| --- | --- | --- |
| `List(long ticket, Path folder)` → `Listed(long ticket, Path folder, List<Row> rows, String problem)` | `ListFolder` | a folder's rows; a folder it cannot read is a `problem`, never a throw |
| `Find` → `Found` | `FindPath` | kept |
| `Start(Path file)` → `Started(Path file, String problem)` | `Launch` | the shell's own open, for M1; Rasterware and the IDE through `Apps.find` with M4 |
| `Recall` → `Recalled`, `Remember` | the session jobs | kept |

`Row(Path path, String name, boolean folder, long size, Instant modified, Kind kind)` is 0.1's `files/Entry` with its
name and kind computed once in the job, so nothing on the GUI thread works them out per frame. `Kind` comes back from
0.1's `files/Kind` (folder, video, image, audio, document, text, code, archive, program, other).

The tree is the view's and its job's, as the template's navigator is, and never the explorer's: it asks
`TreeView.CHILDREN_WANTED`, the view asks `ListPlaces` (the top: the person's folders, then the drives with their
labels) or `ListLevel` (a folder's subfolders), and answers `TreeView.CHILDREN`. A tree row clicked is a `Navigate`.

## What the wiring builds

`Core` lists `Explorer` and the jobs `ListFolder`, `ListPlaces`, `ListLevel`, `FindPath`, `Launch`, `ReadSession`,
`WriteSession`. `Recipes` is unchanged: `look()` and `ui(gui, titleBar)`. `View`'s constructor takes one `Sender` per
record it sends.

## The window

`Ui` builds the handoff's regions, at its reference sizes and in its colours:

```
title bar (the framework's)
top bar     Breadcrumb, 48 px                            | sort note
tree 250    | file list, header 32, rows 34              | Suggestion Rail 400 (empty in M0 and M1)
            | Preview Dock under the list (none until M4)
status bar, 30 px: items · selected · size               | message
```

Three `SplitPane`s: the tree beside the rest, the list beside the rail, and (from M4) the dock under the list. The
template's `Navigator` goes: its path bar becomes Ctrl+L on the top bar (below), its tree becomes the places tree.

`Look` takes the Vexplore design system's tokens, not the handoff's: 0.1 found the design system came after the
handoff and replaced its teal with violet (VexelRay IDE owns cyan in the suite), and measured the ladder against it
(0.1's FN-6). The anchors are the suite's `SuiteLook`, copied while `vex-suite-common` is out of the v0.2 build; the
surfaces between rungs (chrome, the list's `bg-1`, the rail) are roles of Vexplore's own, and `LookTest` pins where
the ladder lands.

The regions are rem, the handoff's pixels over its 16 px root, so they grow with the zoom as their text does. A
narrow window at a high zoom gives the rail its 25 rem and the list what is left (framework-notes FN-2).

## The author's rulings

All of 2026-11-01, and built in M0: Ctrl+L turns the breadcrumb into a field in its place, and Esc or Enter puts it
back; the atlas's faces, with the design's two asked for upstream (framework-notes FN-1); `ListLevel` for the tree
and `ListFolder` for the list, kept apart (M1).
