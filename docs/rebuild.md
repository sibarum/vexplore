# The rebuild

How Vexplore comes back on VexelRay 0.2, proposed before any of it is built. The design
([design/README.md](design/README.md), [HANDOFF.md](design/HANDOFF.md)) is still the requirement and is not reopened
here. 0.1's README and code (`git show 47d0016:README.md`) say what 0.1 did, and its port inventory
(`git show 9ea1c65:docs/framework-notes.md`, *The v0.2 port inventory*) says which APIs went. This file says what the
0.2 version is made of, in what order it comes back, and what it waits on.

The decisions marked **(ask)** are the author's. Everything else is a proposal to be corrected.

## The rule for the port

0.1 is not ported. The project is the template, and each feature comes back as a slice: a change from the model to
the screen, with its test and its `ottermate` check, one at a time, as Rasterware's did. Code from history is copied only
where it has no GUI and no threading in it: `files/` (`Entry`, `Kind`, `Names`, `Destinations`), `suggest/`
(`Suggestions`, `Intents`, `Order`, `Dates`, `Bytes`) and `ops/Plan` are pure functions over values, tested without a
window, and can come back almost as they were. `Model`, `Browser`, `Previewer`, `Destinator`, `Actor`, `Ui`,
`RailView`, `Dock` and `Lens` are rewritten: each existed to move work off the frame loop and back by hand, which is
now the framework's.

## What it is made of

**`-core`, the explorer.** The template's `Editor` becomes `Explorer`: one immutable `State`, changed only by
declared steps, the shape the template already has. 0.1's `Doc` is most of it:

| Field | What it holds |
| --- | --- |
| `folder`, `rows`, `listing` | the folder shown, its rows, and the listing in flight (a stale reply is dropped by comparing it) |
| `selected` | the paths selected in the list, as `Table.SELECTED` last said |
| `pick` | the rail's Select / Condition / Action so far, and the destination |
| `plan` | what the chosen action would do, as the card states it, or nothing |
| `running`, `done` | the operation in flight, and the journal of what can be undone |
| `marks` | named sets of paths kept for later |
| `preview` | what the dock shows for the single selection |
| `git`, `fresh` | git's word on the folder, and which rows landed in the last few seconds |

What the rail suggests is computed in a step from the state (`Suggestions` is pure and fast at hundreds of rows), not
in a job. Measure it at fifty thousand before deciding otherwise.

**The disk, as jobs and sources.**

| Part | Kind | Does |
| --- | --- | --- |
| `ListFolder` | `@Job` | the template's, widened: a folder's rows with size, times and kind; also answers `TreeView.CHILDREN_WANTED` |
| `PlanAction` | `@Job` | reads the destination to say what an action would do, collide with, or leave alone |
| `RunAction` | `@Source` | carries out a plan, sends progress as it goes, stops on a stop message; its reply is the journal entry |
| `Undo` | `@Job` | reverses a journal entry |
| `ReadPreview` | `@Job` | the dock's tiers: bytes, text, and a still picture as a `Feed`, as Rasterware's `ReadPicture` does |
| `WatchFolder` | `@Source` | one per shown folder, a `WatchService` telling the explorer what arrived |
| `GitStatus` | `@Job` | `vex-suite-common-git`'s `Git.status`, crossed as `Status.text()` |
| `Launch` | `@Job` | finds Rasterware or the IDE through `Apps.find` and starts it, or the shell's own open |
| `ReadSession` / `WriteSession` | `@Job` | the template's: the window, the last folder, the marks |

A copy is long and wants a cancel, which a `@Job` cannot give: that is why `RunAction` is a `@Source`.

**`-gui`, the view.** `Ui` lays out the handoff's regions: top bar (`Breadcrumb`), tree (`TreeView`), file list
(`Table`), rail, dock (under the list, in a `SplitPane`), status bar (`StatusBar`). The rail and the dock are
Vexplore's own, as in 0.1. The view hears widget topics and tells the explorer; it decides nothing. The template's
tabs, `Buffer`, `Markdown` and text-file jobs go: text is the IDE's, as it was in 0.1.

## The order

Each milestone ends with its retrospective section, as `CLAUDE.md` asks. Proposed to follow 0.1's plan, with 0.1's
first two milestones split so the window is useful early. **(ask)** whether this order stands.

**M0. The template becomes an explorer.** Delete the editor (tabs, buffers, Markdown, the text jobs); the layout's
regions in place and empty; `Look` on the handoff's colours (teal is selection, amber is Vexplore suggesting),
`Type` on Instrument Sans and IBM Plex Mono. `Messages` and `Recipes` change shape here, so this slice is proposed in
code before it is made.

**M1. Browsing, read only.** The tree of the user's folders and drives (`TreeView`, lazy), the breadcrumb, the list
with its columns, sort and range selection, the status line. Enter opens: a folder navigates, a file goes to `Launch`.
A path on the command line opens that folder, or the folder with that file selected.

**M2. The rail states its effect.** Select, Condition, Action as chips; the rows a suggestion would reach shown with
`ListView.marks` (marked, never selected); the card at the bottom that says what would happen first. No action runs
yet.

**M3. Actions.** Move, Copy, Archive, Delete into Vexplore's trash, Rename one, Mark. Planned by `PlanAction`, run by
`RunAction` with a count and a cancel in the rail, undone by Ctrl+Z through a `History` whose changes send messages
(the ruled pattern; `ValueWidgetUndoTest` is the example). Suggested destinations, and `PickFile.folder` behind
*Choose folder…*. Marks kept across runs through the session jobs, which 0.1 never did.

**M4. The Preview Dock and the row menu.** Tier 4 (hex, strings, entropy) first, since it is the floor, then text,
then still images. The row menu through `ListView.action` / `CHOSEN`. A picture's hand-off goes to Rasterware.

**M5. Fresh files.** `WatchFolder`; a row that landed in the last few seconds tagged *new* in amber, never selected.

**M6. Two Folders and the drag.** Two lists sharing a `PayloadType`, a drop between them through `ListView.LANDED`,
copied or moved by drive and key, undoable. Then the design's part 0.1 never built: a status per row and suggestions
from the comparison, with the rail beside both lists rather than swapped out. **(ask)** what "identical" means:
size and time, or a hash.

**M7. Git.** The GIT column and the branch in the status line, asked after the listing so it never holds the rows up.

**M8. Remote folders.** SFTP through the person's own `ssh`, as 0.1. One connection per host is what `@Destination`
was ruled for: a part that is both `@Source` (what the server answers) and `@Destination` (what is asked of it), keyed
by host. **(ask)** whether this comes back at all in the rebuild, or waits for the providers idea
([ideas/providers.md](ideas/providers.md)).

**Last. Packaging.** The native builds, the ffmpeg-record zip, `installer.json`'s `appId`, the scenes in
`docs/scenes` rewritten against the new landmarks.

## What it waits on

Vexplore is the named witness for several of these: each gets an FN entry the moment it is worked around, and is
taken to the framework rather than worked around for long.

| Need | Where 0.2 stands | Until then |
| --- | --- | --- |
| **Held Shift / Ctrl as intent** (the design's second principle) | `Seams.modifiers()` is the widget library's; the roadmap gives an application a state topic once a witness asks | Vexplore asks, at M2. Suggestions work from the selection alone until it lands |
| **A drop onto a tree row** | `TreeView` takes only its own items; no declared drop zone (roadmap: "Vexplore is that witness") | drops between lists only (M6) |
| **Chips that wrap** | no wrap in `FlexLayout`; `Node.wrap` planned and paused | two chips to a row, counted, as 0.1 did |
| **Progress** | no widget | a count in the rail's notice line, from `RunAction` |
| **Greying a row menu's action per row** | trees have it, lists do not | an action that would do nothing says so on the card |
| **Video, animated images, the suite's viewer** | the codec's players wait on the frame contract's step 5; `PictureViewer` and `Filmstrip` are out of vex-suite-common's build | the first frame only; a picture opens in Rasterware, and there is no viewer of Vexplore's own **(ask)**: bring it back when the suite's is ported, or leave viewing to Rasterware for good |
| **A marked tree row** (destination counts, screen 02) | the tree has ticks, not fills | the counts on the rail only |

## Questions for the author

1. Does the order above stand, or should Two Folders or previews come earlier?
2. Remote folders in the rebuild, or after providers?
3. Vexplore's own image viewer: back when the suite's is ported, or Rasterware only?
4. "Identical" in Two Folders: size and time, or content?
5. M0 changes `Messages`, `Recipes` and the state's shape at once. Propose it as one change, or the deletion first
   and the new shape after?
